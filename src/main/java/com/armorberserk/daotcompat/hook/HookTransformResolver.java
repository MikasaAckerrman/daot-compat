/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.hook;

import com.armorberserk.daotcompat.DAOTCompat;
import com.armorberserk.daotcompat.aot.AOTReflect;
import com.armorberserk.daotcompat.config.DaotConfig;
import com.armorberserk.daotcompat.ragdoll.RagdollClient;
import com.armorberserk.daotcompat.sable.SableBridge;
import com.armorberserk.daotcompat.sable.SubLevelResolver;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Keeps a hook anchored to the sub-level block it grabbed. On the first tick we store the
 * grab point in the sub-level's local space; afterwards we re-project that point through the
 * sub-level's current pose so the hook rides along as the airship moves.
 */
public final class HookTransformResolver {

    // A real grab is within gear range. A reported point millions of blocks out means Sable
    // handed back a sub-level's internal plot coordinate, which we remap onto the airship.
    private static final double PLOT_FRAME_SQR = 1_000_000.0D;
    // How close a hook must stay to the player. Beyond this we let go instead of dragging
    // the player off into nowhere, and the same radius accepts a remapped attach point.
    //
    // Fix 3 (code review round 2): AOT's own maxHookDistance is roughly 250 blocks, so a plain
    // 256-block radius left only ~6 blocks of margin - not enough headroom for a fast-moving
    // Sable airship, where the player's and the ship's relative velocity can eat that margin in
    // a single client tick and falsely detach a perfectly legitimate rope. HOOK_RANGE_MARGIN
    // adds real slack on top of the expected max hook range without disabling the safety check.
    private static final double HOOK_RANGE_MARGIN = 64.0D;
    private static final double MATCH_RADIUS_SQR = Math.pow(250.0D + HOOK_RANGE_MARGIN, 2);
    // Below this the pose effectively did not change; skip the write to avoid feeding jitter.
    private static final double IDLE_SQR = 1.0E-6D;

