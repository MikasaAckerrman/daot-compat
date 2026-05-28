package com.example.daotcompat.hook;

import com.example.daotcompat.DAOTCompat;
import com.example.daotcompat.mixin.accessor.HookPointAccessor;
import com.example.daotcompat.sable.SableBridge;
import com.example.daotcompat.sable.SubLevelResolver;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Per-tick logic that keeps {@code daot.HookPoint.position} in sync with a moving
 * Sable sub-level.
 *
 * <p>Called once per active hook from {@code daot.ODMTickHandler#applyHookMovement}
 * (HEAD inject). One method handles both first-tick attach detection and subsequent
 * world-space re-projection — keeps the hot path branch-flat and the call-sites in
 * the mixin to a single line each.
 */
public final class HookTransformResolver {

    private HookTransformResolver() {}

    /**
     * @param level     the world the player is currently in (must not be null)
     * @param hookPoint a {@code daot.HookPoint} instance (Object-typed — type belongs to AOT jar)
     */
    public static void process(@Nullable Level level, @Nullable Object hookPoint) {
        if (level == null || hookPoint == null) return;

        // Cast through duck-typing interfaces injected by our mixins.
        // If the cast fails, our mixins didn't apply — bail out silently.
        DynamicHookStorage storage;
        HookPointAccessor accessor;
        try {
            storage = (DynamicHookStorage) hookPoint;
            accessor = (HookPointAccessor) hookPoint;
        } catch (ClassCastException e) {
            return;
        }

        // Inactive hook: clear any stale dynamic data and exit.
        if (!accessor.daotCompat$isActive()) {
            if (storage.daotCompat$getDynamicData() != null) {
                storage.daotCompat$setDynamicData(null);
            }
            return;
        }

        // Entity hook (e.g. on a titan): AOT updates position itself via updateEntityPosition().
        // We must not interfere — clear our data if it lingered from a previous block-hook.
        if (accessor.daotCompat$getHookedEntity() != null) {
            if (storage.daotCompat$getDynamicData() != null) {
                storage.daotCompat$setDynamicData(null);
            }
            return;
        }

        Object posObj = accessor.daotCompat$getPosition();
        if (!(posObj instanceof Vec3 worldPos)) return;

        DynamicHookData data = storage.daotCompat$getDynamicData();

        if (data == null) {
            // First tick after attach: probe sub-level and capture local-space anchor.
            SubLevel sl = SubLevelResolver.findContaining(level, worldPos);
            if (sl == null) return; // vanilla world hook — nothing to do
            java.util.UUID slId = sl.getUniqueId();
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
            storage.daotCompat$setDynamicData(new DynamicHookData(slId, localPos));
            return;
        }

        // Subsequent ticks: re-project local → world using the sub-level's CURRENT pose.
        SubLevel sl = SableBridge.getSubLevel(level, data.subLevelId());
        if (sl == null) {
            // Sub-level unloaded or removed — release the hook so the player isn't yanked.
            releaseAndClear(accessor, storage, "subLevel unloaded");
            return;
        }

        Vec3 newWorldPos;
        try {
            newWorldPos = sl.logicalPose().transformPosition(data.localPosition());
        } catch (Throwable t) {
            releaseAndClear(accessor, storage, "transform threw: " + t.getClass().getSimpleName());
            return;
        }

        if (isInvalid(newWorldPos)) {
            releaseAndClear(accessor, storage, "transform produced NaN/Inf");
            return;
        }

        accessor.daotCompat$setPosition(newWorldPos);
    }

    private static void releaseAndClear(HookPointAccessor accessor,
                                        DynamicHookStorage storage,
                                        String reason) {
        DAOTCompat.LOGGER.debug("[daotcompat] releasing hook: {}", reason);
        try {
            accessor.daotCompat$invokeRelease();
        } catch (Throwable t) {
            DAOTCompat.LOGGER.warn("[daotcompat] release() invoker threw", t);
        }
        storage.daotCompat$setDynamicData(null);
    }

    private static boolean isInvalid(@Nullable Vec3 v) {
        return v == null
                || Double.isNaN(v.x) || Double.isNaN(v.y) || Double.isNaN(v.z)
                || Double.isInfinite(v.x) || Double.isInfinite(v.y) || Double.isInfinite(v.z);
    }
}
