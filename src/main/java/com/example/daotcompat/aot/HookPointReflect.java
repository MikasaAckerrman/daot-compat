package com.example.daotcompat.aot;

import com.example.daotcompat.DAOTCompat;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Reflection-based access to {@code daot.HookPoint} public fields and methods.
 *
 * <p><strong>Why reflection, not Mixin accessors:</strong> Empirically tested under
 * Sinytra Connector, NeoForge mixin processor only <em>partially</em> applies mixins to
 * Fabric mod-loaded classes &mdash; it adds the {@code implements} clause but does not
 * generate {@code @Accessor} method bodies, causing {@link AbstractMethodError} at runtime.
 * The exact line was:
 * <pre>
 * java.lang.AbstractMethodError: Receiver class daot.HookPoint does not define or inherit
 *   an implementation of the resolved method 'abstract boolean daotCompat$isActive()'.
 * </pre>
 *
 * <p>{@code daot.HookPoint} fields are all {@code public} so reflection bypasses the issue
 * entirely. {@link Field#get}/{@link Field#set} on a public field has negligible overhead
 * once the {@link Field} handle is cached.
 *
 * <p>Lazy initialisation: handles are populated on first call so a missing AOT install
 * (no {@code daot.HookPoint} class) returns {@code null} from every accessor instead of
 * blowing up at our class load time.
 */
public final class HookPointReflect {

    private static volatile boolean initAttempted = false;
    private static volatile boolean initOk = false;

    private static Field FIELD_POSITION;
    private static Field FIELD_ACTIVE;
    private static Field FIELD_HOOKED_ENTITY;
    private static Method METHOD_RELEASE;

    private HookPointReflect() {}

    private static void ensureInit() {
        if (initAttempted) return;
        synchronized (HookPointReflect.class) {
            if (initAttempted) return;
            initAttempted = true;
            try {
                Class<?> hp = Class.forName("daot.HookPoint", true,
                        HookPointReflect.class.getClassLoader());
                FIELD_POSITION = hp.getField("position");
                FIELD_ACTIVE = hp.getField("active");
                FIELD_HOOKED_ENTITY = hp.getField("hookedEntity");
                METHOD_RELEASE = hp.getMethod("release");
                initOk = true;
                DAOTCompat.LOGGER.info("[daotcompat] daot.HookPoint reflection bound");
            } catch (Throwable t) {
                DAOTCompat.LOGGER.warn("[daotcompat] AOT not available — compat will be no-op: {}",
                        t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }
    }

    public static boolean isAvailable() {
        ensureInit();
        return initOk;
    }

    @Nullable
    public static Vec3 getPosition(Object hookPoint) {
        if (!isAvailable() || hookPoint == null) return null;
        try {
            Object v = FIELD_POSITION.get(hookPoint);
            return (v instanceof Vec3 vec) ? vec : null;
        } catch (Throwable t) {
            DAOTCompat.LOGGER.debug("[daotcompat] getPosition failed", t);
            return null;
        }
    }

    public static boolean setPosition(Object hookPoint, Vec3 newPos) {
        if (!isAvailable() || hookPoint == null || newPos == null) return false;
        try {
            FIELD_POSITION.set(hookPoint, newPos);
            return true;
        } catch (Throwable t) {
            DAOTCompat.LOGGER.debug("[daotcompat] setPosition failed", t);
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
        } catch (Throwable t) {
            DAOTCompat.LOGGER.debug("[daotcompat] release() failed", t);
        }
    }
}
