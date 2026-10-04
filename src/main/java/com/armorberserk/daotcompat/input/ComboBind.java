/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.input;

import com.armorberserk.daotcompat.DAOTCompat;
import com.armorberserk.daotcompat.config.DaotConfig;
import com.armorberserk.daotcompat.ragdoll.RagdollClient;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Combo bind: one keyboard key (the key bound to the chosen action in Controls) + one mouse
 * button, pressed together, trigger the action. Raw button state is polled via
 * {@link InputConstants#isKeyDown(long, int)} because vanilla KeyMappings cannot express chords.
 *
 * <p>Config: {@code comboAction} (NONE / EXIT_RAGDOLL / RELEASE_ROPES) and
 * {@code comboMouseButton} (-1 off, 0 LMB, 1 RMB, 2 MMB). When the combo handles an action, the
 * plain keyboard press for that action no longer fires on its own — otherwise holding X and
 * clicking would exit twice.
 */
public final class ComboBind {

    private static boolean comboWasActive;

    private ComboBind() {}

    public static void tick(LocalPlayer player) {
        String action = DaotConfig.COMBO_ACTION.get();
        int mouseButton = DaotConfig.COMBO_MOUSE_BUTTON.get();
        if ("NONE".equalsIgnoreCase(action) || mouseButton < 0) {
            comboWasActive = false;
            return;
        }

        Minecraft mc = DAOTCompat.minecraft();
        boolean keyDown = keyFor(action).isDown();
        boolean mouseDown = InputConstants.isKeyDown(mc.getWindow().getWindow(), mouseButton);
        boolean both = keyDown && mouseDown;

        if (both && !comboWasActive) {
            if ("RELEASE_ROPES".equalsIgnoreCase(action)) {
                RagdollClient.releaseRopes();
            } else {
                RagdollClient.exit();
            }
            DAOTCompat.LOGGER.info("[combo] {} triggered (key + mouse button {})", action, mouseButton);
        }
        comboWasActive = both;
    }

    /** When true, the plain single-key trigger for this action must be suppressed. */
    public static boolean handlesAction(String action) {
        return DaotConfig.COMBO_MOUSE_BUTTON.get() >= 0
                && DaotConfig.COMBO_ACTION.get().equalsIgnoreCase(action);
    }

    private static KeyMappingRef keyFor(String action) {
        return "RELEASE_ROPES".equalsIgnoreCase(action)
                ? new KeyMappingRef(RagdollKeybinds.RELEASE_ROPES)
                : new KeyMappingRef(RagdollKeybinds.EXIT_RAGDOLL);
    }

    private record KeyMappingRef(net.minecraft.client.KeyMapping mapping) {
        boolean isDown() {
            return mapping.isDown();
        }
    }
}
