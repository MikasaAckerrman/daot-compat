/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.telemetry;

import com.armorberserk.daotcompat.DAOTCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.fml.loading.FMLPaths;

import java.io.File;

/**
 * The developer's eyes: vanilla {@link Screenshot#grab} at the moments that matter.
 *
 * <p>Captures are event-driven (ragdoll start, hook latch, RECOVER, wire break) plus on demand
 * via the telemetry endpoint's {@code /shot}. Files land in the game's {@code screenshots/}
 * folder as {@code daotcompat_<tag>_<millis>.png}; the endpoint serves them back so the log
 * watcher (and the agent behind it) can SEE what the game looked like at the exact tick of an
 * event — no more second-hand user descriptions.
 *
 * <p>Must be called on the render thread (the client tick listener and Minecraft.execute both
 * qualify). Rate-limited so a burst of events can never spam readPixels stalls.
 */
public final class ScreenCapture {

    private static final long MIN_INTERVAL_MS = 400L;
    private static volatile long lastCaptureMs;
    private static volatile boolean grabFailedLogged;

    private ScreenCapture() {}

    public static void capture(String tag) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getMainRenderTarget() == null) return;
        long now = System.currentTimeMillis();
        if (now - lastCaptureMs < MIN_INTERVAL_MS) return;
        lastCaptureMs = now;
        try {
            // Vanilla grab(File gameDir, ...) writes to gameDir/screenshots/<name> — the name
            // is used as-is, no extension appended.
            File dir = FMLPaths.GAMEDIR.get().toFile();
            Screenshot.grab(dir, name(tag, now), mc.getMainRenderTarget(), c -> { });
        } catch (Throwable t) {
            if (!grabFailedLogged) {
                grabFailedLogged = true;
                DAOTCompat.LOGGER.warn("[telemetry] screenshot grab failed: {}", t.toString());
            }
        }
    }

    /** A fresh capture for the HTTP endpoint (called on the telemetry thread, runs on render). */
    public static String captureAsync(String tag) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getMainRenderTarget() == null) return null;
        String file = name(tag, System.currentTimeMillis());
        mc.execute(() -> {
            try {
                File dir = FMLPaths.GAMEDIR.get().toFile();
                Screenshot.grab(dir, file, mc.getMainRenderTarget(), c -> { });
            } catch (Throwable t) {
                DAOTCompat.LOGGER.warn("[telemetry] screenshot grab failed: {}", t.toString());
            }
        });
        return file;
    }

    private static String name(String tag, long millis) {
        return "daotcompat_" + tag + "_" + millis;
    }
}
