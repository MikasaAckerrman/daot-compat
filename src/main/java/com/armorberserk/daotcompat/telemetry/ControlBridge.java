/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.telemetry;

import com.armorberserk.daotcompat.DAOTCompat;
import com.armorberserk.daotcompat.config.DaotConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The developer's hands, next to {@link ScreenCapture}'s eyes: a localhost-only control bridge
 * driven by the telemetry endpoint. Press/hold game keys (including Danny's AOT hook keys),
 * aim the camera, and run chat commands as the player — so the agent behind the endpoint can
 * not only watch the game but act inside it.
 *
 * <p>All mutations are dispatched onto the render thread via {@link Minecraft#execute}; every
 * action is logged (auditable in /events); the whole bridge is gated by the
 * {@code telemetryControl} client config and only ever reachable on 127.0.0.1.
 */
public final class ControlBridge {

    /** key name -> mapping; built once. Names are the friendly aliases plus raw Options fields. */
    private static volatile Map<String, KeyMapping> keys;
    private static volatile boolean keysFailedLogged;

    private record Release(KeyMapping key, long atMs) {}

    private static final List<Release> RELEASES = new CopyOnWriteArrayList<>();

    private ControlBridge() {}

    public static boolean enabled() {
        try {
            return DaotConfig.TELEMETRY_CONTROL.get();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Press a named key. Semantics: {@code down=true} + {@code ticks>0} = hold for N ticks then
     * auto-release (default: a 1-tick tap); {@code ticks=0} = hold until an explicit
     * {@code down=false}; {@code down=false} = release now.
     */
    public static String press(String name, int ticks, Boolean down) {
        if (!enabled()) return "{\"error\":\"control disabled in config\"}";
        KeyMapping key = resolve(name);
        if (key == null) return "{\"error\":\"unknown key '" + Json.esc(name) + "' (see /control/keys)\"}";
        boolean press = down == null || down;
        Minecraft.getInstance().execute(() -> key.setDown(press));
        if (press && ticks > 0) {
            RELEASES.add(new Release(key, System.currentTimeMillis() + ticks * 50L));
        }
        DAOTCompat.LOGGER.info("[telemetry-control] key '{}' {}{}", name, press ? "pressed" : "released",
                press && ticks > 0 ? " for " + ticks + " ticks" : "");
        return "{\"ok\":true,\"key\":\"" + Json.esc(name) + "\"}";
    }

    /** Aim the player's camera. */
    public static String look(float yaw, float pitch) {
        if (!enabled()) return "{\"error\":\"control disabled in config\"}";
        var player = Minecraft.getInstance().player;
        if (player == null) return "{\"error\":\"no player\"}";
        Minecraft.getInstance().execute(() -> {
            player.setYRot(yaw);
            player.setXRot(pitch);
        });
        DAOTCompat.LOGGER.info("[telemetry-control] look yaw={} pitch={}", yaw, pitch);
        return "{\"ok\":true}";
    }

    /** Run a chat command as the player (server-side validation applies). */
    public static String command(String cmd) {
        if (!enabled()) return "{\"error\":\"control disabled in config\"}";
        var player = Minecraft.getInstance().player;
        if (player == null) return "{\"error\":\"no player\"}";
        if (cmd == null || cmd.isBlank()) return "{\"error\":\"empty command\"}";
        Minecraft.getInstance().execute(() -> player.connection.sendCommand(cmd));
        DAOTCompat.LOGGER.info("[telemetry-control] command: /{}", cmd);
        return "{\"ok\":true}";
    }

    /** Available key names (aliases first, then raw Options fields, then AOT hooks). */
    public static List<String> keyNames() {
        Map<String, KeyMapping> map = keys();
        return map == null ? List.of() : new ArrayList<>(map.keySet());
    }

    /** Called every client tick: releases keys whose hold window expired. */
    public static void tick() {
        if (RELEASES.isEmpty()) return;
        long now = System.currentTimeMillis();
        RELEASES.removeIf(r -> {
            if (now >= r.atMs()) {
                r.key().setDown(false);
                return true;
            }
            return false;
        });
    }

    @Nullable
    private static KeyMapping resolve(String name) {
        Map<String, KeyMapping> map = keys();
        return map == null ? null : map.get(name.toLowerCase(Locale.ROOT));
    }

    @Nullable
    private static Map<String, KeyMapping> keys() {
        Map<String, KeyMapping> cached = keys;
        if (cached != null) return cached;
        synchronized (ControlBridge.class) {
            if (keys != null) return keys;
            Map<String, KeyMapping> out = new LinkedHashMap<>();
            Minecraft mc = Minecraft.getInstance();
            // Friendly aliases for the ones an agent actually drives.
            try {
                out.put("forward", mc.options.keyUp);
                out.put("back", mc.options.keyDown);
                out.put("strafe_left", mc.options.keyLeft);
                out.put("strafe_right", mc.options.keyRight);
                out.put("jump", mc.options.keyJump);
                out.put("sneak", mc.options.keyShift);
                out.put("sprint", mc.options.keySprint);
                out.put("attack", mc.options.keyAttack);
                out.put("use", mc.options.keyUse);
            } catch (Throwable ignored) {
            }
            // Raw field names for everything else the game defines.
            try {
                for (Field f : mc.options.getClass().getDeclaredFields()) {
                    if (!KeyMapping.class.isAssignableFrom(f.getType())) continue;
                    if (!Modifier.isStatic(f.getModifiers())) f.setAccessible(true);
                    Object v = f.get(mc.options);
                    if (v instanceof KeyMapping km) out.putIfAbsent(f.getName().toLowerCase(Locale.ROOT), km);
                }
            } catch (Throwable t) {
                if (!keysFailedLogged) {
                    keysFailedLogged = true;
                    DAOTCompat.LOGGER.warn("[telemetry-control] options key scan failed: {}", t.toString());
                }
            }
            // Danny's AOT hook keys — public static on DannysAotClient.
            try {
                Class<?> client = Class.forName("daot.DannysAotClient");
                Object left = client.getField("LEFT_HOOK_KEY").get(null);
                Object right = client.getField("RIGHT_HOOK_KEY").get(null);
                if (left instanceof KeyMapping lk) out.put("aot.left", lk);
                if (right instanceof KeyMapping rk) out.put("aot.right", rk);
            } catch (Throwable ignored) {
                // AOT absent or renamed — simply no hook keys to drive.
            }
            keys = out;
            return out;
        }
    }
}
