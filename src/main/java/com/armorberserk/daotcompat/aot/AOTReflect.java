/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.aot;

import com.armorberserk.daotcompat.DAOTCompat;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Reflective access to Danny's AOT hook state.
 *
 * <p>AOT ships as a Fabric jar loaded through Sinytra Connector, which may place its
 * classes in a different loader than ours, so we scan the reachable loaders once and
 * cache the handles. {@code HookPoint}'s fields are public, so plain reflection is enough
 * and we never need a mixin on a foreign class. When AOT is missing, every call is a no-op.
 */
public final class AOTReflect {

    private static volatile boolean resolved;
    private static volatile boolean ready;

    private static Method leftHook;
    private static Method rightHook;
    private static Field position;
    private static Field active;
    private static Field hookedEntity;
    private static Method releaseFn;

    private AOTReflect() {}

    public static boolean isAvailable() {
        if (!resolved) resolve();
        return ready;
    }

    private static synchronized void resolve() {
        if (resolved) return;
        resolved = true;

        Class<?> hookPoint = Reflect.find("daot.HookPoint");
        Class<?> tickHandler = Reflect.find("daot.ODMTickHandler");
        if (hookPoint == null || tickHandler == null) {
            DAOTCompat.LOGGER.info("Danny's AOT not present - compatibility layer idle.");
            return;
        }
        try {
            position = hookPoint.getField("position");
            active = hookPoint.getField("active");
            hookedEntity = hookPoint.getField("hookedEntity");
            releaseFn = hookPoint.getMethod("release");
            leftHook = tickHandler.getMethod("getLeftHook");
            rightHook = tickHandler.getMethod("getRightHook");
            ready = true;
        } catch (Throwable t) {
            DAOTCompat.LOGGER.warn("Danny's AOT hook API has changed, disabling compat: {}", t.toString());
        }
    }

    @Nullable
    public static Object getLeftHook() {
        return invoke(leftHook);
    }

    @Nullable
    public static Object getRightHook() {
        return invoke(rightHook);
    }

    @Nullable
    private static Object invoke(@Nullable Method method) {
        if (!isAvailable() || method == null) return null;
        try {
            return method.invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    @Nullable
    public static Vec3 getPosition(@Nullable Object hook) {
        if (!isAvailable() || hook == null) return null;
        try {
            return position.get(hook) instanceof Vec3 v ? v : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public static void setPosition(@Nullable Object hook, Vec3 pos) {
        if (!isAvailable() || hook == null || pos == null) return;
        try {
            position.set(hook, pos);
        } catch (Throwable ignored) {
        }
    }

    public static boolean isActive(@Nullable Object hook) {
        if (!isAvailable() || hook == null) return false;
        try {
            return active.getBoolean(hook);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isOnEntity(@Nullable Object hook) {
        if (!isAvailable() || hook == null) return false;
        try {
            return hookedEntity.get(hook) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void release(@Nullable Object hook) {
        if (!isAvailable() || hook == null) return;
        try {
            releaseFn.invoke(hook);
        } catch (Throwable ignored) {
        }
    }
}
