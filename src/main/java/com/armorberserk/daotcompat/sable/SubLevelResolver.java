/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.sable;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Finds the sub-level occupying a world point by probing Sable with a tiny box around it. */
public final class SubLevelResolver {

    private static final double PROBE = 0.05D;

    private SubLevelResolver() {}

    /**
     * Same as {@link #findContaining(Level, Vec3)} but with a "sticky" fast-path: if
     * {@code hint} is non-null and still contains {@code pos}, it is returned immediately
     * without a full {@code queryIntersecting} scan of every sub-level.
     *
     * <p><b>Fix 5 (code review round 2):</b> callers that already know which sub-level they were
     * anchored to last tick (e.g. a hook or spear that isn't moving between sub-levels, the
     * common case) can pass it as {@code hint} to skip the broad-phase scan entirely once the
     * geometry check confirms the point is still inside it.
     */
    @Nullable
    public static SubLevel findContaining(@Nullable Level level, @Nullable Vec3 pos, @Nullable SubLevel hint) {
        if (level == null || pos == null) return null;
        if (!Double.isFinite(pos.x) || !Double.isFinite(pos.y) || !Double.isFinite(pos.z)) return null;

        if (hint != null && !hint.isRemoved()) {
            try {
                if (hint.boundingBox().intersects(probeBox(pos))) return hint;
            } catch (Throwable ignored) {
                // fall through to the full scan below
            }
        }

        SubLevelContainer container = SableBridge.getContainer(level);
        if (container == null) return null;

        try {
            for (SubLevel sl : container.queryIntersecting(probeBox(pos))) {
                if (sl != null && !sl.isRemoved()) return sl;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    @Nullable
    public static SubLevel findContaining(@Nullable Level level, @Nullable Vec3 pos) {
        return findContaining(level, pos, null);
    }

    private static BoundingBox3d probeBox(Vec3 pos) {
        return new BoundingBox3d(
                pos.x - PROBE, pos.y - PROBE, pos.z - PROBE,
                pos.x + PROBE, pos.y + PROBE, pos.z + PROBE);
    }
}
