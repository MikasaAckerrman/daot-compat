package com.example.daotcompat.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessor + invoker for {@code daot.HookPoint}.
 *
 * <p>Object-typed accessors are intentional: the AOT jar uses {@code class_243}/{@code class_1297}
 * (Yarn intermediary) at compile-time. Sinytra Connector remaps those to Mojang
 * ({@code Vec3}/{@code Entity}) at runtime. {@link Object} sidesteps Mixin AP type-checks against
 * the raw classpath; callers cast after Connector has done its work.
 */
@Mixin(targets = "daot.HookPoint", remap = false)
public interface HookPointAccessor {

    /** {@code public Vec3 position} — anchor point in world-space. */
    @Accessor("position")
    Object daotCompat$getPosition();

    @Accessor("position")
    @Mutable
    void daotCompat$setPosition(Object position);

    /** {@code public boolean active} — whether the hook is currently latched. */
    @Accessor("active")
    boolean daotCompat$isActive();

    /** {@code public Entity hookedEntity} — non-null when hook is on a moving entity (e.g. titan). */
    @Accessor("hookedEntity")
    Object daotCompat$getHookedEntity();

    /** {@code public void release()} — invoke AOT's hook teardown (clears state, sets active=false). */
    @Invoker("release")
    void daotCompat$invokeRelease();
}
