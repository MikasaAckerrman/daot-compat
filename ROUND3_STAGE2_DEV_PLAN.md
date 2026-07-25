# DAOT COMPAT — ROUND 3 STAGE 2 DEVELOPMENT PLAN

**Status:** Planning / Architecture Phase  
**Target Version:** daotcompat-1.1.0  
**Branch:** `feature/stage2-keybind-system`  
**Last Updated:** 2026-07-25  

---

## 📋 EXECUTIVE SUMMARY

This stage completely replaces the current automatic grappling system with an **anime-logic keybind-based system**. The player must actively control rope engagement via keybinds instead of automatic gravitational attraction.

### Key Changes from Stage 1:
- ❌ Remove: Automatic rope pull (AOT's default behavior)
- ✅ Add: Keybind-controlled rope engagement (SPACE, W, SHIFT)
- ✅ Add: Rope physics compatible with GrappleHook mod
- ✅ Add: Max rope length limits (balancing mechanic)
- ✅ Add: Hotbar switching while grappling
- ✅ Implement: Anime-style acceleration logic

---

## ⚠️ IMPORTANT: DEW & GAS SYSTEM ADDED

**New document:** `ROUND3_STAGE2_DEW_SPECIFICATION.md` contains complete anime-accurate DEW mechanics:

- ✅ **DEW (Double Space)** — Gas-powered forward impulse
- ✅ **Reverse DEW (Double S)** — Gas-powered backward impulse  
- ✅ **Gas Management** — Resource tank with regeneration/consumption
- ✅ **Rope Interaction** — DEW amplified by rope tension
- ✅ **Spark Effects** — Visual feedback when high-speed sliding
- ✅ **Balance Formulas** — Tunable constants with rationale
- ✅ **Complete Implementation Guide** — Java code templates ready to use

**Read before starting Phase 1:** This significantly expands the system scope but is essential for anime-accurate ODM mechanics.

---

## 🎮 KEYBIND SYSTEM SPECIFICATION

### Default Keybinds (Configurable)

| Keybind | Default Key | Function | Behavior |
|---------|------------|----------|----------|
| **PULL_ROPE** | SPACE | Engage rope / pull toward anchor | Player pulls toward grapple point |
| **ACCELERATE** | W | Move forward along rope path | Only works AFTER PULL_ROPE is active |
| **DESCEND_ROPE** | SHIFT | Lower / slide down rope | Decreases altitude while grappling |
| **SWAP_HOTBAR** | *None* | Switch hotbar slots while grappling | Player can grab items without releasing rope |

### Keybind Control Flow

```
STATE: Player grappled to anchor point

┌─ NOT holding PULL_ROPE
│  └─ Player falls (gravity applies, rope becomes "slack")
│     └─ If PULL_ROPE pressed → go to PULLING
│
├─ Holding PULL_ROPE
│  ├─ W (forward) pressed?
│  │  └─ YES → apply acceleration toward target
│  │  └─ NO → maintain current velocity (inertia only)
│  │
│  ├─ SHIFT (descend) pressed?
│  │  └─ YES → apply downward velocity (-Y direction)
│  │  └─ NO → stay at current altitude
│  │
│  └─ SWAP_HOTBAR binds?
│     └─ YES → switch inventory slots
│     └─ Player velocity/grapple state UNCHANGED
│
└─ Rope detaches if:
   └─ Max length exceeded OR anchor destroyed OR dimension changed
```

### Anime Logic - Acceleration Requirement

**Current (wrong):**
```
Player holds W → accelerates immediately
```

**New (anime-correct):**
```
Step 1: Player presses SPACE (PULL_ROPE)
        └─ Rope engages, player pulls toward anchor
        
Step 2: Player holds W (ACCELERATE) WHILE holding SPACE
        └─ Player builds speed along rope trajectory
        └─ If player releases SPACE first: W does nothing, rope becomes slack
        └─ Result: Can't "gun and run" without active rope engagement
```

---

## 🏗️ ARCHITECTURE CHANGES

### Current Architecture (Stage 1)
```
AOT (automatic pull every tick)
  ↓
ReelControl (button-based dampening of pull)
  └─ Subtracts velocity if button not pressed
```

**Problem:** AOT provides the pull; we only subtract it.  
**Solution:** Replace with explicit pull on demand.

### New Architecture (Stage 2)

```
┌─ KeybindListener (ClientTickEvent.Post)
│  ├─ PULL_ROPE pressed? → Set "ropeEngaged = true"
│  ├─ PULL_ROPE released? → Set "ropeEngaged = false"
│  ├─ ACCELERATE pressed? → Set "accelerating = true"
│  └─ DESCEND_ROPE pressed? → Set "descending = true"
│
├─ GrapplePhysicsController
│  ├─ If ropeEngaged:
│  │  ├─ Calculate direction to anchor
│  │  ├─ If accelerating → apply force toward anchor
│  │  ├─ If descending → apply downward force
│  │  └─ Apply rope tension (prevent jerky motion)
│  │
│  └─ If NOT ropeEngaged:
│     └─ Apply gravity (player falls)
│
└─ RopePhysicsRenderer
   ├─ Render rope from player hand to anchor
   ├─ Apply sag/bend physics (via Sable RopePhysicsObject)
   └─ Wrap rope around obstacles (block collision)
```

---

## 📝 IMPLEMENTATION PHASES

### PHASE 1: Core Keybind Infrastructure (Priority: CRITICAL)

**Duration:** ~2-3 days  
**Deliverable:** Functional keybind system with state tracking

#### 1.1 Create Keybind Registry
- **File:** `src/main/java/com/armorberserk/daotcompat/input/GrappleKeybinds.java`
- **Responsibility:**
  - Define `PULL_ROPE`, `ACCELERATE`, `DESCEND_ROPE`, `SWAP_HOTBAR`
  - Register with Minecraft's keybind system
  - Handle default bindings + user customization
  - Store in config file for persistence

```java
public class GrappleKeybinds {
    public static final KeyMapping PULL_ROPE = 
        new KeyMapping("key.daotcompat.pull_rope", 
                      InputConstants.KEY_SPACE, 
                      "key.categories.daotcompat");
    
    public static final KeyMapping ACCELERATE = 
        new KeyMapping("key.daotcompat.accelerate", 
                      InputConstants.KEY_W, 
                      "key.categories.daotcompat");
    
    // ... DESCEND_ROPE, SWAP_HOTBAR
    
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(PULL_ROPE);
        event.register(ACCELERATE);
        // ... register others
    }
}
```

#### 1.2 Create Grapple State Manager
- **File:** `src/main/java/com/armorberserk/daotcompat/input/GrappleStateManager.java`
- **Responsibility:**
  - Track active keybinds in real-time
  - Maintain flags: `isPullingRope`, `isAccelerating`, `isDescending`
  - Handle state transitions
  - Thread-safe for client/server sync

```java
public class GrappleStateManager {
    private static boolean isPullingRope = false;
    private static boolean isAccelerating = false;
    private static boolean isDescending = false;
    private static int ropePullStartTick = -1;  // For "must press SPACE first" logic
    
    public static void updateState(LocalPlayer player) {
        isPullingRope = GrappleKeybinds.PULL_ROPE.isDown();
        isAccelerating = GrappleKeybinds.ACCELERATE.isDown() && isPullingRope;
        isDescending = GrappleKeybinds.DESCEND_ROPE.isDown() && isPullingRope;
        
        if (isPullingRope && ropePullStartTick == -1) {
            ropePullStartTick = player.tickCount;  // Mark when rope first engaged
        } else if (!isPullingRope) {
            ropePullStartTick = -1;  // Reset when released
        }
    }
    
    public static boolean canAccelerate() {
        return isAccelerating && ropePullStartTick >= 0;  // Must have SPACE pressed first
    }
}
```

#### 1.3 Event Listener for Keybind Updates
- **File:** `src/main/java/com/armorberserk/daotcompat/input/KeybindEventListener.java`
- **Hook into:** `ClientTickEvent.Post` (after all keybinds polled)
- **Responsibility:**
  - Call `GrappleStateManager.updateState()` every client tick
  - Dispatch state change events (for hotbar swap logic)

---

### PHASE 2: Rope Engagement & Pull Physics (Priority: CRITICAL)

**Duration:** ~3-4 days  
**Deliverable:** Player can pull toward anchor via SPACE; W accelerates

#### 2.1 Replace ReelControl with GrapplePhysicsController
- **File:** `src/main/java/com/armorberserk/daotcompat/physics/GrapplePhysicsController.java`
- **Remove:** Old `ReelControl.java` (automatic dampening logic)
- **New Responsibility:**
  - Query `GrappleStateManager` for active keybinds
  - Calculate pull force toward anchor (if `isPullingRope`)
  - Apply acceleration if `isAccelerating` (anime logic: only after SPACE)
  - Apply gravity/falling if NOT pulling
  - Update player velocity correctly

```java
public class GrapplePhysicsController {
    public static void tick(LocalPlayer player) {
        DynamicHookData hookData = DynamicHookData.get(player);
        
        if (hookData == null || !hookData.isValid()) {
            // Not grappling, apply normal gravity
            applyNormalGravity(player);
            return;
        }
        
        if (!GrappleStateManager.isPullingRope()) {
            // Rope is slack, apply gravity (player falls)
            applyFallDamage(player);
            return;
        }
        
        // Rope is engaged (PULL_ROPE is held)
        Vec3 directionToAnchor = hookData.anchorPos()
            .subtract(player.position())
            .normalize();
        
        if (GrappleStateManager.canAccelerate()) {
            // Apply pulling force PLUS acceleration force
            Vec3 pullForce = directionToAnchor.scale(PULL_STRENGTH);
            Vec3 accelerationForce = directionToAnchor.scale(ACCELERATION_STRENGTH);
            
            player.setDeltaMovement(
                player.getDeltaMovement()
                    .add(pullForce)
                    .add(accelerationForce)
            );
        } else {
            // Only pulling, no acceleration
            Vec3 pullForce = directionToAnchor.scale(PULL_STRENGTH);
            player.setDeltaMovement(
                player.getDeltaMovement()
                    .add(pullForce)
            );
        }
        
        // Apply descend if SHIFT pressed
        if (GrappleStateManager.isDescending()) {
            player.setDeltaMovement(
                player.getDeltaMovement()
                    .add(0, -DESCEND_SPEED, 0)
            );
        }
    }
}
```

#### 2.2 Update HookTransformResolver
- **Modification:** Keep existing logic (transforms hook coords on moving ships)
- **Add:** Call to `GrapplePhysicsController.tick()`
- **Remove:** Old `ReelControl` dampening logic

#### 2.3 Rope Slack Detection
- **Responsibility:** When player NOT holding PULL_ROPE:
  - Rope exists but is "slack" (no tension)
  - Player experiences normal Minecraft gravity
  - Rope can still be re-engaged by pressing SPACE
  - Visual: Rope renders as drooping (no tension lines)

---

### PHASE 3: Rope Physics - Block Collision & Sag (Priority: HIGH)

**Duration:** ~4-5 days  
**Deliverable:** Rope bends around blocks; behaves like GrappleHook mod

#### 3.1 Integrate Sable RopePhysicsObject
- **Reference:** `dev.ryanhcode.sable.api.physics.object.rope.RopePhysicsObject`
- **Concept:**
  - Create a `RopePhysicsObject` when hook engages
  - Register with Sable's `PhysicsPipeline`
  - Query rope points for visual rendering (instead of straight line)
  - Rope auto-wraps around obstacles

```java
public class SableRopePhysicsIntegration {
    private static RopePhysicsObject ropePhysics = null;
    
    public static void onRopeEngage(DynamicHookData hookData, LocalPlayer player) {
        // Create rope physics object
        ropePhysics = new RopePhysicsObject(
            player.position(),           // Start point (player hand)
            hookData.anchorPos(),        // End point (hook anchor)
            MAX_ROPE_LENGTH,             // Max length
            ROPE_SEGMENT_COUNT           // Physics points
        );
        
        // Register with Sable physics pipeline
        Sable.getPhysicsPipeline().addRope(ropePhysics);
    }
    
    public static List<Vec3> getRopePoints() {
        if (ropePhysics == null) return List.of();
        
        // Get rope handle and extract segment positions
        return ropePhysics.getRopeHandle()
            .getSegments()
            .stream()
            .map(segment -> segment.getPosition())
            .collect(Collectors.toList());
    }
    
    public static void onRopeDisengage() {
        if (ropePhysics != null) {
            Sable.getPhysicsPipeline().removeRope(ropePhysics);
            ropePhysics = null;
        }
    }
}
```

#### 3.2 Reference GrappleHook Mod Physics
- **Goal:** Match rope behavior from grappling hook mod
- **Key Classes to Study:**
  - `physics.rope.RopeBend` — how rope wraps around block edges
  - `physics.rope.RopeSegmentHandler` — segment dynamics
  - `raycast.WrapEdgeFinder` — detect wrap points on obstacles
- **Implementation:** Use similar raycasting to find where rope touches blocks

#### 3.3 Max Rope Length Limit
- **Constant:** `MAX_ROPE_LENGTH = 48` blocks (anime reference, balance)
- **Check:** Every tick, if current rope length > max:
  - Set rope to max length
  - Or detach if player too far
  - Visual feedback: rope becomes red/broken

```java
public static final double MAX_ROPE_LENGTH = 48.0;  // blocks

public static void enforceRopeLength(LocalPlayer player, DynamicHookData hookData) {
    double distance = player.position().distanceTo(hookData.anchorPos());
    
    if (distance > MAX_ROPE_LENGTH) {
        // Detach rope
        hookData.drop();
        player.displayClientMessage(
            Component.literal("§cRope too long! Detached."),
            true
        );
    }
}
```

---

### PHASE 4A: Gas System & DEW Mechanics (Priority: CRITICAL)

**Duration:** ~4-5 days  
**Deliverable:** Fully functional gas-powered impulse system with anime-accurate mechanics

**Pre-requisite:** Phase 1-3 completed (keybinds, physics, rope bending working)

**Detailed Spec:** See `ROUND3_STAGE2_DEW_SPECIFICATION.md` for complete technical details

#### 4A.1 Double-Tap Detection System
- **File:** `src/main/java/com/armorberserk/daotcompat/input/DoubleTapDetector.java`
- **Responsibility:**
  - Detect SPACE×2 within 0.25–0.35 seconds (DEW)
  - Detect S×2 within 0.25–0.35 seconds (Reverse DEW)
  - Track press timing accurately
  - Reset on cooldown
- **Hook into:** `ClientTickEvent.Post` to poll keybind state

#### 4A.2 Gas Management System
- **File:** `src/main/java/com/armorberserk/daotcompat/gas/GasManager.java`
- **Responsibility:**
  - Track current gas (0–100%)
  - Handle consumption (8–15% per DEW depending on conditions)
  - Handle regeneration (1–5% per second depending on state)
  - Expose gas level for HUD rendering
- **Regeneration Rules:**
  - Idle (grounded): 5% per second
  - Walking: 3% per second
  - Airborne: 1% per second
  - Reel-in active: 4% per second

#### 4A.3 DEW Impulse Calculator
- **File:** `src/main/java/com/armorberserk/daotcompat/physics/DEWImpulseCalculator.java`
- **Responsibility:**
  - Calculate impulse vector based on:
    - Player look direction (+ 15–20° upward tilt)
    - Current velocity (momentum preservation)
    - Rope tension multiplier (1.0–2.3x)
    - Altitude modifier (0.85x on ground, 1.0x airborne)
    - Current speed multiplier (+0.2–0.4x bonus)
  - Support Reverse DEW (0.85x strength, opposite direction)
- **Formula:**
  ```
  impulse = base_vector × rope_multiplier × altitude_multiplier × speed_multiplier
  ```

#### 4A.4 DEW Cooldown Manager
- **File:** `src/main/java/com/armorberserk/daotcompat/physics/DEWCooldownManager.java`
- **Responsibility:**
  - Enforce 0.4–0.6 second cooldown between DEW uses
  - Prevent spam (can't chain DEW faster than limit)
  - Tick down cooldown timer each client tick
  - Soft cooldown after high falls (20–40% strength reduction for 1–1.5s)

#### 4A.5 Configuration File
- **File:** `src/main/resources/config/daotcompat-gas.toml`
- **Contains:**
  - Gas tank capacity (default 100%)
  - Consumption rates (dew_cost, reverse_dew_cost, etc.)
  - Regeneration rates (idle, walking, airborne, reel_in)
  - Impulse strength multipliers (rope tension, altitude, speed)
  - Spark threshold and particle effects
  - Tunable constants for balance adjustment

**Key Values (from DEW Spec):**
```toml
[gas]
max_gas = 100.0
dew_cost = 10.0
dew_rope_bonus_cost = 12.0
reverse_dew_cost = 8.0

[dew_mechanics]
base_impulse = 2.5
rope_weak_tension = 1.3
rope_strong_tension = 2.3
altitude_on_ground = 0.85
cooldown_ms = 400
```

---

### PHASE 4B: Spark Effects & Audio (Priority: HIGH)

**Duration:** ~1-2 days  
**Deliverable:** Visual & audio feedback for high-speed ground contact

#### 4B.1 Spark Particle System
- **File:** `src/main/java/com/armorberserk/daotcompat/render/SparkEffectRenderer.java`
- **Trigger:** Player touches ground with horizontal speed ≥ 12 blocks/sec
- **Effect:**
  - Orange/yellow electric spark particles spawn at feet
  - Particle count scales with speed (0–8 particles per tick)
  - Particles drift backward (relative to movement)
  - Lifetime 0.4–0.6 seconds with exponential fade
  - Optional: Different colors by block type (ore = bright, stone = gray, etc.)

#### 4B.2 Spark Sound Effect
- **Responsibility:**
  - Play metallic scrape/screech sound (~0.2 sec duration)
  - Volume scales with speed (0.3–1.0 range)
  - Pitch variation (0.8–1.2) for randomness
  - Uses Minecraft's `GRINDSTONE_USE` or custom sound

#### 4B.3 Spark Threshold Configuration
- **Tunable:** `SPARK_THRESHOLD` in config (currently 12 blocks/sec)
- **Adjustable by balance team** during Phase 6

---

### PHASE 5: Hotbar Switching While Grappling (Priority: MEDIUM)

**Duration:** ~1-2 days  
**Deliverable:** Player can swap inventory slots while holding rope

#### 5.1 Create Hotbar Swap Keybind
- **File:** `src/main/java/com/armorberserk/daotcompat/input/GrappleKeybinds.java`
- **New keybind:** `SWAP_HOTBAR` (no default, user-configurable)
- **Behavior:**
  - Respond to number keys (1-9) while grappling
  - Switch `LocalPlayer.getInventory()` slot
  - Do NOT affect rope engagement/velocity

#### 5.2 Hotbar Swap Event Handler
- **File:** `src/main/java/com/armorberserk/daotcompat/input/HotbarSwapHandler.java`
- **Hook into:** `ScreenEvent.Init` or `InputEvent.Key`
- **Logic:**
  - Intercept hotbar keybinds (1-9) while grappling
  - Allow them to work normally
  - Prevent them from interfering with grapple physics

```java
public class HotbarSwapHandler {
    @SubscribeEvent
    public static void onScreenKeyPress(ScreenEvent.KeyPressed event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        
        DynamicHookData hookData = DynamicHookData.get(player);
        if (hookData == null) return;  // Not grappling
        
        int hotbarSlot = event.getKey() - GLFW.GLFW_KEY_1;  // 0-8 for slots 0-8
        if (hotbarSlot >= 0 && hotbarSlot <= 8) {
            player.getInventory().selected = hotbarSlot;
            // Rope physics unchanged
        }
    }
}
```

---

### PHASE 5: Rope Rendering & Visual Feedback (Priority: MEDIUM)

**Duration:** ~2-3 days  
**Deliverable:** Rope renders with physics bends; visual feedback for states

#### 5.1 Update HookLineRenderer
- **File:** `src/main/java/com/armorberserk/daotcompat/render/HookLineRenderer.java`
- **Changes:**
  - Query `SableRopePhysicsIntegration.getRopePoints()` instead of straight line
  - Render rope with proper vertices for bent segments
  - Color feedback:
    - 🟢 Green: Rope engaged (PULL_ROPE held)
    - 🟡 Yellow: Rope slack (PULL_ROPE not held)
    - 🔴 Red: Rope broken / too long

#### 5.2 Velocity Vector Indicator (Optional)
- **Visual:** Show player's velocity direction while grappling
- **Renders:** Particle trail or line in direction of movement
- **Helps:** Player understand "momentum carryover"

---

### PHASE 6: Testing & Balance Tuning (Priority: HIGH)

**Duration:** ~2-3 days  
**Deliverable:** Fully playable, balanced, bug-free system

#### 6.1 Unit Tests
- Test keybind state transitions
- Test rope engagement/disengagement
- Test physics calculations (pull force, acceleration, descend)
- Test rope length enforcement

#### 6.2 Integration Tests
- Test with moving ships (Sable sub-levels)
- Test with Create Aeronautics contraptions
- Test rope wrapping around obstacles
- Test dimension switching (rope should detach)
- Test falling damage when rope detaches at height

#### 6.3 Balance Tuning
- Adjust `PULL_STRENGTH` constant (how fast to pull toward anchor)
- Adjust `ACCELERATION_STRENGTH` (how fast W key accelerates)
- Adjust `DESCEND_SPEED` (how fast SHIFT descends)
- Adjust `MAX_ROPE_LENGTH` (48 blocks appropriate?)
- Tune rope physics stiffness (how much does it bend vs snap)

---

## 🔧 CONFIGURATION FILE

Create `config/daotcompat-keybinds.toml`:

```toml
[keybinds]
# Default keybinds (Minecraft key names)
pull_rope = "space"
accelerate = "key.w"
descend_rope = "key.shift"
swap_hotbar = ""  # User-customizable, no default

[physics]
# Force constants
pull_strength = 0.15
acceleration_strength = 0.25
descend_speed = 0.1

# Rope limits
max_rope_length = 48.0

# Physics
rope_segment_count = 20
rope_stiffness = 0.85

[visual]
# Rope rendering
engaged_color = [0, 255, 0]    # Green (RGB)
slack_color = [255, 255, 0]    # Yellow
broken_color = [255, 0, 0]     # Red
rope_thickness = 2.0
```

---

## 📦 DEPENDENCIES NEEDED

### Already Present (from Stage 1)
- ✅ NeoForge 1.21.1
- ✅ Sable (physics API)
- ✅ Create Aeronautics (moving ships)
- ✅ Danny's AOT (grappling core via reflection)

### New/Modified
- ✅ Sable's `RopePhysicsObject` API (already available)
- ✅ TOML config library (NeoForge built-in: `com.electronwill.night-config`)

---

## 🎯 SUCCESS CRITERIA

By end of Stage 2, the system should:

### Core Keybind System
- [x] Player must press SPACE to engage rope (not automatic)
- [x] W key only accelerates AFTER SPACE is pressed
- [x] SHIFT descends while holding SPACE
- [x] Rope detaches if player releases SPACE
- [x] All keybinds are user-configurable

### Gas & DEW System
- [x] Double-tap SPACE within 0.35s triggers DEW impulse
- [x] DEW costs 10% gas (12% if rope engaged)
- [x] DEW impulse direction = look direction + 15–20° upward tilt
- [x] DEW strength multiplied by rope tension (1.0–2.3x)
- [x] DEW strength multiplied by altitude (0.85x on ground)
- [x] DEW strength bonus for existing velocity (+0.2–0.4x)
- [x] Double-tap S triggers Reverse DEW (0.85x strength, opposite direction)
- [x] Reverse DEW costs 8% gas (10% if rope engaged)
- [x] 0.4s cooldown between DEW uses (prevents spam)
- [x] Gas regenerates: 5% idle, 3% walking, 1% airborne, 4% reel-in
- [x] Gas bar visible in HUD (green/yellow/red)
- [x] Can't use DEW if gas < 8%

### Spark & Audio Effects
- [x] Sparks appear when horizontal speed ≥ 12 blocks/sec on ground
- [x] Sparks are orange/yellow colored particles
- [x] Spark particles fade out smoothly over 0.4–0.6s
- [x] Spark sound (metallic scrape) plays at appropriate volume
- [x] Spark volume scales with speed (0.3–1.0)
- [x] Different spark colors possible by block type (optional)

### Rope Integration
- [x] Player falls with gravity when rope is slack (SPACE not held)
- [x] Rope wraps around block obstacles
- [x] Rope length limited to 48 blocks
- [x] Rope detaches on dimension change
- [x] DEW amplified when rope is under tension
- [x] Rope oscillates after DEW impact (natural swinging)

### Hotbar & Inventory
- [x] Hotbar can be swapped while grappling (1-9 keys)
- [x] Inventory swap doesn't affect rope or velocity
- [x] Player can grab items without releasing rope

### Rendering & Visual Feedback
- [x] Rope renders with physics bends (not straight lines)
- [x] Rope color changes (green = engaged, yellow = slack, red = broken)
- [x] No crashes when rope detaches / re-engages
- [x] Smooth visual transitions between states

### Compatibility
- [x] Works on moving ships (Sable sub-levels)
- [x] Works across dimensions (rope detaches on dimension change)
- [x] Works with Create Aeronautics contraptions
- [x] Compatible with GrappleHook mod physics reference
- [x] No conflicts with vanilla Minecraft mechanics

---

## 📊 FILE STRUCTURE AFTER STAGE 2

```
src/main/java/com/armorberserk/daotcompat/
├── input/
│   ├── GrappleKeybinds.java          ← Keybind registration
│   ├── GrappleStateManager.java      ← State tracking
│   ├── KeybindEventListener.java     ← Event listener
│   ├── DoubleTapDetector.java        ← Double-tap (DEW/Reverse DEW) detection
│   └── HotbarSwapHandler.java        ← Hotbar logic
├── gas/
│   ├── GasManager.java               ← Gas tank & regeneration (NEW PHASE 4A)
│   └── DEWCooldownManager.java       ← DEW cooldown tracking (NEW PHASE 4A)
├── physics/
│   ├── GrapplePhysicsController.java ← Main physics engine (replaces ReelControl)
│   ├── SableRopePhysicsIntegration.java ← Sable integration
│   ├── RopeLengthValidator.java      ← Max length checks
│   ├── DEWImpulseCalculator.java     ← DEW impulse calculations (NEW PHASE 4A)
│   └── DEWCooldownManager.java       ← Cooldown between DEW uses
├── render/
│   ├── HookLineRenderer.java         ← Updated to use physics points
│   ├── RopeStateIndicator.java       ← Color feedback
│   ├── SparkEffectRenderer.java      ← Spark particles on high-speed ground contact (NEW PHASE 4B)
│   └── GasHUDRenderer.java           ← Gas tank HUD display (NEW PHASE 4A)
├── config/
│   └── GrappleConfig.java            ← TOML config loading
├── [existing files unchanged]
│   ├── HookTransformResolver.java    ← Keep, call GrapplePhysicsController
│   ├── RemoteHookFollower.java       ← Keep
│   ├── ThunderSpearFollower.java     ← Keep
│   └── ...
└── resources/
    ├── lang/en_us.json               ← Add keybind descriptions + gas terminology
    ├── config/
    │   ├── daotcompat-keybinds.toml  ← Default keybind config
    │   └── daotcompat-gas.toml       ← Gas system config (NEW PHASE 4A)
    └── sounds/
        └── spark_scrape.ogg          ← Spark effect sound (NEW PHASE 4B, optional custom)
```

---

## ⏱️ TIMELINE ESTIMATE

| Phase | Duration | Start | End | Status |
|-------|----------|-------|-----|--------|
| 1: Keybind Infrastructure | 2-3 days | Week 1 | Week 1 | 📋 Planning |
| 2: Rope Engagement & Physics | 3-4 days | Week 1-2 | Week 2 | ⏳ Blocked on Phase 1 |
| 3: Rope Physics (Sable) | 4-5 days | Week 2 | Week 2-3 | ⏳ Blocked on Phase 2 |
| **4A: Gas System & DEW** | **4-5 days** | **Week 2-3** | **Week 3** | **⏳ Blocked on Phase 1** |
| **4B: Spark Effects & Audio** | **1-2 days** | **Week 3** | **Week 3** | **⏳ Blocked on Phase 4A** |
| 5: Hotbar Switching | 1-2 days | Week 2 | Week 2 | ⏳ Blocked on Phase 1 |
| 6: Rendering & Feedback | 2-3 days | Week 3 | Week 3 | ⏳ Blocked on Phase 3 |
| 7: Testing & Balance | 2-3 days | Week 3-4 | Week 4 | ⏳ Blocked on all |
| **TOTAL** | **~18-25 days** | — | — | **📋 Planning** |

**Note:** Phases 4A and 4B are new (DEW + Spark effects). They can start after Phase 1 (keybind infrastructure) is done, since they don't depend on Phase 2-3.

---

## 🚀 HOW TO START

1. **Create feature branch:**
   ```bash
   git checkout -b feature/stage2-keybind-system
   ```

2. **Start Phase 1 (Keybind Infrastructure):**
   - Create files listed in Phase 1.1 - 1.3
   - Implement keybind registration
   - Test in-game: keybinds appear in controls menu

3. **Commit & push regularly:**
   ```bash
   git add src/
   git commit -m "feat: keybind infrastructure (phase 1)"
   git push origin feature/stage2-keybind-system
   ```

4. **Follow phases in order** — each phase depends on previous

5. **Final:** Create pull request when all phases complete

---

## 🔗 REFERENCES

### Documentation Files (In This Repository)
- **ROUND3_STAGE2_DEV_PLAN.md** ← You are here (keybinds, physics, hotbar)
- **ROUND3_STAGE2_DEW_SPECIFICATION.md** — DEW/gas/spark system (REQUIRED READING)
- **STAGE2_SETUP_GUIDE.md** — Environment setup & workflow
- **RESOURCE_ARCHIVE.md** — Archive contents and deployment guide
- **PLAN.txt** — Stage 1 handoff notes
- **CHANGES.md** — Stage 1 changelog

### NeoForge & Minecraft Documentation
- **Keybind System:** https://docs.neoforged.net/docs/input/keybinds
- **Event System:** https://docs.neoforged.net/docs/concepts/events
- **Mixins:** https://docs.neoforged.net/docs/advanced/mixin
- **Particle Engine:** https://docs.neoforged.net/docs/rendering/particles

### Physics & Mod References
- **Sable API:** `dev.ryanhcode.sable.api.physics.object.rope.RopePhysicsObject`
- **GrappleHook Decompiled:** `/home/user/workspace/grapple_ref/decompiled/`
- **Create Aeronautics:** https://github.com/Belgabor/Create-Aeronautics
- **Sable GitHub:** https://github.com/ryanhcode/Sable

### Repository
- **Main Repo:** https://github.com/MikasaAckerrman/daot-compat
- **Branch:** `round3-stage1-fixes` (current)
- **Feature Branch:** `feature/stage2-keybind-system` (to create)

---

**Created by:** AI Agent (2026-07-25)  
**For:** @MikasaAckerrman  
**Status:** Ready for Implementation
