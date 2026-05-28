package com.example.daotcompat.aot;

import com.example.daotcompat.DAOTCompat;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Unified reflection bridge to Danny's AOT — replaces the previous split between
 * {@code AOTHookProvider} and {@code HookPointReflect}.
 *
 * <p><strong>Why one class:</strong> resolving AOT classes through reflection is the
 * single, atomic operation. Either we find {@code daot.ODMTickHandler} AND
 * {@code daot.HookPoint} together (compat is live), or neither (compat is no-op).
 * Splitting risked half-init states.
 *
 * <p><strong>Why multi-classloader scan:</strong> Sinytra Connector 2.0.x loads Fabric
 * mods through its own locator and may place their classes in a classloader distinct
 * from the NeoForge mod loader our compat lives in. {@code Class.forName(..., ourCL)}
 * therefore returns {@link ClassNotFoundException} even though AOT is "loaded" and
 * functional in-game. We probe all reachable classloaders before giving up.
 *
 * <p>The first time this class's bytecode is touched, JVM defers actual loading of
 * any {@code daot.*} reference until a static call site is hit — so the class itself
 * never has a hard link to AOT's classes, only string-name lookups via reflection.
 */
public final class AOTReflect {

    // Resolved handles (null until isAvailable() is called or compat is disabled)
    private static Method GET_LEFT_HOOK;
    private static Method GET_RIGHT_HOOK;
    private static Field FIELD_POSITION;
    private static Field FIELD_ACTIVE;
    private static Field FIELD_HOOKED_ENTITY;
    private static Method METHOD_RELEASE;

    private static volatile boolean initAttempted = false;
    private static volatile boolean initOk = false;

    private AOTReflect() {}

    private static void ensureInit() {
        if (initAttempted) return;
        synchronized (AOTReflect.class) {
            if (initAttempted) return;
            initAttempted = true;

            // Probe every reachable classloader. Connector may install AOT classes in any of them.
            ClassLoader[] loaders = {
                    Thread.currentThread().getContextClassLoader(),
                    LocalPlayer.class.getClassLoader(),     // game (Mojang) classloader — most common host for transformed Fabric mods
                    AOTReflect.class.getClassLoader(),      // our (NeoForge mod) classloader
                    ClassLoader.getSystemClassLoader()      // last resort
            };

            Class<?> hookPointCls = findClass("daot.HookPoint", loaders);
            Class<?> handlerCls = findClass("daot.ODMTickHandler", loaders);

            if (hookPointCls == null || handlerCls == null) {
                DAOTCompat.LOGGER.warn(
                        "[daotcompat] AOT classes NOT FOUND via any classloader — compat is no-op. "
                                + "(Sinytra Connector may not be loading AOT, or AOT class names changed.)");
                return;
            }

            try {
                FIELD_POSITION = hookPointCls.getField("position");
                FIELD_ACTIVE = hookPointCls.getField("active");
                FIELD_HOOKED_ENTITY = hookPointCls.getField("hookedEntity");
                METHOD_RELEASE = hookPointCls.getMethod("release");
                GET_LEFT_HOOK = handlerCls.getMethod("getLeftHook");
                GET_RIGHT_HOOK = handlerCls.getMethod("getRightHook");
                initOk = true;
                DAOTCompat.LOGGER.info(
                        "[daotcompat] AOT bound — {} (loaded via {})",
                        hookPointCls.getName(),
                        hookPointCls.getClassLoader());
            } catch (Throwable t) {
                DAOTCompat.LOGGER.warn(
                        "[daotcompat] AOT classes found but reflection setup failed: {}",
                        t.toString());
            }
        }
    }

    @Nullable
    private static Class<?> findClass(String name, ClassLoader[] loaders) {
        for (ClassLoader cl : loaders) {
            if (cl == null) continue;
            try {
                Class<?> c = Class.forName(name, true, cl);
                DAOTCompat.LOGGER.debug("[daotcompat] resolved {} via {}", name, cl);
                return c;
            } catch (Throwable ignored) {
                // try next loader
            }
        }
        return null;
    }

    public static boolean isAvailable() {
        ensureInit();
        return initOk;
    }

    @Nullable
    public static Object getLeftHook() {
        if (!isAvailable()) return null;
        try {
            return GET_LEFT_HOOK.invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    @Nullable
    public static Object getRightHook() {
        if (!isAvailable()) return null;
        try {
            return GET_RIGHT_HOOK.invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    @Nullable
    public static Vec3 getPosition(Object hookPoint) {
        if (!isAvailable() || hookPoint == null) return null;
        try {
            Object v = FIELD_POSITION.get(hookPoint);
            return (v instanceof Vec3 vec) ? vec : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean setPosition(Object hookPoint, Vec3 newPos) {
        if (!isAvailable() || hookPoint == null || newPos == null) return false;
        try {
            FIELD_POSITION.set(hookPoint, newPos);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isActive(Object hookPoint) {
        if (!isAvailable() || hookPoint == null) return false;
        try {
            Object v = FIELD_ACTIVE.get(hookPoint);
            return v instanceof Boolean b && b;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isOnEntity(Object hookPoint) {
        if (!isAvailable() || hookPoint == null) return false;
        try {
            return FIELD_HOOKED_ENTITY.get(hookPoint) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void release(Object hookPoint) {
        if (!isAvailable() || hookPoint == null) return;
        try {
            METHOD_RELEASE.invoke(hookPoint);
        } catch (Throwable ignored) {
        }
    }
}
