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
 * Runs at the start of the player tick, before Danny's AOT reads hook positions to move
 * players and draw ropes. Targeting the vanilla {@link LocalPlayer} keeps the mixin on a
 * Mojang-mapped class, which applies cleanly even with AOT loaded via Connector.
 *
 * <p>We refresh both our own hooks and every other player's hooks so all ropes stay glued
 * to the airship they grabbed.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerTickMixin {

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void daotcompat$updateHooks(CallbackInfo ci) {
        Level level = ((LocalPlayer) (Object) this).level();

        // Other players' ropes - runs every tick, even when we are not grappling.
        RemoteHookFollower.tick(level);

        // Our own hooks.
        Object left = AOTReflect.getLeftHook();
        Object right = AOTReflect.getRightHook();
        if (left == null && right == null) return;
        HookTransformResolver.process(level, left);
        HookTransformResolver.process(level, right);
    }
}
