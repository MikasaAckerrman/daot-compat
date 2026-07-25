package com.armorberserk.daotcompat.physics;

import com.armorberserk.daotcompat.aot.AOTReflect;
import com.armorberserk.daotcompat.input.GrappleStateManager;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Phase 2 REWRITE (v1.2.0): Rope physics controller with proper constraints.
 *
 * CRITICAL FIX: Removed auto-grapple attraction (magnet effect)
 * 
 * New Logic:
 * - Hooks create TENSION (constraint), not force
 * - SPACE held → Prepares pulling (no auto-pull yet)
 * - Rope distance is LIMITED (MAX_ROPE_LENGTH)
 * - Player velocity is PRESERVED (inertia works)
 * 
 * Future (Task 2.2):
 * - SPACE will shorten rope for actual pulling
 * - SHIFT will lengthen rope for release
 */
@OnlyIn(Dist.CLIENT)
public class GrapplePhysicsController {
    
    private static final double MAX_ROPE_LENGTH = 48.0;  // blocks
    private static final double DESCEND_SPEED = 0.10;
    private static final double MIN_HOOK_RADIUS_SQR = 4.0;  // Too close to anchor
    
    public static void tick(LocalPlayer player) {
        Object leftHook = AOTReflect.getLeftHook();
        Object rightHook = AOTReflect.getRightHook();
        
        boolean hasLeft = leftHook != null;
        boolean hasRight = rightHook != null;
        
        // No hooks → normal gravity
        if (!hasLeft && !hasRight) return;
        
        // Hooks exist → apply rope constraint (limit distance)
        // This applies regardless of SPACE, gives tension feel
        applyRopeConstraint(player, leftHook, rightHook);
        
        // SPACE held → Prepare pulling (no auto-velocity added)
        // TODO (Task 2.2): Implement rope shortening here
        if (GrappleStateManager.isPullingRope()) {
            // Currently just holding tension
            // Future: shorten currentRopeLength
        }
        
        // SHIFT held → Controlled descent (small downward velocity)
        if (GrappleStateManager.isDescending()) {
            player.setDeltaMovement(player.getDeltaMovement().add(0, -DESCEND_SPEED, 0));
        }
    }
    
    /**
     * Apply rope length constraint.
     * If player is beyond MAX_ROPE_LENGTH from hook(s), return them to the boundary.
     * Preserves tangential velocity (pendulum effect).
     * Removes radial velocity (toward/away from hook).
     */
    private static void applyRopeConstraint(LocalPlayer player, Object leftHook, Object rightHook) {
        Vec3 playerPos = player.position();
        Vec3 closestHookPos = null;
        double minDistanceSqr = Double.MAX_VALUE;
        
        // Find closest hook
        if (leftHook != null) {
            Vec3 hookPos = AOTReflect.getPosition(leftHook);
            if (hookPos != null) {
                double distSqr = playerPos.distanceToSqr(hookPos);
                if (distSqr < minDistanceSqr) {
                    minDistanceSqr = distSqr;
                    closestHookPos = hookPos;
                }
            }
        }
        
        if (rightHook != null) {
            Vec3 hookPos = AOTReflect.getPosition(rightHook);
            if (hookPos != null) {
                double distSqr = playerPos.distanceToSqr(hookPos);
                if (distSqr < minDistanceSqr) {
                    minDistanceSqr = distSqr;
                    closestHookPos = hookPos;
                }
            }
        }
        
        if (closestHookPos == null) return;
        
        double distance = Math.sqrt(minDistanceSqr);
        
        // If within rope length → no constraint
        if (distance <= MAX_ROPE_LENGTH) {
            return;
        }
        
        // Beyond rope length → constrain to sphere surface
        Vec3 towardHook = closestHookPos.subtract(playerPos).normalize();
        Vec3 constrainedPos = closestHookPos.subtract(towardHook.scale(MAX_ROPE_LENGTH));
        player.setPos(constrainedPos.x, constrainedPos.y, constrainedPos.z);
        
        // Remove radial velocity component (velocity toward/away from hook)
        Vec3 velocity = player.getDeltaMovement();
        double radialSpeed = velocity.dot(towardHook);
        if (radialSpeed > 0) {
            // Remove outward radial component
            Vec3 newVelocity = velocity.subtract(towardHook.scale(radialSpeed));
            player.setDeltaMovement(newVelocity);
        }
    }
}
