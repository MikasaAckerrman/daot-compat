package com.example.daotcompat;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for the DAOT Aeronautics Compat mod.
 *
 * <p>Bridges Danny's AOT ODM hooks with Sable sub-levels (used by Create Aeronautics)
 * by tracking hook attachments inside moving sub-levels and re-projecting their
 * world-space position from the sub-level's {@code logicalPose()} every tick.
 *
 * <p>All the heavy lifting happens via Mixins; this class is just the FML init hook.
 */
@Mod(DAOTCompat.MOD_ID)
public final class DAOTCompat {

    public static final String MOD_ID = "daotcompat";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public DAOTCompat(IEventBus modBus) {
        LOGGER.info("[{}] init", MOD_ID);
    }
}
