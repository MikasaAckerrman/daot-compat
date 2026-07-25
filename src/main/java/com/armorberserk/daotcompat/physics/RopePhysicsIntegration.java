package com.armorberserk.daotcompat.physics;

import com.armorberserk.daotcompat.aot.AOTReflect;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Phase 3: Basic rope physics - length tracking and sag calculation.
 * Foundation for future Sable RopePhysicsObject integration.
 * 
 * Currently provides:
 * - Max rope length enforcement
 * - Rope sag calculation (visual cue)
 * - Direction to anchor clamping
 */
@OnlyIn(Dist.CLIENT)
public class RopePhysicsIntegration {
    
    public static final double MAX_ROPE_LENGTH = 48.0;  // Anime reference
    private static final double SINTER_RADIUS = 1.0;     // Rope avoids wrapping inside this distance
    
    /**
     * Calculate current rope length between player and anchor.
     */
    public static double getRopeLength(LocalPlayer player, Vec3 anchorPos) {
        return player.position().distanceTo(anchorPos);
    }
    
    /**
     * Check if rope exceeds max length.
     */
    public static boolean isRopeTooLong(LocalPlayer player, Vec3 anchorPos) {
        return getRopeLength(player, anchorPos) > MAX_ROPE_LENGTH;
    }
    
    /**
     * Calculate sag amount for visual rope representation.
     * Longer ropes sag more.
     */
    public static double getRopeSag(double ropeLength) {
        // Sag increases with rope length
        // Max sag at max length: ~5 blocks
        double sagAmount = (ropeLength / MAX_ROPE_LENGTH) * 5.0;
        return Math.min(sagAmount, 5.0);
    }
    
    /**
     * Get rope direction, clamped to prevent backwards pull.
     */
    public static Vec3 getRopeDirection(LocalPlayer player, Vec3 anchorPos) {
        Vec3 toAnchor = anchorPos.subtract(player.position());
        if (toAnchor.length() < 0.5) {
            return Vec3.ZERO;
        }
        return toAnchor.normalize();
    }
    
    /**
     * Calculate rope tension (0-1) based on:
     * - Distance to anchor
     * - Player look angle alignment with anchor
     */
    public static float calculateRopeTension(LocalPlayer player, Vec3 anchorPos) {
        Vec3 toAnchor = anchorPos.subtract(player.position());
        Vec3 lookDir = player.getLookAngle();
        
        // Alignment: -1 to 1 (1 = looking at anchor)
        float alignment = (float) toAnchor.normalize().dot(lookDir);
        alignment = Math.max(-1, Math.min(1, alignment));
        
        // Normalize to 0-1
        float tension = (alignment + 1.0f) / 2.0f;
        
        // Reduce tension if far from anchor
        double distance = toAnchor.length();
        if (distance > MAX_ROPE_LENGTH * 0.8) {
            tension *= 0.5f;  // Weak tension when near max
        }
        
        return tension;
    }
    
    /**
     * Check if rope can wrap around obstacles.
     * Currently just a placeholder - full raycasting deferred to Phase 3+.
     */
    public static boolean shouldRopeWrap(LocalPlayer player, Vec3 anchorPos) {
        // Simple heuristic: if rope is long, it might wrap
        double ropeLen = getRopeLength(player, anchorPos);
        return ropeLen > 30.0;  // Only consider wrap for long ropes
    }
}
