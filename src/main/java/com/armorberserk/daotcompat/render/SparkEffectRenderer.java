package com.armorberserk.daotcompat.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Spark particles + sound when sliding at high speed on ground.
 * Threshold: 12 blocks/sec horizontal speed
 */
@OnlyIn(Dist.CLIENT)
public class SparkEffectRenderer {
    
    private static final double SPARK_THRESHOLD = 12.0;
    private static final int PARTICLE_COUNT_BASE = 8;
    
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.onGround()) return;
        
        double horizontalSpeed = Math.sqrt(
            player.getDeltaMovement().x * player.getDeltaMovement().x +
            player.getDeltaMovement().z * player.getDeltaMovement().z
        );
        
        if (horizontalSpeed >= SPARK_THRESHOLD) {
            spawnSparks(player, horizontalSpeed);
        }
    }
    
    private static void spawnSparks(LocalPlayer player, double speed) {
        Minecraft minecraft = Minecraft.getInstance();
        ParticleEngine particleEngine = minecraft.particleEngine;
        double intensity = Math.min(1.0, speed / 20.0);
        int particleCount = (int) (PARTICLE_COUNT_BASE * intensity);
        
        for (int i = 0; i < particleCount; i++) {
            Vec3 pos = player.position().add(
                (Math.random() - 0.5) * 0.5,
                0.1,
                (Math.random() - 0.5) * 0.5
            );
            
            Vec3 vel = player.getDeltaMovement().scale(-0.3).add(
                (Math.random() - 0.5) * 0.2,
                Math.random() * 0.3,
                (Math.random() - 0.5) * 0.2
            );
            
            // Add flame particles
            particleEngine.createParticle(
                ParticleTypes.FLAME,
                pos.x, pos.y, pos.z,
                vel.x, vel.y, vel.z
            );
        }
        
        // Play sound
        if (speed >= SPARK_THRESHOLD) {
            player.playSound(
                SoundEvents.GRINDSTONE_USE,
                0.5f,
                0.8f + (float) Math.random() * 0.4f
            );
        }
    }
}
