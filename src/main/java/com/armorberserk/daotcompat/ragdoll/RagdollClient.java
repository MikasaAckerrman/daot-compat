/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.ragdoll;

import com.armorberserk.daotcompat.DAOTCompat;
import com.armorberserk.daotcompat.config.DaotConfig;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client-side senders for {@link RagdollTriggerPayload}. The client only ever *reports*:
 * "my hook just failed at this velocity", "that was a hard crash — stun me", or "let me out
 * of the ragdoll". Whether a ragdoll actually happens is decided server-side by
 * {@link RagdollLink}.
 *
 * <p>The client mirrors the server's stun window locally so {@code HookTransformResolver} can
 * suppress its own hooks without waiting for a round-trip; the server window stays authoritative.
 */
public final class RagdollClient {

    private static final boolean AVAILABLE = detect();
    private static final boolean PATCH_AVAILABLE;
    private static final java.lang.reflect.Method PATCH_IS_RAGDOLLED;
    private static volatile long stunUntilMs;
    // Window during which the local player is expected to be in a ragdoll we launched.
    // Used for: auto-exit on re-hook, Shift-in-air exit, camera mode syncing.
    private static volatile long ragdollUntilMs;

    static {
        boolean patch = false;
        java.lang.reflect.Method m = null;
        try {
            Class<?> state = Class.forName("twtlinmiao.sableplayerragdollpatch.RagdollPlayerState");
            m = state.getMethod("isRagdolled", net.minecraft.world.entity.player.Player.class);
            patch = true;
        } catch (Throwable ignored) {
        }
        PATCH_AVAILABLE = patch;
        PATCH_IS_RAGDOLLED = m;
    }

    private RagdollClient() {}

