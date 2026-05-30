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

    @Nullable
    public static SubLevel findContaining(@Nullable Level level, @Nullable Vec3 pos) {
        if (level == null || pos == null) return null;
        if (!Double.isFinite(pos.x) || !Double.isFinite(pos.y) || !Double.isFinite(pos.z)) return null;

        SubLevelContainer container = SableBridge.getContainer(level);
        if (container == null) return null;

        BoundingBox3d box = new BoundingBox3d(
                pos.x - PROBE, pos.y - PROBE, pos.z - PROBE,
                pos.x + PROBE, pos.y + PROBE, pos.z + PROBE);
        try {
            for (SubLevel sl : container.queryIntersecting(box)) {
                if (sl != null && !sl.isRemoved()) return sl;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
