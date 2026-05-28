package com.example.daotcompat.hook;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * External storage for {@link DynamicHookData} keyed by {@code daot.HookPoint} instance.
 *
 * <p>Replaces the original {@code @Unique} field on {@code HookPointMixin} which Sinytra
 * Connector failed to apply correctly to Fabric mod-classes. {@link WeakHashMap} keys are
 * weakly referenced &mdash; when AOT clears its hook map (player disconnect, hook GC'd),
 * our entry vanishes too. Zero leak risk.
 *
 * <p>Synchronised wrapper because {@link WeakHashMap} is not thread-safe and although
 * we read/write only from the client tick, defensive synchronisation costs near-nothing
 * here (a handful of map ops per tick).
 */
public final class DynamicHookMap {

    private static final Map<Object, DynamicHookData> MAP =
            Collections.synchronizedMap(new WeakHashMap<>());

    private DynamicHookMap() {}

    @Nullable
    public static DynamicHookData get(@Nullable Object hookPoint) {
        if (hookPoint == null) return null;
        return MAP.get(hookPoint);
    }

    public static void put(@Nullable Object hookPoint, @Nullable DynamicHookData data) {
        if (hookPoint == null) return;
        if (data == null) {
            MAP.remove(hookPoint);
        } else {
            MAP.put(hookPoint, data);
        }
    }

    public static void clear() {
        MAP.clear();
    }
}
