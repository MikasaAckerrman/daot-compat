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


    public static final ModConfigSpec.BooleanValue HOOK_CHORDS_ENABLED = BUILDER
            .comment("Fire ODM hooks only from chords: hold the modifier key + press the mouse button (left hook = modifier+LMB, right hook = modifier+RMB). While a chord is enabled the plain mouse buttons no longer fire hooks")
            .define("hookChordsEnabled", true);

    public static final ModConfigSpec.ConfigValue<String> HOOK_MODIFIER_KEY = BUILDER
            .comment("Keyboard modifier for hook chords: a letter (X = 'Ч' on ЙЦУКЕН), digit, or InputConstants name like 'key.keyboard.left.shift'")
            .define("hookModifierKey", "X");

    public static final ModConfigSpec.IntValue HOOK_LEFT_MOUSE = BUILDER
            .comment("Mouse button for the LEFT hook chord: 0 = LMB, 1 = RMB, 2 = MMB")
            .defineInRange("hookLeftMouse", 0, 0, 7);

    public static final ModConfigSpec.IntValue HOOK_RIGHT_MOUSE = BUILDER
            .comment("Mouse button for the RIGHT hook chord: 0 = LMB, 1 = RMB, 2 = MMB")
            .defineInRange("hookRightMouse", 1, 0, 7);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private DaotConfig() {}
}
