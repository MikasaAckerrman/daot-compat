package com.armorberserk.daotcompat.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Central registry for all ODM grappling keybinds.
 * 
 * Includes:
 * - PULL_ROPE (SPACE by default): Engage/pull toward rope anchor
 * - ACCELERATE (W by default): Speed up while moving
 * - DESCEND_ROPE (SHIFT by default): Lower/release rope tension
 * - SWAP_HOTBAR (Unbound by default): Switch inventory slots while grappling
 * 
 * All keybinds are user-customizable via Minecraft's Controls menu.
 */
@OnlyIn(Dist.CLIENT)
public class GrappleKeybinds {
    private static final String CATEGORY = "key.categories.daotcompat";
    
    // Rope engagement keybinds
    public static final KeyMapping PULL_ROPE = new KeyMapping(
        "key.daotcompat.pull_rope",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_SPACE,
        CATEGORY
    );
    
    public static final KeyMapping ACCELERATE = new KeyMapping(
        "key.daotcompat.accelerate",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_W,
        CATEGORY
    );
    
    public static final KeyMapping DESCEND_ROPE = new KeyMapping(
        "key.daotcompat.descend_rope",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_LEFT_SHIFT,
        CATEGORY
    );
    
    // Inventory management while grappling
    public static final KeyMapping SWAP_HOTBAR = new KeyMapping(
        "key.daotcompat.swap_hotbar",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_UNKNOWN,  // No default binding
        CATEGORY
    );
    
    // DEW & Gas system keybinds
    // Note: DEW is detected via double-tap of PULL_ROPE (SPACE)
    // Reverse DEW is detected via double-tap of a movement key (S)
    public static final KeyMapping REVERSE_DEW = new KeyMapping(
        "key.daotcompat.reverse_dew",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_S,
        CATEGORY
    );
    
    /**
     * Register all keybinds with the client.
     * Call this during RegisterKeyMappingsEvent.
     */
    public static void registerKeybinds(RegisterKeyMappingsEvent event) {
        event.register(PULL_ROPE);
        event.register(ACCELERATE);
        event.register(DESCEND_ROPE);
        event.register(SWAP_HOTBAR);
        event.register(REVERSE_DEW);
    }
    
    /**
     * Get current state of PULL_ROPE keybind (being held).
     * @return true if PULL_ROPE is currently pressed
     */
    public static boolean isPullingRope() {
        return PULL_ROPE.isDown();
    }
    
    /**
     * Check if PULL_ROPE was pressed this tick (and consume the press).
     * Used for reel-in activation.
     * @return true if pressed this tick
     */
    public static boolean isPullRopePressed() {
        return PULL_ROPE.consumeClick();
    }
    
    /**
     * Get current state of ACCELERATE keybind.
     * @return true if ACCELERATE (W) is currently pressed
     */
    public static boolean isAccelerating() {
        return ACCELERATE.isDown();
    }
    
    /**
     * Get current state of DESCEND_ROPE keybind.
     * @return true if DESCEND_ROPE (SHIFT) is currently pressed
     */
    public static boolean isDescending() {
        return DESCEND_ROPE.isDown();
    }
    
    /**
     * Check if SWAP_HOTBAR keybind was pressed.
     * @return true if pressed this tick
     */
    public static boolean isSwappingHotbar() {
        return SWAP_HOTBAR.consumeClick();
    }
    
    /**
     * Check if REVERSE_DEW keybind is held (for double-tap detection).
     * @return true if S key is currently pressed
     */
    public static boolean isReverseDeWPressed() {
        return REVERSE_DEW.isDown();
    }
}
