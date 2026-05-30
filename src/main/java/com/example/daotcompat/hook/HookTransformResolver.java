package com.example.daotcompat.hook;

import com.example.daotcompat.DAOTCompat;
import com.example.daotcompat.aot.AOTReflect;
import com.example.daotcompat.sable.SableBridge;
import com.example.daotcompat.sable.SubLevelResolver;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-tick logic that keeps {@code daot.HookPoint.position} in sync with a moving
 * Sable sub-level.
 *
 * <p>Reads/writes {@code HookPoint} state through {@link AOTReflect} (reflection on
 * its public fields) and stores per-hook compat data in {@link DynamicHookMap} (external
 * {@link java.util.WeakHashMap}). This bypass avoids the Mixin-on-Fabric-class issue
 * observed under Sinytra Connector.
 *
 * <p>Diagnostic logging is rate-limited (every 40 ticks ~= 2s) and gated on state
 * transitions to keep the log readable while still exposing what's happening.
 */
public final class HookTransformResolver {

    /** Per-side state we observed last tick — used to gate logs to transitions only. */
    private enum LastState { NONE, INACTIVE, ENTITY, NO_SUBLEVEL, TRACKING }

    private static LastState lastLeft = LastState.NONE;
    private static LastState lastRight = LastState.NONE;
    private static final AtomicLong tickCounter = new AtomicLong();

    private HookTransformResolver() {}

