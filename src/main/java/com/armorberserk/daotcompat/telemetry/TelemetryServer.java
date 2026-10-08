/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.telemetry;

import com.armorberserk.daotcompat.DAOTCompat;
import com.armorberserk.daotcompat.config.DaotConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import org.jetbrains.annotations.Nullable;

/**
 * The developer's window into a running game: a localhost-only HTTP endpoint plus a periodic
 * JSON file dump, both fed by {@link LogTap} and {@link LiveState}.
 *
 * <p>Bound to 127.0.0.1 only — never exposed to the network. Routes:
 * <ul>
 *   <li>{@code GET /state}  — the latest per-tick {@link LiveState} snapshot</li>
 *   <li>{@code GET /events?limit=200&filter=daot|all} — recent captured log lines</li>
 *   <li>{@code GET /health} — liveness + versions</li>
 * </ul>
 *
 * <p>As a network-free fallback the same state (plus the last 30 daot lines) is rewritten to
 * {@code logs/daotcompat-live.json} every ~5 seconds while the game runs.
 */
public final class TelemetryServer {

    private static final int FILE_EVERY_TICKS = 100; // 100 ticks = 5 s
    private static volatile HttpServer server;
    private static volatile boolean startAttempted;
    private static volatile boolean fileFailedLogged;
    private static int tickCounter;

    private TelemetryServer() {}

    /** Starts the endpoint once; call lazily (first client tick), after configs are loaded. */
    public static synchronized void start() {
        if (startAttempted || server != null) return;
        startAttempted = true;
        if (!DaotConfig.TELEMETRY_ENABLED.get()) {
            DAOTCompat.LOGGER.info("[telemetry] disabled in config");
            return;
        }
        int port;
        try {
            port = DaotConfig.TELEMETRY_PORT.get();
        } catch (Throwable t) {
            DAOTCompat.LOGGER.warn("[telemetry] port not readable yet: {}", t.toString());
            return;
        }
        try {
            HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            http.setExecutor(Executors.newFixedThreadPool(1, r -> {
                Thread t = new Thread(r, "daotcompat-telemetry");
                t.setDaemon(true);
                return t;
            }));
            http.createContext("/state", ex -> safeHandle(ex, TelemetryServer::handleState));
            http.createContext("/events", ex -> safeHandle(ex, TelemetryServer::handleEvents));
            http.createContext("/health", ex -> safeHandle(ex, TelemetryServer::handleHealth));
            http.createContext("/shot", ex -> safeHandleBytes(ex, TelemetryServer::handleShot));
            http.createContext("/shot/last", ex -> safeHandleBytes(ex, TelemetryServer::handleLastShot));
            http.createContext("/control/keys", ex -> safeHandle(ex, TelemetryServer::handleControlKeys));
            http.createContext("/control/input", ex -> safeHandle(ex, TelemetryServer::handleControlInput));
            http.createContext("/control/look", ex -> safeHandle(ex, TelemetryServer::handleControlLook));
            http.createContext("/control/cmd", ex -> safeHandle(ex, TelemetryServer::handleControlCmd));
            http.start();
            server = http;
            DAOTCompat.LOGGER.info("[telemetry] live at http://127.0.0.1:{}/state (logs: /events, file: logs/daotcompat-live.json)", port);
        } catch (Throwable t) {
            DAOTCompat.LOGGER.warn("[telemetry] HTTP endpoint failed to start on port {}: {}", port, t.toString());
        }
    }

