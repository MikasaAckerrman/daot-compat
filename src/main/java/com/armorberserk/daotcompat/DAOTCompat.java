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
import com.armorberserk.daotcompat.ragdoll.RagdollOdmBridge;
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
    private static boolean ragdollUseWasDown;
    private static boolean recoverRequested;
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

        // Client: a LOWEST-priority post-client-tick pass catches hooks that AOT fires
        // during its own ClientTickEvent (which runs AFTER LocalPlayer.tick). Correcting
        // here means the rope renders at the visual point, not the raw plot coordinate.
        if (FMLEnvironment.dist.isClient()) {
            modBus.addListener(RagdollKeybinds::onRegisterKeyMappings);
            modBus.addListener(InGameLogOverlay::onRegisterKeyMappings);
            modBus.addListener(InGameLogOverlay::onRegisterGuiLayers);
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
                if (!ragdolled && !RagdollClient.isStunned() && !RagdollClient.isSelfReleaseRecent()) {
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
                        ScreenCapture.capture("ragdoll");
                    }
                    // A fresh Shift PRESS exits mid-air (hold does NOT — ODM uses Shift for
                    // reel-in, so a held Shift must not kick the player out of the ragdoll).
                    while (Minecraft.getInstance().options.keyShift.consumeClick()) {
                        RagdollClient.exit();
                        break;
                    }
                    // v1.6.0/v1.7.0 RECOVERY: only a hook that latches FOR THE FIRST TIME while
                    // ragdolled counts (edge, not level — pre-crash hooks must not instantly
                    // cancel the ragdoll). The server hands the ragdoll body's position + crash
                    // momentum to the player and ends the ragdoll — from that tick AOT's own
                    // physics (rope, swing, gas, reel) owns the player completely.
                    boolean freshLatch = (lLatched && !prevLeftLatched) || (rLatched && !prevRightLatched);
                    if (!recoverRequested && DaotConfig.RAGDOLL_FORCE_ENABLED.get() && freshLatch) {
                        recoverRequested = true;
                        RagdollClient.sendRecover();
                        ScreenCapture.capture("latch");
                        LOGGER.info("[ragdoll] hook latched while ragdolled -> RECOVER (crash momentum handoff)");
                    }
                    // ПКМ в рэгдолле = выстрел крюками: сиденье глотает ванильный use,
                    // поэтому стреляем программно — крюк летит по прицелу и цепляется.
                    // Edge-детект isDown: ванильный handleKeybinds осушает consumeClick до нас.
                    boolean useDownNow = Minecraft.getInstance().options.keyUse.isDown();
                    if (useDownNow && !ragdollUseWasDown) {
                        RagdollOdmBridge.fireHooksAtCrosshair(player);
                    }
                    ragdollUseWasDown = useDownNow;
                    RagdollClient.stopOdmSounds();
                    RagdollCameraSync.sync();

                    // Project the ragdoll body into world space (seat plot pos -> sub-level pose).
                    // While ragdolled everything visual hangs off the PLAYER entity — AOT ropes,
                    // the F5 camera — and the ragdoll mod keeps that entity as a separate
                    // invisible body. The optional glue snaps it onto the ragdoll every tick.
                    if (player.getVehicle() != null) {
                        ragdollWorld = RagdollWorldTracker.seatWorldPos(
                                player.level(), player.getVehicle().position(), player.position());
                    }
                    if (DaotConfig.RAGDOLL_BODY_GLUE.get() && ragdollWorld != null) {
                        double glueDelta = player.position().distanceTo(ragdollWorld);
                        if (glueDelta > 0.25D) {
                            // Smooth follow, not a snap: the server's passenger sync lags a
                            // fast-tumbling body by several blocks (live data: deltas 2.8–8.3
                            // while sliding at 35 m/s) — snapping every tick read as the
                            // "jerky" feel. Halve the gap each tick instead.
                            Vec3 from = player.position();
                            player.setPos(
                                    from.x + (ragdollWorld.x - from.x) * 0.5D,
                                    from.y + (ragdollWorld.y - from.y) * 0.5D,
                                    from.z + (ragdollWorld.z - from.z) * 0.5D);
                            com.armorberserk.daotcompat.util.LogThrottle.info("ragdoll-glue", 2,
                                    String.format(java.util.Locale.ROOT,
                                            "player following ragdoll body (gap %.1f blocks)", glueDelta));
                        }
                    }
                } else {
                    // Not ragdolled: drain vanilla sneak clicks so nothing queues up.
                    recoverRequested = false;
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

        LOGGER.info("DAOT Aeronautics Compat by armorberserk loaded (v1.8.1: control-bridge fixes, smooth body glue, correct shot paths)");
    }

    /** Static accessor for client-side helpers that need the game instance. */
    public static Minecraft minecraft() {
        return Minecraft.getInstance();
    }
}
