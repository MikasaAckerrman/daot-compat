/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.mixin.client;

import com.armorberserk.daotcompat.aot.AOTReflect;
import com.armorberserk.daotcompat.hook.HookTransformResolver;
import com.armorberserk.daotcompat.hook.RemoteHookFollower;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs at the start and end of the player tick. The HEAD pass ensures the hooks are
 * corrected before AOT's movement logic reads them; the TAIL pass catches fresh hooks
 * that AOT set during the tick (so the very first rendered frame already points at the
 * airship instead of flashing to a random spot for one frame).
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerTickMixin {

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void daotcompat$updateHooksHead(CallbackInfo ci) {
        updateAll();
    }

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void daotcompat$updateHooksTail(CallbackInfo ci) {
        updateAll();
    }

    private void updateAll() {
        Level level = ((LocalPlayer) (Object) this).level();
        RemoteHookFollower.tick(level);
        Object left = AOTReflect.getLeftHook();
        Object right = AOTReflect.getRightHook();
        if (left != null) HookTransformResolver.process(level, left);
        if (right != null) HookTransformResolver.process(level, right);
    }
}
