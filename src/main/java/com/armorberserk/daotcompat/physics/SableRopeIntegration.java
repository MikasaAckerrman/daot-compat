package com.armorberserk.daotcompat.physics;

import com.armorberserk.daotcompat.aot.AOTReflect;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import java.util.HashMap;
import java.util.Map;

/**
 * Phase 3+: Sable RopePhysicsObject integration (v1.3.0).
 * 
 * IMPROVED (v1.3.0): Uses RopeSegmentHandler as fallback
 * for rope point generation. When Sable is NOT available,
 * we use our own segment-based wrapping system.
 * 
 * When Sable IS available (optional dependency):
 * - RopePhysicsObject handles physics
 * - We query it for visual rope points
 * - Full rope wrapping physics support
 */
@OnlyIn(Dist.CLIENT)
public class SableRopeIntegration {
    
    // Rope handlers per hook (same as GrapplePhysicsController)
    private static final Map<Integer, RopeSegmentHandler> segmentHandlers = new HashMap<>();
    
    private static final double MAX_ROPE_LENGTH = 48.0;
    private static final double ROPE_SEGMENT_COUNT = 16;
    
    /**
     * Create and register rope physics object.
     * Called when rope is engaged (PULL_ROPE pressed).
     * 
     * v1.3.0: Integrates with our RopeSegmentHandler for fallback
     * when Sable is not available.
     */
    public static void createRope(LocalPlayer player, Object leftHook, Object rightHook) {
        if (leftHook == null && rightHook == null) {
            return;
        }
        
        Vec3 playerPos = player.position();
        Vec3 anchorPos = null;
        Object activeHook = null;
        
        // Get anchor position (use first available hook)
        if (leftHook != null) {
            anchorPos = AOTReflect.getPosition(leftHook);
            activeHook = leftHook;
        } else if (rightHook != null) {
            anchorPos = AOTReflect.getPosition(rightHook);
            activeHook = rightHook;
        }
        
        if (anchorPos == null || activeHook == null) return;
        
        // Create segment handler if needed
        int hookId = activeHook.hashCode();
        segmentHandlers.putIfAbsent(hookId, new RopeSegmentHandler(anchorPos, playerPos));
        
        // TODO: Optional Sable integration
        // if (SableAvailable) {
        //     RopePhysicsObject rope = new RopePhysicsObject(
        //         playerPos,
        //         anchorPos,
        //         MAX_ROPE_LENGTH,
        //         (int)ROPE_SEGMENT_COUNT
        //     );
        //     PhysicsPipeline.addRope(rope);
        // }
    }
    
    /**
     * Update rope physics (called every tick).
     * 
     * v1.3.0: Uses RopeSegmentHandler for wrapping detection
     * when Sable is not available.
     */
    public static void updateRope(LocalPlayer player, Object leftHook, Object rightHook, double ropeLen) {
        if (leftHook == null && rightHook == null) {
            return;
        }
        
        Vec3 playerPos = player.position();
        Vec3 anchorPos = null;
        Object activeHook = null;
        
        if (leftHook != null) {
            anchorPos = AOTReflect.getPosition(leftHook);
            activeHook = leftHook;
        } else if (rightHook != null) {
            anchorPos = AOTReflect.getPosition(rightHook);
            activeHook = rightHook;
        }
        
        if (anchorPos == null || activeHook == null) return;
        
        // Update segment handler
        int hookId = activeHook.hashCode();
        RopeSegmentHandler handler = segmentHandlers.get(hookId);
        if (handler != null && player.level() != null) {
            handler.update(anchorPos, playerPos, ropeLen, player.level());
        }
        
        // TODO: Optional Sable integration
        // if (SableAvailable) {
        //     ropeHandle.updatePos(playerPos, anchorPos, ropeLen);
        // }
    }
    
    /**
     * Get rope points for visual rendering.
     * Returns interpolated points along the rope path,
     * including wrapping around obstacles.
     * 
     * IMPROVED (v1.3.0):
     * - First tries Sable if available
     * - Falls back to RopeSegmentHandler segments
     * - Finally falls back to straight line with sag
     */
    public static Vec3[] getRopePoints(LocalPlayer player, Object leftHook, Object rightHook) {
        if (leftHook == null && rightHook == null) {
            return new Vec3[0];
        }
        
        Vec3 playerPos = player.position();
        Vec3 anchorPos = null;
        Object activeHook = null;
        
        if (leftHook != null) {
            anchorPos = AOTReflect.getPosition(leftHook);
            activeHook = leftHook;
        } else if (rightHook != null) {
            anchorPos = AOTReflect.getPosition(rightHook);
            activeHook = rightHook;
        }
        
        if (anchorPos == null) {
            return new Vec3[0];
        }
        
        // Try to get points from segment handler (our rope wrapping system)
        int hookId = activeHook.hashCode();
        RopeSegmentHandler handler = segmentHandlers.get(hookId);
        if (handler != null) {
            Vec3[] segments = handler.getSegments();
            if (segments.length > 0) {
                return interpolateRopePoints(segments);
            }
        }
        
        // TODO: If Sable available, use: ropeHandle.getSegments()
        
        // Fallback: straight line with sag
        return generateRopePointsWithSag(playerPos, anchorPos);
    }
    
    /**
     * Remove rope from physics engine (called when rope is released).
     */
    public static void removeRope(Object hook) {
        if (hook == null) return;
        
        int hookId = hook.hashCode();
        segmentHandlers.remove(hookId);
        
        // TODO: Optional Sable integration
        // if (SableAvailable) {
        //     PhysicsPipeline.removeRope(ropeHandle);
        // }
    }
    
    /**
     * Synchronize segment handler from physics system (GrapplePhysicsController).
     * Called after handler.update() to ensure physics and rendering use same data.
     * 
     * [FIX v1.3.5] Fix rope wrapping synchronization
     * GrapplePhysicsController updates its own handler, we need to sync it
     * so RopeLineRenderer gets the same segment data for correct visualization.
     */
    public static void syncHandler(int hookId, RopeSegmentHandler handler) {
        if (handler != null) {
            segmentHandlers.put(hookId, handler);
        }
    }
    
    /**
     * Interpolate smooth rope points from segment corners.
     * Takes the discrete segment points and creates a smooth curve.
     */
    private static Vec3[] interpolateRopePoints(Vec3[] segments) {
        if (segments.length < 2) {
            return segments;
        }
        
        // For now, return segments as-is
        // Future: interpolate between segment corners for smooth curve
        return segments;
    }
    
    /**
     * Generate rope points with visual sag (fallback when Sable not available).
     * Creates a parabolic curve from anchor to player.
     */
    private static Vec3[] generateRopePointsWithSag(Vec3 playerPos, Vec3 anchorPos) {
        int segments = 10;
        Vec3[] points = new Vec3[segments];
        
        double distance = playerPos.distanceTo(anchorPos);
        double sag = Math.min(distance / 8.0, 2.0);  // Visual sag amount
        
        for (int i = 0; i < segments; i++) {
            double t = (double) i / (segments - 1);
            Vec3 lerped = playerPos.lerp(anchorPos, t);
            
            // Add sag (parabolic drop in middle)
            double sagAmount = sag * Math.sin(t * Math.PI);
            Vec3 sagged = new Vec3(lerped.x, lerped.y - sagAmount, lerped.z);
            
            points[i] = sagged;
        }
        
        return points;
    }
}

