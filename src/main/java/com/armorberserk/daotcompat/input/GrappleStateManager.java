package com.armorberserk.daotcompat.input;

import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Centralized state tracker for all keybind-related grappling mechanics.
 * 
 * Maintains flags for:
 * - Rope engagement state (PULL_ROPE held)
 * - Acceleration state (W held while rope engaged)
 * - Descend state (SHIFT held while rope engaged)
 * - Hotbar swap pending
 * 
 * Updates every client tick via KeybindEventListener.
 * Thread-safe for client-side rendering and physics calculations.
 */
@OnlyIn(Dist.CLIENT)
public class GrappleStateManager {
    
    // Current keybind states
    private static boolean isPullingRope = false;
    private static boolean isAccelerating = false;
    private static boolean isDescending = false;
    private static boolean isSwappingHotbar = false;
    private static boolean isReverseDewPressed = false;
    
    // Timing for anime-style mechanics
    private static int ropePullStartTick = -1;      // When PULL_ROPE first engaged
    private static int lastAccelerationTick = -1;   // For acceleration deceleration
    
    // Previous frame states (for state changes)
    private static boolean wasPullingRope = false;
    private static boolean wasAccelerating = false;
    
    /**
     * Update all keybind states. Call this every client tick from KeybindEventListener.
     * 
     * @param player The local player
     */
    public static void updateState(LocalPlayer player) {
        if (player == null) return;
        
        // Store previous states for change detection
        wasPullingRope = isPullingRope;
        wasAccelerating = isAccelerating;
        
        // Update current states
        isPullingRope = GrappleKeybinds.isPullingRope();
        isAccelerating = GrappleKeybinds.isAccelerating();
        isDescending = GrappleKeybinds.isDescending();
        isSwappingHotbar = GrappleKeybinds.isSwappingHotbar();
        isReverseDewPressed = GrappleKeybinds.isReverseDeWPressed();
        
        // Handle rope pull start tracking (for anime logic: W only works after SPACE)
        if (isPullingRope && !wasPullingRope) {
            // Just started pulling rope
            ropePullStartTick = player.tickCount;
        } else if (!isPullingRope) {
            // Released rope
            ropePullStartTick = -1;
        }
        
        // Track last acceleration time
        if (isAccelerating && canAccelerate()) {
            lastAccelerationTick = player.tickCount;
        }
    }
    
    /**
     * Check if player is currently pulling rope (PULL_ROPE held).
     * This is the primary engagement state for rope mechanics.
     */
    public static boolean isPullingRope() {
        return isPullingRope;
    }
    
    /**
     * Check if player just started pulling rope this tick.
     * Useful for one-time events (sound, particles, etc).
     */
    public static boolean ropeEngagedThisTick() {
        return isPullingRope && !wasPullingRope;
    }
    
    /**
     * Check if player just released rope this tick.
     * Useful for cleanup (particle effects, rope oscillation, etc).
     */
    public static boolean ropeReleasedThisTick() {
        return !isPullingRope && wasPullingRope;
    }
    
    /**
     * Anime logic: W (acceleration) only works AFTER SPACE is held.
     * Cannot accelerate without active rope engagement.
     * 
     * @return true if W is pressed AND SPACE was pressed first (ropePullStartTick >= 0)
     */
    public static boolean canAccelerate() {
        return isAccelerating && ropePullStartTick >= 0;
    }
    
    /**
     * Simplified acceleration check (W key pressed, regardless of rope).
     */
    public static boolean isAcceleratingRaw() {
        return isAccelerating;
    }
    
    /**
     * Check if player just started accelerating this tick.
     */
    public static boolean accelerationStartedThisTick() {
        return isAccelerating && !wasAccelerating;
    }
    
    /**
     * Check if player is descending (SHIFT held while rope engaged).
     */
    public static boolean isDescending() {
        return isDescending;
    }
    
    /**
     * Check if player wants to swap hotbar.
     */
    public static boolean isSwappingHotbar() {
        return isSwappingHotbar;
    }
    
    /**
     * Check if reverse DEW key (S) is pressed.
     * Used by DoubleTapDetector for Reverse DEW double-tap detection.
     */
    public static boolean isReverseDewPressed() {
        return isReverseDewPressed;
    }
    
    /**
     * Get how many ticks ago rope engagement started.
     * Useful for animations or mechanics that depend on engagement duration.
     * Returns -1 if rope is not engaged.
     */
    public static int getTicksSincePullStart(LocalPlayer player) {
        if (ropePullStartTick < 0) return -1;
        return player.tickCount - ropePullStartTick;
    }
    
    /**
     * Get how many ticks ago acceleration started.
     * Returns -1 if not accelerating.
     */
    public static int getTicksSinceAccelerationStart(LocalPlayer player) {
        if (lastAccelerationTick < 0) return -1;
        return player.tickCount - lastAccelerationTick;
    }
    
    /**
     * Reset all state (used when exiting world or on death).
     */
    public static void reset() {
        isPullingRope = false;
        isAccelerating = false;
        isDescending = false;
        isSwappingHotbar = false;
        isReverseDewPressed = false;
        ropePullStartTick = -1;
        lastAccelerationTick = -1;
        wasPullingRope = false;
        wasAccelerating = false;
    }
    
    /**
     * Debug: Get a string representation of current state.
     */
    public static String getDebugInfo() {
        return String.format(
            "GrappleState{pull:%b, accel:%b, desc:%b, swap:%b, revDew:%b}",
            isPullingRope, isAccelerating, isDescending, isSwappingHotbar, isReverseDewPressed
        );
    }
}
