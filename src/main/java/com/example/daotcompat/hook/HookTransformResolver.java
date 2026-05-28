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
                transition(side, LastState.NO_SUBLEVEL);
                return; // vanilla world hook — nothing to do
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
            DAOTCompat.LOGGER.info("[daotcompat] {}: hook ATTACHED to sublevel {} (worldPos={}, localPos={})",
                    side, slId, fmt(worldPos), fmt(localPos));
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

        AOTReflect.setPosition(hookPoint, newWorldPos);
        // Periodic heartbeat while tracking (~ once per 2s)
        if (tickCounter.incrementAndGet() % 40 == 0) {
            DAOTCompat.LOGGER.debug("[daotcompat] {}: tracking, world={}, local={}",
                    side, fmt(newWorldPos), fmt(data.localPosition()));
        }
    }

    private static void transition(String side, LastState next) {
        LastState prev = "L".equals(side) ? lastLeft : lastRight;
        if (prev == next) return;
        if ("L".equals(side)) lastLeft = next; else lastRight = next;
        // Log every meaningful state transition once
        DAOTCompat.LOGGER.info("[daotcompat] {}: state {} -> {}", side, prev, next);
    }

    private static String fmt(Vec3 v) {
        return v == null ? "null" : String.format("(%.2f,%.2f,%.2f)", v.x, v.y, v.z);
    }

    private static void releaseAndClear(Object hookPoint, String reason) {
        DAOTCompat.LOGGER.debug("[daotcompat] releasing hook: {}", reason);
        AOTReflect.release(hookPoint);
        DynamicHookMap.put(hookPoint, null);
    }

    private static boolean isInvalid(@Nullable Vec3 v) {
        return v == null
                || Double.isNaN(v.x) || Double.isNaN(v.y) || Double.isNaN(v.z)
                || Double.isInfinite(v.x) || Double.isInfinite(v.y) || Double.isInfinite(v.z);
    }
}