    /** Call every client tick: throttled file dump of state + recent daot log lines. */
    public static void tickFileDump() {
        if (server == null) return;
        if (++tickCounter % FILE_EVERY_TICKS != 0) return;
        try {
            Path file = FMLPaths.GAMEDIR.get().resolve("logs").resolve("daotcompat-live.json");
            String doc = "{\"state\":" + LiveState.json() + ",\"events\":" + LogTap.toJson(30, true) + "}";
            Files.writeString(file, doc, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            if (!fileFailedLogged) {
                fileFailedLogged = true;
                DAOTCompat.LOGGER.warn("[telemetry] file dump failed: {}", t.toString());
            }
        }
    }

    private interface Handler {
        String handle(HttpExchange ex) throws IOException;
    }

    private static void safeHandle(HttpExchange ex, Handler handler) {
        try {
            String body = handler.handle(ex);
            respond(ex, 200, body);
        } catch (Throwable t) {
            respond(ex, 500, "{\"error\":\"" + Json.esc(t.toString()) + "\"}");
        }
    }

    private static String handleState(HttpExchange ex) {
        return LiveState.json();
    }

    private static String handleEvents(HttpExchange ex) {
        String query = rawQuery(ex);
        int limit = intParam(query, "limit", 200);
        boolean daotOnly = query != null && query.contains("filter=daot");
        return LogTap.toJson(Math.max(1, Math.min(1200, limit)), daotOnly);
    }

    // --- Control endpoints: the agent's hands (gated by telemetryControl, localhost only) ---

    private static String handleControlKeys(HttpExchange ex) {
        StringBuilder b = new StringBuilder(256);
        b.append("{\"count\":").append(ControlBridge.keyNames().size()).append(",\"keys\":[");
        List<String> names = ControlBridge.keyNames();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) b.append(',');
            b.append('"').append(Json.esc(names.get(i))).append('"');
        }
        b.append("]}");
        return b.toString();
    }

    private static String handleControlInput(HttpExchange ex) {
        String query = rawQuery(ex);
        String key = param(query, "key");
        if (key == null || key.isBlank()) return "{\"error\":\"missing ?key= (see /control/keys)\"}";
        String d = param(query, "down");
        Boolean down = "true".equals(d) ? Boolean.TRUE : ("false".equals(d) ? Boolean.FALSE : null);
        int ticks = intParam(query, "ticks", down == Boolean.FALSE ? 0 : 1);
        return ControlBridge.press(key, ticks, down);
    }

    private static String handleControlLook(HttpExchange ex) {
        String query = rawQuery(ex);
        Float yaw = floatParam(query, "yaw");
        Float pitch = floatParam(query, "pitch");
        if (yaw == null || pitch == null) return "{\"error\":\"missing ?yaw=&pitch=\"}";
        return ControlBridge.look(yaw, pitch);
    }

    private static String handleControlCmd(HttpExchange ex) {
        String cmd = param(rawQuery(ex), "c");
        if (cmd == null) return "{\"error\":\"missing ?c=<command>\"}";
        return ControlBridge.command(cmd);
    }

    // --- query-param helpers ---

    @Nullable
    private static String rawQuery(HttpExchange ex) {
        String q = ex.getRequestURI().getRawQuery();
        return q == null ? "" : q;
    }

    @Nullable
    private static String param(@Nullable String query, String name) {
        if (query == null || query.isBlank()) return null;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            if (!key.equals(name)) continue;
            String val = eq < 0 ? "" : pair.substring(eq + 1);
            try {
                return java.net.URLDecoder.decode(val, java.nio.charset.StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return val;
            }
        }
        return null;
    }

    private static int intParam(@Nullable String query, String name, int fallback) {
        String v = param(query, name);
        if (v == null) return fallback;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Nullable
    private static Float floatParam(@Nullable String query, String name) {
        String v = param(query, name);
        if (v == null) return null;
        try {
            return Float.parseFloat(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String handleHealth(HttpExchange ex) {
        return "{\"ok\":true,\"mod\":\"daotcompat\",\"jarVersion\":\"" + Json.esc(jarVersion()) + "\"}";
    }

    // --- Screenshot endpoints: the developer's eyes into the running game ---

    private interface BytesHandler {
        byte[] handle(HttpExchange ex) throws IOException;
    }

    private static void safeHandleBytes(HttpExchange ex, BytesHandler handler) {
        try {
            byte[] bytes = handler.handle(ex);
            if (bytes == null) {
                respond(ex, 500, "{\"error\":\"capture failed\"}");
                return;
            }
            try {
                ex.getResponseHeaders().set("Content-Type", "image/png");
                ex.getResponseHeaders().set("X-Shot-Name", shotName(ex));
                ex.sendResponseHeaders(200, bytes.length);
                ex.getResponseBody().write(bytes);
            } catch (IOException ignored) {
            } finally {
                try {
                    ex.close();
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable t) {
            respond(ex, 500, "{\"error\":\"" + Json.esc(t.toString()) + "\"}");
        }
    }

    private static String shotName(HttpExchange ex) {
        return ex.getAttribute("shotName") instanceof String s ? s : "";
    }

    /** Captures NOW (scheduled onto the render thread), waits briefly, serves the PNG. */
    private static byte[] handleShot(HttpExchange ex) throws IOException {
        String file = ScreenCapture.captureAsync("http");
        if (file == null) return null;
        // Vanilla Screenshot.grab(File gameDir, name, ...) writes to gameDir/screenshots/<name>
        // with NO extension appended — the name is used as-is.
        Path png = FMLPaths.GAMEDIR.get().resolve("screenshots").resolve(file);
        try {
            for (int i = 0; i < 20 && !Files.exists(png); i++) {
                Thread.sleep(50);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (!Files.exists(png)) return null;
        ex.setAttribute("shotName", png.getFileName().toString());
        return Files.readAllBytes(png);
    }

    /** Serves the newest daotcompat_* frame (the last event capture) without capturing. */
    private static byte[] handleLastShot(HttpExchange ex) throws IOException {
        Path dir = FMLPaths.GAMEDIR.get().resolve("screenshots");
        Path newest = null;
        try (var stream = Files.list(dir)) {
            var it = stream.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith("daotcompat_");
            }).iterator();
            while (it.hasNext()) {
                Path p = it.next();
                if (newest == null || p.toFile().lastModified() > newest.toFile().lastModified()) {
                    newest = p;
                }
            }
        } catch (Throwable t) {
            return null;
        }
        if (newest == null) return null;
        ex.setAttribute("shotName", newest.getFileName().toString());
        return Files.readAllBytes(newest);
    }

    private static String jarVersion() {
        return "1.2.9";
    }

    private static void respond(HttpExchange ex, int code, String body) {
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            ex.sendResponseHeaders(code, bytes.length);
            ex.getResponseBody().write(bytes);
        } catch (IOException ignored) {
            // client hung up; nothing to do
        } finally {
            try {
                ex.close();
            } catch (Throwable ignored) {
            }
        }
    }
}
