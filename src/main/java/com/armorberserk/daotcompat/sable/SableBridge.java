/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.sable;

import com.armorberserk.daotcompat.DAOTCompat;
import dev.ryanhcode.sable.api.entity.EntitySubLevelUtil;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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

    /**
     * All non-removed sub-levels whose (world-space) bounding box intersects {@code box}. Used by
     * the high-speed collision guard as a cheap "is there a ship near this movement segment at all"
     * gate so the expensive per-sub-step re-check is skipped entirely in ordinary terrain.
     */
    public static List<SubLevel> getIntersecting(@Nullable Level level, @Nullable AABB box) {
        if (box == null) return Collections.emptyList();
        SubLevelContainer container = getContainer(level);
        if (container == null) return Collections.emptyList();
        try {
            List<SubLevel> out = new ArrayList<>();
            BoundingBox3d probe = new BoundingBox3d(
                    box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
            for (SubLevel sl : container.queryIntersecting(probe)) {
                if (sl != null && !sl.isRemoved()) out.add(sl);
            }
            return out;
        } catch (Throwable t) {
            DAOTCompat.LOGGER.debug("getIntersecting failed", t);
            return Collections.emptyList();
        }
    }

    /**
     * Recompute {@code entity.xo/yo/zo} (and {@code xOld/yOld/zOld}) from the sub-level's
     * <em>previous-tick</em> pose so the client's render interpolation follows the ship's motion
     * instead of swinging wildly. See {@code com.armorberserk.daotcompat.spear.ThunderSpearFollower}
     * for why this is needed on top of the per-tick {@code setPos} correction.
     *
     * <p>Delegates to Sable's own first-party helper
     * {@code dev.ryanhcode.sable.api.entity.EntitySubLevelUtil#setOldPosNoMovement(Entity)}. That
     * method has a graceful {@code else} branch (pins old-pos to the current pos) when the entity
     * isn't a Sable-tracked sub-level entity, so it is always safe to call unconditionally.
     *
     * <p>Wrapped in {@code try/catch(Throwable)} to honour the {@code SableBridge} contract that
     * every Sable touch-point degrades to a no-op if Sable is absent or a future version drops/renames
     * the API — exactly as the surrounding methods do for {@code SubLevelContainer}. {@code EntitySubLevelUtil}
     * is a stable {@code public static} API in the {@code compileOnly} Sable jar, but the guard costs
     * nothing and keeps the mod loading against Sable builds that predate it.
     */
    public static void setOldPosNoMovement(@Nullable Entity entity) {
        if (entity == null) return;
        try {
            EntitySubLevelUtil.setOldPosNoMovement(entity);
        } catch (Throwable t) {
            DAOTCompat.LOGGER.debug("setOldPosNoMovement failed", t);
        }
    }
}
