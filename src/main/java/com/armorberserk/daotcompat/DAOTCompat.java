/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat;

import com.armorberserk.daotcompat.aot.AOTReflect;
import com.armorberserk.daotcompat.collision.HighSpeedSubLevelGuard;
import com.armorberserk.daotcompat.config.DaotConfig;
import com.armorberserk.daotcompat.hook.DynamicHookMap;
import com.armorberserk.daotcompat.hook.HookTransformResolver;
import com.armorberserk.daotcompat.hook.RemoteHookFollower;
import com.armorberserk.daotcompat.input.RagdollKeybinds;
import com.armorberserk.daotcompat.network.DaotNetworking;
import com.armorberserk.daotcompat.ragdoll.RagdollCameraSync;
import com.armorberserk.daotcompat.ragdoll.RagdollClient;
import com.armorberserk.daotcompat.ragdoll.RagdollLink;
import com.armorberserk.daotcompat.ragdoll.RagdollWorldTracker;
import com.armorberserk.daotcompat.spear.ThunderSpearClientFollower;
import com.armorberserk.daotcompat.spear.ThunderSpearFollower;
import com.armorberserk.daotcompat.telemetry.InGameLogOverlay;
import com.armorberserk.daotcompat.telemetry.LiveState;
import com.armorberserk.daotcompat.telemetry.LogTap;
import com.armorberserk.daotcompat.telemetry.ScreenCapture;
import com.armorberserk.daotcompat.telemetry.TelemetryServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Makes Danny's AOT behave on Create: Aeronautics / Sable airships.
 *
 * <p>Client: hooks on sub-levels are re-projected every tick (single pass, after AOT's own
 * tick via Connector); lodged thunder spears — and their drawn wires — are carried on moving
 * sub-levels; a high-speed collision guard keeps the player from tunneling through decks;
 * failed ODM grappling feeds Sable: Ragdolls (hard crash = stun, weak anchor = trip).
 *
 * <p>Server: lodged thunder spears ride their sub-level; a small payload bridge lets the
 * client report ODM failures so the server can launch/exit Sable: Ragdolls sessions.
 */
@Mod(DAOTCompat.MOD_ID)
public final class DAOTCompat {

    public static final String MOD_ID = "daotcompat";
    private static int restTicks;
    private static int flightStableTicks;
    private static int recoverWaitTicks;
    private static boolean wasRagdolled;
    private static boolean prevLeftLatched;
    private static boolean prevRightLatched;
    private static boolean prevLeftRetracting;
    private static boolean prevRightRetracting;
    public static final Logger LOGGER = LoggerFactory.getLogger("DAOT Compat");

    public DAOTCompat(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, DaotConfig.SPEC);
        modBus.addListener(DaotNetworking::onRegisterPayloads);

        // Server: keep lodged spears glued to their sub-level.
        NeoForge.EVENT_BUS.addListener((EntityTickEvent.Pre event) ->
                ThunderSpearFollower.onTick(event.getEntity()));

        // Диагностика: почему рэгдолл закончился (EXPIRED / RELEASED / PLAYER_DEATH)
        NeoForge.EVENT_BUS.addListener((dev.leo.sableplayerragdoll.api.RagdollEndEvent event) ->
                LOGGER.info("[ragdoll] ENDED for {} reason {}", event.player().getGameProfile().getName(),
                        event.reason()));

