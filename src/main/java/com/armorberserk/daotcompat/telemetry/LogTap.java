/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.telemetry;

import com.armorberserk.daotcompat.DAOTCompat;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Property;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Captures every line the game logs into a small in-memory ring buffer.
 *
 * <p>The game's logging backend is Log4j2 2.22.1 behind SLF4J (log4j-slf4j2-impl), so the tap is
 * a programmatic Log4j2 appender attached to the ROOT logger at client startup. The buffer feeds
 * two consumers: the in-game HUD overlay ({@link InGameLogOverlay}) and the localhost telemetry
 * endpoint ({@link TelemetryServer}) — so both the player and the developer see the same lines.
 *
 * <p>{@code append} runs on whatever thread logged the line, so the ring is guarded by a plain
 * lock and the handler never logs anything itself (no recursion) and never throws
 * ({@code ignoreExceptions = true} plus an inner catch as a second belt).
 */
public final class LogTap {

    private static final int CAPACITY = 1200;
    private static final int MAX_MESSAGE = 400;

    private static final ArrayDeque<Line> RING = new ArrayDeque<>();
    private static final Object LOCK = new Object();
    private static final AtomicLong SEQ = new AtomicLong();
    private static volatile boolean attached;

    /** One captured log line, newest last. */
    public record Line(long seq, long timeMs, String thread, String level, String logger,
                       String message, boolean daot) {}

    private LogTap() {}

    /** Lines from our mod and the ragdoll stack — the overlay's default filter. */
    public static boolean isDaotLine(String loggerName) {
        if (loggerName == null) return false;
        String l = loggerName.toLowerCase(Locale.ROOT);
        return l.contains("daot") || l.contains("ragdoll");
    }

    /** Attaches the appender to the Log4j2 root logger; idempotent, safe to call once at startup. */
    public static void attach() {
        if (attached) return;
        synchronized (LogTap.class) {
            if (attached) return;
            try {
                LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
                Configuration cfg = ctx.getConfiguration();
                TapAppender appender = new TapAppender();
                appender.start();
                cfg.addAppender(appender);
                cfg.getRootLogger().addAppender(appender, null, null);
                ctx.updateLoggers();
                attached = true;
                DAOTCompat.LOGGER.info("[telemetry] log tap attached to the root logger");
            } catch (Throwable t) {
                DAOTCompat.LOGGER.warn("[telemetry] log tap attach failed: {}", t.toString());
            }
        }
    }

    static void capture(LogEvent event) {
        try {
            String msg = event.getMessage() == null ? "" : event.getMessage().getFormattedMessage();
            if (msg == null) msg = "";
            if (msg.length() > MAX_MESSAGE) msg = msg.substring(0, MAX_MESSAGE);
            String logger = event.getLoggerName() == null ? "" : event.getLoggerName();
            Line line = new Line(SEQ.incrementAndGet(), event.getTimeMillis(),
                    event.getThreadName(), event.getLevel().name(), logger, msg, isDaotLine(logger));
            synchronized (LOCK) {
                RING.addLast(line);
                while (RING.size() > CAPACITY) RING.removeFirst();
            }
        } catch (Throwable ignored) {
            // The tap must never break logging.
        }
    }

    /** Newest-last copy of the most recent {@code limit} lines, optionally daot-filtered. */
    public static List<Line> snapshot(int limit, boolean daotOnly) {
        List<Line> out = new ArrayList<>(Math.min(limit, 64));
        synchronized (LOCK) {
            Iterator<Line> it = RING.descendingIterator();
            while (it.hasNext() && out.size() < limit) {
                Line l = it.next();
                if (!daotOnly || l.daot()) out.add(l);
            }
        }
        Collections.reverse(out);
        return out;
    }

    /** Serialises the last {@code limit} lines as a JSON array (newest last). */
    public static String toJson(int limit, boolean daotOnly) {
        List<Line> lines = snapshot(limit, daotOnly);
        StringBuilder b = new StringBuilder(256 + lines.size() * 160);
        b.append("{\"count\":").append(lines.size()).append(",\"events\":[");
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            if (i > 0) b.append(',');
            b.append("{\"seq\":").append(l.seq())
                    .append(",\"time\":").append(l.timeMs())
                    .append(",\"thread\":\"").append(Json.esc(l.thread())).append('"')
                    .append(",\"level\":\"").append(Json.esc(l.level())).append('"')
                    .append(",\"logger\":\"").append(Json.esc(l.logger())).append('"')
                    .append(",\"daot\":").append(l.daot())
                    .append(",\"message\":\"").append(Json.esc(l.message())).append("\"}");
        }
        b.append("]}");
        return b.toString();
    }

    private static final class TapAppender extends AbstractAppender {
        TapAppender() {
            super("DaotcompatTelemetryTap", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(LogEvent event) {
            capture(event);
        }
    }
}
