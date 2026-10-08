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
import java.util.concurrent.Executors;

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
        String query = ex.getRequestURI().getRawQuery();
        int limit = 200;
        boolean daotOnly = false;
        if (query != null) {
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                String key = eq < 0 ? pair : pair.substring(0, eq);
                String val = eq < 0 ? "" : pair.substring(eq + 1);
                if ("limit".equals(key)) {
                    try {
                        limit = Math.max(1, Math.min(1200, Integer.parseInt(val)));
                    } catch (NumberFormatException ignored) {
                    }
                } else if ("filter".equals(key)) {
                    daotOnly = val.contains("daot");
                }
            }
        }
        return LogTap.toJson(limit, daotOnly);
    }

    private static String handleHealth(HttpExchange ex) {
        return "{\"ok\":true,\"mod\":\"daotcompat\",\"jarVersion\":\"" + Json.esc(jarVersion()) + "\"}";
    }

    private static String jarVersion() {
        return "1.2.3";
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
