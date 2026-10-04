/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.hook;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.UUID;

/**
 * A hook's (or spear's) grab point: the sub-level it caught, the anchor in sub-level local space,
 * and the dimension ({@link ResourceKey}) in which the anchor was recorded.
 *
 * <p>The dimension key is used to guard against stale anchors after a player crosses a portal —
 * Sable sub-level UUIDs are not globally unique across dimensions, so a hit in the Overworld
 * could otherwise be confused with a coincidentally same-UUID ship in the Nether.
 */
public record DynamicHookData(UUID subLevelId, Vec3 localPosition, ResourceKey<Level> dimensionKey) {

    public DynamicHookData {
        Objects.requireNonNull(subLevelId, "subLevelId");
        Objects.requireNonNull(localPosition, "localPosition");
        Objects.requireNonNull(dimensionKey, "dimensionKey");
    }
}
