# DAOT Compat — Changes (feature/4-reflection-fix)

## Summary of changes relative to baseline

### Modified files

| File | What changed |
|------|-------------|
| `hook/DynamicHookData.java` | Added `ResourceKey<Level> dimensionKey` field to the record. Stored at anchor creation time so dimension-mismatch checks can skip Sable API calls entirely. |
| `hook/HookTransformResolver.java` | Passes `level.dimension()` to all `DynamicHookData` constructors. `follow()` now checks dimension equality before querying Sable; if dimension changed, drops the anchor with a `LOGGER.debug` line. |
| `hook/RemoteHookFollower.java` | Added `leftDim` / `rightDim` (`ResourceKey<Level>`) fields to the inner `Anchor` class. `store()` accepts the dimension; `follow()` drops a stale anchor if `level.dimension()` no longer matches. Debug log emitted on drop. |
| `spear/ThunderSpearFollower.java` | **Tick-ordering**: class JavaDoc now explicitly documents that `EntityTickEvent.Pre` fires before `entity.tick()`, so our reposition always precedes AOT's `explode()` call in the same tick. **Dimension guard**: drops anchor and debug-logs if `entity.level().dimension()` changes. **Detonation drift log**: added `LAST_WORLD_POS` map; when a lodged spear transitions to un-lodged (explosion/removal) a one-shot INFO log compares the detonation position against the last anchor-projected world position — near-zero drift confirms the follower worked. **Edge case cleanup**: all early-exit paths that remove from `ANCHORS` also remove from `LAST_WORLD_POS`. |
| `DAOTCompat.java` | Registers `DAOTCompatKeyMappings.REEL_IN` on the mod event bus (`RegisterKeyMappingsEvent`). Calls `ReelControl.apply(player)` at the end of the `ClientTickEvent.Post` LOWEST-priority handler, after existing hook projection calls. |

### New files

| File | Purpose |
|------|---------|
| `client/DAOTCompatKeyMappings.java` | Declares the `REEL_IN` `KeyMapping` (default: `LEFT_ALT`, category `key.categories.daotcompat`). Key choice documented in class JavaDoc. |
| `hook/ReelControl.java` | `static apply(LocalPlayer)` — called from `ClientTickEvent.Post` LOWEST. When `REEL_IN` is not held and at least one hook is active, reads the player's velocity (which AOT just set), computes the unit vector toward the active hook(s), and subtracts the pull component so the player stops being reeled in. Perpendicular velocity (gravity, lateral) is preserved. |
| `assets/daotcompat/lang/en_us.json` | English translations for the keybind category (`"DAOT Compat"`) and the key name (`"Reel In (ODM)"`). |

## Round 2+3 (code review fixes, keybind semantics fix, RU localization, swing safety)

### Part A — FIX_BRIEF.txt code review fixes
| File | Fix | What/why |
|------|-----|---------|
| `DAOTCompat.java` | Fix 1 | Removed the duplicate per-tick processing path. `HookTransformResolver.process`/`RemoteHookFollower.tick` now run exactly once, from `ClientTickEvent.Post` LOWEST. |
| `mixin/client/LocalPlayerTickMixin.java` | Fix 1 | Deleted (its only job was the now-removed duplicate call). Removed its entry from `daotcompat.mixins.json` too. |
| `aot/RemoteHookReflect.java` | Fix 6 | `hooks()` now returns a defensive `ArrayList` copy taken under `synchronized(map)`, with a try/catch swallowing `ConcurrentModificationException`, instead of handing back AOT's live `Map.values()`. |
| `hook/RemoteHookFollower.java` | Fix 2, Fix 5, Fix 6 | Added a monotonic tick counter + `lastSeenTick` per anchor with a 100-tick TTL sweep (guards against AOT hook-object pooling). `follow()` now passes the previously-resolved sub-level as a `hint` to `SubLevelResolver.findContaining`, skipping the full scan on the common "still on the same ship" case. TTL sweep iterates under `synchronized(STATE)`. |
| `spear/ThunderSpearFollower.java` | Fix 2 | `ANCHORS` values wrapped in a `TrackedAnchor(data, lastSeenTick)` record with the same 100-tick TTL sweep as above. |
| `hook/HookTransformResolver.java` | Fix 3, Fix 5 | *(already applied in the source we received — verified, not re-touched)* `MATCH_RADIUS_SQR` uses a documented 64-block `HOOK_RANGE_MARGIN`; `SubLevelResolver.findContaining` already has the sticky-hint overload. |
| `sable/SubLevelResolver.java` | Fix 5 | *(already applied in the source we received — verified, not re-touched)* `findContaining(level, pos, hint)` overload with the sticky fast-path. |

