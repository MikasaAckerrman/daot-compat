package com.armorberserk.daotcompat.gas;

import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Gas tank management for DEW (double-tap) mechanics.
 * Tank: 0-100%
 * Consumption: DEW 10%, Reverse DEW 8%
 * Regen: idle 5%/s, walking 3%/s, airborne 1%/s, reel-in 4%/s
 */
@OnlyIn(Dist.CLIENT)
public class GasManager {
    
    private static final float MAX_GAS = 100.0f;
    private static float currentGas = 100.0f;
    
    // Consumption
    private static final float DEW_COST = 10.0f;
    private static final float REVERSE_DEW_COST = 8.0f;
    
    // Regen rates (% per second)
    private static final float REGEN_IDLE = 5.0f;
    private static final float REGEN_WALKING = 3.0f;
    private static final float REGEN_AIRBORNE = 1.0f;
    private static final float REGEN_REEL_IN = 4.0f;
    
    public static float getCurrentGas() {
        return currentGas;
    }
    
    public static float getGasPercent() {
        return (currentGas / MAX_GAS) * 100.0f;
    }
    
    public static boolean canUseDEW() {
        return currentGas >= DEW_COST;
    }
    
    public static boolean canUseReverseDEW() {
        return currentGas >= REVERSE_DEW_COST;
    }
    
    public static void consumeForDEW() {
        currentGas = Math.max(0, currentGas - DEW_COST);
    }
    
    public static void consumeForReverseDEW() {
        currentGas = Math.max(0, currentGas - REVERSE_DEW_COST);
    }
    
    public static void tick(LocalPlayer player) {
        if (player == null) return;
        
        float regenRate = REGEN_IDLE;
        
        if (!player.onGround()) {
            regenRate = REGEN_AIRBORNE;
        } else {
            double horSpeed = Math.sqrt(
                player.getDeltaMovement().x * player.getDeltaMovement().x +
                player.getDeltaMovement().z * player.getDeltaMovement().z
            );
            if (horSpeed > 0.01) {
                regenRate = REGEN_WALKING;
            }
        }
        
        currentGas = Math.min(MAX_GAS, currentGas + (regenRate / 20.0f));  // 20 ticks/sec
    }
    
    public static void reset() {
        currentGas = MAX_GAS;
    }
}
