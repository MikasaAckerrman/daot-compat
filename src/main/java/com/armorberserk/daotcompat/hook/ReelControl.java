/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.hook;

import com.armorberserk.daotcompat.aot.AOTReflect;
import com.armorberserk.daotcompat.client.DAOTCompatKeyMappings;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Intercepts the ODM pull force that Danny's AOT applies every client tick, but only while the
 * player asks us to (holding {@link DAOTCompatKeyMappings#HANG}).
 *
 * <p><b>Round 3 semantics (was inverted before, see {@link DAOTCompatKeyMappings} javadoc):</b>
 * by default this is a no-op - AOT's own pull-in behaviour is left completely untouched, exactly
 * like vanilla AOT. Only while {@code HANG} is held do we read-modify-write the velocity AOT just
 * set (via {@code ClientTickEvent.Post} at {@link net.neoforged.bus.api.EventPriority#LOWEST},
 * i.e. after every normal-priority handler including AOT's) to strip the inward pull component,
 * leaving the player hanging/braking at roughly a fixed distance from the hook(s).
 *
 * <p>Only the component of velocity that points <em>toward</em> the hook is removed; the
 * tangential (swing) component, gravity and lateral momentum are left intact so the player still
 * falls naturally and can swing while braking. When both hooks are active the pull directions are
 * averaged before the projection, which handles symmetric dual-hook setups correctly.
 *
 * <h3>Swing safety clamps (fixes the reported "spins around an axis at insane speed" bug)</h3>
 * <p>A naive "strip the inward velocity component" projection is the standard trick to turn a
 * pull into an orbit, but it has no built-in bound: near the anchor point the geometry becomes a
 * classic pendulum singularity (angular speed = tangential speed / radius, so radius → 0 makes
 * the implied angular speed blow up), and small per-tick numerical drift in the remaining
 * tangential component can compound over many ticks with nothing to cap it. Two guards fix this:
 * <ul>
 *   <li>{@link #MIN_HOOK_RADIUS} - a hook closer than this is excluded from the pull-direction
 *       average entirely for that tick; the projection math is not meaningfully defined that
 *       close to the anchor and must not be fed back into AOT's own physics.</li>
 *   <li>{@link #MAX_TANGENTIAL_SPEED} - a hard clamp on the magnitude of the corrected velocity
 *       we hand back, so no code path (ours or an interaction with AOT's) can ever produce an
 *       "insane" speed, regardless of how it got there.</li>
 * </ul>
 */
public final class ReelControl {

    /** Fix 4 (code review round 2): explicit sentinel instead of comparing a Vec3 to Vec3.ZERO,
     *  which conflated "no hook contributed" with the rare edge case of the hook occupying the
     *  exact same point as the player (e.g. after a teleport/lag spike). */
    private static final double MIN_CONTRIBUTION_LENGTH = 1.0E-3;

    // See class javadoc "Swing safety clamps".
    private static final double MIN_HOOK_RADIUS = 2.0D; // blocks
    private static final double MIN_HOOK_RADIUS_SQR = MIN_HOOK_RADIUS * MIN_HOOK_RADIUS;
    // ~3 blocks/tick is ~60 blocks/sec - already a generously fast anime-style swing, and a hard
    // ceiling no corrected velocity may exceed.
    private static final double MAX_TANGENTIAL_SPEED = 3.0D;

    private ReelControl() {}

    /**
     * Called from {@code ClientTickEvent.Post} (LOWEST priority) after AOT has applied its
     * pull velocity for this tick.
     */
    public static void apply(LocalPlayer player) {
        if (!AOTReflect.isAvailable()) return;
        if (!DAOTCompatKeyMappings.HANG.isDown()) return; // default: don't touch AOT's pull at all

        Object left = AOTReflect.getLeftHook();
        Object right = AOTReflect.getRightHook();
        boolean leftActive = left != null && AOTReflect.isActive(left);
        boolean rightActive = right != null && AOTReflect.isActive(right);
        if (!leftActive && !rightActive) return; // no active hooks — cheap exit

        // Build a combined pull direction from all active, sufficiently-distant hooks.
        // Each contributing direction is normalised before summing so neither hook dominates by
        // distance; the sum is then re-normalised to get a unit vector.
        Vec3 playerPos = player.position();
        Vec3 pullDir = Vec3.ZERO;
        boolean leftContributed = false;
        boolean rightContributed = false;

        if (leftActive) {
            Vec3 hp = AOTReflect.getPosition(left);
            if (hp != null) {
                Vec3 toHook = hp.subtract(playerPos);
                double lenSqr = toHook.lengthSqr();
                // MIN_HOOK_RADIUS guard: too close to the anchor for the projection to be
                // numerically meaningful - exclude it rather than risk feeding back a huge
                // tangential correction.
                if (lenSqr > MIN_HOOK_RADIUS_SQR) {
                    double len = Math.sqrt(lenSqr);
                    if (len > MIN_CONTRIBUTION_LENGTH) {
                        pullDir = pullDir.add(toHook.scale(1.0 / len));
                        leftContributed = true;
                    }
                }
            }
        }
        if (rightActive) {
            Vec3 hp = AOTReflect.getPosition(right);
            if (hp != null) {
                Vec3 toHook = hp.subtract(playerPos);
                double lenSqr = toHook.lengthSqr();
                if (lenSqr > MIN_HOOK_RADIUS_SQR) {
                    double len = Math.sqrt(lenSqr);
                    if (len > MIN_CONTRIBUTION_LENGTH) {
                        pullDir = pullDir.add(toHook.scale(1.0 / len));
                        rightContributed = true;
                    }
                }
            }
        }

        if (!leftContributed && !rightContributed) return;
        if (leftContributed && rightContributed) {
            // Sum of two unit vectors is not necessarily unit — re-normalise.
            double dirLen = pullDir.length();
            if (dirLen < 1.0E-6) return;
            pullDir = pullDir.scale(1.0 / dirLen);
        }

        // Strip the pull component: project current velocity onto pullDir, remove it if positive
        // (positive = player is moving toward hook = being pulled in).
        Vec3 velocity = player.getDeltaMovement();
        double pullComponent = velocity.dot(pullDir);
        Vec3 corrected = pullComponent > 0 ? velocity.subtract(pullDir.scale(pullComponent)) : velocity;

        // Hard safety net (see class javadoc): never hand back an unbounded speed.
        double speedSqr = corrected.lengthSqr();
        if (speedSqr > MAX_TANGENTIAL_SPEED * MAX_TANGENTIAL_SPEED) {
            corrected = corrected.scale(MAX_TANGENTIAL_SPEED / Math.sqrt(speedSqr));
        }

        player.setDeltaMovement(corrected);
    }
}
