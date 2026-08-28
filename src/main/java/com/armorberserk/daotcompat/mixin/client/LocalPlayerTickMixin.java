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
 * HEAD of LocalPlayer.tick: corrects hooks before AOT's movement logic reads them.
 * A second pass runs from a NeoForge ClientTickEvent (registered in DAOTCompat) to catch
 * hooks that AOT fires later in the same game tick, before the frame renders.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerTickMixin {

    @Inject(method = "tick()V", at = @At("HEAD"))
    private void daotcompat$updateHooksHead(CallbackInfo ci) {
        Level level = ((LocalPlayer) (Object) this).level();
        RemoteHookFollower.tick(level);
        Object left = AOTReflect.getLeftHook();
        Object right = AOTReflect.getRightHook();
        if (left != null) HookTransformResolver.process(level, left);
        if (right != null) HookTransformResolver.process(level, right);
    }
}
