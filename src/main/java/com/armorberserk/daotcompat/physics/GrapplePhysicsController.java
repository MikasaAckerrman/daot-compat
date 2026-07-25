package com.armorberserk.daotcompat.physics;

import com.armorberserk.daotcompat.aot.AOTReflect;
import com.armorberserk.daotcompat.input.GrappleStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import java.util.HashMap;
import java.util.Map;

/**
 * Phase 2 REWRITE (v1.2.0): Rope physics with collision detection.
 *
 * CRITICAL FIXES:
 * 1. Removed auto-grapple attraction (magnet effect)
 * 2. Added rope length constraint
 * 3. Added rope collision detection (Task 1.3)
 * 4. Added rope wrapping around block edges (v1.3.0)
 * 
 * New Logic:
 * - Hooks create TENSION (constraint), not force
 * - Rope distance LIMITED to MAX_ROPE_LENGTH
 * - Rope WRAPS around block edges (segments)
 * - Player velocity PRESERVED (inertia)
 * - SPACE held → prepares pulling (no auto-pull yet)
 * 
 * Future (Task 2.2):
 * - SPACE will shorten rope for actual pulling
 * - SHIFT will lengthen rope for release
 */
@OnlyIn(Dist.CLIENT)
public class GrapplePhysicsController {
    
    private static final double MAX_ROPE_LENGTH = 48.0;  // blocks
    private static final double MIN_ROPE_LENGTH = 2.0;   // blocks (can't pull closer than this)
    private static final double REEL_SPEED = 0.4;        // blocks per tick when pulling
    private static final double RELEASE_SPEED = 0.2;     // blocks per tick when releasing
    private static final double DESCEND_SPEED = 0.10;
    private static final double MIN_HOOK_RADIUS_SQR = 4.0;
    
    // Task 2.2: Current rope length (per hook engagement)
    private static double currentRopeLength = MAX_ROPE_LENGTH;
    
    // Rope segment handlers (for wrapping detection) - per hook
    private static final Map<Integer, RopeSegmentHandler> segmentHandlers = new HashMap<>();
    
    // Track hook states for sound effects
    private static boolean prevLeftHookActive = false;
    private static boolean prevRightHookActive = false;
    
    public static void tick(LocalPlayer player, Object leftHook, Object rightHook) {
        // Hook objects passed as parameters (cached from DAOTCompat)
        // Avoids expensive AOTReflect calls here
        
        boolean hasLeft = leftHook != null;
        boolean hasRight = rightHook != null;
        
        // 🔊 SOUND EFFECTS FOR ROPE ENGAGEMENT
        // Left hook zipped/unzipped
        if (hasLeft && !prevLeftHookActive) {
            // 🎯 Rope attached to surface
            playRopeHookSound(player, true);
            prevLeftHookActive = true;
        } else if (!hasLeft && prevLeftHookActive) {
            // ❌ Rope broke/released
            playRopeBreakSound(player);
            prevLeftHookActive = false;
        }
        
        // Right hook zipped/unzipped
        if (hasRight && !prevRightHookActive) {
            // 🎯 Rope attached to surface
            playRopeHookSound(player, false);
            prevRightHookActive = true;
        } else if (!hasRight && prevRightHookActive) {
            // ❌ Rope broke/released
            playRopeBreakSound(player);
            prevRightHookActive = false;
        }
        
        // No hooks → normal gravity, reset rope length
        if (!hasLeft && !hasRight) {
            currentRopeLength = MAX_ROPE_LENGTH;
            return;
        }
        
        // Task 2.2: Handle SPACE (pulling) and SHIFT (releasing)
        if (GrappleStateManager.isPullingRope()) {
            // SPACE held → Shorten rope (pull toward hook)
            currentRopeLength = Math.max(MIN_ROPE_LENGTH, currentRopeLength - REEL_SPEED);
        } else if (GrappleStateManager.isDescending()) {
            // SHIFT held → Lengthen rope (release)
            currentRopeLength = Math.min(MAX_ROPE_LENGTH, currentRopeLength + RELEASE_SPEED);
        } else {
            // Neither held → Gradually restore to max (slack)
            if (currentRopeLength < MAX_ROPE_LENGTH) {
                currentRopeLength = Math.min(MAX_ROPE_LENGTH, currentRopeLength + RELEASE_SPEED * 0.5);
            }
        }
        
        // Check rope collision
        checkRopeCollision(player, leftHook, rightHook);
        
        // Apply rope constraint with current rope length
        applyRopeConstraint(player, leftHook, rightHook);
    }
    
    /**
     * 🔊 Play sound when rope hooks/zips to surface.
     * Different pitches for left vs right for stereo effect.
     */
    private static void playRopeHookSound(LocalPlayer player, boolean isLeftHook) {
        if (player == null) return;
        float pitch = isLeftHook ? 0.85f : 1.15f;  // Left lower, right higher
        player.playSound(SoundEvents.TRIPWIRE_CLICK_ON, 0.7f, pitch);
    }
    
    /**
     * 🔊 Play sound when rope breaks/detaches from surface.
     */
    private static void playRopeBreakSound(LocalPlayer player) {
        if (player == null) return;
        player.playSound(SoundEvents.CHAIN_BREAK, 0.6f, 0.8f + (float) Math.random() * 0.4f);
    }
    
