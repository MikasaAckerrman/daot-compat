/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.spear;

import com.armorberserk.daotcompat.DAOTCompat;
import net.minecraft.world.entity.Entity;
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
            Class<?> tracker = Class.forName("daot.FlyingThunderSpearTracker");
            activeSpearsField = tracker.getField("activeSpears");
            Class<?> flyingSpear = Class.forName("daot.FlyingThunderSpearTracker$FlyingSpear");
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
    public static boolean carryVisual(Entity spearEntity, Vec3 oldPos, Vec3 newPos) {
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
