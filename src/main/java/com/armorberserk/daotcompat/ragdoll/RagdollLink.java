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
                case ROPE_FORCE -> rope(player, new Vec3(payload.vx(), payload.vy(), payload.vz()), false);
                case ROPE_REEL -> rope(player, new Vec3(payload.vx(), payload.vy(), payload.vz()), true);
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
     * A rope, not a magnet (v1.5.0 rework of the old ROPE_FORCE tractor beam).
     *
     * <p>The v1.3.0–v1.4.1 implementation converged the body's velocity toward the anchor every
     * tick — a homing pull that steered away tangential momentum, fired even with the anchor
     * below a slack rope, and gave the player no agency ("I just get dragged"). A real ODM cable
     * is a CONSTRAINT:
     *
     * <ul>
     *   <li>slack ({@code d <= L}): no force at all — gravity, crash momentum and the joint
     *       solver run free;</li>
     *   <li>taut ({@code d > L}): only the radial (receding) velocity component is cancelled,
     *       preserving the tangential one — a pendulum swing, plus a gentle stretch-recovery
     *       spring back onto the rope sphere;</li>
     *   <li>reel (client holds Shift, AOT's reel-in muscle memory): a winch — the rope
     *       shortens at {@code ragdollReelSpeed} and the body is hoisted along the rope line.</li>
     * </ul>
     *
     * <p>Geometry is measured from the PHYSICS body (session sub-level pose × seat plot
     * position), not the player entity — the player hangs ~4 blocks off the body server-side
     * (the glue is client-only), which had bent every distance in the old code.
     */
    private static final Map<UUID, Rope> ROPES = new HashMap<>();
    private record Rope(Vec3 anchor, double length) {}
    private static final double MIN_ROPE_LENGTH = 1.5D;
    private static final double ANCHOR_JUMP_SQR = 16.0D; // > 4 blocks between ticks = re-hook

    private static void rope(ServerPlayer player, Vec3 anchorPos, boolean reeling) {
        if (!player.isAlive() || !RagdollAPI.isRagdolled(player)) return;
        dev.ryanhcode.sable.sublevel.ServerSubLevel body = ragdollBody(player);
        if (body == null) return;
        Vec3 bodyWorld = bodyWorld(player, body);
        if (bodyWorld == null) bodyWorld = player.position();

        Rope rope = ROPES.get(player.getUUID());
        if (rope == null || rope.anchor().distanceToSqr(anchorPos) > ANCHOR_JUMP_SQR) {
            rope = new Rope(anchorPos, bodyWorld.distanceTo(anchorPos));
            ROPES.put(player.getUUID(), rope);
            DAOTCompat.LOGGER.info("[ragdoll-rope] attached: L={} to anchor {}",
                    String.format(java.util.Locale.ROOT, "%.1f", rope.length()), fmt(anchorPos));
        }
        double length = rope.length();
        if (reeling) {
            length = Math.max(MIN_ROPE_LENGTH, length
                    - com.armorberserk.daotcompat.config.DaotConfig.RAGDOLL_REEL_SPEED.get() / 20.0D);
            ROPES.put(player.getUUID(), new Rope(rope.anchor(), length));
        }

        Vec3 toAnchor = anchorPos.subtract(bodyWorld);
        double d = toAnchor.length();
        if (d < 1.0E-6D) return;
        Vec3 n = toAnchor.scale(1.0D / d);

        try {
            var handle = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(body);
            org.joml.Vector3d current = new org.joml.Vector3d();
            handle.getLinearVelocity(current);
            double vRadial = current.x * n.x + current.y * n.y + current.z * n.z; // + = approaching

            org.joml.Vector3d dv = new org.joml.Vector3d();
            if (d > length + 0.05D) { // taut: cancel receding radial velocity, keep the swing
                if (vRadial < 0.0D) {
                    dv.add(-vRadial * n.x, -vRadial * n.y, -vRadial * n.z);
                }
                double stretch = Math.min((d - length) * 0.6D, 1.5D);
                dv.add(stretch * n.x, stretch * n.y, stretch * n.z);
            }
            if (reeling) { // winch: drive the radial approach speed to reelSpeed
                double dvR = org.joml.Math.clamp(
                        com.armorberserk.daotcompat.config.DaotConfig.RAGDOLL_REEL_SPEED.get() - vRadial, -3.0, 3.0);
                dv.add(dvR * n.x, dvR * n.y, dvR * n.z);
            }
            // Safety clamp: at most 3 m/s of velocity change per axis per tick, so a bad client
            // value can never fling the physics body into orbit.
            dv.x = org.joml.Math.clamp(dv.x, -3.0, 3.0);
            dv.y = org.joml.Math.clamp(dv.y, -3.0, 3.0);
            dv.z = org.joml.Math.clamp(dv.z, -3.0, 3.0);
            if (dv.lengthSquared() > 1.0E-8D) {
                handle.addLinearAndAngularVelocity(dv, new org.joml.Vector3d(0, 0, 0));
            }
            com.armorberserk.daotcompat.util.LogThrottle.info("rope-force", 2,
                    String.format(java.util.Locale.ROOT, "L=%.1f d=%.1f %s vR=%.1f dv=(%.1f,%.1f,%.1f)",
                            length, d, reeling ? "REEL" : (d > length ? "taut" : "slack"),
                            vRadial, dv.x, dv.y, dv.z));
        } catch (Throwable t) {
            DAOTCompat.LOGGER.debug("[ragdoll] rope constraint failed", t);
        }
    }

    /** Drops the rope state when the ragdoll session ends (called from the RagdollEndEvent). */
    public static void forgetRope(ServerPlayer player) {
        Rope removed = ROPES.remove(player.getUUID());
        if (removed != null) {
            DAOTCompat.LOGGER.info("[ragdoll-rope] released with the ragdoll (L={})",
                    String.format(java.util.Locale.ROOT, "%.1f", removed.length()));
        }
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
