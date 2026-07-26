package com.armorberserk.daotcompat.physics;

import com.armorberserk.daotcompat.aot.AOTReflect;
import com.armorberserk.daotcompat.gas.GasManager;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Task 2.1 REWRITE (v1.3.0): DEW impulse properly adds to velocity.
 *
 * CRITICAL FIX: DEW now ADDS impulse to current velocity, not replaces it.
 * This preserves momentum and creates smooth, predictable acceleration.
 *
 * Physics:
 * - Current velocity is preserved
 * - DEW adds directional impulse in look direction
 * - Upward tilt creates natural arc
 * - Rope multiplier increases strength when engaged
 * - Speed multiplier adapts to current velocity
 */
@OnlyIn(Dist.CLIENT)
public class DEWImpulseCalculator {
    
    private static final double BASE_IMPULSE = 0.18;  // [FIX v1.3.5] increased from 0.12 (1.5x stronger)
    private static final double UPWARD_TILT = 15.0 * Math.PI / 180.0;  // 15 degrees up
    private static final double MAX_VELOCITY = 2.8;  // blocks per tick [FIX v1.3.5: prevent unbounded acceleration]
    
    /**
     * Calculate DEW forward impulse.
     * Adds momentum to current velocity in the look direction.
     * 
     * [FIX v1.3.5] Added velocity cap to prevent unbounded acceleration.
     * Calculates what impulse would be, then clamps result to MAX_VELOCITY.
     */
    public static Vec3 calculateDEW(LocalPlayer player) {
        Vec3 currentVel = player.getDeltaMovement();
        Vec3 lookDir = player.getLookAngle();
        
        // Apply upward tilt to look direction
        double horizontalLength = Math.sqrt(lookDir.x * lookDir.x + lookDir.z * lookDir.z);
        Vec3 tiltedDir = new Vec3(
            lookDir.x,
            lookDir.y + Math.sin(UPWARD_TILT) * 0.5,  // Moderate upward component
            lookDir.z
        ).normalize();
        
        // Calculate base impulse strength
        double gasPercentage = GasManager.getGasPercent() / 100.0;  // 0-1
        double ropeMultiplier = calculateRopeMultiplier(player);
        double speedMultiplier = 1.0 + (currentVel.length() / 20.0) * 0.4;  // Higher speed = stronger impulse
        double altitudeBonus = player.onGround() ? 0.9 : 1.1;  // Bonus in air, penalty on ground
        
        double strength = BASE_IMPULSE * gasPercentage * ropeMultiplier * speedMultiplier * altitudeBonus;
        
        // [FIX v1.3.5] Apply velocity cap after calculating impulse
        // This prevents unbounded acceleration while preserving momentum direction
        Vec3 impulse = tiltedDir.scale(strength);
        Vec3 newVelocity = currentVel.add(impulse);
        double newSpeed = newVelocity.length();
        
        if (newSpeed > MAX_VELOCITY) {
            // Clamp to MAX_VELOCITY while preserving direction
            Vec3 clampedVel = newVelocity.normalize().scale(MAX_VELOCITY);
            return clampedVel.subtract(currentVel);  // Return the clamped impulse
        }
        
        // Return impulse vector (will be added to current velocity)
        return impulse;
    }
    
    /**
     * Calculate DEW reverse impulse (backward + slightly down).
     * Used for controlled backward movement / braking.
     */
    public static Vec3 calculateReverseDEW(LocalPlayer player) {
        Vec3 currentVel = player.getDeltaMovement();
        Vec3 lookDir = player.getLookAngle();
        
        // Reverse direction (180 degrees)
        Vec3 reverseLook = lookDir.scale(-1.0);
        
        // Add downward tilt for control
        Vec3 tiltedDir = new Vec3(
            reverseLook.x,
            reverseLook.y - Math.sin(UPWARD_TILT) * 0.3,  // Slight downward
            reverseLook.z
        ).normalize();
        
        // Reverse DEW is slightly weaker (80% strength)
        double gasPercentage = GasManager.getGasPercent() / 100.0;
        double ropeMultiplier = calculateRopeMultiplier(player);
        double speedMultiplier = 1.0 + (currentVel.length() / 25.0) * 0.3;
        double altitudeBonus = player.onGround() ? 0.85 : 1.0;
        
        double strength = BASE_IMPULSE * 1.0 * gasPercentage * ropeMultiplier * speedMultiplier * altitudeBonus;
        
        // [FIX v1.3.5] Apply velocity cap for Reverse DEW (same logic as forward)
        Vec3 impulse = tiltedDir.scale(strength);
        Vec3 newVelocity = currentVel.add(impulse);
        double newSpeed = newVelocity.length();
        
        if (newSpeed > MAX_VELOCITY) {
            Vec3 clampedVel = newVelocity.normalize().scale(MAX_VELOCITY);
            return clampedVel.subtract(currentVel);
        }
        
        return impulse;
    }
    
    /**
     * Calculate rope engagement multiplier.
     * Grapple hooks provide mechanical advantage.
     * 1 hook = 1.6x, 2 hooks = 2.3x
     */
    private static double calculateRopeMultiplier(LocalPlayer player) {
        Object left = AOTReflect.getLeftHook();
        Object right = AOTReflect.getRightHook();
        
        int hooksActive = 0;
        
        if (left != null) {
            Vec3 pos = AOTReflect.getPosition(left);
            if (pos != null && pos.distanceToSqr(player.position()) < 48.0 * 48.0) {
                hooksActive++;
            }
        }
        
        if (right != null) {
            Vec3 pos = AOTReflect.getPosition(right);
            if (pos != null && pos.distanceToSqr(player.position()) < 48.0 * 48.0) {
                hooksActive++;
            }
        }
        
        if (hooksActive == 0) return 1.0;
        if (hooksActive == 1) return 1.6;
        return 2.3;  // Both hooks active
    }
}