### Part B — Keybind rename + semantics inversion (the actual user-reported bug fix)
| File | What/why |
|------|---------|
| `client/DAOTCompatKeyMappings.java` | Renamed `REEL_IN` → `HANG`. Old semantics: pull only worked while held (default = no pull at all). New semantics: AOT's pull works normally by default; holding `HANG` is what suppresses it. This was the root cause of "rope doesn't pull me in" (user tested without holding the undiscoverable, English-only key) and contributed to the spin bug (the unclamped correction ran by default on every tick instead of only when asked for). |
| `hook/ReelControl.java` | Inverted the early-return condition to match. Added `MIN_HOOK_RADIUS` (2 blocks) — hooks closer than this are excluded from the pull-direction average, since the tangential-velocity projection is numerically unstable near the anchor (classic pendulum ω=v/r singularity as r→0). Added `MAX_TANGENTIAL_SPEED` (3 blocks/tick, ~60 blocks/s) as a hard clamp on the corrected velocity's magnitude — a safety net against any runaway speed regardless of cause. Also applied Fix 4 (explicit `leftContributed`/`rightContributed` booleans instead of comparing a `Vec3` to `Vec3.ZERO`). |

### Part C — Russian localization
| File | What/why |
|------|---------|
| `assets/daotcompat/lang/ru_ru.json` | New. `key.categories.daotcompat` = "Крюки ODM (DAOT Compat)", `key.daotcompat.hang` = "Придержать трос (не тянуть)" — so Russian-speaking players can actually see what the key does in Options → Controls. |
| `assets/daotcompat/lang/en_us.json` | Updated to the renamed `key.daotcompat.hang` id and new English wording ("Hang (Stop Pull)"). |

Build verified: `BUILD SUCCESSFUL` with JDK 21 + Gradle 8.11.1 against real `libs/` jars (sable-neoforge 1.2.2, sable-companion-common 1.6.0, dannys-aot 2.2.0, create-aeronautics 1.2.1, create 6.0.10).

## Round 2+3 (code review fixes, keybind semantics fix, RU localization, swing safety)

### Part A — FIX_BRIEF.txt fixes

