/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

/**
 * Client-side keybindings registered by DAOT Compat.
 *
 * <p><b>Round 3 rename/semantics fix:</b> this key used to be called {@code REEL_IN} and worked
 * backwards from what players expect: the pull only happened while the key was HELD, and the
 * default (key released) was "hang in place, no pull at all". Two user-reported bugs traced back
 * to exactly that: (1) "the rope doesn't pull me in" - because by default it never did, and (2)
 * runaway spin near the anchor - because the un-clamped tangential-velocity math kicked in by
 * default on every active hook, not just when the player asked for it.
 *
 * <p>Renamed to {@link #HANG} and inverted: by default AOT's own pull behaves completely
 * normally (rope pulls you in, exactly like vanilla AOT). Only while {@code HANG} is HELD do we
 * intercept and strip the inward pull, letting the player brake and hang at a fixed distance -
 * mirroring the reference mod "Grapple Mod" (yyon), whose dedicated "stop swinging" key works the
 * same way (hold to stop, release to resume normal grapple behaviour). This is the intuitive
 * default: a grapple should attract you unless you actively tell it to stop.
 *
 * <p>{@link #HANG} is bound to {@code LEFT_ALT} by default because:
 * <ul>
 *   <li>Vanilla Minecraft does not bind LEFT_ALT to any action.</li>
 *   <li>Danny's AOT does not claim it in its own bindings.</li>
 *   <li>It is physically adjacent to the sprint/jump keys, matching the "hold to engage"
 *       muscle memory pattern players already use for ODM boost.</li>
 * </ul>
 * Players can rebind it in Options → Controls → (Russian: "Крюки ODM (DAOT Compat)").
 */
public final class DAOTCompatKeyMappings {

    /**
     * While held, {@link com.armorberserk.daotcompat.hook.ReelControl} strips the inward pull
     * component from the player's velocity each tick, letting the player hang/brake in place.
     * While released (the default), AOT's normal pull-in behaviour is left completely untouched.
     */
    public static final KeyMapping HANG = new KeyMapping(
            "key.daotcompat.hang",
            InputConstants.Type.KEYSYM,
            342, // GLFW_KEY_LEFT_ALT — free in vanilla and AOT; see class javadoc
            "key.categories.daotcompat"
    );

    private DAOTCompatKeyMappings() {}
}
