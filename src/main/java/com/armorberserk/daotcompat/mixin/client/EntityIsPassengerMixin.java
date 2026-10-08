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
 * While the local player is ragdolled AND genuinely seated, isPassenger() returns false for
 * the LocalPlayer.
 *
 * <p>Root cause: AOT's ODMTickHandler checks player.isPassenger() and blocks ALL ODM
 * processing when the player is seated on the ragdoll body ("ODM is Disabled"). This mixin
 * lies about the passenger state for the LOCAL PLAYER ONLY during seated ragdoll sessions
 * (Ragdoll Reactions' impact ragdolls), so AOT's isPassenger gate passes and the gear fires
 * hooks normally.
 *
 * <p>Since v2.0.0 OUR OWN ragdolls run UNSEATED — the player is genuinely not a passenger, no
 * lie needed. The {@code getVehicle() != null} guard matters: {@code isRagdolledLive()} keeps
 * a 30-second fallback window open after a session that ended without our exit (stun expiry),
 * and lying about a player who has legitimately mounted a ship seat or a horse in that window
 * would break their riding.
 *
 * <p>Scope: ONLY LocalPlayer instances, ONLY while actually riding something AND ragdolled.
 * The actual seat mechanism (vehicle field, mounting) is unaffected — isPassenger() is a
 * query, not a state modifier.
 */
@Mixin(Entity.class)
public abstract class EntityIsPassengerMixin {

    @Inject(method = "isPassenger", at = @At("HEAD"), cancellable = true)
    private void daotcompat$forceNotPassenger(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof LocalPlayer player
                && player.getVehicle() != null
                && RagdollClient.isRagdolledLive()) {
            cir.setReturnValue(false);
        }
    }
}