| File | What / Why |
|------|-----------|
| `aot/RemoteHookReflect.java` | Fix 6: `hooks()` now returns a defensive `List<?>` copy (`new ArrayList<>(m.values())`) taken under `synchronized(m)` instead of handing back a live `Map.values()` view, preventing a possible `ConcurrentModificationException` if AOT's map is mutated (e.g. by a network packet handler) during our iteration. |
| `hook/RemoteHookFollower.java` | Fix 2: added a monotonic `tickCounter` and `Anchor.lastSeenTick`; an anchor untouched for more than `STALE_TICKS` (100 ticks / 5s) is force-dropped with a debug log ("stale anchor removed (possible AOT object pooling)") instead of relying solely on `WeakHashMap` GC, which would never fire if AOT recycles hook-data objects from a pool. Also wires a `SubLevel` hint (Fix 5) from the previously-tracked sub-level into `SubLevelResolver.findContaining`. |
| `hook/HookTransformResolver.java` | Fix 3: `MATCH_RADIUS_SQR` now derives from a named `HOOK_RANGE_MARGIN = 64.0` constant (`(250 + 64)²` instead of a bare `256²`), giving fast-moving Sable airships enough per-tick slack that a legitimate rope isn't falsely detached. |
| `sable/SubLevelResolver.java` | Fix 5: added `findContaining(Level, Vec3, @Nullable SubLevel hint)` overload with a sticky fast-path — if `hint` still geometrically contains the point, it's returned immediately, skipping the full `queryIntersecting` scan. The old single-arg method now delegates to this with `hint = null`, preserving all existing call sites. |
| `mixin/client/LocalPlayerTickMixin.java` | Fix 1: rather than removing the mixin outright (ordering vs. the Fabric-API/Connector bridge that AOT relies on can't be verified with 100% confidence), added a static `processedThisTick` guard flag so the real hook-projection work runs exactly once per client tick regardless of which entry point (mixin HEAD vs. `ClientTickEvent.Post`) fires first. |
| `DAOTCompat.java` | Fix 1: added a `ClientTickEvent.Pre` LOWEST listener that resets `LocalPlayerTickMixin.processedThisTick` at the start of every tick; the existing `ClientTickEvent.Post` listener now checks the flag before doing hook-projection work. Extensive class-level JavaDoc documents why the guard-flag approach was chosen over deleting the mixin. |
| `hook/ReelControl.java` | Fix 4 (folded into the Part B rewrite below): explicit `leftContributed` / `rightContributed` booleans replace the old "toHook length <= 1e-3" implicit sentinel, so "hook not contributing" can never be confused with "hook exactly at the player's position" (rare teleport/network-lag edge case). |

*(Fix 6's "update call sites" note: `RemoteHookFollower.tick()` already iterated the return value with a for-each over `Object`, so no signature change was needed there beyond `RemoteHookReflect.hooks()` itself.)*

### Part B — HANG keybind semantics + swing safety (the actual bug fix)

| File | What / Why |
|------|-----------|
| `client/DAOTCompatKeyMappings.java` | Renamed `REEL_IN` → `HANG` (field + translation key `key.daotcompat.hang`) and **inverted the semantics**: by default (key not held) AOT's pull applies completely normally; holding `HANG` is what now stops/brakes the pull. Class doc explains this matches player expectations (a grapple should pull you in by default) and cites "Grapple Mod" by yyon's dedicated "stop swinging" key as design precedent (that mod is not modified, only referenced). |
| `hook/ReelControl.java` | Inverted the early-return condition to match the new `HANG` semantics (do nothing unless the key is held). Added `MIN_HOOK_RADIUS = 2.0` blocks: hooks closer than this to the player are excluded from the pull-direction average, since the direction vector becomes numerically unstable as r → 0 (pendulum ω = v/r singularity) and must not be fed back into AOT's physics. Added `MAX_TANGENTIAL_SPEED = 3.0` blocks/tick (~60 blocks/sec, generous for a fast swing): a hard clamp on the magnitude of the *final* corrected velocity vector, applied unconditionally as a last-resort safety net so no code path can ever hand AOT an unbounded/"insane" speed. Folded in Fix 4's explicit `leftContributed`/`rightContributed` sentinels as part of the same coherent diff. |
| `DAOTCompat.java` | Updated the `ReelControl.apply(player)` call-site comment to reflect the inverted meaning ("strip inward pull only while HANG is held"). |
| `assets/daotcompat/lang/en_us.json` | Updated key name to `key.daotcompat.hang` → `"Hang (Stop Pull)"`, reflecting the new semantics. |

**Root cause recap:** two user-reported bugs — "rope doesn't pull the player in" (because by design, not holding any key suppressed all pull) and "player spins at insane speed near the anchor" (the old per-tick radial-strip projection had no minimum-radius guard or output speed clamp, so it fed a numerically unstable/unbounded tangential velocity back into AOT) — are both fixed by this default-pull / hold-to-stop inversion plus the two new safety clamps.

### Part C — Russian localization

| File | What / Why |
|------|-----------|
| `assets/daotcompat/lang/ru_ru.json` | New file. `key.categories.daotcompat` → "Крюки ODM (DAOT Compat)"; `key.daotcompat.hang` → "Придержать трос (не тянуть)". Tone/style matched to the reference `grapplemod` RU translation (informal, short, action-oriented). |

### Verification

`JAVA_HOME=/home/user/workspace/jdk21 ./gradle-8.11.1/bin/gradle --no-daemon clean build -x test` → `BUILD SUCCESSFUL`.

## Stage 1 fixes (spear render, high-speed clipping)

### Bug 1 — thunder spears lodged on a sub-level don't render

**What was wrong (root cause).** `spear/ThunderSpearFollower.java` re-places a lodged spear each
server tick with `entity.setPos(next)` (`ThunderSpearFollower.java:142`) to keep it glued to the
moving airship. That fixes *where the spear is* but not *how the client draws it*:
`ThunderSpearEntityRenderer.render()` samples `entity.getPosition(partialTick)`
(`method_30950`, see `~/workspace/aot_ref/decompiled/daot/ThunderSpearEntityRenderer.java:90`),
which linearly interpolates between the previous-tick position `(xo,yo,zo)` and the current
`(x,y,z)` every frame. `setPos` updates only `(x,y,z)`; `(xo,yo,zo)` is left at the stale
pre-ship-move world point, so the interpolated draw position swings a full tick of ship
translation+rotation every frame — off the ship, into blocks, or outside the rendered area — which
reads as "the spear doesn't render" / flickers away, worst as the ship gains rotation.

**What changed.**
- `sable/SableBridge.java`: new `setOldPosNoMovement(Entity)` that delegates to Sable's own
  first-party helper `dev.ryanhcode.sable.api.entity.EntitySubLevelUtil.setOldPosNoMovement`
  (verified `public static` in `libs/sable-neoforge-1.21.1-1.2.2.jar`, package
  `dev.ryanhcode.sable.api.entity`), wrapped in the usual `try/catch(Throwable)` so it degrades to a
  no-op if Sable is absent — matching every other method in this class.
- `spear/ThunderSpearFollower.java`: call `SableBridge.setOldPosNoMovement(entity)` immediately
  after the `setPos` reposition. That recomputes `(xo,yo,zo)` from the sub-level's *previous-tick*
  pose (`trackingSubLevel.lastPose()`), so the next interpolated frame follows the ship smoothly.
  Read of the decompiled helper (`~/workspace/sable_ref/decompiled/.../EntitySubLevelUtil.java:39-58`)
  confirms its `else` branch (entity not Sable-"tracked") simply pins old-pos to the current pos,
  which still removes the swing — so the call is safe unconditionally, no tracking registration
  required. Class javadoc updated ("Fix 3"); a `TODO` records the fuller integration path
  (registering the spear into Sable's `sable$trackingSubLevel` system via the
  `EntityMovementExtension` mixin interface) for a later session.

### Bug 2 — player falls/clips through a physics sub-level object at high speed

**What was verified (honest account).** Sable injects sub-level block collision by `@Redirect`ing
vanilla `Entity.collide(Vec3)` inside `move()`
(`dev.ryanhcode.sable.mixin.entity.entity_sublevel_collision.EntityMixin#sable$collideRedirect`)
to `dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision#collide` (decompiled from
`libs/sable-neoforge-1.21.1-1.2.2.jar`). That method is **discretely sub-stepped, not continuously
swept** like vanilla `Level.getCollisions(entity, box.expandTowards(delta))`:
- non-player entities: `substeps = Math.min(10, Math.max(1, (int)(motion.length()/0.015625)))`
  — hard-capped at 10 (`SubLevelEntityCollision.java:145`);
- **local player: a fixed `substeps = 8`, independent of speed** (`SubLevelEntityCollision.java:146-148`).

Each sub-step is a static SAT overlap of the player OBB vs. the ship's block OBBs. At normal speeds
8 samples overlap the ~1.8-tall player box with room to spare, so nothing is missed; at the high
per-tick displacement AOT ODM gear produces, the fixed sample spacing (`motion.length()/8`) grows
past `player-box + deck-thickness` and consecutive samples straddle a thin deck with none inside it
— discrete-sampling tunnelling. This bites sub-levels and not static terrain precisely because
static terrain uses the continuous swept query. Additionally, `SubLevelEntityCollision.collide`
does **no** block collision for a `ServerPlayer` at all — it returns motion unchanged and only
honours an already-set tracking sub-level (`SubLevelEntityCollision.java:96-110`) — so player-vs-
sub-level collision is entirely client-side. I also checked `hook/ReelControl.java` (the spec's
alternative hypothesis): it already clamps corrected velocity to `MAX_TANGENTIAL_SPEED = 3.0`
blocks/tick and only acts while `HANG` is held, so it is not the source of a huge single-tick
displacement; AOT's own untouched pull is where high speeds come from.

**What changed — a conservative, clearly-labelled mitigation (not a Sable-internal root-cause fix).**
We cannot raise Sable's sub-step count from our own mod without mixing into Sable's classes, which
this project deliberately avoids (see `DAOTCompat` "Fix 1"). Instead:
- New `collision/HighSpeedSubLevelGuard.java`, invoked last in the existing `ClientTickEvent.Post`
  LOWEST listener in `DAOTCompat.java`. Once per tick, **only** when the local player's per-tick
  displacement exceeds `FAST_THRESHOLD` (1.0 block/tick) **and** `SableBridge.getIntersecting`
  reports a sub-level near the movement segment (a no-op away from ships / at slow speed), it
  re-walks the straight segment from the pre-tick position (`xo,yo,zo`) to the post-tick position
  in `<= 0.9`-block sub-steps and tests whether the player box is embedded in solid sub-level
  geometry at any intermediate step (transforming a lattice of sample points into each sub-level's
  local frame and testing strict containment against the block collision shape — sub-level blocks
  live in the same `Level` at their local coords, exactly as Sable reads them in
  `EntityMixin#getInBlockState`). If an embedded intermediate step is found (the resolved path
  passed through ship geometry Sable's coarser sampling skipped), the player is clamped back to the
  last embedding-free sub-step and the velocity component along the motion is zeroed, mirroring
  vanilla swept collision.
- `sable/SableBridge.java`: new `getIntersecting(Level, AABB)` (wraps
  `SubLevelContainer.queryIntersecting`) as the cheap near-a-ship gate.

**Safe by construction:** the guard fires only when a genuine intermediate sample is inside solid
geometry, so it can't fight resolution that already worked (standing on a deck leaves every sample
on/above the surface via the strict-containment `INSET`, never embedded) and does nothing in
ordinary terrain. It is a best-effort safety net: the sample lattice reliably catches roughly
deck-thickness geometry but is not a pixel-exact swept solid, so it is documented as a mitigation
rather than a guaranteed fix for every possible shape.

### Verification (Stage 1)

`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew clean build -x test` → `BUILD SUCCESSFUL`.
