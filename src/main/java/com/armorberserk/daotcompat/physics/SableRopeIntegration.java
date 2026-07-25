package com.armorberserk.daotcompat.physics;

import com.armorberserk.daotcompat.aot.AOTReflect;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Phase 3+: Sable RopePhysicsObject integration.
 * 
 * Creates a physical rope that wraps around blocks/obstacles.
 * Sable handles the wrapping logic automatically.
 * 
 * We only need to:
 * 1. Create RopePhysicsObject when rope engaged
 * 2. Update it every tick
 * 3. Query it for visual rope points
 * 4. Remove when rope released
 */
@OnlyIn(Dist.CLIENT)
public class SableRopeIntegration {
    
    /**
     * Create and register rope physics object.
     * Called when rope is engaged (PULL_ROPE pressed).
     * 
     * Note: This is a placeholder. Full Sable integration requires:
     * - PhysicsPipeline.addRope() call
     * - RopeHandle tracking
     * - Segment position queries
     * 
     * Since we don't have Sable source available at compile time,
     * we use reflection or optional dependency approach.
     */
    public static void createRope(LocalPlayer player) {
        Object leftHook = AOTReflect.getLeftHook();
        Object rightHook = AOTReflect.getRightHook();
        
        if (leftHook == null && rightHook == null) {
            return;
        }
        
        Vec3 playerPos = player.position();
        Vec3 anchorPos = null;
        
        // Get anchor position (use first available hook)
        if (leftHook != null) {
            anchorPos = AOTReflect.getPosition(leftHook);
        } else if (rightHook != null) {
            anchorPos = AOTReflect.getPosition(rightHook);
        }
        
        if (anchorPos == null) return;
        
        // TODO: Create RopePhysicsObject
        // RopePhysicsObject rope = new RopePhysicsObject(
        //     playerPos,
        //     anchorPos,
        //     MAX_ROPE_LENGTH,
        //     ROPE_SEGMENT_COUNT
        // );
        // PhysicsPipeline.addRope(rope);
    }
    
    /**
     * Get rope points for visual rendering.
     * Returns interpolated points along the rope path,
     * including wrapping around obstacles.
     * 
     * If Sable integration available: use RopeHandle.getSegments()
     * Otherwise: return straight line fallback
     */
    public static Vec3[] getRopePoints(LocalPlayer player) {
        Object leftHook = AOTReflect.getLeftHook();
        Object rightHook = AOTReflect.getRightHook();
        
        if (leftHook == null && rightHook == null) {
            return new Vec3[0];
        }
        
        Vec3 playerPos = player.position();
        Vec3 anchorPos = null;
        
        if (leftHook != null) {
            anchorPos = AOTReflect.getPosition(leftHook);
        } else if (rightHook != null) {
            anchorPos = AOTReflect.getPosition(rightHook);
        }
        
        if (anchorPos == null) {
            return new Vec3[0];
        }
        
        // Fallback: straight line with slight sag
        return generateRopePointsWithSag(playerPos, anchorPos);
    }
    
    /**
     * Generate rope points with visual sag (fallback when Sable not available).
     */
    private static Vec3[] generateRopePointsWithSag(Vec3 start, Vec3 end) {
        int segments = 10;
        Vec3[] points = new Vec3[segments];
        
        double distance = start.distanceTo(end);
        double sag = Math.min(distance / 8.0, 2.0);  // Visual sag amount
        
        for (int i = 0; i < segments; i++) {
            double t = (double) i / (segments - 1);
            Vec3 lerped = start.lerp(end, t);
            
            // Add sag (parabolic drop in middle)
            double sagAmount = sag * Math.sin(t * Math.PI);
            Vec3 sagged = new Vec3(lerped.x, lerped.y - sagAmount, lerped.z);
            
            points[i] = sagged;
        }
        
        return points;
    }
    
    /**
     * Clean up rope physics on release.
     */
    public static void removeRope() {
        // TODO: PhysicsPipeline.removeRope(rope);
    }
}
