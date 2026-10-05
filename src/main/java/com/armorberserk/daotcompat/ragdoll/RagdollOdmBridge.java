/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.ragdoll;

import com.armorberserk.daotcompat.DAOTCompat;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Fires the ODM gear's hooks programmatically while the player is ragdolled.
 *
 * <p>AOT creates HookPoints inside its own per-tick input logic, which never runs for a player
 * seated on a ragdoll (the seat consumes the interaction). We bypass that: client-side raycast
 * from the eyes along the crosshair, construct a HookPoint through AOT's public constructor and
 * public fields, and insert it into AOT's hook maps reflectively. From that moment AOT's own
 * tick logic owns the hook — rope pull, reeling — and our ROPE_FORCE bridge drags the ragdoll
 * body toward it.
 *
 * <p>Deterministic latch: the hook latches instantly at the aimed point (no flight animation) —
 * while ragdolled the flight animation would be unnoticeable, and this keeps the behaviour
 * independent of AOT's extension internals.
 */
public final class RagdollOdmBridge {

    private static volatile boolean mapsResolved;
    private static Object leftHooksMap;
    private static Object rightHooksMap;
    private static Method mapPut;

    private RagdollOdmBridge() {}

    private static synchronized boolean resolveMaps() {
        if (mapsResolved) return leftHooksMap != null && mapPut != null;
        mapsResolved = true;
        try {
            Class<?> tickHandler = Class.forName("daot.ODMTickHandler");
            // КРИТИЧНО: leftHooks/rightHooks — PRIVATE static поля, getField() (только публичные)
            // молча падал — из-за этого ПКМ-выстрел из рэгдолла не работал вовсе.
            for (Field f : tickHandler.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())
                        && f.getType().getName().contains("Map")) {
                    f.setAccessible(true);
                    Object value = f.get(null);
                    if ("leftHooks".equals(f.getName())) leftHooksMap = value;
                    if ("rightHooks".equals(f.getName())) rightHooksMap = value;
                }
            }
            if (leftHooksMap == null || rightHooksMap == null) {
                DAOTCompat.LOGGER.warn("[ragdoll-odm] AOT hook maps not resolved: left={}, right={}",
                        leftHooksMap, rightHooksMap);
                return false;
            }
            for (java.lang.reflect.Method m : leftHooksMap.getClass().getMethods()) {
                if ("put".equals(m.getName()) && m.getParameterCount() == 2) {
                    mapPut = m;
                    break;
                }
            }
            DAOTCompat.LOGGER.info("[ragdoll-odm] hook maps resolved: left={}, right={}, put={}",
                    leftHooksMap != null, rightHooksMap != null, mapPut != null);
            return mapPut != null;
        } catch (Throwable t) {
            DAOTCompat.LOGGER.warn("[ragdoll-odm] AOT hook maps resolve failed: {}", t.toString());
            return false;
        }
    }

    /**
     * Fires both hooks toward the crosshair (dual latch). Returns true if at least one hook
     * landed in AOT's maps.
     */
    public static boolean fireHooksAtCrosshair(LocalPlayer player) {
        if (!resolveMaps()) return false;

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        double range = 250.0D;
        Vec3 end = eye.add(look.scale(range));

        BlockHitResult hit = player.level().clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 latchPos = hit.getType() != HitResult.Type.MISS
                ? hit.getLocation()
                : end;

        boolean any = insertInto(leftHooksMap, player, eye, latchPos)
                | insertInto(rightHooksMap, player, eye, latchPos);
        DAOTCompat.LOGGER.info("[ragdoll-odm] hooks fired from ragdoll: left+right inserted={}, latch {} type {}",
                any, fmt(latchPos), hit.getType());
        return any;
    }

    private static boolean insertInto(Object hooksMap, LocalPlayer player, Vec3 eye, Vec3 latchPos) {
        try {
            Class<?> hpClass = Class.forName("daot.HookPoint");
            Object hook = hpClass.getConstructor().newInstance();
            setPublic(hpClass, hook, "active", true);
            setPublic(hpClass, hook, "isExtending", false);
            setPublic(hpClass, hook, "isRetracting", false);
            setPublic(hpClass, hook, "startPosition", eye);
            setPublic(hpClass, hook, "position", latchPos);
            setPublic(hpClass, hook, "maxRopeLength", 250.0D);
            setPublic(hpClass, hook, "playerVelocity", player.getDeltaMovement());

            UUID playerUuid = player.getUUID();
            for (java.lang.reflect.Method m : hooksMap.getClass().getMethods()) {
                if (!"put".equals(m.getName()) || m.getParameterCount() != 2) continue;
                Class<?>[] params = m.getParameterTypes();
                if (params[0].isAssignableFrom(UUID.class) && params[1].isInstance(hook)) {
                    m.invoke(hooksMap, playerUuid, hook);
                    DAOTCompat.LOGGER.info("[ragdoll-odm] hook inserted into map {}", hooksMap.getClass().getSimpleName());
                    return true;
                }
            }
            DAOTCompat.LOGGER.warn("[ragdoll-odm] put method not found on {}", hooksMap.getClass());
            return false;
        } catch (Throwable t) {
            DAOTCompat.LOGGER.warn("[ragdoll-odm] hook insert failed: {}", t.toString());
            return false;
        }
    }

    private static void setPublic(Class<?> c, Object instance, String field, Object value) throws Exception {
        Field f = c.getField(field);
        f.set(instance, value);
    }

    private static String fmt(Vec3 v) {
        return String.format(java.util.Locale.ROOT, "(%.1f, %.1f, %.1f)", v.x, v.y, v.z);
    }
}
