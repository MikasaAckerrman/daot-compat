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
import com.armorberserk.daotcompat.ragdoll.RagdollOdmBridge;
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
                if (ragdolled) {
                    // Edge-based: consumeClick() fires only on a fresh sneak press, never on hold.
                    while (Minecraft.getInstance().options.keyShift.consumeClick()) {
                        RagdollClient.exit();
                        break;
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
                    // Rope-force bridge: works for ANY active hook (terrain, ship, whatever).
                    // Sends the hook position every tick — server pulls the ragdoll body toward it.
                    Vec3 ropeAnchor = null;
                    if (DaotConfig.RAGDOLL_FORCE_ENABLED.get()) {
                        if (left != null && AOTReflect.isActive(left)) {
                            ropeAnchor = AOTReflect.getPosition(left);
                        }
                        if (ropeAnchor == null && right != null && AOTReflect.isActive(right)) {
                            ropeAnchor = AOTReflect.getPosition(right);
                        }
                        if (ropeAnchor != null) RagdollClient.sendRopeForce(ropeAnchor);
                    }
                    // v1.4.0: exit when the BODY ARRIVES at the anchor, not when the hook latches.
                    // The old instant-exit-on-anchor killed the ragdoll within the same tick, so
                    // rope-force never pulled anything and the body tumbled on its own trajectory.
                    // One EXIT request per ragdoll session — the server needs a tick to apply it.
                    if (!arriveExitRequested && ropeAnchor != null && player.position().distanceToSqr(ropeAnchor)
                            < sqr(DaotConfig.RAGDOLL_ARRIVE_RADIUS.get())) {
                        arriveExitRequested = true;
                        RagdollClient.exit();
                        LOGGER.info("[ragdoll] body arrived at the anchor -> exiting ragdoll");
                    }
                } else {
                    // Not ragdolled: drain vanilla sneak clicks so nothing queues up.
                    arriveExitRequested = false;
                    Minecraft.getInstance().options.keyShift.consumeClick();
                }
                RagdollClient.tickSoundSuppressionState();

                // Telemetry: F6 overlay edge-detect, per-tick live snapshot, throttled file dump.
                InGameLogOverlay.tick();
                LiveState.capture(player, left, right, ragdolled);
                TelemetryServer.tickFileDump();
            });
        }

        LOGGER.info("DAOT Aeronautics Compat by armorberserk loaded (v1.4.0: telemetry + in-game log overlay + ragdoll rope-pull exit)");
    }

    /** Static accessor for client-side helpers that need the game instance. */
    public static Minecraft minecraft() {
        return Minecraft.getInstance();
    }

    private static double sqr(double v) {
        return v * v;
    }
}
