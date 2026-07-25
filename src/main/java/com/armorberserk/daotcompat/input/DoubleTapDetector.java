package com.armorberserk.daotcompat.input;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.api.distmarker.OnlyIn;

/**
 * Detects double-tap inputs for DEW and Reverse DEW mechanics.
 * 
 * DEW activation: SPACE pressed twice within 0.25–0.35 seconds
 * Reverse DEW: S pressed twice within 0.25–0.35 seconds
 * 
 * This system differentiates between:
 * - Single SPACE: Rope reel-in (continuous pull)
 * - Double SPACE: DEW (instantaneous gas impulse)
 * 
 * Works on client tick updates from KeybindEventListener.
 */
@OnlyIn(Dist.CLIENT)
public class DoubleTapDetector {
    
    // Double-tap window (milliseconds)
    private static final long DOUBLE_TAP_THRESHOLD_MS = 350;  // 0.25–0.35 seconds
    
    // SPACE key timing
    private static long lastSpacePressTime = -1;
    private static int spacePressTicks = 0;
    
    // S key timing (for Reverse DEW)
    private static long lastSPressTime = -1;
    private static int sPressTicks = 0;
    
    /**
     * Detect if SPACE was double-tapped for DEW activation.
     * 
     * Logic:
     * 1. Check if SPACE is being pressed (consumeClick)
     * 2. If pressed, check time since last press
     * 3. If within threshold → double-tap detected
     * 4. Reset for next detection cycle
     * 
     * @return true if double-tap detected this tick
     */
    public static boolean detectDoubleTapSpace() {
        long now = System.currentTimeMillis();
        
        // Check if SPACE was just pressed this tick
        if (GrappleKeybinds.isPullRopePressed()) {
            // SPACE pressed
            if (now - lastSpacePressTime <= DOUBLE_TAP_THRESHOLD_MS) {
                // Within window - double-tap detected!
                lastSpacePressTime = -1;  // Reset to prevent triple-tap
                spacePressTicks = 0;
                return true;
            }
            // Not within window - record this as first press
            lastSpacePressTime = now;
            spacePressTicks = 0;
        } else if (lastSpacePressTime > 0 && now - lastSpacePressTime > DOUBLE_TAP_THRESHOLD_MS) {
            // Window expired, reset
            lastSpacePressTime = -1;
            spacePressTicks = 0;
        }
        
        return false;
    }
    
    /**
     * Detect if S was double-tapped for Reverse DEW activation.
     * 
     * Similar logic to SPACE but for the S key (Reverse DEW).
     * S key doesn't have a primary function in ODM, so double-tap is primary trigger.
     * 
     * @return true if double-tap detected this tick
     */
    public static boolean detectDoubleTapS() {
        long now = System.currentTimeMillis();
        
        // Check if S was just pressed this tick
        // Note: We need a keybind for this. Currently using REVERSE_DEW keybind.
        if (GrappleKeybinds.REVERSE_DEW.consumeClick()) {
            // S pressed
            if (now - lastSPressTime <= DOUBLE_TAP_THRESHOLD_MS) {
                // Within window - double-tap detected!
                lastSPressTime = -1;
                sPressTicks = 0;
                return true;
            }
            // Not within window - record this as first press
            lastSPressTime = now;
            sPressTicks = 0;
        } else if (lastSPressTime > 0 && now - lastSPressTime > DOUBLE_TAP_THRESHOLD_MS) {
            // Window expired, reset
            lastSPressTime = -1;
            sPressTicks = 0;
        }
        
        return false;
    }
    
    /**
     * Get time remaining until double-tap window closes for SPACE.
     * Returns 0 if window is closed or not active.
     */
    public static long getSpaceWindowTimeRemaining() {
        if (lastSpacePressTime < 0) return 0;
        long elapsed = System.currentTimeMillis() - lastSpacePressTime;
        long remaining = DOUBLE_TAP_THRESHOLD_MS - elapsed;
        return Math.max(0, remaining);
    }
    
    /**
     * Get time remaining until double-tap window closes for S.
     */
    public static long getSWindowTimeRemaining() {
        if (lastSPressTime < 0) return 0;
        long elapsed = System.currentTimeMillis() - lastSPressTime;
        long remaining = DOUBLE_TAP_THRESHOLD_MS - elapsed;
        return Math.max(0, remaining);
    }
    
    /**
     * Reset all double-tap detection state.
     * Call this on world exit or player death.
     */
    public static void reset() {
        lastSpacePressTime = -1;
        lastSPressTime = -1;
        spacePressTicks = 0;
        sPressTicks = 0;
    }
}
