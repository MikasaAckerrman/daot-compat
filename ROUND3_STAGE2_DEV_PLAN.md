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

### PHASE 4: Hotbar Switching While Grappling (Priority: MEDIUM)

**Duration:** ~1-2 days  
**Deliverable:** Player can swap inventory slots while holding rope

#### 4.1 Create Hotbar Swap Keybind
- **File:** `src/main/java/com/armorberserk/daotcompat/input/GrappleKeybinds.java`
- **New keybind:** `SWAP_HOTBAR` (no default, user-configurable)
- **Behavior:**
  - Respond to number keys (1-9) while grappling
  - Switch `LocalPlayer.getInventory()` slot
  - Do NOT affect rope engagement/velocity

#### 4.2 Hotbar Swap Event Handler
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

- [x] Player must press SPACE to engage rope (not automatic)
- [x] W key only accelerates AFTER SPACE is pressed
- [x] SHIFT descends while holding SPACE
- [x] Rope detaches if player releases SPACE
- [x] Player falls with gravity when rope is slack
- [x] Rope wraps around block obstacles
- [x] Rope length limited to 48 blocks
- [x] Hotbar can be swapped while grappling
- [x] Rope renders with physics bends (not straight lines)
- [x] All keybinds are user-configurable
- [x] No crashes when rope detaches / re-engages
- [x] Works on moving ships (Sable sub-levels)
- [x] Works across dimensions (rope detaches on dimension change)

---

## 📊 FILE STRUCTURE AFTER STAGE 2

```
src/main/java/com/armorberserk/daotcompat/
├── input/
│   ├── GrappleKeybinds.java          ← Keybind registration
│   ├── GrappleStateManager.java      ← State tracking
│   ├── KeybindEventListener.java     ← Event listener
│   └── HotbarSwapHandler.java        ← Hotbar logic
├── physics/
│   ├── GrapplePhysicsController.java ← Main physics engine (replaces ReelControl)
│   ├── SableRopePhysicsIntegration.java ← Sable integration
│   └── RopeLengthValidator.java      ← Max length checks
├── render/
│   ├── HookLineRenderer.java         ← Updated to use physics points
│   └── RopeStateIndicator.java       ← Color feedback
├── config/
│   └── GrappleConfig.java            ← TOML config loading
├── [existing files unchanged]
│   ├── HookTransformResolver.java    ← Keep, call GrapplePhysicsController
│   ├── RemoteHookFollower.java       ← Keep
│   ├── ThunderSpearFollower.java     ← Keep
│   └── ...
└── resources/
    ├── lang/en_us.json               ← Add keybind descriptions
    └── config/
        └── daotcompat-keybinds.toml  ← Default config
```

---

## ⏱️ TIMELINE ESTIMATE

| Phase | Duration | Start | End | Status |
|-------|----------|-------|-----|--------|
| 1: Keybind Infrastructure | 2-3 days | Week 1 | Week 1 | 📋 Planning |
| 2: Rope Engagement & Physics | 3-4 days | Week 1-2 | Week 2 | ⏳ Blocked on Phase 1 |
| 3: Rope Physics (Sable) | 4-5 days | Week 2 | Week 2-3 | ⏳ Blocked on Phase 2 |
| 4: Hotbar Switching | 1-2 days | Week 2 | Week 2 | ⏳ Blocked on Phase 1 |
| 5: Rendering & Feedback | 2-3 days | Week 3 | Week 3 | ⏳ Blocked on Phase 3 |
| 6: Testing & Balance | 2-3 days | Week 3 | Week 3 | ⏳ Blocked on all |
| **TOTAL** | **~14-20 days** | — | — | **📋 Planning** |

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

## 🔗 REFERENCES & LINKS

- **Sable RopePhysicsObject:** `dev.ryanhcode.sable.api.physics.object.rope.RopePhysicsObject`
- **GrappleHook Mod (reference):** Physics in `/home/user/workspace/grapple_ref/decompiled/`
- **NeoForge Keybind Docs:** https://docs.neoforged.net/docs/input/keybinds
- **This Repo:** https://github.com/MikasaAckerrman/daot-compat

---

**Created by:** AI Agent (2026-07-25)  
**For:** @MikasaAckerrman  
**Status:** Ready for Implementation
