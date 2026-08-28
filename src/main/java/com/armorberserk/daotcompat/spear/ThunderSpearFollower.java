/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.spear;

import com.armorberserk.daotcompat.DAOTCompat;
import com.armorberserk.daotcompat.aot.SpearReflect;
import com.armorberserk.daotcompat.hook.DynamicHookData;
import com.armorberserk.daotcompat.sable.SableBridge;
import com.armorberserk.daotcompat.sable.SubLevelResolver;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Keeps a lodged thunder spear glued to the sub-level block it struck.
 *
 * <p>A vanilla arrow only rides the entity it hits, never a block, so a spear stuck in an
 * airship stays at a fixed world point while the airship sails on. We anchor the spear in
 * sub-level local space on the first lodged tick and re-place it from the sub-level's pose
 * each server tick, so its fuse explosion stays where it struck.
 */
public final class ThunderSpearFollower {

    // Don't move the spear for sub-millimetre changes (a stationary ship must be a no-op).
    private static final double IDLE_SQR = 1.0E-4D;

    private static final Map<Entity, DynamicHookData> ANCHORS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<Entity> LOGGED =
            Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));

    private ThunderSpearFollower() {}

    public static void onTick(Entity entity) {
        Level level = entity.level();
        if (level == null || level.isClientSide()) return; // server holds the real position
        if (!SpearReflect.isThunderSpear(entity)) return;

        if (!SpearReflect.isLodged(entity)) {
            ANCHORS.remove(entity);
            LOGGED.remove(entity);
            return;
        }

        Vec3 pos = entity.position();
        DynamicHookData anchor = ANCHORS.get(entity);
        if (anchor == null) {
            SubLevel sl = SubLevelResolver.findContaining(level, pos);
            if (LOGGED.add(entity)) diagnose(level, pos, sl);
            if (sl == null) return; // stuck in ordinary terrain or a titan - AOT handles those
            UUID id = sl.getUniqueId();
            if (id == null) return;
            Vec3 local;
            try {
                local = sl.logicalPose().transformPositionInverse(pos);
            } catch (Throwable t) {
                return;
            }
            if (notFinite(local)) return;
            anchor = new DynamicHookData(id, local);
            ANCHORS.put(entity, anchor);
        }

        SubLevel sl = SableBridge.getSubLevel(level, anchor.subLevelId());
        if (sl == null) {
            ANCHORS.remove(entity);
            return;
        }
        Vec3 next;
        try {
            next = sl.logicalPose().transformPosition(anchor.localPosition());
        } catch (Throwable t) {
            ANCHORS.remove(entity);
            return;
        }
        if (notFinite(next)) {
            ANCHORS.remove(entity);
            return;
        }
        if (pos.distanceToSqr(next) < IDLE_SQR) return; // nothing moved - leave the spear be
        entity.setPos(next.x, next.y, next.z);
    }

    /** One-shot per spear: dumps where it lodged and how the sub-levels map that point. */
    private static void diagnose(Level level, Vec3 pos, SubLevel found) {
        DAOTCompat.LOGGER.info("[spear] lodged at {} | sublevel={}",
                fmt(pos), found == null ? "NONE" : found.getUniqueId());
        if (found != null) {
            try {
                Vec3 local = found.logicalPose().transformPositionInverse(pos);
                Vec3 round = found.logicalPose().transformPosition(local);
                DAOTCompat.LOGGER.info("[spear]   local={} roundtrip={} drift={}",
                        fmt(local), fmt(round), fmt(round.subtract(pos)));
            } catch (Throwable ignored) {
            }
            return;
        }
        // No sub-level contains the reported point: show how each loaded sub-level maps it,
        // so we can tell whether the spear position is in a plot frame instead of world space.
        for (SubLevel sl : SableBridge.getAllSubLevels(level)) {
            if (sl == null) continue;
            try {
                DAOTCompat.LOGGER.info("[spear]   candidate {} transform(pos)={} inverse(pos)={}",
                        sl.getUniqueId(),
                        fmt(sl.logicalPose().transformPosition(pos)),
                        fmt(sl.logicalPose().transformPositionInverse(pos)));
            } catch (Throwable ignored) {
            }
        }
    }

    private static String fmt(Vec3 v) {
        return v == null ? "null"
                : String.format(java.util.Locale.ROOT, "(%.2f, %.2f, %.2f)", v.x, v.y, v.z);
    }

    private static boolean notFinite(Vec3 v) {
        return !Double.isFinite(v.x) || !Double.isFinite(v.y) || !Double.isFinite(v.z);
    }
}
