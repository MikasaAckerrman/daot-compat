/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.input;

import com.armorberserk.daotcompat.DAOTCompat;
import com.mojang.blaze3d.platform.InputConstants;
import com.armorberserk.daotcompat.config.DaotConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Hook chords: the ODM gear's hooks fire only from key+mouse combinations instead of the plain
 * buttons — left hook on {@code modifier + LMB}, right hook on {@code modifier + RMB} (defaults:
 * modifier = X, i.e. "Ч" on ЙЦУКЕН).
 *
 * <p>How it works: AOT's {@code ODMTickHandler} keeps its own {@code leftKey}/{@code rightKey}
 * KeyMappings and reads {@code isDown()} every tick. We force those KeyMappings' private
 * {@code isDown}/{@code clickCount} fields to the chord state each client tick: while the
 * modifier is held, pressing the bound mouse button lights the hook key up; releasing either
 * input puts it down. AOT's own fire mechanics (gas, reeling, dual-hook) run untouched on top.
 *
 * <p>While the player is ragdolled and STUNNED, both hook keys are forced down=false — a stun
 * suppresses the gear. In an ordinary ragdoll the chords work normally, so you can re-hook
 * mid-ragdoll and swing out of it.
 */
public final class HookChords {

    private static volatile boolean probed;
    private static Field leftKeyField;
    private static Field rightKeyField;
    private static Field isDownField;
    private static Field clickCountField;
    private static boolean leftWasActive;
    private static boolean rightWasActive;

    private HookChords() {}

    /** Poll every client tick. Forces AOT's hook keys to the chord state. */
    public static void tick(LocalPlayer player) {
        if (!DaotConfig.HOOK_CHORDS_ENABLED.get()) return;
        if (!probe()) return;

        boolean stunned = com.armorberserk.daotcompat.ragdoll.RagdollClient.isStunned();
        boolean modifierDown = isKeyDown(DaotConfig.HOOK_MODIFIER_KEY.get());
        int leftMouse = DaotConfig.HOOK_LEFT_MOUSE.get();
        int rightMouse = DaotConfig.HOOK_RIGHT_MOUSE.get();

        boolean leftActive = !stunned && modifierDown
                && InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), leftMouse);
        boolean rightActive = !stunned && modifierDown
                && InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), rightMouse);

        force(leftKeyField, leftActive, leftWasActive);
        leftWasActive = leftActive;
        force(rightKeyField, rightActive, rightWasActive);
        rightWasActive = rightActive;
    }

    private static void force(Field keyField, boolean active, boolean wasActive) {
        try {
            KeyMapping mapping = (KeyMapping) keyField.get(null);
            if (mapping == null) return;
            isDownField.setBoolean(mapping, active);
            // Mimic a fresh press on the rising edge so any consumeClick()-style consumer sees it.
            clickCountField.setInt(mapping, active && !wasActive ? 1 : 0);
        } catch (Throwable ignored) {
        }
    }

    private static boolean probe() {
        if (probed) return leftKeyField != null;
        probed = true;
        try {
            Class<?> tickHandler = Class.forName("daot.ODMTickHandler");
            leftKeyField = tickHandler.getField("leftKey");
            rightKeyField = tickHandler.getField("rightKey");
            Class<?> km = KeyMapping.class;
            isDownField = km.getDeclaredField("isDown");
            isDownField.setAccessible(true);
            clickCountField = km.getDeclaredField("clickCount");
            clickCountField.setAccessible(true);
            return leftKeyField != null && rightKeyField != null;
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
