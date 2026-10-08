/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.ragdoll;

import com.armorberserk.daotcompat.sable.SableBridge;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side world position of the ragdoll body, for the telemetry snapshot.
 *
 * <p>Since v2.0.0 the ragdoll runs UNSEATED — there is no seat entity to project any more — so
 * the tracker picks the sub-level whose world-space origin is closest to the player (the body
 * is BODY_SYNCed to ride the player's trajectory, so "nearest to the player" is the ragdoll).
 * Wrong sub-levels project millions of blocks away, same proximity logic as recoverPlotFrame.
 */
public final class RagdollWorldTracker {

    // The body rides the player; the sync can lag a few ticks behind a fast swing.
    private static final double MAX_DISTANCE_SQR = 300.0D * 300.0D;

    private RagdollWorldTracker() {}

    /**
     * The ragdoll body's approximate world position, or null when no sub-level projects
     * anywhere near the player.
     */
    @Nullable
    public static Vec3 bodyWorldPos(@Nullable Level level, Vec3 playerPos) {
        if (level == null) return null;
        Vec3 best = null;
        double bestDist = Double.MAX_VALUE;
        for (SubLevel sl : SableBridge.getAllSubLevels(level)) {
            if (sl == null || sl.isRemoved()) continue;
            Vec3 world;
            try {
                world = sl.logicalPose().transformPosition(Vec3.ZERO);
            } catch (Throwable t) {
                continue;
            }
            if (world == null || !Double.isFinite(world.x) || !Double.isFinite(world.y)
                    || !Double.isFinite(world.z)) continue;
            double d = world.distanceToSqr(playerPos);
            if (d < MAX_DISTANCE_SQR && d < bestDist) {
                best = world;
                bestDist = d;
            }
        }
        return best;
    }
}
