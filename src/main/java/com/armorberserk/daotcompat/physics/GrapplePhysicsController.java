package com.armorberserk.daotcompat.physics;

import com.armorberserk.daotcompat.aot.AOTReflect;
import com.armorberserk.daotcompat.input.GrappleStateManager;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Phase 2: New keybind-controlled rope physics.
 * 
 * Logic:
 * - PULL_ROPE (SPACE) held → pull toward hooks
 * - PULL_ROPE released → player falls freely
 * - W + PULL_ROPE → accelerate toward hooks
 * - SHIFT + PULL_ROPE → controlled descent
 */
@OnlyIn(Dist.CLIENT)
public class GrapplePhysicsController {
    
    private static final double PULL_STRENGTH = 0.15;
    private static final double ACCEL_STRENGTH = 0.20;
    private static final double DESCEND_SPEED = 0.10;
    private static final double MIN_HOOK_RADIUS_SQR = 4.0;  // Too close to anchor
    
    public static void tick(LocalPlayer player) {
        Object leftHook = AOTReflect.getLeftHook();
        Object rightHook = AOTReflect.getRightHook();
        
        boolean hasLeft = leftHook != null;
        boolean hasRight = rightHook != null;
        
        // No hooks → normal gravity
        if (!hasLeft && !hasRight) return;
        
        // Hooks exist but player NOT pulling → apply gravity (slack rope)
        if (!GrappleStateManager.isPullingRope()) {
            applyGravity(player);
            return;
        }
        
        // PULL_ROPE is held → apply pulling force toward hooks
        Vec3 playerPos = player.position();
        Vec3 pullDir = Vec3.ZERO;
        int hooksContributing = 0;
        
        // Left hook
        if (hasLeft) {
            Vec3 hookPos = AOTReflect.getPosition(leftHook);
            if (hookPos != null) {
                Vec3 toHook = hookPos.subtract(playerPos);
                if (toHook.lengthSqr() > MIN_HOOK_RADIUS_SQR) {
                    pullDir = pullDir.add(toHook.normalize());
                    hooksContributing++;
                }
            }
        }
        
        // Right hook
        if (hasRight) {
            Vec3 hookPos = AOTReflect.getPosition(rightHook);
            if (hookPos != null) {
                Vec3 toHook = hookPos.subtract(playerPos);
                if (toHook.lengthSqr() > MIN_HOOK_RADIUS_SQR) {
                    pullDir = pullDir.add(toHook.normalize());
                    hooksContributing++;
                }
            }
        }
        
        if (hooksContributing == 0) return;
        
        // Normalize pull direction
        pullDir = pullDir.normalize();
        
        // Calculate pull force
        double pull = PULL_STRENGTH;
        if (GrappleStateManager.canAccelerate()) {
            pull += ACCEL_STRENGTH;  // W key bonus
        }
        
        // Apply pull
        Vec3 vel = player.getDeltaMovement();
        player.setDeltaMovement(vel.add(pullDir.scale(pull)));
        
        // SHIFT: controlled descent
        if (GrappleStateManager.isDescending()) {
            player.setDeltaMovement(player.getDeltaMovement().add(0, -DESCEND_SPEED, 0));
        }
    }
    
    private static void applyGravity(LocalPlayer player) {
        // Rope is slack, let gravity apply (no special handling)
        // Minecraft gravity applies automatically
    }
}
