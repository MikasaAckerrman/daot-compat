/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.ragdoll;

import com.armorberserk.daotcompat.DAOTCompat;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server → client: the ragdoll body's crash momentum, delivered right after a RECOVER.
 *
 * <p>The server reads the physics body's linear velocity and hands it to the player (teleport +
 * this payload). Client-side velocity is what Danny's AOT integrates its rope physics from, so
 * the local player must adopt it too — then the swing, gas boost and reel are AOT's own,
 * completely unmodified.
 */
public record RagdollRecoverPayload(double vx, double vy, double vz)
        implements CustomPacketPayload {

    public static final Type<RagdollRecoverPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(DAOTCompat.MOD_ID, "ragdoll_recover"));

    public static final StreamCodec<FriendlyByteBuf, RagdollRecoverPayload> STREAM_CODEC =
            CustomPacketPayload.codec(RagdollRecoverPayload::write, RagdollRecoverPayload::new);

    private RagdollRecoverPayload(FriendlyByteBuf buf) {
        this(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    private void write(FriendlyByteBuf buf) {
        buf.writeDouble(vx);
        buf.writeDouble(vy);
        buf.writeDouble(vz);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RagdollRecoverPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof LocalPlayer player) {
                player.setDeltaMovement(new Vec3(payload.vx, payload.vy, payload.vz));
                DAOTCompat.LOGGER.info("[ragdoll] crash momentum handed to the player: ({}, {}, {}) — ODM physics owns the swing now",
                        String.format(java.util.Locale.ROOT, "%.1f", payload.vx),
                        String.format(java.util.Locale.ROOT, "%.1f", payload.vy),
                        String.format(java.util.Locale.ROOT, "%.1f", payload.vz));
            }
        });
    }
}
