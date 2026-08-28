/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.aot;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * Reflective access to Danny's AOT client-side store of other players' hooks
 * ({@code RemoteHookTracker} and its {@code RemoteHookData}). All the fields we touch are
 * public. Client-only by nature; if AOT is absent every call is a harmless no-op.
 */
public final class RemoteHookReflect {

    private static volatile boolean resolved;
    private static volatile boolean ready;
    private static Method getAllHooks;
    private static Field leftActive;
    private static Field leftPosition;
    private static Field rightActive;
    private static Field rightPosition;

    private RemoteHookReflect() {}

    private static synchronized void resolve() {
        if (resolved) return;
        resolved = true;
        Class<?> tracker = Reflect.find("daot.RemoteHookTracker");
        Class<?> data = Reflect.find("daot.RemoteHookTracker$RemoteHookData");
        if (tracker == null || data == null) return;
        try {
            getAllHooks = tracker.getMethod("getAllHooks");
            leftActive = data.getField("leftActive");
            leftPosition = data.getField("leftPosition");
            rightActive = data.getField("rightActive");
            rightPosition = data.getField("rightPosition");
            ready = true;
        } catch (Throwable ignored) {
            // AOT present but remote-hook API changed - stay disabled
        }
    }

    public static boolean isAvailable() {
        if (!resolved) resolve();
        return ready;
    }

    /** @return every tracked remote hook record, or empty. */
    public static Collection<?> hooks() {
        if (!isAvailable()) return Collections.emptyList();
        try {
            return getAllHooks.invoke(null) instanceof Map<?, ?> m ? m.values() : Collections.emptyList();
        } catch (Throwable t) {
            return Collections.emptyList();
        }
    }

    public static boolean isActive(Object data, boolean left) {
        if (!ready || data == null) return false;
        try {
            return (left ? leftActive : rightActive).getBoolean(data);
        } catch (Throwable t) {
            return false;
        }
    }

    @Nullable
    public static Vec3 getPosition(Object data, boolean left) {
        if (!ready || data == null) return null;
        try {
            return (left ? leftPosition : rightPosition).get(data) instanceof Vec3 v ? v : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public static void setPosition(Object data, boolean left, Vec3 pos) {
        if (!ready || data == null || pos == null) return;
        try {
            (left ? leftPosition : rightPosition).set(data, pos);
        } catch (Throwable ignored) {
        }
    }
}
