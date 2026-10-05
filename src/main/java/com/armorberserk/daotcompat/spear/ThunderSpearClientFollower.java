/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.spear;

import com.armorberserk.daotcompat.aot.SpearReflect;
import com.armorberserk.daotcompat.config.DaotConfig;
import com.armorberserk.daotcompat.hook.DynamicHookData;
import com.armorberserk.daotcompat.sable.SableBridge;
import com.armorberserk.daotcompat.sable.SubLevelResolver;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Client mirror of {@code ThunderSpearFollower}: carries lodged spear <em>visuals</em> on moving
 * airships.
 *
 * <p>The server keeps the real {@code ThunderSpearEntity} glued to its sub-level, but two
 * client-side visuals still lag behind:
 *
 * <ol>
 *   <li>the entity renderer interpolates between the client's own previous and current
 *       position — vanilla position sync for a server-following entity arrives in bursts, so
 *       the model swings; we mirror the anchor client-side and set the position (plus Sable's
 *       {@code setOldPosNoMovement}) every client tick instead;</li>
 *   <li>{@code daot.FlyingThunderSpearTracker} keeps its own visual spear + wire pinned to a
 *       world-space snapshot taken at lodge time and never reads Sable poses; {@link
 *       SpearVisualReflect} carries those fields.</li>
 * </ol>
 *
 * <p>Anchors are derived independently on the client (entity position → containing sub-level →
 * local space), exactly like the server does, so no anchor-sync packets are needed.
 */
public final class ThunderSpearClientFollower {

    private static final double IDLE_SQR = 1.0E-4D;
    private static final long STALE_TICKS = 100L;

    private record Tracked(DynamicHookData anchor, Vec3 lastWorld, long lastSeenTick) {}

    private static final Map<Entity, Tracked> ANCHORS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static long tickCounter;
    // Hot-path cache: the first spear entity we meet pins its EntityType; every later entity is
    // filtered by a single reference compare instead of a reflective class check per entity.
    private static volatile net.minecraft.world.entity.EntityType<?> cachedSpearType;

    private ThunderSpearClientFollower() {}

    public static void tick(Level level) {
        if (!(level instanceof ClientLevel clientLevel) || !DaotConfig.SPEAR_VISUAL_SYNC.get()) return;
        tickCounter++;
        expireStaleAnchors();

        for (Entity entity : clientLevel.entitiesForRendering()) {
            if (entity.isRemoved()) continue;
            net.minecraft.world.entity.EntityType<?> type = entity.getType();
            if (cachedSpearType == null) {
                if (!SpearReflect.isThunderSpear(entity)) continue;
                cachedSpearType = type;
            } else if (type != cachedSpearType) {
                continue;
            }
            if (!SpearReflect.isLodged(entity)) {
                ANCHORS.remove(entity);
                continue;
            }

            Tracked tracked = ANCHORS.get(entity);
            DynamicHookData anchor = tracked == null ? null : tracked.anchor();
            Vec3 pos = entity.position();

            if (anchor == null) {
                SubLevel sl = SubLevelResolver.findContaining(level, pos);
                if (sl == null) continue;
                Vec3 local;
                try {
                    local = sl.logicalPose().transformPositionInverse(pos);
                } catch (Throwable t) {
                    continue;
                }
                anchor = new DynamicHookData(sl.getUniqueId(), local, level.dimension());
                ANCHORS.put(entity, new Tracked(anchor, pos, tickCounter));
                // Best effort: hand the spear to Sable's own tracking; when Sable carries it
                // natively our manual mirror becomes a harmless no-op.
                SableBridge.setTrackingSubLevel(entity, sl);
                continue;
            }
            ANCHORS.put(entity, new Tracked(anchor, tracked.lastWorld(), tickCounter));

            if (!anchor.dimensionKey().equals(level.dimension())) {
                ANCHORS.remove(entity);
                continue;
            }
            SubLevel sl = SableBridge.getSubLevel(level, anchor.subLevelId());
            if (sl == null) {
                ANCHORS.remove(entity);
                continue;
            }
            Vec3 next;
            try {
                next = sl.logicalPose().transformPosition(anchor.localPosition());
            } catch (Throwable t) {
                continue;
            }
            if (notFinite(next) || pos.distanceToSqr(next) < IDLE_SQR) continue;

            Vec3 oldWorld = tracked.lastWorld();
            entity.setPos(next.x, next.y, next.z);
            SableBridge.setOldPosNoMovement(entity);
            SpearVisualReflect.carryVisual(entity, oldWorld == null ? pos : oldWorld, next);
            // Plot-world fix: if the tracker spawned the visual at ±20M (plot coords), pull it
            // back to the entity's real client position every tick.
            SpearVisualReflect.fixAbnormalVisuals(entity, entity.position());
            ANCHORS.put(entity, new Tracked(anchor, next, tickCounter));
        }
    }

    private static void expireStaleAnchors() {
        synchronized (ANCHORS) {
            ANCHORS.entrySet().removeIf(e -> tickCounter - e.getValue().lastSeenTick() > STALE_TICKS);
        }
    }

    private static boolean notFinite(Vec3 v) {
        return !Double.isFinite(v.x) || !Double.isFinite(v.y) || !Double.isFinite(v.z);
    }
}
