package com.example.daotcompat.mixin.client;

import com.example.daotcompat.aot.AOTReflect;
import com.example.daotcompat.hook.HookTransformResolver;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Re-projects the local-space anchor of every active ODM hook to world-space at the start
 * of each {@link LocalPlayer#tick()} call &mdash; <em>before</em> AOT consumes the position
 * in its own client tick callback.
 *
 * <p>Why this target, not {@code daot.ODMTickHandler#applyHookMovement}:
 * <ul>
 *   <li>{@link LocalPlayer} is a Mojang-mapped class, so the mixin signature is fully
 *       resolvable at compile-time and at Mixin processing time.</li>
 *   <li>The AOT method is built against Yarn intermediary names ({@code class_746}); a
 *       direct {@code @Inject} would require either {@link Object}-typed callback parameters
 *       (rejected by Mixin AP signature matching) or a Yarn-stub jar in the classpath.</li>
 *   <li>{@link LocalPlayer#tick()} runs once per client tick before vanilla physics, well
 *       before AOT's post-tick hook consumption &mdash; the order is correct.</li>
 * </ul>
 *
 * <p>The hook objects are fetched through {@link AOTReflect} (multi-classloader reflection
 * lookup) so AOT loaded by Sinytra Connector into a separate classloader is still
 * reachable from our NeoForge mod.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerTickMixin {

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void daotcompat$reprojectHooksHead(CallbackInfo ci) {
        Object leftHook = AOTReflect.getLeftHook();
        Object rightHook = AOTReflect.getRightHook();
        if (leftHook == null && rightHook == null) return; // AOT absent or no hooks tracked yet

        LocalPlayer self = (LocalPlayer) (Object) this;
        Level level = self.level();
        HookTransformResolver.process(level, leftHook, "L");
        HookTransformResolver.process(level, rightHook, "R");
    }
}
