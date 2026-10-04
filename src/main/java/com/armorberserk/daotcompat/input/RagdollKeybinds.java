/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Client keybinds. Currently only the quick ragdoll exit; grapple movement itself stays on
 * Danny's AOT's own controls — this mod deliberately does not compete with it.
 */
public final class RagdollKeybinds {

    /** Instantly releases the current ragdoll session (server validates and may refuse during a stun). */
    public static final KeyMapping EXIT_RAGDOLL = new KeyMapping(
            "key.daotcompat.exit_ragdoll",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            "key.categories.daotcompat");

    /** Releases both ODM ropes without releasing the ragdoll — ropes can be re-shot any time. */
    public static final KeyMapping RELEASE_ROPES = new KeyMapping(
            "key.daotcompat.release_ropes",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN,
            "key.categories.daotcompat");

    private RagdollKeybinds() {}

    /** Must be registered on the MOD event bus (RegisterKeyMappingsEvent is a mod-bus event). */
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(EXIT_RAGDOLL);
        event.register(RELEASE_ROPES);
    }
}
