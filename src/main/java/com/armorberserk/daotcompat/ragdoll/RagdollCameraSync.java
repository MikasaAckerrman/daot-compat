/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.ragdoll;

import com.armorberserk.daotcompat.DAOTCompat;
import dev.leo.sableplayerragdoll.neoforge.config.RagdollClientConfig;
import net.minecraft.client.CameraType;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Keeps the ragdoll camera in the perspective the player is currently in: if they are in first
 * person when the ragdoll starts, the ragdoll plays in first person; if in third person — in
 * third. Achieved by flipping the ragdoll mod's own {@code useFirstPersonCamera} client config
 * value (found reflectively as the static {@link ModConfigSpec.ConfigValue} whose path contains
 * {@code useFirstPersonCamera}) every time the perspective changes while a ragdoll is active.
 */
public final class RagdollCameraSync {

    private static volatile boolean probed;
    private static volatile ModConfigSpec.ConfigValue<Boolean> cameraValue;
    private static volatile boolean probeFailed;
    private static CameraType lastSynced;

    private RagdollCameraSync() {}

    /** Call every client tick while a ragdoll is plausibly active (cheap: no-op until change). */
    public static void sync() {
        CameraType current = DAOTCompat.minecraft().options.getCameraType();
        if (current == lastSynced) return; // only write on a real perspective change
        ModConfigSpec.ConfigValue<Boolean> value = probe();
        if (value == null) return;
        try {
            value.set(current == CameraType.FIRST_PERSON);
            lastSynced = current;
            DAOTCompat.LOGGER.debug("[ragdoll] camera synced to {}", current);
        } catch (Throwable t) {
            DAOTCompat.LOGGER.debug("[ragdoll] camera sync failed", t);
        }
    }

    /** Force a re-sync on the next tick (used right after we trigger a ragdoll). */
    public static void reset() {
        lastSynced = null;
    }

    private static ModConfigSpec.ConfigValue<Boolean> probe() {
        if (probed) return cameraValue;
        probed = true;
        try {
            for (Field f : RagdollClientConfig.class.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) continue;
                if (!f.getType().getName().endsWith("ModConfigSpec$ConfigValue")) continue;
                f.setAccessible(true);
                Object v = f.get(null);
                boolean byName = f.getName().toLowerCase().contains("firstpersoncamera");
                boolean byPath = v instanceof ModConfigSpec.ConfigValue<?> cfg
                        && cfg.getPath().toString().toLowerCase().contains("usefirstpersoncamera");
                if (v instanceof ModConfigSpec.ConfigValue<?> && (byName || byPath)) {
                    @SuppressWarnings("unchecked")
                    ModConfigSpec.ConfigValue<Boolean> casted = (ModConfigSpec.ConfigValue<Boolean>) v;
                    cameraValue = casted;
                    return cameraValue;
                }
            }
            DAOTCompat.LOGGER.debug("[ragdoll] useFirstPersonCamera config value not found");
        } catch (Throwable t) {
            DAOTCompat.LOGGER.debug("[ragdoll] camera config probe failed", t);
        }
        probeFailed = true;
        return null;
    }
}
