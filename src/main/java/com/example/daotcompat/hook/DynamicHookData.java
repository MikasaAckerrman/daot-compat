package com.example.daotcompat.hook;

import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.UUID;

/**
 * Per-{@code daot.HookPoint} state attached by this mod when a hook is anchored
 * inside a Sable sub-level. Stores the local-space coordinate so it can be
 * re-projected to world-space every tick using the sub-level's current pose.
 *
 * @param subLevelId    UUID of the {@code dev.ryanhcode.sable.sublevel.SubLevel} the hook is anchored to.
 * @param localPosition local coordinate of the anchor inside that sub-level (Mojang {@link Vec3}).
 */
public record DynamicHookData(UUID subLevelId, Vec3 localPosition) {

    public DynamicHookData {
        Objects.requireNonNull(subLevelId, "subLevelId");
        Objects.requireNonNull(localPosition, "localPosition");
    }
}
