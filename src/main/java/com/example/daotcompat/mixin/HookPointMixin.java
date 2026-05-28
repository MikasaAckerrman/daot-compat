package com.example.daotcompat.mixin;

import com.example.daotcompat.hook.DynamicHookData;
import com.example.daotcompat.hook.DynamicHookStorage;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Adds {@link DynamicHookStorage} duck-typing to {@code daot.HookPoint}.
 *
 * <p>Targeted by class name (string) because:
 * <ul>
 *   <li>{@code daot.HookPoint} is a mod class (Danny's AOT), not a Mojang class.</li>
 *   <li>{@code remap = false} prevents Mixin from trying to remap the target name
 *       through Mojang/Yarn mappings &mdash; AOT keeps the {@code daot/} package intact
 *       even after Sinytra Connector remaps its byte-code at runtime.</li>
 * </ul>
 */
@Mixin(targets = "daot.HookPoint", remap = false)
public abstract class HookPointMixin implements DynamicHookStorage {

    @Unique
    @Nullable
    private DynamicHookData daotcompat$dynamicData;

    @Override
    @Nullable
    public DynamicHookData daotCompat$getDynamicData() {
        return this.daotcompat$dynamicData;
    }

    @Override
    public void daotCompat$setDynamicData(@Nullable DynamicHookData data) {
        this.daotcompat$dynamicData = data;
    }
}
