/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.collision;

import com.armorberserk.daotcompat.DAOTCompat;
import com.armorberserk.daotcompat.sable.SableBridge;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * Defensive safety net against the local player tunnelling through a Sable sub-level (airship) at
 * high speed — the "falls/clips straight through a physics object" bug, which shows up exactly with
 * AOT ODM-gear speeds.
 *
 * <h3>What the bug actually is (investigated, not guessed)</h3>
 * <p>Vanilla world collision never tunnels at normal speeds because {@code Entity.move()} feeds a
 * <em>swept</em> box — {@code Level.getCollisions(entity, box.expandTowards(deltaMovement))} — to a
 * continuous query that visits every block the box passes through. Sable's blocks are <b>not</b> in
 * {@code Level}'s normal collision path; they are injected via a {@code @Redirect} of
 * {@code Entity.collide(Vec3)} in
 * {@code dev.ryanhcode.sable.mixin.entity.entity_sublevel_collision.EntityMixin#sable$collideRedirect},
 * which calls {@code dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision#collide}.
 * That method is <em>discretely sub-stepped</em>, not continuously swept:
 * <ul>
 *   <li>For a non-player entity the sub-step count is
 *       {@code Math.min(10, Math.max(1, (int)(motion.length() / 0.015625)))} — hard-capped at 10
 *       ({@code SubLevelEntityCollision.java:145}).</li>
 *   <li>For the <b>local player</b> it is a <b>fixed 8</b>, independent of speed
 *       ({@code SubLevelEntityCollision.java:146-148}).</li>
 * </ul>
 * At each sub-step it does a static SAT overlap of the player OBB against the ship's block OBBs. At
 * ordinary speeds 8 samples cover the ~1.8-block-tall player box with overlap to spare, so nothing
 * is missed. At the high per-tick displacement AOT ODM gear produces, the fixed sample spacing
 * ({@code motion.length()/8}) grows past the player-box + deck thickness, so consecutive samples can
 * straddle a thin deck with no sample inside it — the classic discrete-sampling tunnel. It bites
 * sub-levels and not static terrain precisely because static terrain uses the continuous swept
 * query and sub-levels use this capped discrete one.
 *
 * <p>On top of that, {@code SubLevelEntityCollision.collide} does <b>no</b> block collision at all
 * for a {@code ServerPlayer} — it returns the motion unchanged and only trusts an already-set
 * "tracking" sub-level ({@code SubLevelEntityCollision.java:96-110}). Player-vs-sub-level collision
 * is therefore entirely client-side, which is why this guard is client-side and corrects only the
 * local player (we never touch server-authoritative state for entities we don't own).
 *
 * <p>Sable's own tracking field ({@code @Unique sable$trackingSubLevel}, exposed via the mixin
 * interface {@code EntityMovementExtension#sable$setTrackingSubLevel}) is the anchor Sable uses once
 * the player is <em>already</em> standing on a ship; it does nothing to widen the swept resolution
 * during the fast tick, so it is not the lever here.
 *
 * <h3>What this guard does</h3>
 * <p>Honest status: <b>a conservative mitigation, not a guaranteed root-cause fix.</b> We cannot
 * change Sable's sub-step count from our own mod without a mixin into Sable's classes (which the
 * project deliberately avoids — see {@link DAOTCompat} "Fix 1"). Instead, once per client tick, if
 * the local player's per-tick displacement is fast ({@code > FAST_THRESHOLD}) and there is a
 * sub-level near the movement segment at all, we re-walk the straight segment from the pre-tick
 * position to the post-tick position in {@code <= MAX_SUBSTEP}-block steps and check whether the
 * player box is embedded in solid sub-level geometry at any intermediate step. If it is (i.e. the
 * resolved path passed through a ship block that Sable's coarser sampling skipped), we clamp the
 * player back to the last embedding-free sub-step and kill the velocity component along the motion,
 * mirroring what vanilla swept collision would have done.
 *
 * <p>It is safe by construction: it only ever fires when a genuine intermediate sample is inside
 * solid geometry, so it cannot fight vanilla/Sable resolution that already worked (standing on a
 * deck leaves every sample on or above the surface, never embedded), and it does nothing away from
 * ships or at slow speed.
 */
public final class HighSpeedSubLevelGuard {

    /** Per-tick displacement (blocks) above which we bother doing the swept re-check. Below this,
     *  vanilla + Sable's 8 sub-steps are already dense enough — matches "fast" ODM movement. */
    private static final double FAST_THRESHOLD = 1.0D;
    private static final double FAST_THRESHOLD_SQR = FAST_THRESHOLD * FAST_THRESHOLD;

    /** Genuine teleports (portal, /tp, respawn) move much further in one tick and must not be
     *  treated as physics motion to be clamped. */
    private static final double TELEPORT_CUTOFF = 32.0D;

    /** Each re-check sub-step is at most this long, so the player box (~1.8 tall) always overlaps
     *  between consecutive samples — the density Sable's fixed 8 fails to guarantee at speed. */
    private static final double MAX_SUBSTEP = 0.9D;

    /** Keep sample points strictly inside the player box, and strictly inside a block's collision
     *  shape, so mere surface contact (feet resting on a deck) is never mistaken for embedding. */
    private static final double INSET = 1.0E-3D;

    /** Vertical spacing of the sample lattice inside the player box. */
    private static final double SAMPLE_STEP_Y = 0.4D;

    private HighSpeedSubLevelGuard() {}

    public static void tick(LocalPlayer player) {
        if (player == null) return;
        // Only ordinary, physics-driven local movement. Skip anything that isn't vanilla walking/
        // falling collision to begin with.
        if (player.isSpectator() || player.noPhysics || player.isPassenger()) return;

        Level level = player.level();
        if (level == null) return;

        Vec3 to = player.position();
        Vec3 from = new Vec3(player.xo, player.yo, player.zo); // position at the start of this tick
        Vec3 disp = to.subtract(from);
        double distSqr = disp.lengthSqr();
        if (distSqr < FAST_THRESHOLD_SQR) return;              // slow: Sable/vanilla already handled it
        double dist = Math.sqrt(distSqr);
        if (dist > TELEPORT_CUTOFF) return;                    // teleport, not a physics move

        // Cheap "is there a ship near this segment at all" gate. The box at the destination expanded
        // back to cover the origin, inflated by a block. Empty => ordinary terrain => do nothing.
        AABB destBox = player.getBoundingBox();
        AABB sweptBox = destBox.expandTowards(from.subtract(to)).inflate(1.0D);
        List<SubLevel> nearby = SableBridge.getIntersecting(level, sweptBox);
        if (nearby.isEmpty()) return;

        int steps = Math.max(1, (int) Math.ceil(dist / MAX_SUBSTEP));
        Vec3 lastFree = from;
        Vec3 clampTo = null;
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / (double) steps;
            Vec3 sample = from.add(disp.scale(t));
            if (isEmbedded(level, nearby, destBox, to, sample)) {
                clampTo = lastFree; // last sample known to be embedding-free (== from if i==1)
                break;
            }
            lastFree = sample;
        }
        if (clampTo == null) return; // straight path was clear at our resolution: nothing to correct

        // Clamp the player to just before the geometry it was about to pass through, and remove the
        // velocity component that carried it there (leave tangential/lateral momentum intact, like
        // vanilla per-axis collision).
        player.setPos(clampTo.x, clampTo.y, clampTo.z);
        Vec3 v = player.getDeltaMovement();
        if (dist > 1.0E-6) {
            Vec3 dir = disp.scale(1.0D / dist);
            double along = v.dot(dir);
            if (along > 0.0D) {
                player.setDeltaMovement(v.subtract(dir.scale(along)));
            }
        }
        // Pin the render/old position to the clamped point so the correction doesn't itself cause a
        // one-frame interpolation swing (same class of issue as the thunder-spear render fix).
        SableBridge.setOldPosNoMovement(player);

        DAOTCompat.LOGGER.debug(
                "[clip-guard] clamped local player from tunnelling a sub-level: {} -> {} (moved {} blocks/tick)",
                fmt(to), fmt(clampTo), String.format(java.util.Locale.ROOT, "%.2f", dist));
    }

    /**
     * True if the player's box, placed at {@code sample}, overlaps solid collision geometry of any
     * of the given sub-levels. Point-samples a lattice inside the box, transforms each point into
     * each sub-level's local frame (where its blocks physically live in {@code Level}, exactly as
     * Sable reads them in {@code EntityMixin#getInBlockState}), and tests strict containment against
     * the block's collision shape.
     */
    private static boolean isEmbedded(Level level, List<SubLevel> subs, AABB destBox, Vec3 to, Vec3 sample) {
        AABB box = destBox.move(sample.x - to.x, sample.y - to.y, sample.z - to.z);
        double cx = (box.minX + box.maxX) * 0.5D;
        double cz = (box.minZ + box.maxZ) * 0.5D;
        double[] xs = {box.minX + INSET, cx, box.maxX - INSET};
        double[] zs = {box.minZ + INSET, cz, box.maxZ - INSET};

        for (SubLevel sl : subs) {
            if (sl == null || sl.isRemoved()) continue;
            for (double py = box.minY + INSET; py <= box.maxY - INSET; py += SAMPLE_STEP_Y) {
                for (double px : xs) {
                    for (double pz : zs) {
                        if (pointInSolid(level, sl, px, py, pz)) return true;
                    }
                }
            }
            // Always test the very top of the box too, not just the last <=SAMPLE_STEP_Y row, so a
            // deck level with the player's head is never missed by the loop's step granularity.
            double topY = box.maxY - INSET;
            for (double px : xs) {
                for (double pz : zs) {
                    if (pointInSolid(level, sl, px, topY, pz)) return true;
                }
            }
        }
        return false;
    }

    private static boolean pointInSolid(Level level, SubLevel sl, double wx, double wy, double wz) {
        Vec3 local;
        try {
            local = sl.logicalPose().transformPositionInverse(new Vec3(wx, wy, wz));
        } catch (Throwable t) {
            return false;
        }
        if (!Double.isFinite(local.x) || !Double.isFinite(local.y) || !Double.isFinite(local.z)) {
            return false;
        }
        BlockPos bp = BlockPos.containing(local.x, local.y, local.z);
        BlockState state;
        VoxelShape shape;
        try {
            state = level.getBlockState(bp);
            if (state.isAir()) return false;
            shape = state.getCollisionShape(level, bp);
        } catch (Throwable t) {
            return false;
        }
        if (shape.isEmpty()) return false;

        double lx = local.x - bp.getX();
        double ly = local.y - bp.getY();
        double lz = local.z - bp.getZ();
        for (AABB s : shape.toAabbs()) {
            if (lx > s.minX + INSET && lx < s.maxX - INSET
                    && ly > s.minY + INSET && ly < s.maxY - INSET
                    && lz > s.minZ + INSET && lz < s.maxZ - INSET) {
                return true;
            }
        }
        return false;
    }

    private static String fmt(Vec3 v) {
        return v == null ? "null"
                : String.format(java.util.Locale.ROOT, "(%.2f, %.2f, %.2f)", v.x, v.y, v.z);
    }
}