    // Hooks that already fired a ragdoll trigger. WeakHashSet: entries vanish with the hook
    // object, so this never grows unbounded.
    private static final Set<Object> RAGDOLLED_HOOKS =
            Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));

    // Hooks created while the player was ragdolled (one-shot diagnostics).
    private static final Set<Object> FIRED_WHILE_RAGDOLLED =
            Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));

    private HookTransformResolver() {}

    /** World position of the most recent anchored-hook correction (for the rope-force bridge). */
    private static volatile Vec3 lastAnchorWorldPos;

    @Nullable
    public static Vec3 getLastAnchorWorldPos() {
        return lastAnchorWorldPos;
    }

    public static void process(@Nullable Level level, @Nullable Object hook) {
        if (level == null || hook == null || !AOTReflect.isAvailable()) return;

        // Stun window: the player is pinned and their gear is suppressed. Release anything the
        // gear still holds and refuse new anchoring until the stun expires (server is the
        // authority; this local mirror just makes the suppression instant).
        if (RagdollClient.isStunned()) {
            if (DynamicHookMap.get(hook) != null) {
                AOTReflect.release(hook);
                DynamicHookMap.put(hook, null);
            }
            return;
        }

        // Only block hooks on a sub-level are our concern; clear tracking for anything else.
        if (!AOTReflect.isActive(hook) || AOTReflect.isOnEntity(hook)) {
            DynamicHookMap.put(hook, null);
            return;
        }

        Vec3 world = AOTReflect.getPosition(hook);
        if (world == null || notFinite(world)) return;

        DynamicHookData anchor = DynamicHookMap.get(hook);
        if (anchor == null) {
            // Диагностика: стреляет ли вообще УПМ, пока игрок в рэгдолле (одна строка на крюк).
            if (RagdollClient.isRagdolledLive() && FIRED_WHILE_RAGDOLLED.add(hook)) {
                DAOTCompat.LOGGER.info("[ragdoll] ODM hook FIRED while player is ragdolled — input works");
            }
            attach(level, hook, world);
        } else {
            checkTrip(hook, anchor, world);
            follow(level, hook, anchor, world);
        }
    }

    private static void attach(Level level, Object hook, Vec3 world) {
        // Progressive probe: point-blank hooks land a hair off the ship surface where the
        // default 0.05 probe misses. Widen before giving up — this is the "chains but not
        // always" fix for close-range grapples.
        SubLevel sl = SubLevelResolver.findContaining(level, world, 0.05D);
        if (sl == null) sl = SubLevelResolver.findContaining(level, world, 0.5D);
        if (sl == null) sl = SubLevelResolver.findContaining(level, world, 1.5D);
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
            DynamicHookMap.put(hook, new DynamicHookData(id, local, level.dimension()));
            // Re-hooking in mid-air while ragdolled ends it: the rope caught something solid,
            // so the player is back in control. Live check — works even 10 minutes into a ragdoll.
            if (RagdollClient.isRagdolledLive()) {
                RagdollClient.clearRagdollWindow();
                RagdollClient.exit();
                DAOTCompat.LOGGER.info("[hook] re-hooked in air while ragdolled -> exiting ragdoll");
            }
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
            DynamicHookMap.put(hook, new DynamicHookData(id, reported, level.dimension()));
            return;
        }
    }

    private static void follow(Level level, Object hook, DynamicHookData anchor, Vec3 world) {
        // Guard against a stale anchor from a previous dimension. Sable UUID collisions across
        // dimensions are astronomically unlikely, but an explicit check makes the drop debuggable.
        if (!anchor.dimensionKey().equals(level.dimension())) {
            DAOTCompat.LOGGER.debug("[hook] dimension changed from {} to {}, releasing hook",
                    anchor.dimensionKey().location(), level.dimension().location());
            drop(hook);
            return;
        }
        SubLevel sl = SableBridge.getSubLevel(level, anchor.subLevelId());
        if (sl == null) {
            // [FIX v1.3.5] Improved error handling for Hook Sync on physics objects
            DAOTCompat.LOGGER.debug("[hook] sub-level not found (UUID: {}), releasing hook",
                    anchor.subLevelId());
            drop(hook);
            return;
        }
        Vec3 next;
        try {
            next = sl.logicalPose().transformPosition(anchor.localPosition());
        } catch (Throwable t) {
            DAOTCompat.LOGGER.error("[hook] failed to transform position", t);
            drop(hook);
            return;
        }
        if (notFinite(next)) {
            DAOTCompat.LOGGER.warn("[hook] transformed position is not finite");
            drop(hook);
            return;
        }
        if (world.distanceToSqr(next) < IDLE_SQR) return; // ship effectively idle

        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && next.distanceToSqr(player.position()) > MATCH_RADIUS_SQR) {
            drop(hook); // sub-level moved out of reach; let go instead of dragging the player
            return;
        }
        AOTReflect.setPosition(hook, next);
        lastAnchorWorldPos = next; // feed the rope-force bridge
        DAOTCompat.LOGGER.debug("[hook] synchronized to moving sub-level at {}", next);
    }

    /**
     * Trip rule: the anchor is on (roughly) the same flat plane the player is moving across and
     * the player is arriving fast. Physically the gear yanks you forward and your legs give out —
     * that is a ragdoll, not a clean swing. Fires once per hook.
     */
    private static void checkTrip(Object hook, DynamicHookData anchor, Vec3 world) {
        if (RAGDOLLED_HOOKS.contains(hook)) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        Vec3 motion = player.getDeltaMovement();
        double horizontal = Math.hypot(motion.x, motion.z);
        if (horizontal < DaotConfig.TRIP_MIN_SPEED.get()) return;
        if (world.distanceToSqr(player.position()) > sqr(DaotConfig.TRIP_MAX_DISTANCE.get())) return;
        if (Math.abs(world.y - player.getY()) > DaotConfig.TRIP_HEIGHT_DELTA.get()) return;

        RAGDOLLED_HOOKS.add(hook);
        RagdollClient.triggerTrip(new Vec3(motion.x, 0.0D, motion.z));
        DAOTCompat.LOGGER.info("[hook] tripped at weak anchor: {} m/s horizontal, anchor {} blocks away",
                String.format(java.util.Locale.ROOT, "%.1f", horizontal),
                String.format(java.util.Locale.ROOT, "%.1f", Math.sqrt(world.distanceToSqr(player.position()))));
    }

    private static void drop(Object hook) {
        // Anchor loss at speed is a real ODM crash: the gear yanked you and let go. Report the
        // player's velocity so the server can ragdoll them with it. AOT-initiated releases
        // (manual retract, range limit) never pass through here — they surface as isActive()==false.
        //
        // Two severity tiers: below stunMinSpeed it is an ordinary crash (ragdoll, ODM stays
        // usable during and after); at or above stunMinSpeed the server pins the player for
        // stunTicks with lockDismount and suppresses ODM hooks for the whole window.
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && DaotConfig.RAGDOLL_ENABLED.get() && !RAGDOLLED_HOOKS.contains(hook)) {
            Vec3 motion = player.getDeltaMovement();
            double horizontal = Math.hypot(motion.x, motion.z);
            double stunThreshold = DaotConfig.STUN_MIN_SPEED.get();
            if (horizontal >= stunThreshold) {
                RAGDOLLED_HOOKS.add(hook);
                RagdollClient.triggerStun(motion);
            } else if (horizontal >= DaotConfig.CRASH_MIN_SPEED.get()) {
                RAGDOLLED_HOOKS.add(hook);
                RagdollClient.triggerCrash(motion);
            }
            if (RAGDOLLED_HOOKS.contains(hook)) {
                DAOTCompat.LOGGER.info("[hook] hard release at {} m/s horizontal -> {}",
                        String.format(java.util.Locale.ROOT, "%.1f", horizontal),
                        horizontal >= stunThreshold ? "STUN" : "ragdoll");
            }
        }
        AOTReflect.release(hook);
        DynamicHookMap.put(hook, null);
        lastAnchorWorldPos = null; // anchor gone — stop feeding the bridge
    }

    private static double sqr(double v) {
        return v * v;
    }

    private static boolean notFinite(Vec3 v) {
        return !Double.isFinite(v.x) || !Double.isFinite(v.y) || !Double.isFinite(v.z);
    }
}
