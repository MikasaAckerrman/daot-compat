/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.ragdoll;

import com.armorberserk.daotcompat.DAOTCompat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client → server request to launch a ragdoll (TRIGGER = ordinary failed grapple, STUN = hard
 * crash at very high speed) or EXIT the current one. The server re-validates everything:
 * stun windows, cooldown, speed clamp and the {@code isRagdolled} check — the client is never
 * trusted beyond "please consider ragdolling me".
 */
public record RagdollTriggerPayload(Action action, double vx, double vy, double vz)
        implements CustomPacketPayload {

    public enum Action { TRIGGER, STUN, EXIT, BODY_SYNC }

    public static final RagdollTriggerPayload EXIT = new RagdollTriggerPayload(Action.EXIT, 0, 0, 0);

    public static final Type<RagdollTriggerPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(DAOTCompat.MOD_ID, "ragdoll_trigger"));

    public static final StreamCodec<FriendlyByteBuf, RagdollTriggerPayload> STREAM_CODEC =
            CustomPacketPayload.codec(RagdollTriggerPayload::write, RagdollTriggerPayload::new);

    public RagdollTriggerPayload(FriendlyByteBuf buf) {
        this(buf.readEnum(Action.class), buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeEnum(action);
        buf.writeDouble(vx);
        buf.writeDouble(vy);
        buf.writeDouble(vz);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RagdollTriggerPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RagdollLink.handleServer(serverPlayer, payload);
            }
        });
    }
}
