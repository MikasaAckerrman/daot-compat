/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.util;

import com.armorberserk.daotcompat.DAOTCompat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate-limited logging for per-tick events: the first occurrence logs immediately, repeats are
 * counted and flushed at most once per {@code intervalSeconds} with a "+N suppressed" suffix.
 * Keeps the log readable while still exposing every high-frequency system.
 */
public final class LogThrottle {

    private record State(long lastLogMs, int suppressed) {}

    private static final Map<String, long[]> LAST = new ConcurrentHashMap<>();
    private static final Map<String, Integer> SUPPRESSED = new ConcurrentHashMap<>();

    private LogThrottle() {}

    public static void info(String key, int intervalSeconds, String message) {
        long now = System.currentTimeMillis();
        long[] last = LAST.computeIfAbsent(key, k -> new long[]{0L});
        if (now - last[0] >= intervalSeconds * 1000L) {
            Integer sup = SUPPRESSED.remove(key);
            String suffix = (sup != null && sup > 0) ? " | +" + sup + " suppressed" : "";
            DAOTCompat.LOGGER.info("[{}] {}{}", key, message, suffix);
            last[0] = now;
        } else {
            SUPPRESSED.merge(key, 1, Integer::sum);
        }
    }

    public static void debug(String key, int intervalSeconds, String message) {
        if (DAOTCompat.LOGGER.isDebugEnabled()) info(key, intervalSeconds, message);
    }
}
