package com.example.daotcompat.sable;

import com.example.daotcompat.DAOTCompat;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Null-safe wrapper around Sable's public API. All methods return {@code null}
 * when Sable is unavailable, the level is unmanaged, or the requested sub-level
 * is missing/removed. Callers must always check for {@code null}.
 */
public final class SableBridge {

    private SableBridge() {}

    @Nullable
    public static SubLevelContainer getContainer(@Nullable Level level) {
        if (level == null) return null;
        try {
            return SubLevelContainer.getContainer(level);
        } catch (Throwable t) {
            // Sable not loaded, container not initialised, or wrong level type
            DAOTCompat.LOGGER.debug("[daotcompat] SubLevelContainer lookup failed", t);
            return null;
        }
    }

    @Nullable
    public static SubLevel getSubLevel(@Nullable Level level, @Nullable UUID id) {
        if (id == null) return null;
        SubLevelContainer container = getContainer(level);
        if (container == null) return null;
        try {
            SubLevel sl = container.getSubLevel(id);
            return (sl != null && !sl.isRemoved()) ? sl : null;
        } catch (Throwable t) {
            DAOTCompat.LOGGER.debug("[daotcompat] SubLevel lookup failed", t);
            return null;
        }
    }
}
