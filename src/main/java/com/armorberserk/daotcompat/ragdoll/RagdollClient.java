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
    private static volatile long stunUntilMs;

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

    /** True while a locally-requested stun window is still open (mirrors the server window). */
    public static boolean isStunned() {
        return System.currentTimeMillis() < stunUntilMs;
    }

    /** Hook anchor was just lost while moving fast — ordinary hard crash (ODM stays usable). */
    public static void triggerCrash(Vec3 playerMotion) {
        send(RagdollTriggerPayload.Action.TRIGGER, playerMotion);
    }

    /** Hook anchor lost at very high speed — hard crash with a stun window. */
    public static void triggerStun(Vec3 playerMotion) {
        stunUntilMs = System.currentTimeMillis() + DaotConfig.STUN_TICKS.get() * 50L;
        send(RagdollTriggerPayload.Action.STUN, playerMotion);
        DAOTCompat.LOGGER.info("[hook] STUN requested ({} ms)", DaotConfig.STUN_TICKS.get() * 50L);
    }

    /** Arrived at a weak flat anchor at speed — trip. */
    public static void triggerTrip(Vec3 horizontalMotion) {
        send(RagdollTriggerPayload.Action.TRIGGER, horizontalMotion);
    }

    /** Ask the server to release the current ragdoll session immediately. */
    public static void exit() {
        LocalPlayer player = DAOTCompat.minecraft().player;
        if (player == null) return;
        PacketDistributor.sendToServer(RagdollTriggerPayload.EXIT);
    }

    private static void send(RagdollTriggerPayload.Action action, Vec3 motion) {
        if (!enabled()) return;
        LocalPlayer player = DAOTCompat.minecraft().player;
        if (player == null) return;
        PacketDistributor.sendToServer(new RagdollTriggerPayload(action, motion.x, motion.y, motion.z));
    }
}
