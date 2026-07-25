package com.armorberserk.daotcompat.input;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Event listener for keybind state updates.
 * 
 * Hooks into:
 * - ClientTickEvent.Post: Update keybind states every tick
 * - RegisterKeyMappingsEvent: Register our custom keybinds on startup
 * 
 * This is the bridge between NeoForge events and our keybind/physics systems.
 */
@OnlyIn(Dist.CLIENT)
public class KeybindEventListener {
    
    /**
     * Called at the END of each client tick (after input processing).
     * Updates GrappleStateManager with current keybind states.
     */
    @SubscribeEvent
    public static void onClientTickEnd(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            // Not in game, reset states
            GrappleStateManager.reset();
            return;
        }
        
        // Update keybind states every tick
        GrappleStateManager.updateState(player);
        
        // Optional: Debug logging (remove in production)
        // if (player.tickCount % 20 == 0) {
        //     LOGGER.debug(GrappleStateManager.getDebugInfo());
        // }
    }
    
    /**
     * Called before screen input is processed.
     * Can be used to prevent keybind conflicts with UI elements.
     * 
     * Currently a placeholder - extend if needed for GUI-aware keybinds.
     */
    @SubscribeEvent
    public static void onScreenKeyPress(ScreenEvent.KeyPressed.Pre event) {
        // If a screen is open, we might want to disable some keybinds
        // For now, keybinds work through the screen normally
        // This can be extended to add special handling if needed
    }
    
    /**
     * Register keybinds with the client on startup.
     * This must be called during mod initialization or ClientSetupEvent.
     */
    public static void init() {
        GrappleKeybinds.registerKeybinds();
    }
}
