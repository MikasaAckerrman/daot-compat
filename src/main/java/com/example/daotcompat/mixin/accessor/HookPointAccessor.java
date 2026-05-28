package com.example.daotcompat.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read/write access to the public field {@code daot.HookPoint.position}.
 *
 * <p>Why {@link Object}, not {@code Vec3}: at compile-time the AOT jar is the original
 * Fabric build with {@code net.minecraft.class_243}-typed signatures. Sinytra Connector
 * remaps that to Mojang {@code Vec3} at runtime. Using {@link Object} bypasses Mixin AP's
 * type-check against the raw classpath signature; callers cast to {@code Vec3} after
 * Connector has done its work.
 *
 * <p>Cast pattern:
 * <pre>{@code
 *   HookPointAccessor acc = (HookPointAccessor) hookPoint;
 *   Vec3 pos = (Vec3) acc.daotCompat$getPosition();
 *   acc.daotCompat$setPosition(newPos);
 * }</pre>
 */
@Mixin(targets = "daot.HookPoint", remap = false)
public interface HookPointAccessor {

    @Accessor("position")
    Object daotCompat$getPosition();

    @Accessor("position")
    @Mutable
    void daotCompat$setPosition(Object position);
}
