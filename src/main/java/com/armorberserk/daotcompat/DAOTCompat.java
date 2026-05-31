/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat;

import com.armorberserk.daotcompat.spear.ThunderSpearFollower;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Makes Danny's AOT behave on Create: Aeronautics / Sable airships.
 *
 * <p>Client side: a grappling hook that grabs a moving sub-level is followed every tick so
 * the rope no longer drifts or flings the player (see the LocalPlayer mixin).
 *
 * <p>Server side: a thunder spear lodged in a sub-level is re-placed from the sub-level's
 * pose each tick so its fuse explosion lands where it stuck (see {@link ThunderSpearFollower}).
 */
@Mod(DAOTCompat.MOD_ID)
public final class DAOTCompat {

    public static final String MOD_ID = "daotcompat";
    public static final Logger LOGGER = LoggerFactory.getLogger("DAOT Compat");

    public DAOTCompat(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener((EntityTickEvent.Post event) ->
                ThunderSpearFollower.onTick(event.getEntity()));
        LOGGER.info("DAOT Aeronautics Compat by armorberserk loaded");
    }
}