    /**
     * @param level     the world the local player is in (must not be null)
     * @param hookPoint a {@code daot.HookPoint} instance from {@code ODMTickHandler}
     * @param side      "L" or "R" — for log prefixing
     */
    public static void process(@Nullable Level level, @Nullable Object hookPoint, String side) {
        if (level == null || hookPoint == null) return;
        if (!AOTReflect.isAvailable()) return;

        boolean active = AOTReflect.isActive(hookPoint);
        if (!active) {
            transition(side, LastState.INACTIVE);
            if (DynamicHookMap.get(hookPoint) != null) {
                DynamicHookMap.put(hookPoint, null);
            }
            return;
        }

        if (AOTReflect.isOnEntity(hookPoint)) {
            transition(side, LastState.ENTITY);
            if (DynamicHookMap.get(hookPoint) != null) {
                DynamicHookMap.put(hookPoint, null);
            }
            return;
        }

        Vec3 worldPos = AOTReflect.getPosition(hookPoint);
        if (worldPos == null || isInvalid(worldPos)) return;

        DynamicHookData data = DynamicHookMap.get(hookPoint);

        if (data == null) {
            // First tick after attach: probe sub-level and capture local-space anchor.
            SubLevel sl = SubLevelResolver.findContaining(level, worldPos);
            if (sl == null) {
                // RECOVERY: Sable's clip mixin sometimes returns the hit in a sub-level's
                // PLOT/local frame (~20,000,000 blocks away) instead of the visual frame.
                // AOT then stores that as hook.position and yanks the player toward the
                // plot origin (the "random direction" bug). Detect by absurd distance from
                // the player and recover by finding the owning sub-level: its pose maps the
                // plot coords back to a visual point near the player.
                if (tryRecoverPlotCoords(level, hookPoint, worldPos, side)) {
                    return;
                }
                if (transition(side, LastState.NO_SUBLEVEL)) {
                    logHookVsPlayer(side, worldPos, "NO_SUBLEVEL");
                }
                return; // genuine vanilla world hook — nothing to do
            }

            UUID slId = sl.getUniqueId();
            if (slId == null) return;

            Vec3 localPos;
            try {
                Pose3dc pose = sl.logicalPose();
                localPos = pose.transformPositionInverse(worldPos);
            } catch (Throwable t) {
                DAOTCompat.LOGGER.debug("[daotcompat] {}: inverse transform failed at attach", side, t);
                return;
            }
            if (isInvalid(localPos)) return;

            DynamicHookMap.put(hookPoint, new DynamicHookData(slId, localPos));
            // Full diagnostic on attach: pose components + (best-effort) player position so
            // we can verify whether AOT's reported world coord matches the airship's visual
            // location, or whether Sable's clip mixin returned a frame-mismatched coord.
            Pose3dc poseDbg = sl.logicalPose();
            DAOTCompat.LOGGER.info(
                    "[daotcompat] {}: hook ATTACHED sublevel={} world={} local={}\n" +
                    "    pose.position    = {}\n" +
                    "    pose.rotationPt  = {}\n" +
                    "    pose.orientation = {}\n" +
                    "    pose.scale       = {}",
                    side, slId, fmt(worldPos), fmt(localPos),
                    fmtJoml(poseDbg.position()),
                    fmtJoml(poseDbg.rotationPoint()),
                    poseDbg.orientation() == null ? "null" :
                            String.format(java.util.Locale.ROOT, "(%.4f, %.4f, %.4f, %.4f)",
                                    poseDbg.orientation().x(), poseDbg.orientation().y(),
                                    poseDbg.orientation().z(), poseDbg.orientation().w()),
                    fmtJoml(poseDbg.scale()));
            try {
                net.minecraft.client.player.LocalPlayer p =
                        net.minecraft.client.Minecraft.getInstance().player;
                if (p != null) {
                    DAOTCompat.LOGGER.info(
                            "[daotcompat] {}: player at {} | hook→player Δ = {}",
                            side, fmt(p.position()), fmt(p.position().subtract(worldPos)));
                }
            } catch (Throwable ignored) { /* main-menu / no player */ }
            transition(side, LastState.TRACKING);
            return;
        }

        // Subsequent ticks: re-project local → world using the sub-level's CURRENT pose.
        SubLevel sl = SableBridge.getSubLevel(level, data.subLevelId());
        if (sl == null) {
            DAOTCompat.LOGGER.info("[daotcompat] {}: sublevel {} unloaded — releasing hook",
                    side, data.subLevelId());
            releaseAndClear(hookPoint, "subLevel unloaded");
            return;
        }

        Vec3 newWorldPos;
        try {
            newWorldPos = sl.logicalPose().transformPosition(data.localPosition());
        } catch (Throwable t) {
            DAOTCompat.LOGGER.warn("[daotcompat] {}: transform threw {} — releasing", side, t);
            releaseAndClear(hookPoint, "transform threw: " + t.getClass().getSimpleName());
            return;
        }

        if (isInvalid(newWorldPos)) {
            DAOTCompat.LOGGER.warn("[daotcompat] {}: NaN/Inf in transform output — releasing", side);
            releaseAndClear(hookPoint, "transform produced NaN/Inf");
            return;
        }

        // Sanity-check: how far did our recompute diverge from AOT's stored position?
        // - tiny diff (< 0.001m) — pose effectively unchanged, leave AOT's value alone
        //   (avoids floating-point jitter that would destabilise rope physics)
        // - small diff (< 32m) — ship moved, normal — apply update
        // - huge diff (≥ 32m) — symptom of bad math (wrong rotationPoint frame etc.); REFUSE the
        //   write and log loudly so we surface the bug instead of teleporting the player away.
        double divergeSqr = worldPos.distanceToSqr(newWorldPos);
        if (divergeSqr < 0.000001) {
            recordTracking(side, worldPos, divergeSqr, false);
            return;
        }
        if (divergeSqr >= 1024.0) {
            DAOTCompat.LOGGER.warn(
                    "[daotcompat] {}: REFUSED huge transform jump: stored={} computed={} delta={}m  "
                            + "pose=[pos={}]  local={}",
                    side, fmt(worldPos), fmt(newWorldPos),
                    String.format(java.util.Locale.ROOT, "%.2f", Math.sqrt(divergeSqr)),
                    fmtJoml(sl.logicalPose().position()),
                    fmt(data.localPosition()));
            recordTracking(side, worldPos, divergeSqr, false);
            return;
        }

        AOTReflect.setPosition(hookPoint, newWorldPos);
        recordTracking(side, newWorldPos, divergeSqr, true);
    }

    private static void recordTracking(String side, Vec3 pos, double divergeSqr, boolean updated) {
        // 1 Hz heartbeat — also marks whether we wrote a new position this tick.
        if (tickCounter.incrementAndGet() % 20 == 0) {
            DAOTCompat.LOGGER.info(
                    "[daotcompat] {}: tracking world={} delta={}m {}",
                    side, fmt(pos),
                    String.format(java.util.Locale.ROOT, "%.4f", Math.sqrt(divergeSqr)),
                    updated ? "(applied)" : "(no-op)");
        }
    }

    private static boolean transition(String side, LastState next) {
        LastState prev = "L".equals(side) ? lastLeft : lastRight;
        if (prev == next) return false;
        if ("L".equals(side)) lastLeft = next; else lastRight = next;
        // Log every meaningful state transition once
        DAOTCompat.LOGGER.info("[daotcompat] {}: state {} -> {}", side, prev, next);
        return true;
    }

