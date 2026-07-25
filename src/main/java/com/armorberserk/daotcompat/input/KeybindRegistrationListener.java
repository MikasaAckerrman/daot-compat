package com.armorberserk.daotcompat.input;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Handles keybind registration (MOD BUS event).
 * Separated from KeybindEventListener because RegisterKeyMappingsEvent 
 * is a MOD event and must be registered on MOD_BUS, while tick/screen 
 * events are FORGE events and go on NeoForge.EVENT_BUS.
 */
@OnlyIn(Dist.CLIENT)
public class KeybindRegistrationListener {
    
    /**
     * Register all keybinds with the client (MOD BUS event).
     * This is called during mod initialization.
     */
    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        GrappleKeybinds.registerKeybinds(event);
    }
}