        // v2.0.0: the ragdoll runs UNSEATED, so the player WOULD take vanilla fall damage on
        // the crash landing (a seated passenger never did) — the ragdoll itself is the "impact
        // absorbed" state, so cancel the fall damage while a ragdoll session is live.
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.LivingFallEvent event) -> {
            if (RagdollLink.available()
                    && event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                    && dev.leo.sableplayerragdoll.api.RagdollAPI.isRagdolled(serverPlayer)) {
                event.setCanceled(true);
            }
        });

        // Client: a LOWEST-priority post-client-tick pass catches hooks that AOT fires
        // during its own ClientTickEvent (which runs AFTER LocalPlayer.tick). Correcting
        // here means the rope renders at the visual point, not the raw plot coordinate.
        if (FMLEnvironment.dist.isClient()) {
            modBus.addListener(RagdollKeybinds::onRegisterKeyMappings);
            modBus.addListener(InGameLogOverlay::onRegisterKeyMappings);
            modBus.addListener(InGameLogOverlay::onRegisterGuiLayers);
            modBus.addListener(com.armorberserk.daotcompat.telemetry.TransitionBlink::onRegisterGuiLayers);
            // Log tap first: from here on every log line is visible in the HUD overlay
            // and on the localhost telemetry endpoint while the game runs.
            LogTap.attach();
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (ClientTickEvent.Post event) -> {
                LocalPlayer player = Minecraft.getInstance().player;
                if (player == null) return;

                // Telemetry endpoint starts on the first tick, after configs are loaded.
                TelemetryServer.start();

                RemoteHookFollower.tick(player.level());
                Object left = AOTReflect.getLeftHook();
                Object right = AOTReflect.getRightHook();
                if (left != null) HookTransformResolver.process(player.level(), left);
                if (right != null) HookTransformResolver.process(player.level(), right);

                ThunderSpearClientFollower.tick(player.level());

                if (DaotConfig.ANTI_TUNNEL.get()) {
                    HighSpeedSubLevelGuard.tick(player);
                }


                // Quick ragdoll exit: drain presses so one tap is one exit request. The server
                // refuses during a stun window — that is the point of being stunned.
                while (RagdollKeybinds.EXIT_RAGDOLL.consumeClick()) {
                    RagdollClient.exit();
                }

                // Release both ODM ropes without leaving the ragdoll.
                while (RagdollKeybinds.RELEASE_ROPES.consumeClick()) {
                    RagdollClient.releaseRopes();
                }

                // While actually ragdolled (live check): a fresh Shift PRESS exits mid-air (hold
                // does NOT — ODM uses Shift for reel-in, so a held Shift must not kick the player
                // out of the ragdoll), the looping ODM gear sound is suppressed every tick, and
                // the ragdoll camera follows the player's current F5 perspective.
                boolean ragdolled = RagdollClient.isRagdolledLive();
                // Ragdoll body projected into world space (null unless tracked this tick) —
                // computed inside the ragdoll block, consumed by telemetry below.
                Vec3 ragdollWorld = null;

                // Hook edges, shared by wire-break detection and the RECOVER trigger.
                boolean lLatched = AOTReflect.isLatched(left);
                boolean rLatched = AOTReflect.isLatched(right);
                boolean lRetracting = AOTReflect.isRetracting(left);
                boolean rRetracting = AOTReflect.isRetracting(right);

                // Titan wire-break (AOT's own "Wire broke!" path, titanWireBreakCooldowns in
                // ODMTickHandler): a hook that was latched and goes inactive WITHOUT retracting
                // had its cable cut. At speed that is a hard crash — ragdoll, momentum intact.
                if (!ragdolled && !RagdollClient.isStunned()) {
                    boolean leftBroke = prevLeftLatched && left != null && !AOTReflect.isActive(left) && !prevLeftRetracting;
                    boolean rightBroke = prevRightLatched && right != null && !AOTReflect.isActive(right) && !prevRightRetracting;
                    if (leftBroke || rightBroke) {
                        Vec3 motion = player.getDeltaMovement();
                        double horizontal = Math.hypot(motion.x, motion.z);
                        if (horizontal >= DaotConfig.CRASH_MIN_SPEED.get()) {
                            RagdollClient.triggerCrash(motion);
                            ScreenCapture.capture("wirebroke");
                            LOGGER.info("[hook] WIRE BROKE at {} m/s horizontal -> ragdoll (titan cut / snap)",
                                    String.format(java.util.Locale.ROOT, "%.1f", horizontal));
                        } else {
                            LOGGER.info("[hook] wire broke at {} m/s horizontal — below crashMinSpeed, no ragdoll",
                                    String.format(java.util.Locale.ROOT, "%.1f", horizontal));
                        }
                    }
                }

                if (ragdolled) {
                    if (!wasRagdolled) {
                        // Transition INTO the ragdoll (crash): blink + a frame for the record.
                        com.armorberserk.daotcompat.telemetry.TransitionBlink.begin(true);
                        ScreenCapture.capture("ragdoll");
                    }
                    // The ragdoll body's projected world position (nearest sub-level to the
                    // player) — needed early: the smooth-recover gate reads the body gap.
                    ragdollWorld = RagdollWorldTracker.bodyWorldPos(player.level(), player.position());
                    // v2.0.0 — the ragdoll runs UNSEATED (autoSeat(false) server-side): the
                    // player entity is NOT a passenger, so vanilla never zeroes their motion and
                    // Danny's AOT keeps owning them through the whole ragdoll — the latched
                    // ropes swing on, the gas boost and the reel work exactly as without a
                    // ragdoll. Our job here is only to mirror that motion onto the visible
                    // ragdoll body (BODY_SYNC) and to end the session when the player recovers.
                    // Shift stays AOT's native reel; X (the exit keybind) still exits manually.
                    if (!RagdollClient.isStunned()) {
                        Vec3 vel = player.getDeltaMovement();
                        if (DaotConfig.RAGDOLL_FORCE_ENABLED.get()) {
                            RagdollClient.sendBodySync(vel);
                        }
                        // v2.1.0 SMOOTH RECOVER: once the player is properly FLYING on the ODM
                        // again (airborne, a hook latched, real speed held for ~0.75 s), the
                        // ragdoll has served its purpose. Wait for the body to converge onto
                        // the player (the P-controller gap), then swap — same place, same
                        // velocity: the body "rises into the pilot" instead of popping. A hard
                        // cut after 2 s so a snagged body can never hold the session hostage.
                        boolean anyLatched = lLatched || rLatched;
                        if (DaotConfig.RAGDOLL_SMOOTH_RECOVER.get() && anyLatched && !player.onGround()
                                && vel.lengthSqr() > 64.0D) {
                            flightStableTicks++;
                        } else {
                            flightStableTicks = 0;
                            recoverWaitTicks = 0;
                        }
                        if (flightStableTicks >= 15) {
                            recoverWaitTicks++;
                            boolean bodyConverged = ragdollWorld != null
                                    && player.position().distanceTo(ragdollWorld) < 1.5D;
                            if (bodyConverged || recoverWaitTicks >= 40) {
                                flightStableTicks = 0;
                                recoverWaitTicks = 0;
                                RagdollClient.exit();
                                ScreenCapture.capture("recover");
                                LOGGER.info("[ragdoll] flight stabilized -> smooth recover ({} m/s, body gap {} blocks)",
                                        String.format(java.util.Locale.ROOT, "%.1f", vel.length()),
                                        ragdollWorld != null ? String.format(java.util.Locale.ROOT, "%.1f",
                                                player.position().distanceTo(ragdollWorld)) : "?");
                            }
                        }
                        // Auto-recover: on the ground and at rest for ~2 s — the session ends
                        // (also smoothly: exit() detaches, the body lies where it dropped),
                        // the player (the physics object all along) just continues playing.
                        if (player.onGround() && Math.hypot(vel.x, vel.z) < 2.0D) {
                            if (++restTicks >= 40) {
                                restTicks = 0;
                                RagdollClient.exit();
                                LOGGER.info("[ragdoll] body at rest -> auto-exit");
                            }
                        } else {
                            restTicks = 0;
                        }
                    } else {
                        // Stunned: pinned, ODM suppressed — kill the gear's looping drone.
                        // restTicks reset: the rest window must not count pre-stun ticks
                        // towards the post-stun auto-exit.
                        restTicks = 0;
                        flightStableTicks = 0;
                        recoverWaitTicks = 0;
                        RagdollClient.stopOdmSounds();
                    }
                    RagdollCameraSync.sync();
                } else {
                    if (wasRagdolled) {
                        // Transition OUT of the ragdoll (smooth recover / rest / X): blink.
                        com.armorberserk.daotcompat.telemetry.TransitionBlink.begin(false);
                        ScreenCapture.capture("recover");
                    }
                    restTicks = 0;
                    flightStableTicks = 0;
                    recoverWaitTicks = 0;
                    // Not ragdolled: drain vanilla sneak clicks so nothing queues up.
                    Minecraft.getInstance().options.keyShift.consumeClick();
                }
                RagdollClient.tickSoundSuppressionState();

                // Edge memory for the next tick's transitions.
                wasRagdolled = ragdolled;
                prevLeftLatched = lLatched;
                prevRightLatched = rLatched;
                prevLeftRetracting = lRetracting;
                prevRightRetracting = rRetracting;

                // Telemetry: F6 overlay edge-detect, per-tick live snapshot, throttled file dump,
                // control-bridge key releases.
                InGameLogOverlay.tick();
                LiveState.capture(player, left, right, ragdolled, ragdollWorld);
                TelemetryServer.tickFileDump();
                com.armorberserk.daotcompat.telemetry.ControlBridge.tick();
            });
        }

        LOGGER.info("DAOT Aeronautics Compat by armorberserk loaded (v2.1.1: eyelid-blink transition polish for first-person continuity)");
    }

    /** Static accessor for client-side helpers that need the game instance. */
    public static Minecraft minecraft() {
        return Minecraft.getInstance();
    }
}
