/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.ragdoll;

import com.armorberserk.daotcompat.DAOTCompat;
import dev.leo.sableplayerragdoll.api.DespawnCondition;
import dev.leo.sableplayerragdoll.api.RagdollAPI;
import dev.leo.sableplayerragdoll.api.RagdollLaunchOptions;
import dev.leo.sableplayerragdoll.api.RagdollSession;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side bridge into Sable: Ragdolls.
 *
 * <p>The ragdoll API is server-authoritative: {@link RagdollAPI#launch(ServerPlayer, Vec3)}
 * must be called with a {@link ServerPlayer} on the server thread, which is why the client
 * failure detectors funnel through {@link RagdollTriggerPayload}.
 *
 * <p>Two severity tiers, driven by the client but re-validated here:
 *
 * <ul>
 *   <li>{@code TRIGGER} — an ordinary failed grapple: a normal ragdoll. The player can Shift
 *       out (after the ragdoll mod's own {@code minDismountTicks}) and keeps using the ODM gear
 *       while down — nothing here suppresses AOT.</li>
 *   <li>{@code STUN} — a hard crash at very high speed: the ragdoll is launched with
 *       {@code lockDismount(true)} plus a fixed {@code afterTicks} despawn, so the player is
 *       pinned for {@code stunTicks} and cannot Shift-exit. While the stun window is open this
 *       class ignores further TRIGGER/EXIT from that player; the client mirrors the window and
 *       suppresses its own hooks (see {@code HookTransformResolver}).</li>
 * </ul>
 *
 * <p>Availability: Sable: Ragdolls is an optional runtime dependency, probed once via
 * {@code Class.forName}; every direct {@code RagdollAPI} call is only reached when that probe
 * passed and is wrapped in {@code catch (Throwable)} so a future API break degrades to a
 * logged no-op instead of crashing the game.
 */
public final class RagdollLink {

    private static final boolean AVAILABLE = detect();
    private static final double MAX_LAUNCH_SPEED = 64.0D;
    private static final long COOLDOWN_TICKS = 40L;

    private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Long> STUN_UNTIL = new HashMap<>();

    private RagdollLink() {}

    private static boolean detect() {
        try {
            Class.forName("dev.leo.sableplayerragdoll.api.RagdollAPI");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Whether the ragdoll stack is present in this (server) runtime. */
    public static boolean available() {
        return AVAILABLE;
    }

    /** Entry point for {@link RagdollTriggerPayload} on the server thread. */
    public static void handleServer(ServerPlayer player, RagdollTriggerPayload payload) {
        if (!AVAILABLE) return;
        try {
            long now = player.level().getGameTime();
            Long stunUntil = STUN_UNTIL.get(player.getUUID());
            if (stunUntil != null) {
                if (now < stunUntil) return; // stunned: ignore everything
                STUN_UNTIL.remove(player.getUUID());
            }
            switch (payload.action()) {
                case TRIGGER -> trigger(player, new Vec3(payload.vx(), payload.vy(), payload.vz()));
                case STUN -> stun(player, new Vec3(payload.vx(), payload.vy(), payload.vz()));
                case EXIT -> exit(player);
                case BODY_SYNC -> bodySync(player, new Vec3(payload.vx(), payload.vy(), payload.vz()));
            }
        } catch (Throwable t) {
            DAOTCompat.LOGGER.debug("[ragdoll] link call failed", t);
        }
    }

    private static void trigger(ServerPlayer player, Vec3 velocity) {
        if (!player.isAlive() || RagdollAPI.isRagdolled(player)) return;
        if (onCooldown(player)) return;
        // v2.0.0 UNSEATED: autoSeat(false) keeps the player OUT of the seat, so vanilla never
        // zeroes their deltaMovement — Danny's AOT keeps owning them through the whole ragdoll
        // (the latched ropes swing on, gas and reel work natively). The visible body is dragged
        // along by BODY_SYNC. This replaces every prior "bridge the physics" attempt.
        RagdollAPI.launch(player, clamp(velocity, MAX_LAUNCH_SPEED),
                RagdollLaunchOptions.builder().autoSeat(false).build());
        DAOTCompat.LOGGER.info("[ragdoll] launched (unseated) for {} at {} m/s",
                player.getGameProfile().getName(),
                String.format(java.util.Locale.ROOT, "%.1f", velocity.length()));
    }

    private static void stun(ServerPlayer player, Vec3 velocity) {
        if (!player.isAlive() || RagdollAPI.isRagdolled(player)) return;
        int ticks = com.armorberserk.daotcompat.config.DaotConfig.STUN_TICKS.get();
        RagdollLaunchOptions options = RagdollLaunchOptions.builder()
                .autoSeat(false)
                .lockDismount(true)
                .despawnConditions(List.of(DespawnCondition.afterTicks(ticks)))
                .build();
        RagdollAPI.launch(player, clamp(velocity, MAX_LAUNCH_SPEED), options);
        STUN_UNTIL.put(player.getUUID(), player.level().getGameTime() + ticks);
        DAOTCompat.LOGGER.info("[ragdoll] STUN (unseated) for {} ({} ticks) at {} m/s",
                player.getGameProfile().getName(), ticks,
                String.format(java.util.Locale.ROOT, "%.1f", velocity.length()));
    }

    private static void exit(ServerPlayer player) {
        var session = RagdollAPI.activeSession(player);
        if (session != null) session.release();
    }

    /**
     * BODY_SYNC (v2.0.0): the player runs UNSEATED — full native AOT physics on the player
     * entity — while the visible ragdoll body is a Sable sublevel that knows nothing about the
     * ODM. This is a P-controller on BOTH velocity and position: converge the body's linear
     * velocity onto the player's every tick (the client reports the player's true velocity),
     * plus a positional correction term, so the ragdoll rides the player's rope trajectory
     * instead of drifting away (velocity-only matching accumulates position error — the body
     * carries its OWN gravity from Sable on top of the player's, and contact solves can leave
     * it snagged behind; the position term reels it back).
     */
    private static final double BODY_POS_GAIN = 0.25D;

    private static void bodySync(ServerPlayer player, Vec3 playerVel) {
        if (!player.isAlive() || !RagdollAPI.isRagdolled(player)) return;
        dev.ryanhcode.sable.sublevel.ServerSubLevel body = ragdollBody(player);
        if (body == null) return;
        Vec3 bodyPos = null;
        try {
            bodyPos = body.logicalPose().transformPosition(Vec3.ZERO);
            if (bodyPos != null && (!Double.isFinite(bodyPos.x) || !Double.isFinite(bodyPos.y)
                    || !Double.isFinite(bodyPos.z))) bodyPos = null;
        } catch (Throwable ignored) {
        }
        try {
            var handle = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(body);
            org.joml.Vector3d current = new org.joml.Vector3d();
            handle.getLinearVelocity(current);
            // Position error feedback (blocks): pulls the body back onto the player's track.
            Vec3 toPlayer = bodyPos != null ? player.position().subtract(bodyPos) : Vec3.ZERO;
            // Snappy but orbit-proof: ±6 m/s per axis per tick.
            double dx = org.joml.Math.clamp(playerVel.x - current.x + BODY_POS_GAIN * toPlayer.x, -6.0, 6.0);
            double dy = org.joml.Math.clamp(playerVel.y - current.y + BODY_POS_GAIN * toPlayer.y, -6.0, 6.0);
            double dz = org.joml.Math.clamp(playerVel.z - current.z + BODY_POS_GAIN * toPlayer.z, -6.0, 6.0);
            handle.addLinearAndAngularVelocity(new org.joml.Vector3d(dx, dy, dz), new org.joml.Vector3d(0, 0, 0));
            com.armorberserk.daotcompat.util.LogThrottle.info("body-sync", 5,
                    String.format(java.util.Locale.ROOT,
                            "player vel (%.1f, %.1f, %.1f) | body vel (%.1f, %.1f, %.1f) | pos gap %s | dv (%.1f, %.1f, %.1f)",
                            playerVel.x, playerVel.y, playerVel.z, current.x, current.y, current.z,
                            bodyPos != null ? String.format(java.util.Locale.ROOT, "%.1f", toPlayer.length()) : "?",
                            dx, dy, dz));
        } catch (Throwable t) {
            DAOTCompat.LOGGER.debug("[ragdoll] body sync failed", t);
        }
    }

    /**
     * The active ragdoll session's own physics sub-level — the body BODY_SYNC drives.
     *
     * <p>Live-telemetry finding (08.10): the old lookup probed a 0.05-block box at the player's
     * position, but a ragdoll is six small limb sub-levels and the seat point falls between
     * them — the probe missed every part, the pull never engaged, and the body tumbled on its
     * own trajectory. The session record exposes {@code subLevel()} directly; the class is
     * package-private, so the accessor is resolved reflectively once and cached (failures
     * logged, never swallowed silently).
     */
    @Nullable
    private static dev.ryanhcode.sable.sublevel.ServerSubLevel ragdollBody(ServerPlayer player) {
        try {
            RagdollSession session = RagdollAPI.activeSession(player);
            if (session == null) return null;
            if (SUB_LEVEL_ACCESSOR == null && !subLevelAccessorFailed) {
                try {
                    SUB_LEVEL_ACCESSOR = session.getClass().getMethod("subLevel");
                    SUB_LEVEL_ACCESSOR.setAccessible(true);
                    DAOTCompat.LOGGER.info("[ragdoll] session subLevel() accessor resolved");
                } catch (Throwable t) {
                    subLevelAccessorFailed = true;
                    DAOTCompat.LOGGER.warn("[ragdoll] session.subLevel() not accessible: {}", t.toString());
                }
            }
            if (SUB_LEVEL_ACCESSOR == null) return null;
            Object sl = SUB_LEVEL_ACCESSOR.invoke(session);
            return sl instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel ssl ? ssl : null;
        } catch (Throwable t) {
            DAOTCompat.LOGGER.debug("[ragdoll] ragdoll body lookup failed", t);
            return null;
        }
    }

    private static volatile java.lang.reflect.Method SUB_LEVEL_ACCESSOR;
    private static volatile boolean subLevelAccessorFailed;

    private static boolean onCooldown(ServerPlayer player) {
        long now = player.level().getGameTime();
        Long next = COOLDOWNS.get(player.getUUID());
        if (next != null && now < next) return true;
        COOLDOWNS.put(player.getUUID(), now + COOLDOWN_TICKS);
        return false;
    }

    private static Vec3 clamp(Vec3 v, double max) {
        double len = v.length();
        return (len > max && len > 1.0E-9) ? v.scale(max / len) : v;
    }
}
