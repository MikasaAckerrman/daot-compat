/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.telemetry;

import com.armorberserk.daotcompat.config.DaotConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * The eyelid blink: a short sin-curve screen darkening on every ragdoll-mode transition.
 *
 * <p>First-person continuity analysis (v2.1): the camera itself is already mathematically
 * continuous across both transitions (the UNSEATED ragdoll keeps vanilla's camera on the
 * player entity — no teleports, no seat parenting). The remaining breaks are irreducible model
 * swaps done by the ragdoll mod: the first-person hands/viewmodel vanish the instant the mod
 * hides the player and pop back at the swap, plus one-frame add/remove pops of the ragdoll
 * body. Hiding a model swap behind a brief blink is the industry-standard treatment: the eye
 * reads "I blinked", not "the world glitched".
 *
 * <p>Triggered on BOTH edges of the ragdolled state (into the ragdoll — crash, and out of it —
 * smooth recover / rest / X), covering every transition path. Gated and tunable live via the
 * {@code ragdollTransitionBlink} / {@code ragdollTransitionBlinkMs} client config.
 */
public final class TransitionBlink {

    private static volatile long startMs = -1L;
    private static volatile long durationMs = 350L;

    private TransitionBlink() {}

    /** Must be registered on the MOD event bus (RegisterGuiLayersEvent is a client mod-bus event). */
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(com.armorberserk.daotcompat.DAOTCompat.MOD_ID,
                        "transition_blink"),
                TransitionBlink::render);
    }

    /**
     * Begins a blink; into-ragdoll is longer (impact feel), out-of-ragdoll shorter (wake-up).
     */
    public static void begin(boolean intoRagdoll) {
        long configured = configuredMs();
        if (configured <= 0L) return;
        durationMs = intoRagdoll ? configured + 100L : configured;
        startMs = System.currentTimeMillis();
    }

    private static long configuredMs() {
        try {
            return DaotConfig.RAGDOLL_TRANSITION_BLINK_MS.get();
        } catch (Throwable t) {
            return 350L;
        }
    }

    // LayeredDraw.Layer#render(GuiGraphics, DeltaTracker)
    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        try {
            if (!DaotConfig.RAGDOLL_TRANSITION_BLINK.get()) {
                startMs = -1L;
                return;
            }
        } catch (Throwable ignored) {
        }
        long start = startMs;
        if (start < 0L) return;
        long elapsed = System.currentTimeMillis() - start;
        if (elapsed >= durationMs) {
            startMs = -1L;
            return;
        }
        // Sin curve: perfectly smooth 0 -> full -> 0, no corners for the eye to catch.
        double phase = (double) elapsed / (double) durationMs;
        int alpha = (int) (Math.sin(Math.PI * phase) * 210.0D);
        if (alpha > 0) {
            g.fill(0, 0, g.guiWidth(), g.guiHeight(), alpha << 24);
        }
    }
}
