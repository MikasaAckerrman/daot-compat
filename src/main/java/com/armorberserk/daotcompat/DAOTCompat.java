/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat;

import com.armorberserk.daotcompat.aot.AOTReflect;
import com.armorberserk.daotcompat.collision.HighSpeedSubLevelGuard;
import com.armorberserk.daotcompat.config.DaotConfig;
import com.armorberserk.daotcompat.hook.HookTransformResolver;
import com.armorberserk.daotcompat.hook.RemoteHookFollower;
import com.armorberserk.daotcompat.input.ComboBind;
import com.armorberserk.daotcompat.input.RagdollKeybinds;
import com.armorberserk.daotcompat.network.DaotNetworking;
import com.armorberserk.daotcompat.ragdoll.RagdollCameraSync;
import com.armorberserk.daotcompat.ragdoll.RagdollClient;
import com.armorberserk.daotcompat.spear.ThunderSpearClientFollower;
import com.armorberserk.daotcompat.spear.ThunderSpearFollower;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
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
    public static final Logger LOGGER = LoggerFactory.getLogger("DAOT Compat");

    public DAOTCompat(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, DaotConfig.SPEC);
        modBus.addListener(DaotNetworking::onRegisterPayloads);

        // Server: keep lodged spears glued to their sub-level.
        NeoForge.EVENT_BUS.addListener((EntityTickEvent.Pre event) ->
                ThunderSpearFollower.onTick(event.getEntity()));

        // Client: a LOWEST-priority post-client-tick pass catches hooks that AOT fires
        // during its own ClientTickEvent (which runs AFTER LocalPlayer.tick). Correcting
        // here means the rope renders at the visual point, not the raw plot coordinate.
        if (FMLEnvironment.dist.isClient()) {
            modBus.addListener(RagdollKeybinds::onRegisterKeyMappings);
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (ClientTickEvent.Post event) -> {
                LocalPlayer player = Minecraft.getInstance().player;
                if (player == null) return;

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
                // refuses during a stun window — that is the point of being stunned. When the
                // combo bind handles this action, the single key press is suppressed on purpose.
                boolean comboHandlesExit = ComboBind.handlesAction("EXIT_RAGDOLL");
                while (RagdollKeybinds.EXIT_RAGDOLL.consumeClick()) {
                    if (!comboHandlesExit) RagdollClient.exit();
                }

                // Release both ODM ropes without leaving the ragdoll.
                while (RagdollKeybinds.RELEASE_ROPES.consumeClick()) {
                    RagdollClient.releaseRopes();
                }

                // Combo bind: keyboard key + mouse button together (daotcompat-client.toml).
                ComboBind.tick(player);

                // While actually ragdolled (live check): Shift exits mid-air, the looping ODM
                // gear sound is suppressed every tick (AOT restarts it while hooks are held),
                // and the ragdoll camera follows the player's current F5 perspective.
                if (RagdollClient.isRagdolledLive()) {
                    if (!comboHandlesExit && Minecraft.getInstance().options.keyShift.isDown()) {
                        RagdollClient.exit();
                    }
                    RagdollClient.stopOdmSounds();
                    RagdollCameraSync.sync();
                }
                RagdollClient.tickSoundSuppressionState();
            });
        }

        LOGGER.info("DAOT Aeronautics Compat by armorberserk loaded (v1.1.0: attach fixes + spear visuals + ragdoll link)");
    }

    /** Static accessor for client-side helpers that need the game instance. */
    public static Minecraft minecraft() {
        return Minecraft.getInstance();
    }
}
