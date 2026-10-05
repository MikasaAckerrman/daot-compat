/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.input;

import com.armorberserk.daotcompat.DAOTCompat;
import com.armorberserk.daotcompat.config.DaotConfig;
import com.armorberserk.daotcompat.ragdoll.RagdollClient;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.lwjgl.glfw.GLFW;

/**
 * Hook chords: the ODM gear's hooks fire only from key+mouse combinations — left hook on
 * {@code modifier + LMB}, right hook on {@code modifier + RMB} (defaults: modifier = X, i.e.
 * "Ч" on ЙЦУКЕН).
 *
 * <p>Mechanism (v2, robust): on probe we REBIND AOT's {@code leftKey}/{@code rightKey}
 * KeyMappings to {@link InputConstants#UNKNOWN} via their public {@code setKey} — vanilla input
 * can never set them again (and they stop conflicting with attack/use). Every client tick we
 * then call their PUBLIC {@code setDown(boolean)} with the chord state. AOT's own per-tick
 * logic reads {@code isDown()} and runs all of its mechanics (gas, reeling, dual-hook) on top.
 *
 * <p>During a STUN both keys are forced down=false — the gear is suppressed. In an ordinary
 * ragdoll the chords keep working, so you can re-hook mid-ragdoll.
 */
public final class HookChords {

    private static volatile boolean probed;
    private static KeyMapping leftKey;
    private static KeyMapping rightKey;
    private static boolean leftWasActive;
    private static boolean rightWasActive;

    private HookChords() {}

    /** Poll every client tick. */
    public static void tick(LocalPlayer player) {
        if (!DaotConfig.HOOK_CHORDS_ENABLED.get()) return;
        if (!probe()) return;

        boolean stunned = RagdollClient.isStunned();
        boolean modifierDown = isKeyDown(DaotConfig.HOOK_MODIFIER_KEY.get());
        long window = Minecraft.getInstance().getWindow().getWindow();

        boolean leftActive = !stunned && modifierDown
                && InputConstants.isKeyDown(window, DaotConfig.HOOK_LEFT_MOUSE.get());
        boolean rightActive = !stunned && modifierDown
                && InputConstants.isKeyDown(window, DaotConfig.HOOK_RIGHT_MOUSE.get());

        if (leftKey.isDown() != leftActive) leftKey.setDown(leftActive);
        if (rightKey.isDown() != rightActive) rightKey.setDown(rightActive);
        leftWasActive = leftActive;
        rightWasActive = rightActive;
    }

    private static synchronized boolean probe() {
        if (probed) return leftKey != null;
        probed = true;
        try {
            Class<?> tickHandler = Class.forName("daot.ODMTickHandler");
            leftKey = (KeyMapping) tickHandler.getField("leftKey").get(null);
            rightKey = (KeyMapping) tickHandler.getField("rightKey").get(null);
            if (leftKey == null || rightKey == null) return false;
            // Rebind both to UNKNOWN: vanilla input events can never flip them again.
            InputConstants.Key dead = InputConstants.UNKNOWN;
            leftKey.setKey(dead);
            rightKey.setKey(dead);
            DAOTCompat.LOGGER.info("[chords] AOT hook keys rebound to UNKNOWN — chords take over");
            return true;
        } catch (Throwable t) {
            DAOTCompat.LOGGER.warn("[chords] AOT hook key fields not found, chords disabled: {}", t.toString());
            return false;
        }
    }

    /** "X" -> GLFW_KEY_X (65 + offset), digits map 48..57, otherwise InputConstants fallback. */
    private static boolean isKeyDown(String keyName) {
        if (keyName == null || keyName.isBlank()) return false;
        int code = -1;
        String s = keyName.trim().toUpperCase(java.util.Locale.ROOT);
        if (s.length() == 1 && s.charAt(0) >= 'A' && s.charAt(0) <= 'Z') {
            code = GLFW.GLFW_KEY_A + (s.charAt(0) - 'A');
        } else if (s.length() == 1 && s.charAt(0) >= '0' && s.charAt(0) <= '9') {
            code = GLFW.GLFW_KEY_0 + (s.charAt(0) - '0');
        } else {
            try {
                var key = InputConstants.getKey("key.keyboard." + keyName.trim().toLowerCase());
                if (key != null && key.getType() == InputConstants.Type.KEYSYM) code = key.getValue();
            } catch (Throwable ignored) {
            }
        }
        return code >= 0 && InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), code);
    }
}
