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
    /** The wire-break/latch fields; additive — missing them must NOT disable the base layer. */
    private static volatile boolean latchFieldsReady;

    private static Method leftHook;
    private static Method rightHook;
    private static Field position;
    private static Field active;
    private static Field extending;
    private static Field retracting;
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
        // Base layer: hook position/state/release. If ANY of these is gone the whole point of
        // the compat is void, so failure here legitimately disables us.
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
            return;
        }
        // Latch-state fields (isExtending/isRetracting): additive for the wire-break detector
        // and the smooth-recover gate. A renamed field degrades JUST those features.
        try {
            extending = hookPoint.getField("isExtending");
            retracting = hookPoint.getField("isRetracting");
            latchFieldsReady = true;
        } catch (Throwable t) {
            DAOTCompat.LOGGER.warn("AOT HookPoint latch fields (isExtending/isRetracting) missing — wire-break and latch-edge detection degrade to active-only: {}", t.toString());
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

    /**
     * A hook that has actually latched (not mid-flight, not retracting) — the trigger for
     * handing crash momentum over to the player (see RagdollLink). Degrades to "active" when
     * the latch-state fields are missing on a future AOT build.
     */
    public static boolean isLatched(@Nullable Object hook) {
        if (!isAvailable() || hook == null) return false;
        try {
            if (!latchFieldsReady) return active.getBoolean(hook);
            return active.getBoolean(hook) && !extending.getBoolean(hook) && !retracting.getBoolean(hook);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Whether the latch-state fields (isExtending/isRetracting) resolved — wire-break detection needs them. */
    public static boolean latchStateAvailable() {
        if (!resolved) resolve();
        return latchFieldsReady;
    }

    /** True while the hook is reeling itself back in (a manual retract, NOT a wire break). */
    public static boolean isRetracting(@Nullable Object hook) {
        if (!isAvailable() || hook == null || !latchFieldsReady) return false;
        try {
            return retracting.getBoolean(hook);
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

    /** Releases both of the local player's hooks (if active). Client-side objects. */
    public static void releaseBoth() {
        release(getLeftHook());
        release(getRightHook());
    }
}
