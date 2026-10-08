/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.spear;

import com.armorberserk.daotcompat.DAOTCompat;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * Carries the {@code daot.FlyingThunderSpearTracker} visual spear along a moving sub-level.
 *
 * <p>AOT's client visual tracker spawns a decorative flying-spear state machine the moment the
 * server reports a lodge, and pins it to the lodge-time world snapshot. It has no idea Sable
 * sub-levels exist, so once the real entity is glued to an airship (server-side by
 * {@code ThunderSpearFollower}) the drawn spear and its wire stay behind at the old world point.
 *
 * <p>This class reflectively walks {@code FlyingThunderSpearTracker.activeSpears}, matches the
 * entry that sits at (or near) the entity's previous position, and rewrites its position fields
 * ({@code pos}, {@code prevX/Y/Z}) to the sub-level-carried coordinates. Field names are verified
 * against the AOT 2.5.1.1 jar ({@code pos} is a {@code Vec3}, {@code prevX/Y/Z} are doubles);
 * every access is defensive so a renamed field degrades to a no-op.
 */
public final class SpearVisualReflect {

    private static final double MATCH_RADIUS = 8.0D;

    private static volatile boolean probed;
    private static Field activeSpearsField;
    private static Field stateField;
    private static Field posField;
    private static Field prevXField;
    private static Field prevYField;
    private static Field prevZField;

    private SpearVisualReflect() {}

    private static synchronized boolean ensureProbe() {
        if (probed) return activeSpearsField != null;
        probed = true;
        try {
            // Cross-loader lookup: AOT ships through Sinytra Connector, so a plain
            // Class.forName on our own loader may not see it (Reflect.find scans the
            // reachable loaders — same rule as every other AOT touchpoint in this mod).
            Class<?> tracker = com.armorberserk.daotcompat.aot.Reflect.find("daot.FlyingThunderSpearTracker");
            Class<?> flyingSpear = com.armorberserk.daotcompat.aot.Reflect.find("daot.FlyingThunderSpearTracker$FlyingSpear");
            if (tracker == null || flyingSpear == null) {
                DAOTCompat.LOGGER.debug("[spear-visual] FlyingThunderSpearTracker not found in any loader");
                return false;
            }
            activeSpearsField = tracker.getField("activeSpears");
            for (Field f : flyingSpear.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                switch (f.getName()) {
                    case "state" -> stateField = f;
                    case "pos" -> posField = f;
                    case "prevX" -> prevXField = f;
                    case "prevY" -> prevYField = f;
                    case "prevZ" -> prevZField = f;
                }
            }
            boolean ok = activeSpearsField != null && posField != null
                    && prevXField != null && prevYField != null && prevZField != null;
            if (!ok) DAOTCompat.LOGGER.debug("[spear-visual] FlyingSpear fields not fully resolved");
            return ok;
        } catch (Throwable t) {
            DAOTCompat.LOGGER.debug("[spear-visual] tracker probe failed", t);
            return false;
        }
    }

    /**
     * Moves every lodged visual spear sitting near {@code oldPos} to {@code newPos}.
     *
     * @return true if at least one visual entry was carried.
     */
    public static boolean carryVisual(Vec3 oldPos, Vec3 newPos) {
        if (!ensureProbe()) return false;
        try {
            List<?> spears = (List<?>) activeSpearsField.get(null);
            if (spears == null) return false;
            boolean carried = false;
            for (Object spear : spears) {
                if (spear == null) continue;
                if (!isLodged(spear)) continue;
                Vec3 visualPos = readPos(spear);
                if (visualPos == null) continue;
                // Tolerance covers a few ticks of accumulated drift before we latch on.
                if (visualPos.distanceToSqr(oldPos) > MATCH_RADIUS * MATCH_RADIUS) continue;
                writePos(spear, oldPos, newPos);
                carried = true;
            }
            return carried;
        } catch (Throwable t) {
            DAOTCompat.LOGGER.debug("[spear-visual] carry failed", t);
            return false;
        }
    }


    /**
     * Plot-world fix: AOT's lodge packet carries PLOT-space coordinates (±20M blocks in flat
     * plot worlds), so the tracker spawns the visual spear far outside the loaded world. Any
     * lodged visual sitting at an absurd distance is rewritten to the real spear entity's
     * position every tick — the entity itself is world-synced correctly.
     */
    public static int fixAbnormalVisuals(Vec3 correctPos) {
        if (!ensureProbe()) return 0;
        try {
            List<?> spears = (List<?>) activeSpearsField.get(null);
            if (spears == null) return 0;
            int fixed = 0;
            for (Object spear : spears) {
                if (spear == null) continue;
                Vec3 visualPos = readPos(spear);
                if (visualPos == null) continue;
                if (Math.abs(visualPos.x) > 1_000_000.0D
                        || Math.abs(visualPos.y) > 1_000_000.0D
                        || Math.abs(visualPos.z) > 1_000_000.0D) {
                    writePos(spear, correctPos, correctPos);
                    fixed++;
                }
            }
            if (fixed > 0) DAOTCompat.LOGGER.info("[spear-visual] fixed {} abnormal plot-space visual(s)", fixed);
            return fixed;
        } catch (Throwable t) {
            DAOTCompat.LOGGER.debug("[spear-visual] abnormal fix failed", t);
            return 0;
        }
    }

    private static boolean isLodged(Object spear) {
        if (stateField == null) return true; // no state info: match by position only
        try {
            Object state = stateField.get(spear);
            return state != null && "LODGED".equals(((Enum<?>) state).name());
        } catch (Throwable t) {
            return true;
        }
    }

    private static Vec3 readPos(Object spear) {
        try {
            return (Vec3) posField.get(spear);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void writePos(Object spear, Vec3 oldPos, Vec3 newPos) {
        try {
            posField.set(spear, newPos);
            prevXField.setDouble(spear, oldPos.x);
            prevYField.setDouble(spear, oldPos.y);
            prevZField.setDouble(spear, oldPos.z);
        } catch (Throwable ignored) {
        }
    }
}
