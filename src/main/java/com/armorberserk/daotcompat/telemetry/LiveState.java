/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.telemetry;

import com.armorberserk.daotcompat.DAOTCompat;
import com.armorberserk.daotcompat.aot.AOTReflect;
import com.armorberserk.daotcompat.config.DaotConfig;
import com.armorberserk.daotcompat.hook.HookTransformResolver;
import com.armorberserk.daotcompat.ragdoll.RagdollClient;
import com.armorberserk.daotcompat.spear.ThunderSpearClientFollower;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A per-tick snapshot of everything the debug tooling cares about: player, vehicle (ragdoll
 * seat), hook state, ragdoll windows and the config that drives the rope-force bridge.
 *
 * <p>Written on the client tick thread, read by the HUD (render thread) and the telemetry HTTP
 * thread. Fields are individually volatile: for a debug overlay torn multi-field reads are
 * acceptable, but the JSON document is swapped atomically as one string.
 */
public final class LiveState {

    private static volatile String json = "{\"error\":\"not captured yet\"}";

    // HUD summary fields (plain volatile; debug data, tearing is fine).
    private static volatile long capturedAtMs;
    private static volatile long gameTick;
    private static volatile int fps;
    private static volatile boolean ragdolledLive;
    private static volatile boolean stunned;
    private static volatile boolean soundSuppression;
    private static volatile double playerX, playerY, playerZ;
    private static volatile double velX, velY, velZ;
    private static volatile String dimension = "?";
    private static volatile String vehicleType = "none";
    private static volatile double vehicleX, vehicleY, vehicleZ;
    private static volatile double vehicleDist = -1.0D;
    @Nullable
    private static volatile Vec3 leftHookPos;
    @Nullable
    private static volatile Vec3 rightHookPos;
    @Nullable
    private static volatile Vec3 anchorPos;

    // The ragdoll body projected into world space (null = not tracked this tick), and how far
    // the (invisible) player entity hangs off it — THE visual-mismatch metric.
    @Nullable
    private static volatile Vec3 ragdollWorldPos;
    private static volatile double ragdollDist = -1.0D;

    private LiveState() {}

    public static void capture(LocalPlayer player, @Nullable Object leftHook, @Nullable Object rightHook,
                               boolean ragdolled, @Nullable Vec3 ragdollWorld) {
        capturedAtMs = System.currentTimeMillis();
        gameTick = player.level().getGameTime();
        fps = DAOTCompat.minecraft().getFps();
        ragdolledLive = ragdolled;
        stunned = RagdollClient.isStunned();
        soundSuppression = RagdollClient.isSoundSuppressionActive();

        Vec3 pos = player.position();
        playerX = pos.x; playerY = pos.y; playerZ = pos.z;
        ragdollWorldPos = ragdollWorld;
        ragdollDist = ragdollWorld != null ? pos.distanceTo(ragdollWorld) : -1.0D;
        Vec3 vel = player.getDeltaMovement();
        velX = vel.x; velY = vel.y; velZ = vel.z;
        dimension = player.level().dimension().location().toString();

        Entity vehicle = player.getVehicle();
        if (vehicle != null) {
            vehicleType = vehicle.getType().toShortString();
            Vec3 vp = vehicle.position();
            vehicleX = vp.x; vehicleY = vp.y; vehicleZ = vp.z;
            vehicleDist = pos.distanceTo(vp);
        } else {
            vehicleType = "none";
            vehicleDist = -1.0D;
        }

        leftHookPos = AOTReflect.isActive(leftHook) ? AOTReflect.getPosition(leftHook) : null;
        rightHookPos = AOTReflect.isActive(rightHook) ? AOTReflect.getPosition(rightHook) : null;
        anchorPos = HookTransformResolver.getLastAnchorWorldPos();

        json = buildJson();
    }

