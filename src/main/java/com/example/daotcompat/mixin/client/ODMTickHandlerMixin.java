package com.example.daotcompat.mixin.client;

import com.example.daotcompat.hook.HookTransformResolver;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks into {@code daot.ODMTickHandler#applyHookMovement(LocalPlayer, HookPoint, HookPoint)}
 * at HEAD to update both hook positions every tick before AOT consumes them.
 *
 * <p>Client-only because {@code applyHookMovement} takes a {@link LocalPlayer} — the method
 * is never invoked on a dedicated server. This mixin lives in the {@code client} section of
 * the mixin config so dedicated-server installs don't try to apply it.
 *
 * <p>Callback parameters use {@link Object} for AOT types — see
 * {@link com.example.daotcompat.mixin.accessor.HookPointAccessor} for rationale.
 */
@Mixin(targets = "daot.ODMTickHandler", remap = false)
public abstract class ODMTickHandlerMixin {

    @Inject(method = "applyHookMovement", at = @At("HEAD"), remap = false)
    private static void daotcompat$preApplyHookMovement(Object player,
                                                        Object leftHook,
                                                        Object rightHook,
                                                        CallbackInfo ci) {
        if (!(player instanceof LocalPlayer lp)) return;
        Level level = lp.level();
        HookTransformResolver.process(level, leftHook);
        HookTransformResolver.process(level, rightHook);
    }
}
