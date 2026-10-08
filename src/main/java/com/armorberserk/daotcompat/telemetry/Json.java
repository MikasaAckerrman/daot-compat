/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.telemetry;

import java.util.Locale;

/** Minimal hand-rolled JSON helpers — the payload is numbers, booleans and short strings. */
final class Json {

    private Json() {}

    static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(Math.min(s.length() + 8, 1024));
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) b.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.toString();
    }

    static String n(double v) {
        return Double.isFinite(v) ? String.format(Locale.ROOT, "%.2f", v) : "null";
    }
}