    private static boolean detect() {
        try {
            Class.forName("dev.leo.sableplayerragdoll.api.RagdollAPI");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Ragdoll feature toggled off in config, or the ragdoll stack is not installed. */
    public static boolean enabled() {
        return AVAILABLE && DaotConfig.RAGDOLL_ENABLED.get();
    }

    /**
     * LIVE check "is the local player ragdolled right now", via the patch mod's client-safe
     * {@code RagdollPlayerState.isRagdolled(Player)}. Falls back to the 30s trigger window when
     * the patch is absent. This is what gates auto-exit-on-re-hook, Shift-in-air exit and the
     * sound suppression — none of them should silently expire after 30 seconds.
     */
    public static boolean isRagdolledLive() {
        LocalPlayer player = DAOTCompat.minecraft().player;
        if (player == null) return false;
        if (PATCH_AVAILABLE && PATCH_IS_RAGDOLLED != null) {
            try {
                return (boolean) PATCH_IS_RAGDOLLED.invoke(null, player);
            } catch (Throwable ignored) {
            }
        }
        return isRagdollRecent();
    }

    /** True while a locally-requested stun window is still open (mirrors the server window). */
    public static boolean isStunned() {
        return System.currentTimeMillis() < stunUntilMs;
    }

    /** True while a ragdoll we triggered is plausibly still active (fallback for the live check). */
    public static boolean isRagdollRecent() {
        return System.currentTimeMillis() < ragdollUntilMs;
    }

    public static void clearRagdollWindow() {
        ragdollUntilMs = 0;
    }

    /** Hook anchor was just lost while moving fast — ordinary hard crash (ODM stays usable). */
    public static void triggerCrash(Vec3 playerMotion) {
        markRagdollTriggered();
        stopOdmSounds();
        send(RagdollTriggerPayload.Action.TRIGGER, playerMotion);
    }

    /** Hook anchor lost at very high speed — hard crash with a stun window. */
    public static void triggerStun(Vec3 playerMotion) {
        markRagdollTriggered();
        stopOdmSounds();
        stunUntilMs = System.currentTimeMillis() + DaotConfig.STUN_TICKS.get() * 50L;
        send(RagdollTriggerPayload.Action.STUN, playerMotion);
        DAOTCompat.LOGGER.info("[hook] STUN requested ({} ms)", DaotConfig.STUN_TICKS.get() * 50L);
    }

    /** Arrived at a weak flat anchor at speed — trip. */
    public static void triggerTrip(Vec3 horizontalMotion) {
        markRagdollTriggered();
        stopOdmSounds();
        send(RagdollTriggerPayload.Action.TRIGGER, horizontalMotion);
    }

    /**
     * AOT plays looping ODM sounds (rope tension, gas, flight) while its ODMTickHandler thinks
     * the gear is active; the player seated on a ragdoll still counts as hooked, so the loop
     * drones on. Kill the looped instances reflectively — re-hooking restarts them naturally.
     * Called every client tick while the player is ragdolled (method cached after first probe).
     *
     * <p>ВНИМАНИЕ (root cause of the v1.2.0 sound bug): {@code stopAllSounds} is NOT
     * argumentless — its real signature is {@code stopAllSounds(LocalPlayer)} (Yarn
     * {@code class_746} in the AOT jar). Calling {@code invoke(null)} threw
     * IllegalArgumentException, which the old catch swallowed silently.
     */
    private static volatile boolean soundMethodProbed;
    private static java.lang.reflect.Method stopAllSoundsMethod;
    private static boolean soundSuppressionActive;

    public static void stopOdmSounds() {
        LocalPlayer player = DAOTCompat.minecraft().player;
        if (player == null) return;
        try {
            if (!soundMethodProbed) {
                soundMethodProbed = true;
                Class<?> mgr = Class.forName("daot.ODMSoundManager");
                // Ищем по имени + совместимости параметра: в AOT-джарке параметр объявлен как
                // Yarn class_746 (= LocalPlayer), через Connector в рантайме это mojmap-класс.
                for (java.lang.reflect.Method m : mgr.getDeclaredMethods()) {
                    if (!"stopAllSounds".equals(m.getName()) || m.getParameterCount() != 1) continue;
                    if (!m.getParameterTypes()[0].isAssignableFrom(player.getClass())) continue;
                    m.setAccessible(true);
                    stopAllSoundsMethod = m;
                    break;
                }
            }
            if (stopAllSoundsMethod != null) {
                stopAllSoundsMethod.invoke(null, player);
                if (!soundSuppressionActive) {
                    soundSuppressionActive = true;
                    DAOTCompat.LOGGER.info("[ragdoll] ODM sound suppression START");
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** Logs the end of the suppression window; call every client tick. */
    public static void tickSoundSuppressionState() {
        if (soundSuppressionActive && !isRagdolledLive()) {
            soundSuppressionActive = false;
            DAOTCompat.LOGGER.info("[ragdoll] ODM sound suppression END");
        }
    }

    /** Whether ODM sound suppression is currently holding (for the telemetry snapshot). */
    public static boolean isSoundSuppressionActive() {
        return soundSuppressionActive;
    }

    /** Ask the server to release the current ragdoll session immediately. */
    public static void exit() {
        LocalPlayer player = DAOTCompat.minecraft().player;
        if (player == null) return;
        ragdollUntilMs = 0;
        PacketDistributor.sendToServer(RagdollTriggerPayload.EXIT);
    }

    /** Release both ODM ropes (hooks) client-side — they belong to the local player's gear. */
    public static void releaseRopes() {
        com.armorberserk.daotcompat.aot.AOTReflect.releaseBoth();
    }

    /**
     * RECOVER (v1.6.0): a hook latched while ragdolled. The server reads the physics body's
     * position and linear velocity (the crash momentum), ends the ragdoll session and hands
     * both to the player — from that tick Danny's AOT physics owns the swing, gas and reel.
     */
    public static void sendRecover() {
        if (!enabled() || !isRagdolledLive()) return;
        LocalPlayer player = DAOTCompat.minecraft().player;
        if (player == null) return;
        PacketDistributor.sendToServer(new RagdollTriggerPayload(
                RagdollTriggerPayload.Action.RECOVER, 0.0D, 0.0D, 0.0D));
    }

    private static void markRagdollTriggered() {
        ragdollUntilMs = System.currentTimeMillis() + 30_000L;
        RagdollCameraSync.reset();
    }

    private static void send(RagdollTriggerPayload.Action action, Vec3 motion) {
        if (!enabled()) return;
        LocalPlayer player = DAOTCompat.minecraft().player;
        if (player == null) return;
        PacketDistributor.sendToServer(new RagdollTriggerPayload(action, motion.x, motion.y, motion.z));
    }
}
