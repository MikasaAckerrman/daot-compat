package com.armorberserk.daotcompat.input;

import com.armorberserk.daotcompat.aot.AOTReflect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * CRITICAL FIX (v1.3.2): Handle mouse click input to release ropes.
 * 
 * BUG FOUND: Ropes weren't releasing on simple LeftClick release.
 * They only released when SPACE was held. This is because there was
 * NO input listener tracking mouse clicks.
 * 
 * Solution: Monitor MouseScrollEvent and handle Left Click releases.
 * When player releases LMB, both ropes should detach immediately.
 */
@OnlyIn(Dist.CLIENT)
public class MouseInputListener {
    
    private static boolean wasLeftClickPressed = false;
    
    /**
     * Monitor mouse click state every tick.
     * When LMB is released (was pressed, now not pressed), release ropes.
     */
    @SubscribeEvent
    public static void onMouseInput(InputEvent.MouseButton event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        
        // Track LMB state (Button 0 = LMB)
        if (event.getButton() == 0) {  // Left Mouse Button
            boolean isNowPressed = event.getAction() != 0;  // 1 = pressed, 0 = released
            
            if (wasLeftClickPressed && !isNowPressed) {
                // ✅ LMB was released - detach both ropes immediately!
                releaseRopes(player);
            }
            
            wasLeftClickPressed = isNowPressed;
        }
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
}
