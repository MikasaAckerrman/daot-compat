/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.network;

import com.armorberserk.daotcompat.ragdoll.RagdollTriggerPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Payload registration. Kept in one place so the wire format has a single version string.
 * Registered on the MOD event bus (RegisterPayloadHandlersEvent is a mod-bus event).
 */
public final class DaotNetworking {

    private DaotNetworking() {}

    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("3");
        registrar.playToServer(
                RagdollTriggerPayload.TYPE,
                RagdollTriggerPayload.STREAM_CODEC,
                RagdollTriggerPayload::handle);
    }
}
