/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat;

import com.armorberserk.daotcompat.aot.AOTReflect;
import com.armorberserk.daotcompat.client.DAOTCompatKeyMappings;
import com.armorberserk.daotcompat.collision.HighSpeedSubLevelGuard;
import com.armorberserk.daotcompat.hook.HookTransformResolver;
import com.armorberserk.daotcompat.hook.RemoteHookFollower;
import com.armorberserk.daotcompat.input.HotbarSwapHandler;
import com.armorberserk.daotcompat.input.KeybindEventListener;
import com.armorberserk.daotcompat.input.KeybindRegistrationListener;
import com.armorberserk.daotcompat.physics.GrapplePhysicsController;
import com.armorberserk.daotcompat.render.RopeLineRenderer;
import com.armorberserk.daotcompat.render.SparkEffectRenderer;
import com.armorberserk.daotcompat.spear.ThunderSpearFollower;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Makes Danny's AOT behave on Create: Aeronautics / Sable airships.
 *
 * <p>Client: hooks on sub-levels are re-projected every tick.
 * <p>Server: lodged thunder spears ride their sub-level.
 *
 * <h3>Fix 1 (code review round 2): single tick-processing pass</h3>
 * <p>Earlier builds called {@code HookTransformResolver.process(...)} and
 * {@code RemoteHookFollower.tick(...)} from <em>two</em> places every client tick: a
 * {@code LocalPlayer.tick()} HEAD mixin, and this class's {@code ClientTickEvent.Post} LOWEST
 * listener. The intent was a safety net in case AOT's own hook updates (registered through
 * Fabric's {@code ClientTickEvents.END_CLIENT_TICK} and bridged into the NeoForge tick cycle by
 * Sinytra Connector) landed after {@code LocalPlayer.tick()} - but both call sites ran on every
 * single tick regardless, doubling the reflection + Sable-transform cost for no benefit.
 *
 * <p>We removed the mixin entirely and now process exactly once, from
 * {@code ClientTickEvent.Post} at {@link EventPriority#LOWEST}. Reasoning: Connector's entire
 * purpose is to make a Fabric mod's Fabric-API tick registration observe the same "end of client
 * tick" ordering NeoForge itself uses, so AOT's {@code END_CLIENT_TICK} handler is expected to run
 * as part of the ordinary client tick, before any {@code ClientTickEvent.Post} listener - and
 * {@code LOWEST} priority additionally guarantees we run after every other {@code Post} listener
 * too. If a future Connector/AOT update ever breaks that ordering, the symptom would be a hook
 * visually lagging one tick behind a moving ship, not a physics correctness bug - a much cheaper
 * failure mode than the double-processing we removed.
 */
@Mod(DAOTCompat.MOD_ID)
public final class DAOTCompat {

    public static final String MOD_ID = "daotcompat";
    public static final Logger LOGGER = LoggerFactory.getLogger("DAOT Compat");

    public DAOTCompat(IEventBus modBus) {
        // Server: keep lodged spears glued to their sub-level.
        NeoForge.EVENT_BUS.addListener((EntityTickEvent.Pre event) ->
                ThunderSpearFollower.onTick(event.getEntity()));

        // Client: a single LOWEST-priority post-client-tick pass. See class javadoc "Fix 1".
        if (FMLEnvironment.dist.isClient()) {
            // ========== MOD BUS EVENTS ==========
            // RegisterKeyMappingsEvent is a MOD event - must register on MOD_BUS
            modBus.register(KeybindRegistrationListener.class);
            
            // ========== NEOFORGE EVENT BUS EVENTS ==========
            // ClientTickEvent, ScreenEvent are FORGE events - register on NeoForge.EVENT_BUS
            
            // Phase 1: Keybind handling (ClientTickEvent + ScreenEvent = FORGE events)
            NeoForge.EVENT_BUS.register(KeybindEventListener.class);
            
            // Phase 3+: Rope rendering (RenderEvent = FORGE event)
            NeoForge.EVENT_BUS.register(RopeLineRenderer.class);
            
            // Phase 4B: Spark effects (RenderEvent = FORGE event)
            NeoForge.EVENT_BUS.register(SparkEffectRenderer.class);
            
            // Phase 5: Hotbar swapping (ScreenEvent = FORGE event)
            NeoForge.EVENT_BUS.register(HotbarSwapHandler.class);

            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (ClientTickEvent.Post event) -> {
                LocalPlayer player = Minecraft.getInstance().player;
                if (player == null) return;
                RemoteHookFollower.tick(player.level());
                
                // 🔧 OPTIMIZATION (v1.3.0): Cache AOTReflect calls (expensive reflection)
                // Instead of calling 5+ times per frame, call ONCE and pass as parameter
                Object leftHook = AOTReflect.getLeftHook();
                Object rightHook = AOTReflect.getRightHook();
                
                if (leftHook != null) HookTransformResolver.process(player.level(), leftHook);
                if (rightHook != null) HookTransformResolver.process(player.level(), rightHook);
                
                // Pass cached hooks to physics controller (avoids multiple reflection calls)
                GrapplePhysicsController.tick(player, leftHook, rightHook);

                // Safety net against tunnelling through a sub-level (airship) at ODM speeds.
                HighSpeedSubLevelGuard.tick(player);
            });
        }

        LOGGER.info("DAOT Aeronautics Compat by armorberserk loaded");
    }
}
