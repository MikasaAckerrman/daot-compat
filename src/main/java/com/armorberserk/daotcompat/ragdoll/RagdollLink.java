/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.ragdoll;

import com.armorberserk.daotcompat.DAOTCompat;
import com.armorberserk.daotcompat.sable.SubLevelResolver;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.leo.sableplayerragdoll.api.DespawnCondition;
import dev.leo.sableplayerragdoll.api.RagdollAPI;
import dev.leo.sableplayerragdoll.api.RagdollLaunchOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

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
                case ROPE_FORCE -> ropeForce(player, new Vec3(payload.vx(), payload.vy(), payload.vz()));
            }
        } catch (Throwable t) {
            DAOTCompat.LOGGER.debug("[ragdoll] link call failed", t);
        }
    }

    private static void trigger(ServerPlayer player, Vec3 velocity) {
        if (!player.isAlive() || RagdollAPI.isRagdolled(player)) return;
        if (onCooldown(player)) return;
        // No despawn conditions: the server config governs expiry, Shift exits after
        // minDismountTicks, and the ODM gear stays fully usable while the player is down.
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
     * The rope-force bridge that makes the ODM gear usable while ragdolled: the client sends the
     * velocity its gear logic wants (AOT's rope pull is already inside the client player's delta
     * movement), and we converge the ragdoll physics body's velocity toward it. Safety: the body
     * must be a registered ragdoll sub-level — never a ship.
     */
    private static void ropeForce(ServerPlayer player, Vec3 clientMotion) {
        if (!player.isAlive() || !RagdollAPI.isRagdolled(player)) return;
        SubLevel sl = SubLevelResolver.findContaining(player.serverLevel(), player.position());
        if (!(sl instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel serverSubLevel)) return;
        if (!RagdollAPI.isRagdollSubLevel(serverSubLevel.getUniqueId())) return;
        try {
            var handle = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(serverSubLevel);
            org.joml.Vector3d current = new org.joml.Vector3d();
            handle.getLinearVelocity(current);
            // Converge the body velocity toward the intended motion (rope pull already applied
            // client-side by AOT). Strength is a convergence factor, not a raw impulse.
            double strength = com.armorberserk.daotcompat.config.DaotConfig.RAGDOLL_FORCE_STRENGTH.get();
            Vec3 clamped = clamp(clientMotion, MAX_LAUNCH_SPEED);
            // Safety clamp: at most 3 m/s of velocity change per tick, so a bad client value
            // can never fling the physics body into orbit.
            double dx = org.joml.Math.clamp((clamped.x - current.x) * strength, -3.0, 3.0);
            double dy = org.joml.Math.clamp((clamped.y - current.y) * strength, -3.0, 3.0);
            double dz = org.joml.Math.clamp((clamped.z - current.z) * strength, -3.0, 3.0);
            handle.addLinearAndAngularVelocity(new org.joml.Vector3d(dx, dy, dz), new org.joml.Vector3d(0, 0, 0));
        } catch (Throwable t) {
            DAOTCompat.LOGGER.debug("[ragdoll] rope force failed", t);
        }
    }

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
