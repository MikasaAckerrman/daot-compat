package com.example.daotcompat.aot;

import com.example.daotcompat.DAOTCompat;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Null-safe wrapper around Danny's AOT static accessors.
 *
 * <p>Logs the first non-null result from each side once — useful for confirming AOT
 * is actually creating HookPoint instances when the player wears ODM gear. If the
 * "first non-null" message never appears, AOT's {@code ODMTickHandler.register()}
 * may not be running, or AOT is loading but ODM is disabled.
 */
public final class AOTHookProvider {

    private static final AtomicBoolean firstLeftSeen = new AtomicBoolean();
    private static final AtomicBoolean firstRightSeen = new AtomicBoolean();
    private static final AtomicBoolean errorLogged = new AtomicBoolean();

    private AOTHookProvider() {}

    @Nullable
    public static Object getLeftHook() {
        try {
            Object h = daot.ODMTickHandler.getLeftHook();
            if (h != null && firstLeftSeen.compareAndSet(false, true)) {
                DAOTCompat.LOGGER.info("[daotcompat] first non-null LEFT hook observed: {}",
                        h.getClass().getName());
            }
            return h;
        } catch (Throwable t) {
            if (errorLogged.compareAndSet(false, true)) {
                DAOTCompat.LOGGER.warn("[daotcompat] AOT.getLeftHook unavailable: {}", t.toString());
            }
            return null;
        }
    }

    @Nullable
    public static Object getRightHook() {
        try {
            Object h = daot.ODMTickHandler.getRightHook();
            if (h != null && firstRightSeen.compareAndSet(false, true)) {
                DAOTCompat.LOGGER.info("[daotcompat] first non-null RIGHT hook observed: {}",
                        h.getClass().getName());
            }
            return h;
        } catch (Throwable t) {
            if (errorLogged.compareAndSet(false, true)) {
                DAOTCompat.LOGGER.warn("[daotcompat] AOT.getRightHook unavailable: {}", t.toString());
            }
            return null;
        }
    }
}
