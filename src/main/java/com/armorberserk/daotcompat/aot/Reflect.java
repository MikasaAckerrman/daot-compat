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
 */
final class Reflect {

    private Reflect() {}

    @Nullable
    static Class<?> find(String name) {
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
