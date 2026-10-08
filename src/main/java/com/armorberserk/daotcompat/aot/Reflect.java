/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.aot;

import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Looks up a class by name across the reachable loaders. Danny's AOT comes in through
 * Sinytra Connector, which can place its classes in a loader other than ours, so a plain
 * {@code Class.forName} on our own loader is not enough. Uses a common Mojang class for the
 * game loader so this stays safe on a dedicated server (no client-only references).
 *
 * <p>Public since v2.0.2: {@code SpearVisualReflect} (in the {@code spear} package) needs the
 * same cross-loader lookup — a plain {@code Class.forName} there could silently miss AOT's
 * Connector classloader and kill the whole visual spear carry.
 */
public final class Reflect {

    private Reflect() {}

    @Nullable
    public static Class<?> find(String name) {
        ClassLoader[] loaders = {
                Thread.currentThread().getContextClassLoader(),
                Entity.class.getClassLoader(),
                Reflect.class.getClassLoader(),
                ClassLoader.getSystemClassLoader()
        };
        for (ClassLoader cl : loaders) {
            if (cl == null) continue;
            try {
                return Class.forName(name, false, cl);
            } catch (Throwable ignored) {
                // next loader
            }
        }
        return null;
    }
}
