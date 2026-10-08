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
    private static boolean arriveExitRequested;
    public static final Logger LOGGER = LoggerFactory.getLogger("DAOT Compat");

    public DAOTCompat(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, DaotConfig.SPEC);
        modBus.addListener(DaotNetworking::onRegisterPayloads);

        // Server: keep lodged spears glued to their sub-level.
        NeoForge.EVENT_BUS.addListener((EntityTickEvent.Pre event) ->
                ThunderSpearFollower.onTick(event.getEntity()));

        // Диагностика: почему рэгдолл закончился (EXPIRED / RELEASED / PLAYER_DEATH) + сброс троса
        NeoForge.EVENT_BUS.addListener((dev.leo.sableplayerragdoll.api.RagdollEndEvent event) -> {
            LOGGER.info("[ragdoll] ENDED for {} reason {}", event.player().getGameProfile().getName(),
                    event.reason());
            RagdollLink.forgetRope(event.player());
        });

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
                if (ragdolled) {
                    // The active hook position (any side) — the rope's anchor. Computed FIRST:
                    // the Shift semantics below depend on whether a rope is attached.
                    Vec3 ropeAnchor = null;
                    if (left != null && AOTReflect.isActive(left)) {
                        ropeAnchor = AOTReflect.getPosition(left);
                    }
                    if (ropeAnchor == null && right != null && AOTReflect.isActive(right)) {
                        ropeAnchor = AOTReflect.getPosition(right);
                    }
                    boolean shiftDown = Minecraft.getInstance().options.keyShift.isDown();
                    if (ropeAnchor != null) {
                        // Roped: Shift is AOT's reel-in (hold = winch the body toward the
                        // anchor). Draining clicks so a tap can NOT exit while roped — that is
                        // the point of holding a lifeline. X still exits any time.
                        while (Minecraft.getInstance().options.keyShift.consumeClick()) { }
                        if (shiftDown) RagdollClient.sendRopeReel(ropeAnchor);
                    } else {
                        // Not roped: a fresh sneak press exits mid-air, never a hold.
                        while (Minecraft.getInstance().options.keyShift.consumeClick()) {
                            RagdollClient.exit();
                            break;
                        }
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
                            player.setPos(ragdollWorld.x, ragdollWorld.y, ragdollWorld.z);
                            com.armorberserk.daotcompat.util.LogThrottle.info("ragdoll-glue", 2,
                                    String.format(java.util.Locale.ROOT,
                                            "player snapped onto ragdoll body (delta was %.1f blocks)", glueDelta));
                        }
                    }
                    // Rope geometry report, every tick: the server runs the constraint
                    // (taut/slack) off this; the winch tick arrives separately while Shift
                    // is held (see sendRopeReel above).
                    if (DaotConfig.RAGDOLL_FORCE_ENABLED.get() && ropeAnchor != null) {
                        RagdollClient.sendRopeForce(ropeAnchor);
                    }
                    // v1.5.0: arrival counts only as an intentional winch — the player is
                    // REELING and the body has been hoisted to within arriveRadius of the
                    // anchor. A slack swing that merely passes the anchor does not end the
                    // ragdoll.
                    if (!arriveExitRequested && ropeAnchor != null && shiftDown
                            && player.position().distanceToSqr(ropeAnchor)
                                    < sqr(DaotConfig.RAGDOLL_ARRIVE_RADIUS.get())) {
                        arriveExitRequested = true;
                        RagdollClient.exit();
                        LOGGER.info("[ragdoll] winched to the anchor -> exiting ragdoll");
                    }
                } else {
                    // Not ragdolled: drain vanilla sneak clicks so nothing queues up.
                    arriveExitRequested = false;
                    Minecraft.getInstance().options.keyShift.consumeClick();
                }
                RagdollClient.tickSoundSuppressionState();

                // Telemetry: F6 overlay edge-detect, per-tick live snapshot, throttled file dump.
                InGameLogOverlay.tick();
                LiveState.capture(player, left, right, ragdolled, ragdollWorld);
                TelemetryServer.tickFileDump();
            });
        }

        LOGGER.info("DAOT Aeronautics Compat by armorberserk loaded (v1.5.0: rope constraint + Shift winch replaces tractor-beam pull)");
    }

    /** Static accessor for client-side helpers that need the game instance. */
    public static Minecraft minecraft() {
        return Minecraft.getInstance();
    }

    private static double sqr(double v) {
        return v * v;
    }
}
