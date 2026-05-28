package com.example.daotcompat.hook;

import com.example.daotcompat.DAOTCompat;
import com.example.daotcompat.aot.HookPointReflect;
import com.example.daotcompat.sable.SableBridge;
import com.example.daotcompat.sable.SubLevelResolver;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Per-tick logic that keeps {@code daot.HookPoint.position} in sync with a moving
 * Sable sub-level.
 *
 * <p>Reads/writes {@code HookPoint} state through {@link HookPointReflect} (reflection on
 * its public fields) and stores per-hook compat data in {@link DynamicHookMap} (external
 * {@link java.util.WeakHashMap}). This bypass avoids the Mixin-on-Fabric-class issue
 * observed under Sinytra Connector, where {@code @Accessor} method bodies were not
 * generated, producing {@link AbstractMethodError} at first call.
 *
 * <p>Called from {@link com.example.daotcompat.mixin.client.LocalPlayerTickMixin} HEAD
 * once per client tick &mdash; before AOT consumes the hook position in its own
 * post-tick callback.
 */
public final class HookTransformResolver {

    private HookTransformResolver() {}

    /**
     * @param level     the world the local player is in (must not be null)
     * @param hookPoint a {@code daot.HookPoint} instance from {@code ODMTickHandler}
     */
    public static void process(@Nullable Level level, @Nullable Object hookPoint) {
        if (level == null || hookPoint == null) return;
        if (!HookPointReflect.isAvailable()) return;

        // Inactive hook: clear any stale dynamic data and exit.
        if (!HookPointReflect.isActive(hookPoint)) {
            if (DynamicHookMap.get(hookPoint) != null) {
                DynamicHookMap.put(hookPoint, null);
            }
            return;
        }

        // Entity hook (e.g. on a titan): AOT updates position itself via updateEntityPosition().
        // We must not interfere — clear our data if it lingered from a previous block-hook.
        if (HookPointReflect.isOnEntity(hookPoint)) {
            if (DynamicHookMap.get(hookPoint) != null) {
                DynamicHookMap.put(hookPoint, null);
            }
            return;
        }

        Vec3 worldPos = HookPointReflect.getPosition(hookPoint);
        if (worldPos == null || isInvalid(worldPos)) return;

        DynamicHookData data = DynamicHookMap.get(hookPoint);

        if (data == null) {
            // First tick after attach: probe sub-level and capture local-space anchor.
            SubLevel sl = SubLevelResolver.findContaining(level, worldPos);
            if (sl == null) return; // vanilla world hook — nothing to do

            UUID slId = sl.getUniqueId();
            if (slId == null) return; // sub-level not fully initialised yet, retry next tick

            Vec3 localPos;
            try {
                Pose3dc pose = sl.logicalPose();
                localPos = pose.transformPositionInverse(worldPos);
            } catch (Throwable t) {
                DAOTCompat.LOGGER.debug("[daotcompat] inverse transform failed at attach", t);
                return;
            }
            if (isInvalid(localPos)) return;

            DynamicHookMap.put(hookPoint, new DynamicHookData(slId, localPos));
            return;
        }

        // Subsequent ticks: re-project local → world using the sub-level's CURRENT pose.
        SubLevel sl = SableBridge.getSubLevel(level, data.subLevelId());
        if (sl == null) {
            // Sub-level unloaded or removed — release the hook so the player isn't yanked.
            releaseAndClear(hookPoint, "subLevel unloaded");
            return;
        }

        Vec3 newWorldPos;
        try {
            newWorldPos = sl.logicalPose().transformPosition(data.localPosition());
        } catch (Throwable t) {
            releaseAndClear(hookPoint, "transform threw: " + t.getClass().getSimpleName());
            return;
        }

        if (isInvalid(newWorldPos)) {
            releaseAndClear(hookPoint, "transform produced NaN/Inf");
            return;
        }

        HookPointReflect.setPosition(hookPoint, newWorldPos);
    }

    private static void releaseAndClear(Object hookPoint, String reason) {
        DAOTCompat.LOGGER.debug("[daotcompat] releasing hook: {}", reason);
        HookPointReflect.release(hookPoint);
        DynamicHookMap.put(hookPoint, null);
    }

    private static boolean isInvalid(@Nullable Vec3 v) {
        return v == null
                || Double.isNaN(v.x) || Double.isNaN(v.y) || Double.isNaN(v.z)
                || Double.isInfinite(v.x) || Double.isInfinite(v.y) || Double.isInfinite(v.z);
    }
}
