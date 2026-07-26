package com.armorberserk.daotcompat.input;

import com.armorberserk.daotcompat.DAOTCompat;
import com.armorberserk.daotcompat.gas.GasManager;
import com.armorberserk.daotcompat.physics.DEWImpulseCalculator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * CRITICAL FIX (v1.3.2): Static methods for keybind event handling.
 * 
 * NOTE: These are NOT registered with @SubscribeEvent - they are called
 * directly from DAOTCompat.java to avoid event bus registration issues.
 * 
 * RegisterKeyMappingsEvent is handled in KeybindRegistrationListener.java
 */
@OnlyIn(Dist.CLIENT)
public class KeybindEventListener {
    
    /**
     * Called at the END of each client tick.
     * Updates keybind states, gas tank, and processes DEW activation.
     * 
     * FIXED (v1.3.2): Removed @SubscribeEvent annotation - causes crash on mobile.
     * Now called directly from DAOTCompat event lambda.
     * 
     * FIXED (v1.3.4): Added MouseInputListener.tick() call.
     */
    public static void onClientTickEnd() {
        LocalPlayer player = Minecraft.getInstance().player;
        
        // Track mouse button state for rope release [FIXED v1.3.4]
        MouseInputListener.tick();
        
        if (player == null) {
            GrappleStateManager.reset();
            GasManager.reset();
            DoubleTapDetector.reset();
            MouseInputListener.reset();
            return;
        }
        
        // Update keybind states
        GrappleStateManager.updateState(player);
        
        // Tick gas regeneration
        GasManager.tick(player);
        
        // Check for DEW (double-tap SPACE)
        if (DoubleTapDetector.detectDoubleTapSpace() && GasManager.canUseDEW()) {
            GasManager.consumeForDEW();
            Vec3 impulse = DEWImpulseCalculator.calculateDEW(player);
            player.setDeltaMovement(player.getDeltaMovement().add(impulse));
            // 🔊 Play sound on DEW activation
            player.playSound(SoundEvents.BLAZE_SHOOT, 0.6f, 0.9f + (float) Math.random() * 0.2f);
            // [FIX v1.3.5] Log DEW activation for debugging
            DAOTCompat.LOGGER.debug("[dew] DEW forward activated");
        }
        
        // Check for Reverse DEW (double-tap S)
        if (DoubleTapDetector.detectDoubleTapS() && GasManager.canUseReverseDEW()) {
            GasManager.consumeForReverseDEW();
            Vec3 impulse = DEWImpulseCalculator.calculateReverseDEW(player);
            player.setDeltaMovement(player.getDeltaMovement().add(impulse));
            // 🔊 Play sound on Reverse DEW activation
            player.playSound(SoundEvents.BLAZE_SHOOT, 0.6f, 1.1f + (float) Math.random() * 0.2f);
            // [FIX v1.3.5] Log Reverse DEW activation
            DAOTCompat.LOGGER.debug("[dew] DEW reverse activated");
        }
    }
}

