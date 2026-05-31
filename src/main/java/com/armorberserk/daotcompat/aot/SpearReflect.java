/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.aot;

import java.lang.reflect.Method;

/**
 * Reflective check for Danny's AOT thunder spear entity. Server-safe: no client classes,
 * resolves through {@link Reflect}. When AOT is missing every call is a cheap {@code false}.
 */
public final class SpearReflect {

    private static volatile boolean resolved;
    private static volatile boolean ready;
    private static Class<?> spearClass;
    private static Method isLodged;

    private SpearReflect() {}

    private static synchronized void resolve() {
        if (resolved) return;
        resolved = true;
        Class<?> c = Reflect.find("daot.ThunderSpearEntity");
        if (c == null) return;
        try {
            isLodged = c.getMethod("isLodged");
            spearClass = c;
            ready = true;
        } catch (Throwable ignored) {
            // AOT present but spear API changed - stay disabled
        }
    }

    public static boolean isThunderSpear(Object entity) {
        if (!resolved) resolve();
        return ready && entity != null && spearClass.isInstance(entity);
    }

    public static boolean isLodged(Object entity) {
        if (!isThunderSpear(entity)) return false;
        try {
            return Boolean.TRUE.equals(isLodged.invoke(entity));
        } catch (Throwable t) {
            return false;
        }
    }
}
