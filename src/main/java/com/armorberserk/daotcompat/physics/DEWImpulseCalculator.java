package com.armorberserk.daotcompat.physics;

import com.armorberserk.daotcompat.aot.AOTReflect;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * DEW impulse calculation.
 * Base impulse = look direction + 15° up + velocity momentum
 * Multipliers: rope (1.0-2.3x), altitude (0.85x ground), speed (+0.2-0.4x)
 */
@OnlyIn(Dist.CLIENT)
public class DEWImpulseCalculator {
    
    private static final float BASE_IMPULSE = 2.5f;
    private static final float UPWARD_TILT = 17.5f * (float) Math.PI / 180.0f;
    
    public static Vec3 calculateDEW(LocalPlayer player) {
        Vec3 lookDir = player.getLookAngle();
        
        // Add upward tilt
        Vec3 tiltedDir = new Vec3(
            lookDir.x,
            lookDir.y + Math.sin(UPWARD_TILT),
            lookDir.z
        ).normalize();
        
        // Add momentum (preserve current velocity)
        Vec3 currentVel = player.getDeltaMovement();
        Vec3 impulseDir = tiltedDir.scale(0.8).add(currentVel.normalize().scale(0.2));
        
        // Calculate multipliers
        float ropeMultiplier = calculateRopeMultiplier(player);
        float altitudeMultiplier = player.onGround() ? 0.85f : 1.0f;
        float speedMultiplier = 1.0f + (float)(currentVel.horizontalDistance() / 20.0) * 0.3f;
        
        float strength = BASE_IMPULSE * ropeMultiplier * altitudeMultiplier * speedMultiplier;
        
        return impulseDir.normalize().scale(strength);
    }
    
    public static Vec3 calculateReverseDEW(LocalPlayer player) {
        Vec3 impulse = calculateDEW(player);
        return impulse.scale(-0.85f);  // Reverse direction, 85% strength
    }
    
    private static float calculateRopeMultiplier(LocalPlayer player) {
        Object left = AOTReflect.getLeftHook();
        Object right = AOTReflect.getRightHook();
        
        if (left == null && right == null) return 1.0f;
        
        // Simple: if hooks active, boost multiplier
        int hooksActive = 0;
        if (left != null) {
            Vec3 pos = AOTReflect.getPosition(left);
            if (pos != null) hooksActive++;
        }
        if (right != null) {
            Vec3 pos = AOTReflect.getPosition(right);
            if (pos != null) hooksActive++;
        }
        
        if (hooksActive == 0) return 1.0f;
        return 1.0f + (hooksActive * 0.65f);  // 1.65x for 1 hook, 2.3x for 2 hooks
    }
}
