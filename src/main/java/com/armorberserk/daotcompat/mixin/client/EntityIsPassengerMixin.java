/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.mixin.client;

import com.armorberserk.daotcompat.ragdoll.RagdollClient;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While the local player is ragdolled, isPassenger() returns false for the LocalPlayer.
 *
 * <p>Root cause: AOT's ODMTickHandler checks player.isPassenger() and blocks ALL ODM
 * processing when the player is seated on the ragdoll body ("ODM is Disabled"). This mixin
 * lies about the passenger state for the LOCAL PLAYER ONLY during ragdoll sessions, so
 * AOT's isPassenger gate passes and the gear fires hooks normally.
 *
 * <p>Scope: ONLY LocalPlayer instances, ONLY while RagdollClient.isRagdolledLive() is true.
 * The actual seat mechanism (vehicle field, mounting) is unaffected — isPassenger() is a
 * query, not a state modifier.
 */
@Mixin(Entity.class)
public abstract class EntityIsPassengerMixin {

    @Inject(method = "isPassenger", at = @At("HEAD"), cancellable = true)
    private void daotcompat$forceNotPassenger(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof LocalPlayer && RagdollClient.isRagdolledLive()) {
            cir.setReturnValue(false);
        }
    }
}
