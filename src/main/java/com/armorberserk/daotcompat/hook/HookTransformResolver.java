/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.hook;

import com.armorberserk.daotcompat.DAOTCompat;
import com.armorberserk.daotcompat.aot.AOTReflect;
import com.armorberserk.daotcompat.sable.SableBridge;
import com.armorberserk.daotcompat.sable.SubLevelResolver;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Keeps a hook anchored to the sub-level block it grabbed. On the first tick we store the
 * grab point in the sub-level's local space; afterwards we re-project that point through the
 * sub-level's current pose so the hook rides along as the airship moves.
 */
public final class HookTransformResolver {

    // A real grab is within gear range. A reported point millions of blocks out means Sable
    // handed back a sub-level's internal plot coordinate, which we remap onto the airship.
    private static final double PLOT_FRAME_SQR = 1_000_000.0D;
    // How close a hook must stay to the player. Beyond this we let go instead of dragging
    // the player off into nowhere, and the same radius accepts a remapped attach point.
    //
    // Fix 3 (code review round 2): AOT's own maxHookDistance is roughly 250 blocks, so a plain
    // 256-block radius left only ~6 blocks of margin - not enough headroom for a fast-moving
    // Sable airship, where the player's and the ship's relative velocity can eat that margin in
    // a single client tick and falsely detach a perfectly legitimate rope. HOOK_RANGE_MARGIN
    // adds real slack on top of the expected max hook range without disabling the safety check.
    private static final double HOOK_RANGE_MARGIN = 64.0D;
    private static final double MATCH_RADIUS_SQR = Math.pow(250.0D + HOOK_RANGE_MARGIN, 2);
    // Below this the pose effectively did not change; skip the write to avoid feeding jitter.
    private static final double IDLE_SQR = 1.0E-6D;

    private HookTransformResolver() {}

    public static void process(@Nullable Level level, @Nullable Object hook) {
        if (level == null || hook == null || !AOTReflect.isAvailable()) return;

        // Only block hooks on a sub-level are our concern; clear tracking for anything else.
        if (!AOTReflect.isActive(hook) || AOTReflect.isOnEntity(hook)) {
            DynamicHookMap.put(hook, null);
            return;
        }

        Vec3 world = AOTReflect.getPosition(hook);
        if (world == null || notFinite(world)) return;

        DynamicHookData anchor = DynamicHookMap.get(hook);
        if (anchor == null) {
            attach(level, hook, world);
        } else {
            follow(level, hook, anchor, world);
        }
    }

    private static void attach(Level level, Object hook, Vec3 world) {
        SubLevel sl = SubLevelResolver.findContaining(level, world);
        if (sl != null) {
            UUID id = sl.getUniqueId();
            if (id == null) return;
            Vec3 local;
            try {
                local = sl.logicalPose().transformPositionInverse(world);
            } catch (Throwable t) {
                return;
            }
            if (notFinite(local)) return;
            DynamicHookMap.put(hook, new DynamicHookData(id, local, level.dimension()));
            return;
        }
        // Nothing at the reported point - it may already be a raw plot coordinate.
        recoverPlotFrame(level, hook, world);
    }

    /**
     * Sable sometimes reports a grab in a sub-level's plot space instead of world space, so
     * AOT pins the hook millions of blocks away. We treat the reported point as a local
     * coordinate and find the sub-level whose pose maps it back next to the player.
     */
    private static void recoverPlotFrame(Level level, Object hook, Vec3 reported) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        Vec3 eye = player.position();
        if (reported.distanceToSqr(eye) < PLOT_FRAME_SQR) return; // ordinary world hit

        for (SubLevel sl : SableBridge.getAllSubLevels(level)) {
            if (sl == null || sl.isRemoved()) continue;
            UUID id = sl.getUniqueId();
            if (id == null) continue;
            Vec3 visual;
            try {
                visual = sl.logicalPose().transformPosition(reported);
            } catch (Throwable t) {
                continue;
            }
            if (notFinite(visual) || visual.distanceToSqr(eye) > MATCH_RADIUS_SQR) continue;

            AOTReflect.setPosition(hook, visual);
            DynamicHookMap.put(hook, new DynamicHookData(id, reported, level.dimension()));
            return;
        }
    }

    private static void follow(Level level, Object hook, DynamicHookData anchor, Vec3 world) {
        // Guard against a stale anchor from a previous dimension. Sable UUID collisions across
        // dimensions are astronomically unlikely, but an explicit check makes the drop debuggable.
        if (!anchor.dimensionKey().equals(level.dimension())) {
            DAOTCompat.LOGGER.warn("[hook] dimension changed from {} to {}, releasing hook",
                    anchor.dimensionKey().location(), level.dimension().location());
            drop(hook);
            return;
        }
        SubLevel sl = SableBridge.getSubLevel(level, anchor.subLevelId());
        if (sl == null) {
            // [FIX v1.3.5] Improved error handling for Hook Sync on physics objects
            DAOTCompat.LOGGER.warn("[hook] sub-level not found (UUID: {}), releasing hook",
                    anchor.subLevelId());
            drop(hook);
            return;
        }
        Vec3 next;
        try {
            next = sl.logicalPose().transformPosition(anchor.localPosition());
        } catch (Throwable t) {
            DAOTCompat.LOGGER.error("[hook] failed to transform position", t);
            drop(hook);
            return;
        }
        if (notFinite(next)) {
            DAOTCompat.LOGGER.warn("[hook] transformed position is not finite");
            drop(hook);
            return;
        }
        if (world.distanceToSqr(next) < IDLE_SQR) return; // ship effectively idle

        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && next.distanceToSqr(player.position()) > MATCH_RADIUS_SQR) {
            drop(hook); // sub-level moved out of reach; let go instead of dragging the player
            return;
        }
        AOTReflect.setPosition(hook, next);
        DAOTCompat.LOGGER.debug("[hook] synchronized to moving sub-level at {}", next);
    }

    private static void drop(Object hook) {
        AOTReflect.release(hook);
        DynamicHookMap.put(hook, null);
    }

    private static boolean notFinite(Vec3 v) {
        return !Double.isFinite(v.x) || !Double.isFinite(v.y) || !Double.isFinite(v.z);
    }
}
