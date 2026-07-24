# Stage 1 — Bugfixes (root causes already diagnosed, implement exactly as specified)

Project: `/home/user/workspace/daot` (NeoForge 1.21.1 compat mod `daotcompat`).
Build: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew build -x test` (wrapper already generated,
`libs/` already populated with the 5 exact dependency jars, baseline already verified BUILD SUCCESSFUL —
do not touch `libs/` or the gradle wrapper).

Reference decompiled sources you can read for ground truth (already extracted for you, DO NOT re-decompile,
just read what's there — grep first, read only what you need):
- `~/workspace/aot_ref/decompiled/daot/ThunderSpearEntity.java` — Danny's AOT spear entity (obfuscated
  Yarn-ish `class_XXXX`/`method_XXXX` names — `class_243`=Vec3, `class_1297`=Entity, `method_5773`=tick(),
  `method_19538`=position(), `method_5814`=setPos, `method_18799`=setDeltaMovement).
- `~/workspace/aot_ref/decompiled/daot/ThunderSpearEntityRenderer.java` — its client renderer.
- `~/workspace/sable_ref/decompiled/dev/ryanhcode/sable/api/entity/EntitySubLevelUtil.java` — **Sable's own
  first-party helper**, in particular `setOldPosNoMovement(Entity)`.
- `~/workspace/sable_ref/extracted/` — full unzipped Sable jar if you need to grep for more mixins/classes
  (e.g. `dev/ryanhcode/sable/mixin/entity/entities_stick_sublevels/*`, `SableInterimCalculation`,
  `Sable.HELPER` interface) — grep by class name, decompile with
  `java -jar /tmp/cfr.jar <path/to/Class.class> --outputdir /tmp/out` only for classes you actually need,
  don't decompile the whole jar again.
- `~/workspace/grapple_ref/decompiled/` — reference rope-physics mod decompile (for a LATER stage, not this
  one — ignore for stage 1).

## Bug 1 — Thunder spears lodged in a block on a Sable sub-level don't render (root cause confirmed)

Current code: `src/main/java/com/armorberserk/daotcompat/spear/ThunderSpearFollower.java`,
method `onTick(Entity entity)`, is **server-only** (`if (level.isClientSide()) return;` at the top) and
repositions the lodged spear each server tick via `entity.setPos(next.x, next.y, next.z)` to keep it glued
to the sub-level's current pose. This positional correction is correct and necessary.

**Root cause of invisible rendering:** `entity.setPos(...)` updates the entity's *current* tick position but
does **not** touch `entity.xo/yo/zo` (a.k.a. `xOld/yOld/zOld` in some mappings — the *previous-tick* position
vanilla stores for render interpolation). Every client frame, `Entity.getPosition(partialTick)` — called by
the renderer, see `ThunderSpearEntityRenderer.render()` line using `entity.method_30950(partialTick)` — linearly
interpolates between `(xo,yo,zo)` and the current `(x,y,z)`. Because our correction silently teleports the
entity by a full tick's worth of ship translation *and rotation* every single tick, and `xo/yo/zo` is left
pointing at the stale pre-move world-space value from before the ship moved, the interpolated render position
swings wildly every frame (potentially far from the actual ship, sometimes inside solid blocks, sometimes far
outside any loaded/rendered area) — this reads to the player as "doesn't render at all" / flickers into
nothing, especially as the ship picks up rotation.

Sable ships a first-party fix for exactly this class of problem:
`dev.ryanhcode.sable.api.entity.EntitySubLevelUtil.setOldPosNoMovement(Entity entity)` — it recomputes
`xo/yo/zo` (and current xOld/yOld/zOld fields) from the sub-level's **previous-tick pose**
(`trackingSubLevel.lastPose()`) composed with the entity's local-space offset, so the interpolation this tick
is smooth and correctly follows the ship's motion instead of jumping. Read the decompiled source at
`~/workspace/sable_ref/decompiled/dev/ryanhcode/sable/api/entity/EntitySubLevelUtil.java` to see exactly what
it does, then find the real (non-obfuscated) class in the compileOnly jar
`libs/sable-neoforge-1.21.1-1.2.2.jar` (and/or `libs/sable-companion-common-1.21.1-1.6.0.jar` — this method
may actually live in the companion-common jar since that's the cross-loader common API; check both jars'
package `dev.ryanhcode.sable.api.entity` — use `unzip -l` to see which jar actually contains
`EntitySubLevelUtil.class`, then compile against whichever one has it) so you can call it directly from our
own compat mod code (it's `public static`, no reflection needed — it's a real compile-time API in a
`compileOnly` dependency, exactly like `SubLevel`/`SubLevelContainer` are already used elsewhere in this repo).

### Fix
In `ThunderSpearFollower.onTick(Entity entity)`, right after the successful `entity.setPos(next.x, next.y, next.z)`
call (the line that currently does the reposition, near the end of the method), call
`dev.ryanhcode.sable.api.entity.EntitySubLevelUtil.setOldPosNoMovement(entity)` so the client's next
interpolation frame is correct. Wrap the whole thing so it degrades gracefully if the Sable API class/method is
ever missing (this project's existing style: everything Sable-related goes through `SableBridge`, which
never throws even if Sable isn't installed — follow that convention. If `setOldPosNoMovement` needs a
try/catch(Throwable) guard because it's not literally guaranteed present across all Sable versions the user
might have, add one; if it's a stable public API present since long ago, a plain direct call is fine — use
your judgement after checking the actual jar, and document the choice in a comment).

Also check: does `EntitySubLevelUtil.setOldPosNoMovement` require the entity to be a currently-"tracked"
sub-level entity as understood by `Sable.HELPER.getTrackingSubLevel(entity)` (see the decompiled source —
it has an `else` branch that's a graceful no-op fallback using current position, so it's safe to call
unconditionally, even if Sable's own "tracking" registration was never set up for this manually-managed
entity — verify this by reading the decompiled method body, it's short). If Sable's per-tick sub-level
"tracking" registration is a *separate* opt-in step (e.g. entities must be added to some registry/set to be
considered "sub-level entities" for other Sable features like collision/rotation), investigate whether
registering the spear into that system properly (instead of/in addition to our own manual reposition) would
be a cleaner integration — grep `~/workspace/sable_ref/extracted` for how entities get registered as
"tracking" a sub-level (likely `Sable.HELPER.setTrackingSubLevel(...)` or similar, or a capability, or NBT
persistent data). If such a registration call exists and is simple/safe, use it (register the spear as
tracking its anchor sub-level as soon as we compute the anchor, unregister when the anchor is dropped) — this
is likely the officially-intended way to make Sable's own systems (rendering, movement, etc.) treat our
entity correctly, and may fix the bug more robustly than only patching xo/yo/zo by hand. If it's complex or
requires much larger changes, it's fine to just call `setOldPosNoMovement` manually every tick as described
above — ship the simpler fix, but leave a `// TODO` comment explaining the more complete integration path you
found, so a future session can pick it up.

Update the class javadoc of `ThunderSpearFollower` to document this fix (why render interpolation needed a
second fix beyond the position fix).

## Bug 2 — Player falls/clips through a physics sub-level object at high speed

Investigate this properly before writing a fix — it's a real physics/collision correctness issue, not a
one-line patch, and the fix must live in **our compat mod** (a NeoForge mixin or event listener), since we
cannot modify the Sable or vanilla jars.

Grep `~/workspace/sable_ref/extracted` for how Sable makes sub-level (ship) block collision visible to
vanilla's entity movement/collision code. Look specifically at:
- `dev/ryanhcode/sable/mixin/clip_overwrite/EntityMixin.class` (decompiled at
  `~/workspace/sable_ref/decompiled/dev/ryanhcode/sable/mixin/clip_overwrite/EntityMixin.java` — note: this one
  turned out to be about `Entity.pick()` raycasting/eye position, not general collision — read it, but keep
  looking for the actual collision-shape provider mixins nearby in the same `mixin/` tree).
- `dev/ryanhcode/sable/mixin/entity/entities_in_blocks/EntityMixin.class` (decompile it with
  `java -jar /tmp/cfr.jar <path> --outputdir /tmp/out` if not already decompiled at
  `~/workspace/sable_ref/decompiled/...`).
- Any mixin targeting `Level`/`CollisionGetter`/`Entity#collide`/`Entity#move` — search
  `grep -rl "getBlockCollisions\|getCollisions\|CollisionGetter\|Shapes\." ~/workspace/sable_ref/extracted`
  (on the .class files this greps binary but ripgrep/grep -a can still find readable UTF-8 method-ref strings
  in some cases; more reliably, list all mixin classes under `dev/ryanhcode/sable/mixin/` and decompile the
  ones whose package name suggests collision/physics/movement, e.g. anything with "collision", "collide",
  "physics", "move", "clip" in the path).
- `dev/ryanhcode/sable/api/physics/callback/BlockSubLevelCollisionCallback.class` and
  `dev/ryanhcode/sable/api/block/BlockSubLevelCollisionShape.class` /
  `BlockSubLevelDynamicCollider.class` — these look like the public API surface for how a sub-level's blocks
  expose collision shapes; decompile and read them to understand whether collision is queried as a **swept**
  test across the player's full per-tick movement delta, or only as a **static overlap** test at the
  post-movement position (the latter is the classic cause of tunneling through fast-moving thin platforms:
  if the collision provider is asked "what's the shape near point P" rather than "what shapes intersect this
  swept box from A to B", a player crossing a thin deck within one tick can end up with both the pre-tick and
  post-tick sample points on opposite sides of the collision shape, with no relevant sample in between).

Formulate a concrete hypothesis grounded in what you actually find in the code (not speculation) about why
tunneling happens specifically for **physics/sub-level objects** but not ordinary static world blocks (vanilla
world collision is inherently swept/continuous via `Level.getCollisions(entity, box.expandTowards(deltaMovement))`
inside `Entity.move()`, querying real block shapes at every block position touched by the swept box — this is
why static-world tunneling basically never happens at normal Minecraft speeds; a sub-level is different because
its blocks aren't part of `Level`'s normal block storage at all, they're injected via a separate
mixin/collision-callback layer that may not participate in the same swept query, or may recompute the ship's
world-space collision shapes only once per tick from the ship's *current* pose without accounting for player
displacement happening in the same tick).

### Fix approach (adjust after your investigation, but this is the expected shape of the solution)
Add a NeoForge mixin or event-based safety net in our own mod (new file, e.g.
`src/main/java/com/armorberserk/daotcompat/collision/HighSpeedSubLevelGuard.java` or similar, package to match
existing conventions) that, for the local player (and ideally other entities near sub-levels — but local
player first, since that's what the user experiences and what we can safely correct via `ClientTickEvent`/a
movement mixin without touching server-authoritative entity state for entities we don't own):
1. Each tick, if the player's per-tick displacement is large enough that it could skip past a thin sub-level
   collision shape (e.g. compare `player.position()` this tick vs last tick — a configurable threshold, start
   with something like > 1.0 block/tick as "fast", matching typical AOT ODM gear speeds which are exactly the
   context the user is worried about), do a manual **sub-stepped** collision re-check: split the movement
   segment from `lastPos` to `currentPos` into N sub-steps (e.g. ceil(distance / 0.9) steps so each sub-step
   is under a safe ~0.9 block), and for each sub-step query Sable's sub-level collision (via
   `SableBridge`/`SubLevelResolver` — you already have these; extend `SableBridge` with a method to get a
   sub-level's collision shapes near a point/box if the existing API doesn't already expose one cleanly) —
   if any sub-step's position would be embedded inside solid sub-level geometry that the straight-line
   AABB-vs-final-position check missed, clamp the player to the last valid sub-step position and zero the
   velocity component that would have carried it through (mirroring what vanilla's own swept collision does
   for ordinary blocks).
2. This must be **defensive and conservative**: never fight vanilla's own vertical/horizontal collision
   resolution when it already works fine (ordinary ground, ordinary blocks, slow speeds) — only intervene when
   your own investigation shows the existing Sable collision path was actually bypassed. If you find that
   Sable's collision shapes ARE already swept-safe and the real bug is something else entirely (e.g. purely a
   sub-level pose being one tick stale relative to the player's already-resolved collision, or a mixin
   ordering issue with our OWN `HookTransformResolver`/`ReelControl` teleporting the player abruptly via
   `ReelControl.apply()`'s velocity manipulation, causing a huge single-tick displacement that bypasses
   Sable's per-tick shape refresh), pivot the fix to address whatever you actually find, and document your
   real findings (with file/line references) in the class javadoc and in `CHANGES.md` instead of the
   speculative approach above. Do not ship a fix that "papers over" symptoms without a grounded mechanism —
   if genuinely uncertain after real investigation, implement the conservative sub-stepped re-check above as a
   safety net (it is harmless even if not strictly the root cause: it only ever clamps a player who was about
   to end up inside solid geometry) and clearly label it as a defensive mitigation in comments, not a
   guaranteed root-cause fix, explaining exactly what you found and didn't find.

Only apply this guard when the player is near/on a sub-level (cheap check via `SubLevelResolver`/
`SableBridge.getAllSubLevels` — skip all the extra work entirely in ordinary terrain, this must not add
overhead to normal vanilla gameplay away from ships).

## After both fixes

1. Build: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew clean build -x test` — must end
   `BUILD SUCCESSFUL`. Fix any compile errors yourself (check the actual class/method names in the jars with
   `javap` or CFR decompile if something doesn't exist as expected — do NOT guess API names, always verify
   against the actual jar contents first: `unzip -l libs/sable-neoforge-1.21.1-1.2.2.jar | grep -i entitysublevelutil`
   and decompile with `java -jar /tmp/cfr.jar <extracted .class> --outputdir /tmp/check` to see the real,
   de-obfuscated (Sable ships real source names, not obfuscated like AOT) method signatures before calling
   them).
2. Update `CHANGES.md` with a new `## Stage 1 fixes (spear render, high-speed clipping)` section: what was
   wrong (root cause, with file:line references), what you changed (files touched), and — for bug 2
   especially — an honest account of what you verified vs. what remains a best-effort mitigation.
3. `git add -A && git commit -m "fix: stage 1 - spear render interpolation + high-speed sub-level clipping"`.
4. Report back: final `git diff --stat HEAD~1`, the build outcome, and a plain-language summary in Russian of
   both root causes and fixes (the user communicates in Russian) — 1 short paragraph per bug, no fluff.
