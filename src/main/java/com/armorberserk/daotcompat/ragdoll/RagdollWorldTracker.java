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
 * Client-side projection of the ragdoll body into world space.
 *
 * <p>Live-telemetry finding (09.10): while ragdolled the player rides a {@code ragdoll_seat}
 * entity whose raw position is in the ragdoll sub-level's PLOT space (~20M blocks away), while
 * the player entity itself stays a separate invisible body in world space — the body AOT ropes
 * and the F5 camera hang off. This tracker projects the seat's plot position back through the
 * sub-level pose, giving "where the ragdoll body actually is, in the world, right now" — the
 * reference point for the telemetry snapshot and the optional body-glue.
 *
 * <p>Selection follows the proven recoverPlotFrame pattern: only the sub-level whose transform
 * lands the seat next to the player is the right one — wrong parts/ships project it millions of
 * blocks away.
 */
public final class RagdollWorldTracker {

    // The player entity is server-synced to the ragdoll, so the correct projection is near it.
    // Generous margin: the sync can lag a few ticks behind a fast-tumbling ragdoll.
    private static final double MAX_DISTANCE_SQR = 300.0D * 300.0D;

    private RagdollWorldTracker() {}

    /**
     * The seat's world-space position on the ragdoll body, or null when the player is not
     * riding a plot-space seat / no sub-level projects it anywhere near the player.
     */
    @Nullable
    public static Vec3 seatWorldPos(@Nullable Level level, @Nullable Vec3 seatPos, Vec3 playerPos) {
        if (level == null || seatPos == null) return null;
        Vec3 best = null;
        double bestDist = Double.MAX_VALUE;
        for (SubLevel sl : SableBridge.getAllSubLevels(level)) {
            if (sl == null || sl.isRemoved()) continue;
            Vec3 world;
            try {
                world = sl.logicalPose().transformPosition(seatPos);
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
