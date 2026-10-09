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
 * <p>Since v2.0.0 the ragdoll runs UNSEATED — there is no seat entity to project — so the
 * tracker picks the sub-level whose world-space bounding box center is closest to the player
 * (the body is BODY_SYNCed to ride the player's trajectory, so "nearest to the player" is the
 * ragdoll). Bounding boxes are world-space (proven by the hook anchoring via
 * queryIntersecting); NEVER project the pose origin — ragdoll parts live at ±20M plot
 * coordinates, which produced the v1.2.9 void-fling garbage.
 */
public final class RagdollWorldTracker {

    // The body rides the player; the sync can lag a few ticks behind a fast swing.
    private static final double MAX_DISTANCE_SQR = 300.0D * 300.0D;

    private RagdollWorldTracker() {}

    /**
     * The ragdoll body's approximate world position (bbox center), or null when no sub-level
     * is anywhere near the player.
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
                var bounds = sl.boundingBox();
                if (bounds == null) continue;
                world = new Vec3(
                        (bounds.minX() + bounds.maxX()) * 0.5D,
                        (bounds.minY() + bounds.maxY()) * 0.5D,
                        (bounds.minZ() + bounds.maxZ()) * 0.5D);
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

