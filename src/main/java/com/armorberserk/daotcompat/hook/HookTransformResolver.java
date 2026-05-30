/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.hook;

import com.armorberserk.daotcompat.aot.AOTReflect;
import com.armorberserk.daotcompat.sable.SableBridge;
import com.armorberserk.daotcompat.sable.SubLevelResolver;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Keeps a hook anchored to the sub-level block it grabbed. On the first tick we store the
 * grab point in the sub-level's local space; afterwards we re-project that point through the
 * sub-level's current pose so the hook rides along as the airship moves.
 */
public final class HookTransformResolver {

    // A real grab is within gear range. A reported point millions of blocks out means Sable
    // handed back a sub-level's internal plot coordinate, which we remap onto the airship.
    private static final double PLOT_FRAME_SQR = 1_000_000.0D;
    // How close a remapped point must land to the player to accept it as the right sub-level.
    private static final double MATCH_RADIUS_SQR = 65_536.0D;
    // Below this the pose effectively did not change; skip the write to avoid feeding jitter.
    private static final double IDLE_SQR = 1.0E-6D;
    // No legitimate single-tick motion is this large; ignore it instead of flinging the player.
    private static final double MAX_STEP_SQR = 1024.0D;

    private HookTransformResolver() {}

    public static void process(@Nullable Level level, @Nullable Object hook) {
        if (level == null || hook == null || !AOTReflect.isAvailable()) return;

        // Only block hooks on a sub-level are our concern; clear tracking for anything else.
        if (!AOTReflect.isActive(hook) || AOTReflect.isOnEntity(hook)) {
            DynamicHookMap.put(hook, null);
            return;
        }

        Vec3 world = AOTReflect.getPosition(hook);
        if (world == null || notFinite(world)) return;

        DynamicHookData anchor = DynamicHookMap.get(hook);
        if (anchor == null) {
            attach(level, hook, world);
        } else {
            follow(level, hook, anchor, world);
        }
    }

    private static void attach(Level level, Object hook, Vec3 world) {
        SubLevel sl = SubLevelResolver.findContaining(level, world);
        if (sl != null) {
            UUID id = sl.getUniqueId();
            if (id == null) return;
            Vec3 local;
            try {
                local = sl.logicalPose().transformPositionInverse(world);
            } catch (Throwable t) {
                return;
            }
            if (notFinite(local)) return;
            DynamicHookMap.put(hook, new DynamicHookData(id, local));
            return;
        }
        // Nothing at the reported point - it may already be a raw plot coordinate.
        recoverPlotFrame(level, hook, world);
    }

    /**
     * Sable sometimes reports a grab in a sub-level's plot space instead of world space, so
     * AOT pins the hook millions of blocks away. We treat the reported point as a local
     * coordinate and find the sub-level whose pose maps it back next to the player.
     */
    private static void recoverPlotFrame(Level level, Object hook, Vec3 reported) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        Vec3 eye = player.position();
        if (reported.distanceToSqr(eye) < PLOT_FRAME_SQR) return; // ordinary world hit

        for (SubLevel sl : SableBridge.getAllSubLevels(level)) {
            if (sl == null || sl.isRemoved()) continue;
            UUID id = sl.getUniqueId();
            if (id == null) continue;
            Vec3 visual;
            try {
                visual = sl.logicalPose().transformPosition(reported);
            } catch (Throwable t) {
                continue;
            }
            if (notFinite(visual) || visual.distanceToSqr(eye) > MATCH_RADIUS_SQR) continue;

            AOTReflect.setPosition(hook, visual);
            DynamicHookMap.put(hook, new DynamicHookData(id, reported));
            return;
        }
    }

    private static void follow(Level level, Object hook, DynamicHookData anchor, Vec3 world) {
        SubLevel sl = SableBridge.getSubLevel(level, anchor.subLevelId());
        if (sl == null) {
            drop(hook);
            return;
        }
        Vec3 next;
        try {
            next = sl.logicalPose().transformPosition(anchor.localPosition());
        } catch (Throwable t) {
            drop(hook);
            return;
        }
        if (notFinite(next)) {
            drop(hook);
            return;
        }
        double moved = world.distanceToSqr(next);
        if (moved < IDLE_SQR || moved >= MAX_STEP_SQR) return;
        AOTReflect.setPosition(hook, next);
    }

    private static void drop(Object hook) {
        AOTReflect.release(hook);
        DynamicHookMap.put(hook, null);
    }

    private static boolean notFinite(Vec3 v) {
        return !Double.isFinite(v.x) || !Double.isFinite(v.y) || !Double.isFinite(v.z);
    }
}
