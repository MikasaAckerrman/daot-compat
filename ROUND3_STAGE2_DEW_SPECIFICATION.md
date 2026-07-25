# DAOT COMPAT — DEW & GAS SYSTEM SPECIFICATION

**Version:** 2.0 (Updated with anime-accurate mechanics)  
**Last Updated:** 2026-07-25  
**Status:** Complete technical specification ready for implementation

---

## 📖 TABLE OF CONTENTS

1. [Core Principles](#core-principles)
2. [DEW System (Double Space)](#dew-system-double-space)
3. [Reverse DEW System (Double S)](#reverse-dew-system-double-s)
4. [Gas Resource Management](#gas-resource-management)
5. [Rope Interaction](#rope-interaction)
6. [Spark Effects](#spark-effects)
7. [Complete Control Scheme](#complete-control-scheme)
8. [Implementation Details](#implementation-details)
9. [Configuration & Balance](#configuration--balance)

---

## 🎬 CORE PRINCIPLES

### Two Independent Systems

The ODM gear consists of **two independent but complementary systems:**

1. **Rope System (Trosses/Hooks)**
   - Creates anchor points and tension
   - Provides directional control and sustained movement
   - Keybinds: SPACE (reel-in), SHIFT (descend), W (accelerate)

2. **Gas System (DEW)**
   - Provides vectorial impulses (pushes)
   - Works independently of ropes
   - Amplified when ropes are engaged
   - Keybinds: SPACE×2 (forward), S×2 (reverse)

### Why This Design Matters

From anime logic:
- **Without gas:** You're on the ropes, can't move far
- **Without ropes:** Gas gives you mobility but limited control
- **With both:** Maximum power and maneuverability (like Eren's epic scenes)

---

## 🚀 DEW SYSTEM (Double Space)

### Activation Mechanic

**Input:** Two rapid SPACE presses within 0.25–0.35 seconds

**Requirements:**
- ✅ No rope engagement needed
- ✅ Any altitude (ground, air, underwater)
- ✅ Any view angle (up, down, forward)
- ✅ Requires gas remaining in tank (≥8%)

**Impossible if:**
- ❌ No gas remaining
- ❌ In cooldown period (see below)
- ❌ High-fall damage reduction active

### Impulse Direction

```
Base vector = Player's look direction
             + 15–20° upward tilt
             + Small component of current velocity (preserves momentum)
             
Result: Natural "thrust forward and up" that feels like anime propulsion
```

### Impulse Strength Factors

**Base Formula:**
```
impulse_strength = BASE_IMPULSE × rope_multiplier × altitude_multiplier × speed_multiplier

Where:
  BASE_IMPULSE = 2.5 (tunable constant)
  rope_multiplier = see table below
  altitude_multiplier = 0.85 if on ground, 1.0 if airborne
  speed_multiplier = 1.0 + (current_horizontal_speed / max_speed) × 0.3
```

| Rope State | rope_multiplier | Description |
|------------|-----------------|-------------|
| No ropes engaged | 1.0x | Pure gas impulse, weak |
| Ropes weak (low tension) | 1.3–1.5x | Combined force, moderate |
| Ropes strong (high tension) | 1.8–2.3x | Powerful combined ryvok, anime-style |
| Ropes recently released | 0.9x | Brief penalty as ropes settle |

**Altitude Modifiers:**
- On solid ground: 0.85x (slightly weaker, prevents infinite jumping)
- Airborne: 1.0x (full strength)
- In water: 0.6x (resistance) + brief slowdown

**Speed Modifiers:**
- Stationary: 1.0x base
- Already moving at high speed: +0.2–0.4x bonus
  - Encourages chaining DEW in same direction for speed buildup

### Gas Consumption

| Situation | Gas Cost |
|-----------|----------|
| Base DEW impulse | 10% |
| DEW with strong rope tension | 12–15% |
| DEW on ground | 8% (cheaper due to weaker multiplier) |

**Example:** Tank = 100%
- DEW costs 10% → Tank = 90%
- Rope-assisted DEW costs 12% → Tank = 78%
- Can do ~8–10 DEW per full tank before needing to recharge

---

## 🔄 REVERSE DEW SYSTEM (Double S)

### Activation Mechanic

**Input:** Two rapid S presses within 0.25–0.35 seconds  
*(S = backward movement key in Minecraft controls)*

**Identical requirements to DEW:**
- ✅ No rope needed
- ✅ Any altitude
- ✅ Requires gas (≥6%)

### Impulse Direction

```
Base vector = Opposite to player's look direction
             + 15–20° upward tilt
             + Component opposite to current velocity
             
Effect: "Evasion thrust" — push backward, slightly up, with rotation
```

### Use Cases (from anime)

1. **Emergency brake** — Blast backward to stop forward momentum
2. **Wall evasion** — Quick sideways push when near obstacles
3. **Rope rebound** — After DEW forward, Reverse DEW back for pendulum swinging
4. **Directional correction** — Fix trajectory mid-air

### Strength Multiplier

Reverse DEW is slightly weaker than forward DEW:

```
reverse_impulse = dew_impulse × 0.8–0.9

Reason: Backward movement is more defensive, less aggressive
```

### Gas Consumption

| Situation | Gas Cost |
|-----------|----------|
| Base Reverse DEW | 8% |
| With rope tension | 10–12% |
| On ground | 6% |

**Slightly cheaper than DEW** to encourage use for evasion and control.

---

## ⛽ GAS RESOURCE MANAGEMENT

### Tank Mechanics

**Max capacity:** 100% (full tank)  
**Visual indicator:** HUD bar (upper right, similar to hunger bar)

```
┌─────────────────────────────────────────┐
│ GAS: ████████████░░░░░░░░░░░░░░░░░░ 65% │  ← Visual indicator
│      [████████████][remaining empty]    │
└─────────────────────────────────────────┘
```

**Color coding:**
- 🟢 Green: 75–100% (abundant)
- 🟡 Yellow: 50–74% (moderate)
- 🔴 Red: 0–49% (critical, blink warning)

### Regeneration

| Condition | Regen Rate | Notes |
|-----------|------------|-------|
| Idle (grounded) | 5% per second | Standing still on ground |
| Walking/moving | 3% per second | Normal movement, less regen |
| Airborne | 1% per second | In air, ropes slack — minimal regen |
| On ropes (reel-in) | 4% per second | Engaged with ropes — moderate regen |
| Damaged | 0% per second | Under attack, no regen |

**Rationale:** Forces player to balance between aggression and resource management.

### Tank Depletion

If tank reaches 0%:
- ✅ Normal movement still works
- ✅ Ropes still work
- ❌ DEW disabled
- ❌ Reverse DEW disabled
- 🔄 Gas regenerates after 2-3 seconds

---

## 🔗 ROPE INTERACTION (Updated)

### Single SPACE — Reel-In (Rope Engagement)

**Activation:** SPACE pressed once, held  
**Requirement:** Rope hook engaged  
**Effect:** Pulls player toward anchor point with constant/increasing force

**Physics:**
```
reel_force = BASE_REEL_FORCE × tension_multiplier

As rope tension increases:
  0–20% tension: 1.0x force (weak pull)
  20–50% tension: 1.2x force (moderate)
  50–100% tension: 1.5x force (strong pull)
```

### SHIFT — Descend/Release (Rope Slack Control)

**Activation:** SHIFT pressed while reeling  
**Effect:** Gradually reduces rope tension, lowers player  
**Physics:** Opposite of reel-in, smooth descent

---

### DEW + Rope Interaction (CRITICAL)

#### Before DEW: Rope Tension State

When player does DEW, check rope tension:

```javascript
if (has_rope_engagement) {
    // Rope is already pulling/holding
    tension = calculate_rope_tension();  // 0.0 to 1.0
    rope_multiplier = 1.0 + (tension × 1.3);  // 1.0 to 2.3x
} else {
    rope_multiplier = 1.0;  // No rope bonus
}

dew_impulse = base_impulse × rope_multiplier;
```

#### At Impact: Rope Load

Rope experiences sudden tension increase:

```
When DEW fires with rope engaged:
  rope_tension += 0.3 (sudden shock)
  
Visual effect:
  Rope briefly tightens/glows
  Small particle burst at anchor point
  Sound: "whoosh" of rope tensioning
```

#### After DEW: Rope Oscillation

```
Over next 0.6–0.8 seconds:
  rope_tension decays from peak back to baseline
  
Effect: Player bounces slightly as rope "settles"
         Creates natural swinging motion
```

**Example sequence:**
```
1. Player reel-in on rope (tension = 0.7)
2. Hits DEW → impulse = base × 2.0 (from rope bonus)
3. Gets pushed forward hard
4. Rope stretches, tension spikes
5. Rope oscillates back (natural bounce)
6. Player swings in pendulum motion
```

---

## ✨ SPARK EFFECTS

### When Sparks Appear

**Condition:** Player touches ground WITH high horizontal speed

```
if (is_on_ground && horizontal_speed >= SPARK_THRESHOLD) {
    spawn_sparks();
}

SPARK_THRESHOLD = 12–15 blocks/sec  (tunable)
```

**Why:** Indicates player entering "high-speed slide mode" with reduced friction.

### Spark Visual Design

**Particle Type:** Custom "electric sparks" (or vanilla flame/smoke hybrid)

**Origin:** Bottom of player model (under feet)

**Color Gradient:**
- 🟠 Orange → Yellow (like metal grinding)
- Optional glow effect (subtle)

**Emission Pattern:**
- Multiple particles per tick
- Spread in cone backward (relative to movement direction)
- Random velocity to create "scatter" effect

**Particle Count:**
```
Spark quantity = (horizontal_speed / max_speed) × base_count

Example:
  speed = 12 blocks/sec, max = 20, base = 8
  → 12/20 × 8 = ~5 particles per tick
  
  speed = 19 blocks/sec
  → 19/20 × 8 = ~7 particles per tick
```

### Spark Lifetime & Fade

```
Particle lifetime = 0.4–0.6 seconds
Fade out: Exponential decay
Trail effect: Particles spread backward as player moves forward
```

### Interaction with Block Type

**Optional enhancement:** Different spark colors by material

```
if (block_below == metal/ore) {
    spark_color = BRIGHT_ORANGE;
} else if (block_below == stone) {
    spark_color = GRAY;
} else if (block_below == wood) {
    spark_color = RED_ORANGE;
} else {
    spark_color = DEFAULT_YELLOW;
}
```

### Sound Effect

**Trigger:** Same as sparks (on ground, high speed)  
**Sound:** Short metallic scrape/screech (~0.2 sec)  
**Volume:** Scales with speed

```
sound_volume = 0.3 + (horizontal_speed / max_speed) × 0.7
```

---

## 🎮 COMPLETE CONTROL SCHEME

### Control Mapping

| Action | Keybind | Input Type | Condition | Effect |
|--------|---------|-----------|-----------|--------|
| **Reel-In** | SPACE (1×) | Hold | Rope engaged | Pull toward anchor |
| **Descend** | SHIFT | Hold | Rope engaged | Lower/release rope |
| **Accelerate** | W | Hold | Moving | Speed up movement |
| **DEW Thrust** | SPACE (2×) | Double-tap | <0.35s interval | Forward impulse |
| **Reverse DEW** | S (2×) | Double-tap | <0.35s interval | Backward impulse |
| **Swap Hotbar** | *Custom* | Key press | Always | Change inventory |

### State Diagram

```
                    ┌─────────────────────┐
                    │   NOT GRAPPLED      │
                    │                     │
                    │ SPACE (1×) or       │
                    │ Click nearby rope   │
                    └──────────┬──────────┘
                               │
                               ↓
                    ┌─────────────────────┐
                    │   ROPE ENGAGED      │
                    │   (Reel-in state)   │
                    │                     │
                    │ SPACE (hold): Pull  │
                    │ SHIFT (hold): Lower │
                    │ W (hold): Accelerate│
                    └──────────┬──────────┘
                       ↑       │      ↑
                       │       │      │
        SPACE (2×)─────┤       │      └─── SHIFT (release)
        within 0.35s   │       │            → Rope slack
        + gas ≥8%      │       │
                       │       │
                       │  ┌────↓──────┐
                       └──│ DEW ACTIVE │
                          │           │
                          │ Impulse   │
                          │ applied   │
                          │ Gas –8–12%│
                          └──────┬────┘
                                 │
                         Cooldown 0.4–0.6s
                                 │
                                 ↓
                        Ready for next DEW
```

---

## 🔧 IMPLEMENTATION DETAILS

### 1. Double-Tap Detection System

**File:** `src/main/java/com/armorberserk/daotcompat/input/DoubleTapDetector.java`

```java
public class DoubleTapDetector {
    private static final double DOUBLE_TAP_THRESHOLD_MS = 350;  // 0.35 seconds
    
    private static long lastSpacePressTime = -1;
    private static long lastSPressTime = -1;
    
    public static boolean detectDoubleTapSpace() {
        long now = Minecraft.getInstance().level.getGameTime() * 50; // Convert ticks to ms
        
        if (GrappleKeybinds.PULL_ROPE.consumeClick()) {  // SPACE pressed
            if (now - lastSpacePressTime < DOUBLE_TAP_THRESHOLD_MS) {
                // Double-tap detected!
                lastSpacePressTime = -1;  // Reset
                return true;
            }
            lastSpacePressTime = now;
        }
        return false;
    }
    
    public static boolean detectDoubleTapS() {
        long now = Minecraft.getInstance().level.getGameTime() * 50;
        
        if (KeybindRegistry.REVERSE_DEW.consumeClick()) {  // S pressed
            if (now - lastSPressTime < DOUBLE_TAP_THRESHOLD_MS) {
                lastSPressTime = -1;
                return true;
            }
            lastSPressTime = now;
        }
        return false;
    }
}
```

### 2. Gas Management System

**File:** `src/main/java/com/armorberserk/daotcompat/gas/GasManager.java`

```java
public class GasManager {
    private static final float MAX_GAS = 100.0f;
    private static float currentGas = 100.0f;
    
    // Consumption rates
    private static final float DEW_COST = 10.0f;
    private static final float DEW_ROPE_BONUS_COST = 12.0f;
    private static final float REVERSE_DEW_COST = 8.0f;
    
    // Regeneration rates (% per second)
    private static final float REGEN_IDLE = 5.0f;
    private static final float REGEN_WALKING = 3.0f;
    private static final float REGEN_AIRBORNE = 1.0f;
    private static final float REGEN_REEL_IN = 4.0f;
    
    public static float getCurrentGas() {
        return currentGas;
    }
    
    public static boolean canUseDEW() {
        return currentGas >= DEW_COST && !isInCooldown();
    }
    
    public static void consumeGasForDEW(float ropeMultiplier) {
        float cost = (ropeMultiplier > 1.0f) ? DEW_ROPE_BONUS_COST : DEW_COST;
        currentGas = Math.max(0, currentGas - cost);
    }
    
    public static void consumeGasForReverseDEW() {
        currentGas = Math.max(0, currentGas - REVERSE_DEW_COST);
    }
    
    public static void regenerateGas(LocalPlayer player) {
        float regenRate = REGEN_IDLE;
        
        if (!player.isOnGround()) {
            regenRate = REGEN_AIRBORNE;
        } else if (player.getDeltaMovement().horizontalDistanceSqr() > 0) {
            regenRate = REGEN_WALKING;
        }
        
        if (DynamicHookData.get(player) != null && 
            GrappleStateManager.isPullingRope()) {
            regenRate = REGEN_REEL_IN;
        }
        
        currentGas = Math.min(MAX_GAS, currentGas + (regenRate / 20.0f));  // 20 ticks/sec
    }
}
```

### 3. DEW Impulse Calculator

**File:** `src/main/java/com/armorberserk/daotcompat/physics/DEWImpulseCalculator.java`

```java
public class DEWImpulseCalculator {
    private static final float BASE_IMPULSE = 2.5f;
    
    public static Vec3 calculateDEWImpulse(LocalPlayer player, boolean isReverse) {
        // Get look direction
        Vec3 lookDir = player.getLookAngle();  // Player's facing direction
        
        // Add upward tilt (15–20°)
        float tiltAngle = 17.5f * (Math.PI / 180.0f);  // Convert to radians
        Vec3 tiltedDir = lookDir
            .add(0, Math.sin(tiltAngle), 0)
            .normalize();
        
        // Add velocity component (momentum preservation)
        Vec3 currentVel = player.getDeltaMovement();
        Vec3 horizontalVel = new Vec3(currentVel.x, 0, currentVel.z);
        
        Vec3 impulseDir = tiltedDir
            .scale(0.8)
            .add(horizontalVel.normalize().scale(0.2));
        
        // Calculate multipliers
        float ropeMultiplier = calculateRopeMultiplier(player);
        float altitudeMultiplier = player.isOnGround() ? 0.85f : 1.0f;
        float speedMultiplier = calculateSpeedMultiplier(player, horizontalVel);
        
        // Final strength
        float strength = BASE_IMPULSE * ropeMultiplier * altitudeMultiplier * speedMultiplier;
        
        // Reverse DEW is weaker
        if (isReverse) {
            strength *= 0.85f;
            impulseDir = impulseDir.scale(-1);  // Reverse direction
        }
        
        return impulseDir.normalize().scale(strength);
    }
    
    private static float calculateRopeMultiplier(LocalPlayer player) {
        DynamicHookData hookData = DynamicHookData.get(player);
        if (hookData == null || !hookData.isValid()) {
            return 1.0f;  // No rope
        }
        
        float tension = calculateRopeTension(player, hookData);
        
        if (tension < 0.2f) return 1.0f;
        if (tension < 0.5f) return 1.4f;
        return 1.0f + (tension * 1.3f);  // Up to 2.3x
    }
    
    private static float calculateRopeTension(LocalPlayer player, DynamicHookData hookData) {
        // Tension based on angle to anchor + pull force
        Vec3 toAnchor = hookData.anchorPos().subtract(player.position());
        Vec3 lookDir = player.getLookAngle();
        
        // Cosine similarity (0 = perpendicular, 1 = aligned)
        float alignment = (float) toAnchor.normalize().dot(lookDir);
        
        // Simulate tension (0 to 1)
        return Math.max(0, Math.min(1, alignment * 1.2f));
    }
    
    private static float calculateSpeedMultiplier(LocalPlayer player, Vec3 horizontalVel) {
        float currentSpeed = (float) horizontalVel.length();
        float maxSpeed = 20.0f;  // Tunable
        
        return 1.0f + (currentSpeed / maxSpeed) * 0.3f;
    }
}
```

### 4. Spark Effect System

**File:** `src/main/java/com/armorberserk/daotcompat/render/SparkEffectRenderer.java`

```java
public class SparkEffectRenderer {
    private static final double SPARK_THRESHOLD = 12.0;  // blocks/sec
    
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.isOnGround()) return;
        
        double horizontalSpeed = Math.sqrt(
            player.getDeltaMovement().x * player.getDeltaMovement().x +
            player.getDeltaMovement().z * player.getDeltaMovement().z
        );
        
        if (horizontalSpeed >= SPARK_THRESHOLD) {
            spawnSparks(player, horizontalSpeed);
        }
    }
    
    private static void spawnSparks(LocalPlayer player, double speed) {
        double sparkIntensity = speed / 20.0;  // Normalized to 0–1
        int particleCount = (int) (8 * sparkIntensity);
        
        for (int i = 0; i < particleCount; i++) {
            Vec3 particlePos = player.position()
                .add(
                    (Math.random() - 0.5) * 0.5,  // X spread
                    0.1,                           // Y (at feet)
                    (Math.random() - 0.5) * 0.5   // Z spread
                );
            
            Vec3 particleVel = player.getDeltaMovement()
                .scale(-0.3)  // Backward relative to movement
                .add(
                    (Math.random() - 0.5) * 0.2,
                    Math.random() * 0.3,  // Some upward
                    (Math.random() - 0.5) * 0.2
                );
            
            // Spawn orange/yellow spark particle
            Minecraft.getInstance().levelRenderer.addParticle(
                new SparkParticleOptions(
                    0xFF8800,  // Orange color
                    (float) (0.4 + Math.random() * 0.2)  // Scale
                ),
                particlePos.x, particlePos.y, particlePos.z,
                particleVel.x, particleVel.y, particleVel.z
            );
        }
        
        // Play sound
        if (speed >= SPARK_THRESHOLD) {
            player.playSound(
                SoundEvents.GRINDSTONE_USE,
                0.5f,
                0.8f + (float) Math.random() * 0.4f  // Pitch variation
            );
        }
    }
}
```

### 5. Cooldown System

**File:** `src/main/java/com/armorberserk/daotcompat/physics/DEWCooldownManager.java`

```java
public class DEWCooldownManager {
    private static final int COOLDOWN_TICKS = 8;  // 0.4 seconds (20 ticks/sec)
    private static int dewCooldownTicks = 0;
    
    public static boolean isInCooldown() {
        return dewCooldownTicks > 0;
    }
    
    public static void activateCooldown() {
        dewCooldownTicks = COOLDOWN_TICKS;
    }
    
    @SubscribeEvent
    public static void onTick(ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && dewCooldownTicks > 0) {
            dewCooldownTicks--;
        }
    }
}
```

---

## 📊 CONFIGURATION & BALANCE

### Tunable Constants

Create `src/main/resources/config/daotcompat-gas.toml`:

```toml
[gas]
# Tank capacity
max_gas = 100.0

# Consumption rates (percent of tank)
dew_cost = 10.0
dew_rope_bonus_cost = 12.0
reverse_dew_cost = 8.0

[regeneration]
# Percent per second
idle = 5.0
walking = 3.0
airborne = 1.0
reel_in = 4.0

[dew_mechanics]
# Impulse strength
base_impulse = 2.5

# Rope multipliers
rope_no_tension = 1.0
rope_weak_tension = 1.3
rope_medium_tension = 1.6
rope_strong_tension = 2.3

# Altitude modifiers
altitude_on_ground = 0.85
altitude_airborne = 1.0
altitude_water = 0.6

# Speed bonuses
speed_multiplier_max = 0.4

# Angle for upward tilt (degrees)
upward_tilt_angle = 17.5

# Cooldown between DEW uses (milliseconds)
cooldown_ms = 400

# Reverse DEW strength multiplier
reverse_dew_multiplier = 0.85

[sparks]
# Spark threshold (blocks/sec)
horizontal_speed_threshold = 12.0

# Visual
particle_count_base = 8
particle_lifetime_ticks = 12
particle_spread = 0.5

# Sound
enable_spark_sound = true
sound_volume_base = 0.5
sound_volume_max = 0.9
```

### Balance Rationale

**Why these values?**

1. **Gas costs (8–12%):** Prevents infinite DEW spam
   - Full tank = ~8–10 DEWs before needing regen
   - Encourages strategic gas management

2. **Regeneration rates:** Incentivizes different playstyles
   - Idle regen (5%) favors defensive positioning
   - Walking regen (3%) balances mobility and resource
   - Reel-in regen (4%) rewards rope engagement

3. **Rope multipliers (1.0–2.3x):** Makes rope engagement crucial
   - Pure gas = weak (1.0x)
   - Roped engagement = strong (up to 2.3x)
   - Encourages combining systems

4. **Spark threshold (12 blocks/sec):** Visual feedback at "interesting" speeds
   - Not triggered at casual walking
   - Triggered during combat/high-speed movement
   - Indicates slide mode activation

---

## 🎯 TESTING CHECKLIST

- [ ] Double-tap SPACE within 0.35s triggers DEW
- [ ] DEW consumes correct gas amount (10% base, 12% with rope)
- [ ] DEW impulse has correct upward tilt (~17.5°)
- [ ] DEW strength scales with rope tension
- [ ] DEW strength scales with current speed
- [ ] DEW is weaker on ground (0.85x)
- [ ] Double-tap S triggers Reverse DEW
- [ ] Reverse DEW is 0.85x strength of DEW
- [ ] Gas regenerates at correct rates (idle/walking/airborne/reel-in)
- [ ] Gas bar appears in HUD (upper right)
- [ ] Gas bar colors change (green → yellow → red)
- [ ] Sparks appear when horizontal speed ≥12 blocks/sec on ground
- [ ] Sparks are orange/yellow colored
- [ ] Sparks fade out smoothly
- [ ] Spark sound plays at appropriate volume
- [ ] DEW has 0.4s cooldown between uses
- [ ] Cannot use DEW if gas < 8%
- [ ] DEW can be used while on ropes (boosted)
- [ ] DEW can be used without ropes (weaker)

---

## 🔗 REFERENCES

### Related Files
- `ROUND3_STAGE2_DEV_PLAN.md` — Full implementation plan
- `STAGE2_SETUP_GUIDE.md` — Development environment
- `PLAN.txt` — Stage 1 handoff notes

### Physics References
- Look angle calculation: Minecraft `Vec3#getDirection()`
- Particle system: Minecraft `ParticleEngine`
- Cooldown pattern: Minecraft keybind system (`consumeClick()`)

---

## 📝 NOTES FOR FUTURE PHASES

### Phase 3 Integration
When implementing rope physics (Sable `RopePhysicsObject`):
- Update `calculateRopeTension()` to use actual rope tension
- Add rope oscillation effect after DEW impact
- Visual rope glow when rope is under tension

### Phase 5 Enhancement
When updating rendering:
- Add rope tension visualization (thickness increase)
- Add DEW impact particles at rope anchor point
- Add velocity vector indicator during high-speed movement

### Phase 6 Balancing
Real playtesting will refine:
- `BASE_IMPULSE` constant (currently 2.5)
- `SPARK_THRESHOLD` (currently 12 blocks/sec)
- `COOLDOWN_TICKS` (currently 8 = 0.4s)
- All gas consumption rates

---

**Created by:** AI Agent (2026-07-25)  
**Status:** Complete specification, ready for Phase 1 implementation  
**Next:** Add this as PHASE 4A in DEV_PLAN (after rope basics work)
