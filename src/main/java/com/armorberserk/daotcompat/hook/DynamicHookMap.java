/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.hook;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Stores our anchor data keyed by AOT's hook instance. Weak keys mean an entry is dropped
 * automatically once AOT discards the hook, so there is nothing to clean up manually.
 */
public final class DynamicHookMap {

    private static final Map<Object, DynamicHookData> ANCHORS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private DynamicHookMap() {}

    @Nullable
    public static DynamicHookData get(@Nullable Object hook) {
        return hook == null ? null : ANCHORS.get(hook);
    }

    public static void put(@Nullable Object hook, @Nullable DynamicHookData data) {
        if (hook == null) return;
        if (data == null) {
            ANCHORS.remove(hook);
        } else {
            ANCHORS.put(hook, data);
        }
    }
}
