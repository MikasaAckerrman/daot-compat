package com.armorberserk.daotcompat.input;

import com.armorberserk.daotcompat.aot.AOTReflect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * CRITICAL FIX (v1.3.4): Handle mouse click input to release ropes.
 * 
 * BUG FOUND v1.3.2: InputEvent.MouseButton is abstract - can't register on it!
 * 
 * SOLUTION v1.3.4: No event bus registration.
 * Instead, track mouse button state directly via tick() call.
 * Called from KeybindEventListener.onClientTickEnd()
 */
@OnlyIn(Dist.CLIENT)
public class MouseInputListener {
    
    private static boolean wasLeftClickPressed = false;
    
    /**
     * Called every tick from KeybindEventListener.
     * Tracks left mouse button state and releases ropes when released.
     * 
     * FIXED (v1.3.4): No @SubscribeEvent - direct method call via tick.
     */
    public static void tick() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            wasLeftClickPressed = false;
            return;
        }
        
        // Check if left button is currently being attacked
        // In Minecraft, holding LMB = attacking
        boolean isNowPressed = Minecraft.getInstance().mouseHandler.isLeftPressed();
        
        // Detect release (was pressed, now not)
        if (wasLeftClickPressed && !isNowPressed) {
            // ✅ LMB was released - detach both ropes immediately!
            releaseRopes(player);
        }
        
        wasLeftClickPressed = isNowPressed;
    }
    
    /**
     * Release both left and right ropes when player releases LMB.
     * This is the CORE rope release mechanism.
     */
    private static void releaseRopes(LocalPlayer player) {
        if (player == null) return;
        
        Object leftHook = AOTReflect.getLeftHook();
        Object rightHook = AOTReflect.getRightHook();
        
        if (leftHook != null) {
            AOTReflect.release(leftHook);
        }
        if (rightHook != null) {
            AOTReflect.release(rightHook);
        }
    }
    
    /**
     * Reset state (for when player leaves world/game)
     */
    public static void reset() {
        wasLeftClickPressed = false;
    }
}
