/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.hook;

import com.armorberserk.daotcompat.aot.RemoteHookReflect;
import com.armorberserk.daotcompat.sable.SableBridge;
import com.armorberserk.daotcompat.sable.SubLevelResolver;
import dev.ryanhcode.sable.sublevel.SubLevel;
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

    private static final class Anchor {
        UUID leftSub;
        Vec3 leftLocal;
        Vec3 leftWritten;
        UUID rightSub;
        Vec3 rightLocal;
        Vec3 rightWritten;
    }

    private static final Map<Object, Anchor> STATE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private RemoteHookFollower() {}

    public static void tick(Level level) {
        if (level == null || !RemoteHookReflect.isAvailable()) return;
        for (Object data : RemoteHookReflect.hooks()) {
            if (data == null) continue;
            Anchor anchor = STATE.computeIfAbsent(data, k -> new Anchor());
            follow(level, data, anchor, true);
            follow(level, data, anchor, false);
        }
    }

    private static void follow(Level level, Object data, Anchor anchor, boolean left) {
        if (!RemoteHookReflect.isActive(data, left)) {
            store(anchor, left, null, null, null);
            return;
        }
        Vec3 cur = RemoteHookReflect.getPosition(data, left);
        if (cur == null || notFinite(cur)) return;

        Vec3 written = left ? anchor.leftWritten : anchor.rightWritten;
        // A fresh network snapshot differs from what we last wrote: re-derive the anchor.
        if (written == null || cur.distanceToSqr(written) > 1.0E-6) {
            UUID sub = null;
            Vec3 local = null;
            SubLevel sl = SubLevelResolver.findContaining(level, cur);
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
            store(anchor, left, sub, local, cur);
        }

        UUID sub = left ? anchor.leftSub : anchor.rightSub;
        if (sub == null) return; // ordinary world hook - leave AOT's value untouched

        SubLevel sl = SableBridge.getSubLevel(level, sub);
        if (sl == null) {
            store(anchor, left, null, null, null);
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

    private static void store(Anchor anchor, boolean left, UUID sub, Vec3 local, Vec3 written) {
        if (left) {
            anchor.leftSub = sub;
            anchor.leftLocal = local;
            anchor.leftWritten = written;
        } else {
            anchor.rightSub = sub;
            anchor.rightLocal = local;
            anchor.rightWritten = written;
        }
    }

    private static boolean notFinite(Vec3 v) {
        return !Double.isFinite(v.x) || !Double.isFinite(v.y) || !Double.isFinite(v.z);
    }
}
