/*
 * DAOT Aeronautics Compat
 * Copyright (c) 2026 armorberserk. All rights reserved.
 */
package com.armorberserk.daotcompat.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Particle effects when rope wraps around block edges.
 * 
 * Visually indicates rope bending/wrapping for better feedback.
 * Shows dust particles at wrap points for immersion.
 */
@OnlyIn(Dist.CLIENT)
public class RopeWrapParticles {
    
    private static final double WRAP_PARTICLE_THRESHOLD = 0.5;  // meters per tick
    private static long lastParticleTime = 0;
    private static final long PARTICLE_COOLDOWN = 100;  // ms between particle spawns
    
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.level() == null) return;
        
        // Check if player is moving fast (indicates rope wrapping/pendulum)
        Vec3 velocity = player.getDeltaMovement();
        double speed = velocity.length();
        
        if (speed >= WRAP_PARTICLE_THRESHOLD) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastParticleTime >= PARTICLE_COOLDOWN) {
                spawnWrapParticles(player, speed);
                lastParticleTime = currentTime;
            }
        }
    }
    
    /**
     * Spawn particle effects at player position (indicating rope wrapping).
     */
    private static void spawnWrapParticles(LocalPlayer player, double speed) {
        Minecraft minecraft = Minecraft.getInstance();
        ParticleEngine particleEngine = minecraft.particleEngine;
        
        // Intensity based on speed
        double intensity = Math.min(1.0, speed / 20.0);
        int particleCount = (int) (3 * intensity);
        
        Vec3 playerPos = player.position();
        
        for (int i = 0; i < particleCount; i++) {
            // Random offset around player
            double offsetX = (Math.random() - 0.5) * 0.8;
            double offsetY = (Math.random() - 0.5) * 0.8;
            double offsetZ = (Math.random() - 0.5) * 0.8;
            
            // Random velocity
            double velX = (Math.random() - 0.5) * 0.3;
            double velY = Math.random() * 0.2;
            double velZ = (Math.random() - 0.5) * 0.3;
            
            // Spawn dust particle
            particleEngine.createParticle(
                ParticleTypes.CLOUD,
                playerPos.x + offsetX, playerPos.y + offsetY, playerPos.z + offsetZ,
                velX, velY, velZ
            );
        }
    }
    
    /**
     * Spawn sparkle effect at wrap point (when rope bends around corner).
     */
    public static void spawnWrapSparkle(Vec3 wrapPoint) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.particleEngine == null) return;
        
        ParticleEngine particleEngine = minecraft.particleEngine;
        
        // Burst of small sparkles at wrap point
        for (int i = 0; i < 5; i++) {
            double velX = (Math.random() - 0.5) * 0.5;
            double velY = Math.random() * 0.3;
            double velZ = (Math.random() - 0.5) * 0.5;
            
            particleEngine.createParticle(
                ParticleTypes.CRIT,
                wrapPoint.x, wrapPoint.y, wrapPoint.z,
                velX, velY, velZ
            );
        }
    }
}