    /** Logs the hook world position, the player position, and the vector between them. */
    private static void logHookVsPlayer(String side, Vec3 worldPos, String tag) {
        try {
            net.minecraft.client.player.LocalPlayer p =
                    net.minecraft.client.Minecraft.getInstance().player;
            Vec3 pp = (p == null) ? null : p.position();
            DAOTCompat.LOGGER.info(
                    "[daotcompat] {}: {} hook={} player={} hook→player Δ={} dist={}",
                    side, tag, fmt(worldPos), fmt(pp),
                    pp == null ? "null" : fmt(worldPos.subtract(pp)),
                    pp == null ? "?" : String.format(java.util.Locale.ROOT, "%.2f", worldPos.distanceTo(pp)));
        } catch (Throwable ignored) { /* no player */ }
    }

    private static String fmt(Vec3 v) {
        // Locale.ROOT to force '.' decimal separator — RU locale was rendering
        // (x.xx, y.yy, z.zz) as "(x,xx, y,yy, z,zz)" which looked like 6 numbers in the log.
        return v == null ? "null"
                : String.format(java.util.Locale.ROOT, "(%.2f, %.2f, %.2f)", v.x, v.y, v.z);
    }

    private static String fmtJoml(org.joml.Vector3dc v) {
        return v == null ? "null"
                : String.format(java.util.Locale.ROOT, "(%.2f, %.2f, %.2f)", v.x(), v.y(), v.z());
    }

    private static void releaseAndClear(Object hookPoint, String reason) {
        DAOTCompat.LOGGER.debug("[daotcompat] releasing hook: {}", reason);
        AOTReflect.release(hookPoint);
        DynamicHookMap.put(hookPoint, null);
    }

    /**
     * Distance² beyond which a hook is considered to be in a sub-level's plot/local frame
     * (a frame-mismatch bug) rather than a real world hit. Real hooks are at most a few
     * dozen blocks away; plot coords are ~20,000,000 blocks away. 1000² = 1e6.
     */
    private static final double FAR_FROM_PLAYER_SQR = 1_000_000.0D;

    /** Distance² within which a recovered visual point is accepted as the real hook. 256². */
    private static final double NEAR_PLAYER_SQR = 65_536.0D;

    /**
     * Recovery for the case where AOT stored the hook in a sub-level's PLOT/local frame
     * (Sable's clip mixin returned the raw block location, ~20M blocks away). We treat the
     * stored position as local coords and find the sub-level whose pose maps it back to a
     * visual point near the player, then correct {@code hook.position} and start tracking.
     *
     * @return {@code true} if recovery succeeded (caller should return).
     */
    private static boolean tryRecoverPlotCoords(Level level, Object hookPoint, Vec3 hookPos, String side) {
        net.minecraft.client.player.LocalPlayer p =
                net.minecraft.client.Minecraft.getInstance().player;
        if (p == null) return false;
        Vec3 pp = p.position();
        // Only attempt when the hook is absurdly far — otherwise it's a genuine vanilla hit.
        if (hookPos.distanceToSqr(pp) < FAR_FROM_PLAYER_SQR) return false;

        for (SubLevel cand : SableBridge.getAllSubLevels(level)) {
            if (cand == null || cand.isRemoved()) continue;
            UUID id = cand.getUniqueId();
            if (id == null) continue;
            Vec3 visual;
            try {
                // Treat the plot-frame hook position as THIS sub-level's local coords.
                visual = cand.logicalPose().transformPosition(hookPos);
            } catch (Throwable t) {
                continue;
            }
            if (isInvalid(visual)) continue;
            if (visual.distanceToSqr(pp) <= NEAR_PLAYER_SQR) {
                // Owning sub-level found. Correct AOT's hook to the visual location and
                // begin tracking — the plot coords ARE our stored local anchor.
                AOTReflect.setPosition(hookPoint, visual);
                DynamicHookMap.put(hookPoint, new DynamicHookData(id, hookPos));
                DAOTCompat.LOGGER.info(
                        "[daotcompat] {}: RECOVERED plot-frame hook -> sublevel={} visual={} (was plot={}, player={})",
                        side, id, fmt(visual), fmt(hookPos), fmt(pp));
                transition(side, LastState.TRACKING);
                return true;
            }
        }
        return false;
    }

    private static boolean isInvalid(@Nullable Vec3 v) {
        return v == null
                || Double.isNaN(v.x) || Double.isNaN(v.y) || Double.isNaN(v.z)
                || Double.isInfinite(v.x) || Double.isInfinite(v.y) || Double.isInfinite(v.z);
    }
}
