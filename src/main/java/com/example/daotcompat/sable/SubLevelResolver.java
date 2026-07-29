package com.example.daotcompat.sable;

import com.example.daotcompat.DAOTCompat;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Locates the {@link SubLevel} that contains a given world-space point, if any.
 *
 * <p>Uses {@link SubLevelContainer#queryIntersecting(dev.ryanhcode.sable.companion.math.BoundingBox3dc)}
 * with a tiny axis-aligned box centred on the point. The first non-removed match is returned;
 * overlapping sub-levels are not expected at hook-attach scale, but if they occur the closest
 * one wins by query iteration order.
 */
public final class SubLevelResolver {

    /** Half-extent of the probe box around the query point (world units). */
    private static final double PROBE = 0.05D;

    private SubLevelResolver() {}

    @Nullable
    public static SubLevel findContaining(@Nullable Level level, @Nullable Vec3 worldPos) {
        if (level == null || worldPos == null) return null;
        if (Double.isNaN(worldPos.x) || Double.isNaN(worldPos.y) || Double.isNaN(worldPos.z)) return null;

        SubLevelContainer container = SableBridge.getContainer(level);
        if (container == null) return null;

        BoundingBox3d probe = new BoundingBox3d(
                worldPos.x - PROBE, worldPos.y - PROBE, worldPos.z - PROBE,
                worldPos.x + PROBE, worldPos.y + PROBE, worldPos.z + PROBE);

        try {
            for (SubLevel sl : container.queryIntersecting(probe)) {
                if (sl != null && !sl.isRemoved()) {
                    return sl;
                }
            }
        } catch (Throwable t) {
            DAOTCompat.LOGGER.debug("[daotcompat] SubLevel queryIntersecting failed", t);
        }
        return null;
    }
}
