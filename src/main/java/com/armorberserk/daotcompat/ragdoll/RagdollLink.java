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
                case RECOVER -> recover(player);
            }
        } catch (Throwable t) {
            DAOTCompat.LOGGER.debug("[ragdoll] link call failed", t);
        }
    }

    private static void trigger(ServerPlayer player, Vec3 velocity) {
        if (!player.isAlive() || RagdollAPI.isRagdolled(player)) return;
        if (onCooldown(player)) return;
        // autoSeat(true) (дефолт): игрок сидит на теле рэгдолла → позиции синхронизированы.
        // Гейт isPassenger в AOT обходится через EntityIsPassengerMixin (client-side).
        RagdollAPI.launch(player, clamp(velocity, MAX_LAUNCH_SPEED));
        DAOTCompat.LOGGER.info("[ragdoll] launched for {} at {} m/s",
                player.getGameProfile().getName(),
                String.format(java.util.Locale.ROOT, "%.1f", velocity.length()));
    }

    private static void stun(ServerPlayer player, Vec3 velocity) {
        if (!player.isAlive() || RagdollAPI.isRagdolled(player)) return;
        int ticks = com.armorberserk.daotcompat.config.DaotConfig.STUN_TICKS.get();
        RagdollLaunchOptions options = RagdollLaunchOptions.builder()
                .lockDismount(true)
                .despawnConditions(List.of(DespawnCondition.afterTicks(ticks)))
                .build();
        RagdollAPI.launch(player, clamp(velocity, MAX_LAUNCH_SPEED), options);
        STUN_UNTIL.put(player.getUUID(), player.level().getGameTime() + ticks);
        DAOTCompat.LOGGER.info("[ragdoll] STUN for {} ({} ticks) at {} m/s",
                player.getGameProfile().getName(), ticks,
                String.format(java.util.Locale.ROOT, "%.1f", velocity.length()));
    }

    private static void exit(ServerPlayer player) {
        var session = RagdollAPI.activeSession(player);
        if (session != null) session.release();
    }

    /**
     * RECOVER (v1.6.0): a hook latched while ragdolled — the player caught a lifeline.
     *
     * <p>The v1.3–v1.5 rope bridges (tractor beam, then hand-rolled constraint) all tried to
     * re-implement ODM physics on the ragdoll body and felt dead, because the real thing
     * already lives in Danny's AOT — it just cannot act on a seated player (vanilla zeroes a
     * passenger's deltaMovement every tick; live telemetry: player vel = 0 the whole ragdoll).
     *
     * <p>So instead of bridging forces, we hand over the STATE: the player receives the physics
     * body's position and its linear velocity (the crash momentum, not lost), the ragdoll
     * session ends — and from that tick AOT's own, untouched physics owns the player: the
     * already-latched rope, the swing, the gas boost, the reel. The ragdoll phase preserved its
     * momentum too (Sable's simulation); nothing is invented, everything is inherited.
     */
    private static void recover(ServerPlayer player) {
        if (!player.isAlive() || !RagdollAPI.isRagdolled(player)) return;
        Vec3 spawn = player.position();
        Vec3 velocity = Vec3.ZERO;

        dev.ryanhcode.sable.sublevel.ServerSubLevel body = ragdollBody(player);
        if (body != null) {
            Vec3 bodyWorld = bodyWorld(player, body);
            if (bodyWorld != null) spawn = bodyWorld;
            try {
                var handle = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(body);
                org.joml.Vector3d v = new org.joml.Vector3d();
                handle.getLinearVelocity(v);
                velocity = clamp(new Vec3(v.x, v.y, v.z), MAX_LAUNCH_SPEED);
            } catch (Throwable t) {
                DAOTCompat.LOGGER.debug("[ragdoll] body velocity read failed", t);
            }
        }

        var session = RagdollAPI.activeSession(player);
        if (session != null) session.release();
        player.teleportTo(spawn.x, spawn.y, spawn.z);
        player.setDeltaMovement(velocity);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                player, new RagdollRecoverPayload(velocity.x, velocity.y, velocity.z));
        DAOTCompat.LOGGER.info("[ragdoll] RECOVERED {} at {} with {} m/s — ODM physics takes over",
                player.getGameProfile().getName(), fmt(spawn),
                String.format(java.util.Locale.ROOT, "%.1f", velocity.length()));
    }

    /** The physics body's world position: the sub-level pose applied to the seat's plot position. */
    @Nullable
    private static Vec3 bodyWorld(ServerPlayer player,
                                   dev.ryanhcode.sable.sublevel.ServerSubLevel body) {
        try {
            net.minecraft.world.entity.Entity seat = player.getVehicle();
            if (seat == null) return null;
            Vec3 w = body.logicalPose().transformPosition(seat.position());
            if (!Double.isFinite(w.x) || !Double.isFinite(w.y) || !Double.isFinite(w.z)) return null;
            return w;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * The active ragdoll session's own physics sub-level — the exact body the seat rides.
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

    private static String fmt(Vec3 v) {
        return String.format(java.util.Locale.ROOT, "(%.1f, %.1f, %.1f)", v.x, v.y, v.z);
    }
}
