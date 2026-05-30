/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Makes Danny's AOT grappling hooks behave on Create: Aeronautics / Sable airships.
 * A hook that grabs a moving sub-level block is followed every tick so it no longer
 * drifts or flings the player. Everything runs client-side through a single mixin.
 */
@Mod(DAOTCompat.MOD_ID)
public final class DAOTCompat {

    public static final String MOD_ID = "daotcompat";
    public static final Logger LOGGER = LoggerFactory.getLogger("DAOT Compat");

    public DAOTCompat(IEventBus modBus) {
        LOGGER.info("DAOT Aeronautics Compat by armorberserk loaded");
    }
}
