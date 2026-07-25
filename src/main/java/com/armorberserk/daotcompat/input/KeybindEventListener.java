package com.armorberserk.daotcompat.input;

import com.armorberserk.daotcompat.gas.GasManager;
import com.armorberserk.daotcompat.physics.DEWImpulseCalculator;
import com.armorberserk.daotcompat.render.SparkEffectRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Event listener for keybind state updates, gas tick, and DEW processing.
 */
@OnlyIn(Dist.CLIENT)
public class KeybindEventListener {
    
    /**
     * Register all keybinds with the client.
     */
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        GrappleKeybinds.registerKeybinds(event);
    }
    
    /**
     * Called at the END of each client tick.
     * Updates keybind states, gas tank, and processes DEW activation.
     */
    @SubscribeEvent
    public static void onClientTickEnd(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            GrappleStateManager.reset();
            GasManager.reset();
            DoubleTapDetector.reset();
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
        }
        
        // Check for Reverse DEW (double-tap S)
        if (DoubleTapDetector.detectDoubleTapS() && GasManager.canUseReverseDEW()) {
            GasManager.consumeForReverseDEW();
            Vec3 impulse = DEWImpulseCalculator.calculateReverseDEW(player);
            player.setDeltaMovement(player.getDeltaMovement().add(impulse));
        }
    }
    
    @SubscribeEvent
    public static void onScreenKeyPress(ScreenEvent.KeyPressed.Pre event) {
        // Placeholder for future GUI-aware keybinds
    }
}

