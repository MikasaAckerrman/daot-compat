/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.hook;

import com.armorberserk.daotcompat.DAOTCompat;
import com.armorberserk.daotcompat.aot.RemoteHookReflect;
import com.armorberserk.daotcompat.sable.SableBridge;
import com.armorberserk.daotcompat.sable.SubLevelResolver;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Keeps the ropes of other players following the airship they are grappling.
 *
 * <p>Other players' hooks arrive as periodic world-space snapshots ({@code RemoteHookData})
 * with no sub-level information, so between network updates they lag behind a moving airship
 * on the observer's screen. Whenever a fresh snapshot lands we work out which sub-level it
 * sits in; on the ticks in between we re-project that anchor from the sub-level's current
 * pose, so the rope stays on the ship instead of drifting.
 */
public final class RemoteHookFollower {

    // Fix 2 (code review round 2): if AOT ever reuses RemoteHookData instances (object
    // pooling), a WeakHashMap key never becomes unreachable, so a stale anchor could keep
    // silently applying to a "new" logical hook that happens to reuse the same object. We
    // do not rely on GC alone: every Anchor records the tick it was last confirmed, and a
    // sweep at the end of tick() force-drops anything that has gone quiet for too long.
    // A monotonic tick counter is used instead of System.currentTimeMillis() so this is not
    // affected by wall-clock jumps (nor does it need one - client ticks are already ~50ms).
    private static final long STALE_TICKS = 100L; // ~5s at 20 tps

    private static final class Anchor {
        UUID leftSub;
        Vec3 leftLocal;
        Vec3 leftWritten;
        ResourceKey<Level> leftDim;
        UUID rightSub;
        Vec3 rightLocal;
        Vec3 rightWritten;
        ResourceKey<Level> rightDim;
        long lastSeenTick;
    }

    private static final Map<Object, Anchor> STATE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static long tickCounter;

    private RemoteHookFollower() {}

    public static void tick(Level level) {
        if (level == null || !RemoteHookReflect.isAvailable()) return;
        tickCounter++;
        for (Object data : RemoteHookReflect.hooks()) {
            if (data == null) continue;
            // Common case: a player who is not grappling does no work and holds no state.
            if (!RemoteHookReflect.isActive(data, true) && !RemoteHookReflect.isActive(data, false)) {
                STATE.remove(data);
                continue;
            }
            Anchor anchor = STATE.computeIfAbsent(data, k -> new Anchor());
            anchor.lastSeenTick = tickCounter;
            follow(level, data, anchor, true);
            follow(level, data, anchor, false);
        }
        expireStaleAnchors();
    }

    /** Fix 2: force-drop anchors that have not been confirmed active for STALE_TICKS. */
    private static void expireStaleAnchors() {
        // entrySet()/removeIf() on a Collections.synchronizedMap view is not auto-synchronized
        // by the wrapper itself (same caveat as Fix 6) - synchronize on the map explicitly.
        synchronized (STATE) {
            STATE.entrySet().removeIf(e -> {
                boolean stale = tickCounter - e.getValue().lastSeenTick > STALE_TICKS;
                if (stale) {
                    DAOTCompat.LOGGER.debug("[hook] stale anchor removed (possible AOT object pooling)");
                }
                return stale;
            });
        }
    }

    private static void follow(Level level, Object data, Anchor anchor, boolean left) {
        if (!RemoteHookReflect.isActive(data, left)) {
            store(anchor, left, null, null, null, null);
            return;
        }

        // Guard: if the anchor was created in a different dimension, drop it rather than
        // querying Sable with a potentially-colliding UUID in a new Level.
        ResourceKey<Level> storedDim = left ? anchor.leftDim : anchor.rightDim;
        UUID storedSub = left ? anchor.leftSub : anchor.rightSub;
        if (storedSub != null && storedDim != null && !storedDim.equals(level.dimension())) {
            DAOTCompat.LOGGER.debug("[hook] dropped remote hook: dimension changed from {} to {}",
                    storedDim.location(), level.dimension().location());
            store(anchor, left, null, null, null, null);
        }

        Vec3 cur = RemoteHookReflect.getPosition(data, left);
        if (cur == null || notFinite(cur)) return;

        Vec3 written = left ? anchor.leftWritten : anchor.rightWritten;
        // A fresh network snapshot differs from what we last wrote: re-derive the anchor.
        if (written == null || cur.distanceToSqr(written) > 1.0E-6) {
            UUID sub = null;
            Vec3 local = null;
            // Fix 5 (code review round 2): the overwhelmingly common case is "still on the same
            // ship as last snapshot" - pass the previously-resolved sub-level as a hint so
            // SubLevelResolver can skip the full queryIntersecting scan when it still contains
            // the point, instead of re-scanning every sub-level on every network update.
            UUID previousSub = left ? anchor.leftSub : anchor.rightSub;
            SubLevel hint = previousSub != null ? SableBridge.getSubLevel(level, previousSub) : null;
            SubLevel sl = SubLevelResolver.findContaining(level, cur, hint);
            if (sl != null) {
                UUID id = sl.getUniqueId();
                if (id != null) {
                    try {
                        Vec3 l = sl.logicalPose().transformPositionInverse(cur);
                        if (!notFinite(l)) {
                            sub = id;
                            local = l;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
            store(anchor, left, sub, local, cur, sub != null ? level.dimension() : null);
        }

        UUID sub = left ? anchor.leftSub : anchor.rightSub;
        if (sub == null) return; // ordinary world hook - leave AOT's value untouched

        SubLevel sl = SableBridge.getSubLevel(level, sub);
        if (sl == null) {
            store(anchor, left, null, null, null, null);
            return;
        }
        Vec3 local = left ? anchor.leftLocal : anchor.rightLocal;
        Vec3 next;
        try {
            next = sl.logicalPose().transformPosition(local);
        } catch (Throwable t) {
            return;
        }
        if (notFinite(next)) return;
        RemoteHookReflect.setPosition(data, left, next);
        if (left) anchor.leftWritten = next; else anchor.rightWritten = next;
    }

    private static void store(Anchor anchor, boolean left, UUID sub, Vec3 local, Vec3 written,
                               ResourceKey<Level> dim) {
        if (left) {
            anchor.leftSub = sub;
            anchor.leftLocal = local;
            anchor.leftWritten = written;
            anchor.leftDim = dim;
        } else {
            anchor.rightSub = sub;
            anchor.rightLocal = local;
            anchor.rightWritten = written;
            anchor.rightDim = dim;
        }
    }

    private static boolean notFinite(Vec3 v) {
        return !Double.isFinite(v.x) || !Double.isFinite(v.y) || !Double.isFinite(v.z);
    }
}
