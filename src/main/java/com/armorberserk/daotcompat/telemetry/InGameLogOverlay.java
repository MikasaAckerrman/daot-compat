/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.telemetry;

import com.armorberserk.daotcompat.DAOTCompat;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

/**
 * The player's window into the same data the telemetry endpoint serves: the last log lines
 * floating bottom-right over the HUD, plus a two-line live status header (ragdoll, seat
 * distance, hook positions — exactly what the "body vs ragdoll" debugging needs).
 *
 * <p>{@code F6} cycles the mode: DAOT lines -> ALL lines -> OFF -> DAOT. Edge-detected with
 * {@code isDown()} on purpose: vanilla {@code handleKeybinds()} drains {@code consumeClick()}
 * before NeoForge Post listeners ever run (a trap this mod has already documented).
 */
public final class InGameLogOverlay {

    private static final int LINES = 14;
    private static final int LINE_HEIGHT = 9;

    public static final KeyMapping TOGGLE = new KeyMapping(
            "key.daotcompat.telemetry_overlay",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F6,
            "key.categories.daotcompat");

    private enum Mode { DAOT, ALL, OFF }

    private static volatile Mode mode = Mode.DAOT;
    private static boolean toggleWasDown;

    private InGameLogOverlay() {}

    /** Must be registered on the MOD event bus (RegisterKeyMappingsEvent is a mod-bus event). */
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE);
    }

    /** Must be registered on the MOD event bus (RegisterGuiLayersEvent is a client mod-bus event). */
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(DAOTCompat.MOD_ID, "log_overlay"),
                InGameLogOverlay::render);
    }

    /** Call every client tick: F6 edge-detection cycles the overlay mode. */
    public static void tick() {
        boolean down = TOGGLE.isDown();
        if (down && !toggleWasDown) {
            mode = switch (mode) {
                case DAOT -> Mode.ALL;
                case ALL -> Mode.OFF;
                case OFF -> Mode.DAOT;
            };
            DAOTCompat.LOGGER.info("[telemetry] overlay mode -> {}", mode);
        }
        toggleWasDown = down;
    }

    // LayeredDraw.Layer#render(GuiGraphics, DeltaTracker)
    private static void render(GuiGraphics g, DeltaTracker delta) {
        if (mode == Mode.OFF) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.font == null) return;
        Font font = mc.font;

        List<LogTap.Line> lines = LogTap.snapshot(LINES, mode == Mode.DAOT);
        String status = statusLine();
        String pos = posLine();

        int screenW = g.guiWidth();
        int maxTextW = Math.min(560, screenW / 2);
        int blockHeight = (lines.size() + 2) * LINE_HEIGHT + 3;
        int y = g.guiHeight() - 3 - blockHeight;

        drawRight(g, font, status, y, 0xFF55FFFF, maxTextW); y += LINE_HEIGHT;
        drawRight(g, font, pos, y, 0xFF55FFFF, maxTextW); y += LINE_HEIGHT;
        for (LogTap.Line l : lines) {
            String text = "[" + shortLevel(l) + "] " + l.logger() + ": " + l.message();
            drawRight(g, font, text, y, colorOf(l), maxTextW);
            y += LINE_HEIGHT;
        }
    }

    private static void drawRight(GuiGraphics g, Font font, String text, int y, int color, int maxTextW) {
        String t = font.plainSubstrByWidth(text, maxTextW);
        int textW = font.width(t);
        int x = g.guiWidth() - 3 - textW;
        g.fill(x - 2, y - 1, g.guiWidth() - 1, y + LINE_HEIGHT - 1, 0x9000_0000);
        g.drawString(font, t, x, y, color, true);
    }

    private static int colorOf(LogTap.Line l) {
        return switch (l.level()) {
            case "ERROR", "FATAL" -> 0xFFFF5555;
            case "WARN" -> 0xFFFFFFA0;
            default -> l.daot() ? 0xFFFFFFFF : 0xFFB0B0B0;
        };
    }

    private static String shortLevel(LogTap.Line l) {
        return switch (l.level()) {
            case "ERROR", "FATAL" -> "E";
            case "WARN" -> "W";
            case "DEBUG", "TRACE" -> "D";
            default -> "I";
        };
    }

    private static String statusLine() {
        String veh = LiveState.vehicleDist() >= 0.0D
                ? LiveState.vehicleType() + " d=" + f(LiveState.vehicleDist())
                : "none";
        return "LOG[" + mode + "] ragdoll=" + (LiveState.ragdolledLive() ? "LIVE" : "no")
                + " stun=" + (LiveState.stunned() ? "YES" : "no")
                + " seat=" + veh;
    }

    private static String posLine() {
        Vec3 anchor = LiveState.anchorPos();
        return "me(" + f(LiveState.playerX()) + " " + f(LiveState.playerY()) + " " + f(LiveState.playerZ()) + ")"
                + " L" + hookStr(LiveState.leftHookPos())
                + " R" + hookStr(LiveState.rightHookPos())
                + (anchor != null ? " anchor(" + f(anchor.x) + "," + f(anchor.y) + "," + f(anchor.z) + ")" : "");
    }

    private static String hookStr(Vec3 v) {
        return v == null ? "(-)" : "(" + f(v.x) + "," + f(v.y) + "," + f(v.z) + ")";
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
