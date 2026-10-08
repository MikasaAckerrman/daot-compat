/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-side tunables. The ragdoll thresholds live here because the failure-detection
 * (hook drop / weak anchor trip) runs on the client; the server only validates and launches.
 */
public final class DaotConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue RAGDOLL_ENABLED = BUILDER
            .comment("Ragdoll the player when ODM grappling fails badly (requires Sable: Ragdolls)")
            .define("ragdollEnabled", true);

    public static final ModConfigSpec.DoubleValue CRASH_MIN_SPEED = BUILDER
            .comment("Speed (m/s) at which losing a hook anchor counts as a hard crash (normal ragdoll, ODM still usable afterwards and during it)")
            .defineInRange("crashMinSpeed", 20.0D, 1.0D, 128.0D);

    public static final ModConfigSpec.DoubleValue STUN_MIN_SPEED = BUILDER
            .comment("Speed (m/s) above which a hard crash becomes a STUN: ragdoll you cannot exit early and ODM hooks are suppressed for its duration")
            .defineInRange("stunMinSpeed", 40.0D, 1.0D, 256.0D);

    public static final ModConfigSpec.IntValue STUN_TICKS = BUILDER
            .comment("Stun duration in ticks (20 = 1s). During a stun you cannot exit and ODM hooks are suppressed")
            .defineInRange("stunTicks", 80, 20, 600);

    public static final ModConfigSpec.DoubleValue TRIP_MIN_SPEED = BUILDER
            .comment("Horizontal speed (m/s) at which arriving at a weak, flat anchor trips you")
            .defineInRange("tripMinSpeed", 20.0D, 1.0D, 128.0D);

    public static final ModConfigSpec.DoubleValue TRIP_MAX_DISTANCE = BUILDER
            .comment("How close the anchor must be (blocks) for the trip rule to apply")
            .defineInRange("tripMaxDistance", 3.0D, 0.5D, 16.0D);

    public static final ModConfigSpec.DoubleValue TRIP_HEIGHT_DELTA = BUILDER
            .comment("Anchor must be within this vertical distance of the player's feet (blocks) to count as 'same plane'")
            .defineInRange("tripHeightDelta", 1.5D, 0.0D, 8.0D);

    public static final ModConfigSpec.BooleanValue SPEAR_VISUAL_SYNC = BUILDER
            .comment("Carry lodged thunder spears (and their wires) on moving airships client-side")
            .define("spearVisualSync", true);

    public static final ModConfigSpec.BooleanValue ANTI_TUNNEL = BUILDER
            .comment("Prevent tunnelling through thin airship decks at ODM speeds")
            .define("antiTunnel", true);

    public static final ModConfigSpec.BooleanValue RAGDOLL_FORCE_ENABLED = BUILDER
            .comment("While ragdolled with ropes attached, the gear's rope pull drags your ragdoll body (requires Sable: Ragdolls)")
            .define("ragdollForceEnabled", true);

    public static final ModConfigSpec.DoubleValue RAGDOLL_FORCE_STRENGTH = BUILDER
            .comment("How strongly the ragdoll body's velocity converges toward the rope-pull velocity per tick (0..1)")
            .defineInRange("ragdollForceStrength", 0.5D, 0.0D, 1.0D);


    public static final ModConfigSpec.DoubleValue RAGDOLL_PULL_SPEED = BUILDER
            .comment("Ragdoll body pull speed toward the ODM anchor while ragdolled (m/s)")
            .defineInRange("ragdollPullSpeed", 16.0D, 1.0D, 64.0D);

    public static final ModConfigSpec.DoubleValue RAGDOLL_ARRIVE_RADIUS = BUILDER
            .comment("Distance (blocks) between the ragdoll body and the ODM anchor at which the ragdoll auto-exits (the player is back in control)")
            .defineInRange("ragdollArriveRadius", 3.0D, 0.5D, 16.0D);

    public static final ModConfigSpec.BooleanValue TELEMETRY_ENABLED = BUILDER
            .comment("Live telemetry: in-game log overlay (F6) + localhost HTTP endpoint + logs/daotcompat-live.json")
            .define("telemetryEnabled", true);

    public static final ModConfigSpec.IntValue TELEMETRY_PORT = BUILDER
            .comment("Port for the localhost-only telemetry endpoint (127.0.0.1, never exposed to the network)")
            .defineInRange("telemetryPort", 27415, 1024, 65535);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private DaotConfig() {}
}
