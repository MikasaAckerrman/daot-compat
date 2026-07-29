package com.example.daotcompat.hook;

import org.jetbrains.annotations.Nullable;

/**
 * Duck-typing interface mixed into {@code daot.HookPoint} via {@link com.example.daotcompat.mixin.HookPointMixin}.
 *
 * <p>Cast a {@code daot.HookPoint} reference to this interface at runtime to access:
 * <pre>{@code
 *   DynamicHookStorage storage = (DynamicHookStorage) hookPoint;
 *   DynamicHookData data = storage.daotCompat$getDynamicData();
 * }</pre>
 */
public interface DynamicHookStorage {

    /** @return the dynamic data attached to this hook, or {@code null} if not anchored to a sub-level. */
    @Nullable
    DynamicHookData daotCompat$getDynamicData();

    /** Set or clear the dynamic data. Pass {@code null} to detach. */
    void daotCompat$setDynamicData(@Nullable DynamicHookData data);
}
