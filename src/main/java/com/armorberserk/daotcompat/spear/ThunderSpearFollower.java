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
 *
 * <h3>Tick-ordering guarantee</h3>
 * <p>We listen on {@code EntityTickEvent.Pre}, which NeoForge fires in
 * {@code ServerLevel.tickEntity()} <em>before</em> the call to {@code entity.tick()}.
 * AOT's fuse countdown and its {@code explode()} call happen inside that {@code entity.tick()},
 * so our position correction is always applied before the explosion position is sampled —
 * even in the very tick the spear detonates.
 */
public final class ThunderSpearFollower {

    // Don't move the spear for sub-millimetre changes (a stationary ship must be a no-op).
    private static final double IDLE_SQR = 1.0E-4D;

    // Fix 2 (code review round 2): TTL invalidation, same rationale as RemoteHookFollower -
    // if AOT ever reuses spear-entity references across pooling we still auto-heal, and a
    // monotonic tick counter (not wall-clock) avoids any dependency on real time.
    private static final long STALE_TICKS = 100L; // ~5s at 20 tps
    private static long tickCounter;

    private record TrackedAnchor(DynamicHookData data, long lastSeenTick) {}

    /** Active sub-level anchors for lodged spears. Weak keys auto-drop with the entity. */
    private static final Map<Entity, TrackedAnchor> ANCHORS =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** One-shot diagnostic log gate: present after the first diagnose() call for an entity. */
    private static final Set<Entity> LOGGED =
            Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));

    /**
     * Last world-space position we moved the spear to, used to measure drift at detonation.
     * Null entry means the spear was never on a tracked sub-level.
     */
    private static final Map<Entity, Vec3> LAST_WORLD_POS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ThunderSpearFollower() {}

    public static void onTick(Entity entity) {
        Level level = entity.level();
        if (level == null || level.isClientSide()) return; // server holds the real position
        if (!SpearReflect.isThunderSpear(entity)) return;

        tickCounter++;
        expireStaleAnchors();

        if (!SpearReflect.isLodged(entity)) {
            // The spear just exploded or disappeared.  If we were tracking it, log the
            // detonation drift so users can confirm the fix is working in their setup.
            TrackedAnchor lastAnchor = ANCHORS.remove(entity);
            if (lastAnchor != null) {
                logDetonation(entity, LAST_WORLD_POS.get(entity));
            }
            LAST_WORLD_POS.remove(entity);
            LOGGED.remove(entity);
            return;
        }

        Vec3 pos = entity.position();
        TrackedAnchor tracked = ANCHORS.get(entity);
        DynamicHookData anchor = tracked == null ? null : tracked.data();

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
            anchor = new DynamicHookData(id, local, level.dimension());
            ANCHORS.put(entity, new TrackedAnchor(anchor, tickCounter));
            LAST_WORLD_POS.put(entity, pos);
        } else {
            ANCHORS.put(entity, new TrackedAnchor(anchor, tickCounter));
        }

        // Guard: if entity.level() changed dimension (e.g. a portal edge case), drop the anchor
        // rather than querying Sable with a UUID that may collide in the new Level.
        if (!anchor.dimensionKey().equals(level.dimension())) {
            DAOTCompat.LOGGER.debug("[spear] dropped anchor: dimension changed from {} to {}",
                    anchor.dimensionKey().location(), level.dimension().location());
            ANCHORS.remove(entity);
            LAST_WORLD_POS.remove(entity);
            return;
        }

        SubLevel sl = SableBridge.getSubLevel(level, anchor.subLevelId());
        if (sl == null) {
            ANCHORS.remove(entity);
            LAST_WORLD_POS.remove(entity);
            return;
        }
        Vec3 next;
        try {
            next = sl.logicalPose().transformPosition(anchor.localPosition());
        } catch (Throwable t) {
            ANCHORS.remove(entity);
            LAST_WORLD_POS.remove(entity);
            return;
        }
        if (notFinite(next)) {
            ANCHORS.remove(entity);
            LAST_WORLD_POS.remove(entity);
            return;
        }
        if (pos.distanceToSqr(next) < IDLE_SQR) return; // nothing moved - leave the spear be
        entity.setPos(next.x, next.y, next.z);
        LAST_WORLD_POS.put(entity, next); // remember for detonation drift log
    }

    /** Fix 2: force-drop spear anchors that have not been confirmed lodged for STALE_TICKS. */
    private static void expireStaleAnchors() {
        synchronized (ANCHORS) {
            ANCHORS.entrySet().removeIf(e -> {
                boolean stale = tickCounter - e.getValue().lastSeenTick() > STALE_TICKS;
                if (stale) {
                    DAOTCompat.LOGGER.debug("[spear] stale anchor removed (possible AOT object pooling)");
                }
                return stale;
            });
        }
    }

    /**
     * One-shot per spear: dumps where it detonated and the drift from the last anchor position.
     * A near-zero drift confirms our follower was keeping the spear correctly positioned.
     */
    private static void logDetonation(Entity entity, Vec3 lastWorld) {
        Vec3 current = entity.position();
        if (lastWorld == null) {
            DAOTCompat.LOGGER.info("[spear] detonated at {} (no tracked anchor — was in static terrain)",
                    fmt(current));
        } else {
            Vec3 drift = current.subtract(lastWorld);
            DAOTCompat.LOGGER.info("[spear] detonated at {} | last-anchor-world={} | drift={} (near-zero = fix working)",
                    fmt(current), fmt(lastWorld), fmt(drift));
        }
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