    /**
     * Apply rope length constraint with proper rope wrapping support.
     * If player is beyond rope length from hook (accounting for wraps), constrain to sphere.
     * Preserves tangential velocity (pendulum effect).
     * Removes radial velocity (toward/away from hook).
     * 
     * UPDATED (v1.3.0): Uses RopeSegmentHandler for accurate rope length calculation
     * when rope wraps around block edges.
     */
    private static void applyRopeConstraint(LocalPlayer player, Object leftHook, Object rightHook) {
        Vec3 playerPos = player.position();
        Vec3 closestHookPos = null;
        double minDistanceSqr = Double.MAX_VALUE;
        Object closestHook = null;
        
        // Find closest hook
        if (leftHook != null) {
            Vec3 hookPos = AOTReflect.getPosition(leftHook);
            if (hookPos != null) {
                double distSqr = playerPos.distanceToSqr(hookPos);
                if (distSqr < minDistanceSqr) {
                    minDistanceSqr = distSqr;
                    closestHookPos = hookPos;
                    closestHook = leftHook;
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
                    closestHook = rightHook;
                }
            }
        }
        
        if (closestHookPos == null || player.level() == null) return;
        
        // Update rope segment handler for wrapping detection
        int hookId = closestHook != null ? closestHook.hashCode() : 0;
        final Vec3 finalClosestHookPos = closestHookPos;  // Make effectively final for lambda
        final Vec3 finalPlayerPos = playerPos;             // Make effectively final for lambda
        RopeSegmentHandler handler = segmentHandlers.computeIfAbsent(hookId, 
            k -> new RopeSegmentHandler(finalClosestHookPos, finalPlayerPos));
        
        // Update segments with current positions
        handler.update(closestHookPos, playerPos, currentRopeLength, player.level());
        
        // Calculate actual rope distance (accounting for wraps)
        double actualRopeDistance = calculateActualRopeDistance(handler, closestHookPos, playerPos);
        
        // If within current rope length → no constraint
        if (actualRopeDistance <= currentRopeLength) {
            return;
        }
        
        // Beyond rope length → constrain to sphere surface
        Vec3 towardHook = closestHookPos.subtract(playerPos).normalize();
        Vec3 constrainedPos = closestHookPos.subtract(towardHook.scale(currentRopeLength));
        player.setPos(constrainedPos.x, constrainedPos.y, constrainedPos.z);
        
        // Remove radial velocity component (velocity toward/away from hook)
        Vec3 velocity = player.getDeltaMovement();
        double radialSpeed = velocity.dot(towardHook);
        if (radialSpeed > 0) {
            Vec3 newVelocity = velocity.subtract(towardHook.scale(radialSpeed));
            player.setDeltaMovement(newVelocity);
        }
    }
    
    /**
     * Calculate actual rope distance from hook to player, accounting for segment wraps.
     * If rope wraps around block edges, distance = sum of all segment lengths.
     * Otherwise, straight line distance.
     */
    private static double calculateActualRopeDistance(RopeSegmentHandler handler, Vec3 hookPos, Vec3 playerPos) {
        // Get all segment points (includes wraps)
        Vec3[] segments = handler.getSegments();
        
        if (segments.length < 2) {
            // No segments or wraps - use straight line
            return hookPos.distanceTo(playerPos);
        }
        
        // Sum distances along all segments
        double totalDistance = 0.0;
        for (int i = 0; i < segments.length - 1; i++) {
            totalDistance += segments[i].distanceTo(segments[i + 1]);
        }
        
        return totalDistance;
    }

    /**
     * Task 1.3: Check if rope collides with blocks.
     * If rope hits a block (not hook location), break the hook.
     * 🔊 Plays break sound when rope snaps.
     */
    private static void checkRopeCollision(LocalPlayer player, Object leftHook, Object rightHook) {
        // Check left hook
        if (leftHook != null) {
            Vec3 hookPos = AOTReflect.getPosition(leftHook);
            if (hookPos != null && checkCollisionBetween(player, hookPos)) {
                AOTReflect.release(leftHook);  // Break the hook
                playRopeBreakSound(player);     // 🔊 Sound when rope snaps
                prevLeftHookActive = false;
                return;
            }
        }
        
        // Check right hook
        if (rightHook != null) {
            Vec3 hookPos = AOTReflect.getPosition(rightHook);
            if (hookPos != null && checkCollisionBetween(player, hookPos)) {
                AOTReflect.release(rightHook);  // Break the hook
                playRopeBreakSound(player);     // 🔊 Sound when rope snaps
                prevRightHookActive = false;
                return;
            }
        }
    }

    /**
     * Raycast between player and hook, check if rope would hit a block.
     * Returns true if collision detected (rope should break).
     */
    private static boolean checkCollisionBetween(LocalPlayer player, Vec3 targetPos) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || p.level() == null) return false;
        
        Vec3 fromPos = p.position();
        ClipContext context = new ClipContext(fromPos, targetPos,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                p);
        
        BlockHitResult hit = p.level().clip(context);
        
        // If raycast hit something → collision detected
        return hit != null && hit.getBlockPos() != null;
    }
}
