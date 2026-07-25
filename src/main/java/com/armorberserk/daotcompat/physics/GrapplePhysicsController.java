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
    
    // Sound cooldown to prevent spam [FIX v1.3.2]
    private static int leftHookSoundCooldown = 0;
    private static int rightHookSoundCooldown = 0;
    private static final int SOUND_COOLDOWN_TICKS = 5;  // Minimum 5 ticks between sounds
    
    // Collision check cooldown to prevent lag [FIX v1.3.2]
    private static int collisionCheckCooldown = 0;
    private static final int COLLISION_CHECK_INTERVAL = 10;  // Check every 10 ticks (not every tick)
    
    public static void tick(LocalPlayer player, Object leftHook, Object rightHook) {
        // Hook objects passed as parameters (cached from DAOTCompat)
        // Avoids expensive AOTReflect calls here
        
        // Decrement sound cooldowns [FIX v1.3.2]
        if (leftHookSoundCooldown > 0) leftHookSoundCooldown--;
        if (rightHookSoundCooldown > 0) rightHookSoundCooldown--;
        
        boolean hasLeft = leftHook != null;
        boolean hasRight = rightHook != null;
        
        // 🔊 SOUND EFFECTS FOR ROPE ENGAGEMENT
        // Left hook zipped/unzipped [FIXED v1.3.2: Added cooldown]
        if (hasLeft && !prevLeftHookActive && leftHookSoundCooldown <= 0) {
            // 🎯 Rope attached to surface
            playRopeHookSound(player, true);
            prevLeftHookActive = true;
            leftHookSoundCooldown = SOUND_COOLDOWN_TICKS;
        } else if (!hasLeft && prevLeftHookActive && leftHookSoundCooldown <= 0) {
            // ❌ Rope broke/released
            playRopeBreakSound(player);
            prevLeftHookActive = false;
            leftHookSoundCooldown = SOUND_COOLDOWN_TICKS;
        }
        
        // Right hook zipped/unzipped [FIXED v1.3.2: Added cooldown]
        if (hasRight && !prevRightHookActive && rightHookSoundCooldown <= 0) {
            // 🎯 Rope attached to surface
            playRopeHookSound(player, false);
            prevRightHookActive = true;
            rightHookSoundCooldown = SOUND_COOLDOWN_TICKS;
        } else if (!hasRight && prevRightHookActive && rightHookSoundCooldown <= 0) {
            // ❌ Rope broke/released
            playRopeBreakSound(player);
            prevRightHookActive = false;
            rightHookSoundCooldown = SOUND_COOLDOWN_TICKS;
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
     * 
     * IMPROVED (v1.3.1):
     * - Support for DUAL hooks with proper physics
     * - Both hooks apply constraint simultaneously when both active
     * - Angle limitation to prevent extreme rope angles
     * - Average constraint when both hooks active
     */
    private static void applyRopeConstraint(LocalPlayer player, Object leftHook, Object rightHook) {
        Vec3 playerPos = player.position();
        
        // Get both hook positions
        Vec3 leftPos = null;
        Vec3 rightPos = null;
        
        if (leftHook != null) {
            leftPos = AOTReflect.getPosition(leftHook);
        }
        if (rightHook != null) {
            rightPos = AOTReflect.getPosition(rightHook);
        }
        
        // If no hooks, return
        if (leftPos == null && rightPos == null) return;
        if (player.level() == null) return;
        
        // DUAL HOOK SUPPORT (v1.3.1): Apply constraint to BOTH when active
        if (leftPos != null) {
            applyRopeConstraintToHook(player, leftPos, leftHook, player.level());
        }
        if (rightPos != null) {
            applyRopeConstraintToHook(player, rightPos, rightHook, player.level());
        }
        
        // Limit view angle when rope is engaged (v1.3.1)
        limitRopeViewAngle(player, leftPos, rightPos);
    }
    
    /**
     * Apply rope constraint for a single hook.
     */
    private static void applyRopeConstraintToHook(LocalPlayer player, Vec3 hookPos, Object hook, 
                                                   net.minecraft.world.level.Level level) {
        Vec3 playerPos = player.position();
        
        // Update segment handler for this hook
        int hookId = hook.hashCode();
        final Vec3 finalHookPos = hookPos;
        final Vec3 finalPlayerPos = playerPos;
        RopeSegmentHandler handler = segmentHandlers.computeIfAbsent(hookId,
            k -> new RopeSegmentHandler(finalHookPos, finalPlayerPos));
        
        handler.update(hookPos, playerPos, currentRopeLength, level);
        double actualRopeDistance = calculateActualRopeDistance(handler, hookPos, playerPos);
        
        // If within rope length, no constraint needed
        if (actualRopeDistance <= currentRopeLength) {
            return;
        }
        
        // Constrain to sphere surface
        Vec3 towardHook = hookPos.subtract(playerPos).normalize();
        Vec3 constrainedPos = hookPos.subtract(towardHook.scale(currentRopeLength));
        player.setPos(constrainedPos.x, constrainedPos.y, constrainedPos.z);
        
        // Remove only radial velocity (toward/away from hook)
        Vec3 velocity = player.getDeltaMovement();
        double radialSpeed = velocity.dot(towardHook);
        if (radialSpeed > 0) {
            Vec3 newVelocity = velocity.subtract(towardHook.scale(radialSpeed));
            player.setDeltaMovement(newVelocity);
        }
        
        // 🎥 "ОЩУЩЕНИЕ НАТЯГА" (v1.3.1): Camera wobble when rope is tight
        // This gives player feedback that rope is engaged and constraining
        if (Minecraft.getInstance().player != null && 
            actualRopeDistance > currentRopeLength * 0.95) {  // Trigger at 95% of max
            // Slight camera shake for rope tension feedback
            player.xRotO += (Math.random() - 0.5) * 0.05;
            player.yRotO += (Math.random() - 0.5) * 0.05;
        }
    }
    
    /**
     * Limit view angle when rope is engaged.
     * Prevents extreme angles that would break immersion.
     * 
     * ADDED (v1.3.1): From insruchia.txt #13
     */
    private static void limitRopeViewAngle(LocalPlayer player, Vec3 leftPos, Vec3 rightPos) {
        if (leftPos == null && rightPos == null) return;
        
        // Get rope direction
        Vec3 ropeDirection;
        if (leftPos != null && rightPos != null) {
            // Average direction if both ropes
            ropeDirection = leftPos.add(rightPos).scale(0.5).subtract(player.position()).normalize();
        } else if (leftPos != null) {
            ropeDirection = leftPos.subtract(player.position()).normalize();
        } else {
            ropeDirection = rightPos.subtract(player.position()).normalize();
        }
        
        // Current look pitch and yaw
        float pitch = player.getXRot();
        
        // If looking more than 120° away from rope, soft limit
        // Pitch should be between -90 (up) and 90 (down)
        // Limit to -80 to 60 range when rope engaged for realism
        if (pitch < -80f) {
            player.setXRot(-80f);
        } else if (pitch > 60f) {
            player.setXRot(60f);
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
     * 
     * OPTIMIZED (v1.3.2): Only check collision every 10 ticks to prevent lag.
     */
    private static void checkRopeCollision(LocalPlayer player, Object leftHook, Object rightHook) {
        // Decrement cooldown
        if (collisionCheckCooldown > 0) {
            collisionCheckCooldown--;
            return;  // Skip this check, too soon
        }
        
        // Reset cooldown
        collisionCheckCooldown = COLLISION_CHECK_INTERVAL;
        
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