    private static String buildJson() {
        StringBuilder b = new StringBuilder(512);
        b.append("{\"capturedAtMs\":").append(capturedAtMs)
                .append(",\"gameTick\":").append(gameTick)
                .append(",\"fps\":").append(fps)
                .append(",\"player\":{\"dimension\":\"").append(Json.esc(dimension)).append('"')
                .append(",\"pos\":[").append(Json.n(playerX)).append(',').append(Json.n(playerY)).append(',').append(Json.n(playerZ)).append(']')
                .append(",\"vel\":[").append(Json.n(velX)).append(',').append(Json.n(velY)).append(',').append(Json.n(velZ)).append("]}");
        if (vehicleDist >= 0.0D) {
            b.append(",\"vehicle\":{\"type\":\"").append(Json.esc(vehicleType)).append('"')
                    .append(",\"pos\":[").append(Json.n(vehicleX)).append(',').append(Json.n(vehicleY)).append(',').append(Json.n(vehicleZ)).append(']')
                    .append(",\"distToPlayer\":").append(Json.n(vehicleDist))
                    .append("}");
        } else {
            b.append(",\"vehicle\":null");
        }
        b.append(",\"ragdoll\":{\"live\":").append(ragdolledLive)
                .append(",\"stunned\":").append(stunned)
                .append(",\"soundSuppression\":").append(soundSuppression)
                .append(",\"recoverEnabled\":").append(DaotConfig.RAGDOLL_FORCE_ENABLED.get())
                .append(",\"bodyGlue\":").append(DaotConfig.RAGDOLL_BODY_GLUE.get())
                .append(",\"worldPos\":").append(vecJson(ragdollWorldPos))
                .append(",\"distPlayerToBody\":").append(ragdollDist >= 0.0D ? Json.n(ragdollDist) : "null")
                .append("}");
        b.append(",\"hooks\":{\"left\":").append(vecJson(leftHookPos))
                .append(",\"right\":").append(vecJson(rightHookPos))
                .append(",\"anchor\":").append(vecJson(anchorPos))
                .append("}");
        b.append(",\"spears\":").append(spearsJson());
        b.append('}');
        return b.toString();
    }

    private static String spearsJson() {
        var spears = ThunderSpearClientFollower.snapshot();
        StringBuilder b = new StringBuilder(32 + spears.size() * 64);
        b.append('[');
        for (int i = 0; i < spears.size(); i++) {
            ThunderSpearClientFollower.TrackedSpear s = spears.get(i);
            if (i > 0) b.append(',');
            b.append("{\"id\":").append(s.entityId())
                    .append(",\"pos\":").append(vecJson(s.pos()))
                    .append(",\"native\":").append(s.nativeCarried())
                    .append('}');
        }
        b.append(']');
        return b.toString();
    }

    private static String vecJson(@Nullable Vec3 v) {
        if (v == null) return "null";
        return "[" + Json.n(v.x) + ',' + Json.n(v.y) + ',' + Json.n(v.z) + ']';
    }

    /** The last snapshot as a JSON document; swapped atomically. */
    public static String json() {
        return json;
    }

    // --- HUD accessors (render thread) ---

    public static boolean ragdolledLive() { return ragdolledLive; }
    public static boolean stunned() { return stunned; }
    public static double playerX() { return playerX; }
    public static double playerY() { return playerY; }
    public static double playerZ() { return playerZ; }
    public static double velX() { return velX; }
    public static double velY() { return velY; }
    public static double velZ() { return velZ; }
    public static String vehicleType() { return vehicleType; }
    public static double vehicleDist() { return vehicleDist; }
    @Nullable public static Vec3 leftHookPos() { return leftHookPos; }
    @Nullable public static Vec3 rightHookPos() { return rightHookPos; }
    @Nullable public static Vec3 anchorPos() { return anchorPos; }
    @Nullable public static Vec3 ragdollWorldPos() { return ragdollWorldPos; }
    public static double ragdollDist() { return ragdollDist; }
}
