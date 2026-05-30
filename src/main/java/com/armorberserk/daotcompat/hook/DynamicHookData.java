/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.hook;

import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.UUID;

/** A hook's grab point: the sub-level it caught and the anchor in that sub-level's local space. */
public record DynamicHookData(UUID subLevelId, Vec3 localPosition) {

    public DynamicHookData {
        Objects.requireNonNull(subLevelId, "subLevelId");
        Objects.requireNonNull(localPosition, "localPosition");
    }
}
