/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.mixin.client;

import com.armorberserk.daotcompat.aot.AOTReflect;
import com.armorberserk.daotcompat.hook.HookTransformResolver;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Refreshes both hook anchors at the start of the player tick, before Danny's AOT reads
 * the hook position to move the player. Targeting the vanilla {@link LocalPlayer} keeps the
 * mixin on a Mojang-mapped class, which applies cleanly even with AOT loaded via Connector.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerTickMixin {

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void daotcompat$updateHooks(CallbackInfo ci) {
        Object left = AOTReflect.getLeftHook();
        Object right = AOTReflect.getRightHook();
        if (left == null && right == null) return;

        LocalPlayer player = (LocalPlayer) (Object) this;
        HookTransformResolver.process(player.level(), left);
        HookTransformResolver.process(player.level(), right);
    }
}
