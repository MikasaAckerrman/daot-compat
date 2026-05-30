/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.sable;

import com.armorberserk.daotcompat.DAOTCompat;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Thin, null-safe front for Sable's sub-level API. Anything goes wrong - Sable absent,
 * level not managed, sub-level gone - and you get {@code null} or an empty list back.
 */
public final class SableBridge {

    private SableBridge() {}

    @Nullable
    public static SubLevelContainer getContainer(@Nullable Level level) {
        if (level == null) return null;
        try {
            return SubLevelContainer.getContainer(level);
        } catch (Throwable t) {
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
            return null;
        }
    }

    public static List<? extends SubLevel> getAllSubLevels(@Nullable Level level) {
        SubLevelContainer container = getContainer(level);
        if (container == null) return Collections.emptyList();
        try {
            List<? extends SubLevel> all = container.getAllSubLevels();
            return all != null ? all : Collections.emptyList();
        } catch (Throwable t) {
            DAOTCompat.LOGGER.debug("getAllSubLevels failed", t);
            return Collections.emptyList();
        }
    }
}
