package com.example.daotcompat.aot;

import com.example.daotcompat.DAOTCompat;
import org.jetbrains.annotations.Nullable;

/**
 * Null-safe wrapper around Danny's AOT static accessors.
 *
 * <p>Why this class exists, not direct {@code daot.ODMTickHandler.getLeftHook()} calls:
 * <ul>
 *   <li>Isolates the AOT class reference into one place — easy to swap if AOT renames.</li>
 *   <li>{@link Throwable} catch covers both {@link NoClassDefFoundError} (AOT not installed)
 *       and any unexpected runtime errors inside the AOT static initializer.</li>
 *   <li>Returns {@link Object} so callers cast through {@code DynamicHookStorage}/{@code HookPointAccessor}
 *       — same pattern as elsewhere, runtime-safe under Sinytra Connector.</li>
 * </ul>
 *
 * <p>The reference to {@code daot.ODMTickHandler} is resolved lazily at the first call —
 * a JVM does not load the class until the bytecode reaches the static call site, so
 * this provider class itself loads fine even on a dedicated server where AOT may not
 * be present (though our mods.toml requires it client-side).
 */
public final class AOTHookProvider {

    private AOTHookProvider() {}

    /** @return left-hand hook of the local player, or {@code null} if AOT is unavailable. */
    @Nullable
    public static Object getLeftHook() {
        try {
            return daot.ODMTickHandler.getLeftHook();
        } catch (Throwable t) {
            DAOTCompat.LOGGER.debug("[daotcompat] ODMTickHandler.getLeftHook() failed", t);
            return null;
        }
    }

    /** @return right-hand hook of the local player, or {@code null} if AOT is unavailable. */
    @Nullable
    public static Object getRightHook() {
        try {
            return daot.ODMTickHandler.getRightHook();
        } catch (Throwable t) {
            DAOTCompat.LOGGER.debug("[daotcompat] ODMTickHandler.getRightHook() failed", t);
            return null;
        }
    }
}
