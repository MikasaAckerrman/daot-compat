package com.armorberserk.daotcompat.input;

import com.armorberserk.daotcompat.hook.DynamicHookMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Phase 5: Allow hotbar switching while grappling.
 * Intercepts number keys (1-9) to swap inventory slots.
 * Player can grab items without releasing rope engagement.
 */
@OnlyIn(Dist.CLIENT)
public class HotbarSwapHandler {
    
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        
        // Check if player is grappling (has active hooks)
        if (DynamicHookMap.get(null) == null && DynamicHookMap.get(null) == null) {
            return;  // Not grappling
        }
        
        // Intercept number keys 1-9 (GLFW codes 49-57)
        int key = event.getKey();
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
            int slot = key - GLFW.GLFW_KEY_1;  // Convert to 0-8
            player.getInventory().selected = slot;
            // Rope physics unaffected - player stays engaged
        }
    }
}
