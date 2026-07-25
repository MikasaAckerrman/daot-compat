# DAOT COMPAT - STAGE 2 SETUP & ENVIRONMENT GUIDE

**Purpose:** One-time setup to avoid repetitive environment preparation  
**Target:** Developers continuing Stage 2 implementation  
**Last Updated:** 2026-07-25

---

## 🛠️ PREREQUISITE CHECKLIST

### Required Software
- [ ] **JDK 21** (exact version: OpenJDK 21 or Oracle JDK 21)
  - Verify: `java -version` → should show `21.x.x`
  - If missing: Install from https://jdk.java.net/21/ or your package manager
  - Set `JAVA_HOME`: 
    ```bash
    export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
    # For macOS:
    export JAVA_HOME=$(/usr/libexec/java_home -v 21)
    ```

- [ ] **Git** (version 2.30+)
  - Verify: `git --version`

- [ ] **Gradle** (version 8.0+)
  - Verify: `./gradlew --version` (should be in repo)

### IDE (Choose One)
- [ ] **IntelliJ IDEA** (recommended, CE free version works)
  - Community Edition: https://www.jetbrains.com/idea/download/
  - Open project: `File → Open → /path/to/daot-compat`
  - Let Gradle sync automatically
  
- [ ] **VS Code** with Extensions
  - Extension Pack for Java (Microsoft)
  - Gradle for Java (Microsoft)

---

## 📁 REPOSITORY STRUCTURE (CURRENT STATE)

```
daot-compat/
├── .gradle/                    # Gradle cache (auto-generated, don't touch)
├── .git/                       # Git history
├── build/                      # Build output (IGNORE, in .gitignore)
├── gradle/
│   └── wrapper/                # Gradle wrapper files (commit to repo)
├── libs/                       # Mod JARs (dependencies)
│   ├── create-1.21.1-6.0.10.jar
│   ├── create-aeronautics-1.21.1-1.2.1.jar
│   ├── sable-neoforge-1.21.1-1.2.2.jar
│   └── dannys-aot-fabric-1.21.1-2.2.0.jar
├── src/
│   ├── main/java/com/armorberserk/daotcompat/
│   │   ├── DynamicHookData.java
│   │   ├── HookTransformResolver.java
│   │   ├── RemoteHookFollower.java
│   │   ├── ThunderSpearFollower.java
│   │   ├── mixin/
│   │   ├── reflect/
│   │   ├── render/
│   │   ├── util/
│   │   └── [more packages]
│   ├── main/resources/
│   │   ├── daotcompat.mixins.json
│   │   ├── META-INF/mods.toml
│   │   ├── assets/daotcompat/
│   │   │   ├── lang/
│   │   │   │   ├── en_us.json
│   │   │   │   └── ru_ru.json
│   │   │   └── textures/
│   │   └── config/
│   └── test/  # (if testing added later)
├── build.gradle                # Main Gradle build config
├── settings.gradle             # Gradle project settings
├── gradle.properties           # Gradle properties
├── .gitignore                  # Git ignore rules
├── README.md                   # Original project README
├── PLAN.txt                    # Handoff notes from Round 3 Stage 1
├── CHANGES.md                  # Stage 1 changelog
├── ROUND3_STAGE2_DEV_PLAN.md   # THIS PLAN (new)
└── STAGE2_SETUP_GUIDE.md       # This file (new)
```

---

## ⚡ QUICK START (For Returning Developers)

If you've already cloned the repo and have JDK 21:

```bash
# 1. Navigate to repo
cd ~/workspace/daot-compat

# 2. Verify branch
git branch -a
git checkout feature/stage2-keybind-system  # or whatever branch you're on

# 3. Pull latest changes
git pull origin feature/stage2-keybind-system

# 4. Build to verify setup
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew build -x test

# 5. Expected output:
#    BUILD SUCCESSFUL in Xs
#    10 actionable tasks: 5 executed, 5 up-to-date
```

If build fails, see **Troubleshooting** section below.

---

## 📦 DEPENDENCY MANAGEMENT

### Current Dependencies (DO NOT CHANGE)

These are fetched automatically by Gradle:

1. **Create 6.0.10** (mod)
   - NeoForge mod for contraptions
   - Location: `libs/create-1.21.1-6.0.10.jar`

2. **Create Aeronautics 1.2.1** (mod)
   - Sublevels (moving ships) support
   - Location: `libs/create-aeronautics-1.21.1-1.2.1.jar`

3. **Sable (NeoForge) 1.2.2** (mod)
   - Physics engine for rope
   - Location: `libs/sable-neoforge-1.21.1-1.2.2.jar`

4. **Danny's AOT 2.2.0 (Fabric)** (mod via Sinytra)
   - Grappling hook core
   - Location: `libs/dannys-aot-fabric-1.21.1-2.2.0.jar`

5. **NeoForge 1.21.1** (framework)
   - Downloaded automatically

### If Dependencies Are Missing

```bash
# 1. Check libs/ folder
ls -lah libs/

# 2. If empty or incomplete, re-download:
cd libs/
# Manual download from Modrinth API:
# (Detailed commands in build.gradle comments)

# 3. Or delete build cache and rebuild:
rm -rf .gradle build
./gradlew clean build -x test
```

---

## 🎯 BUILD COMMANDS QUICK REFERENCE

```bash
# Build JAR (no tests)
./gradlew build -x test
# Output: build/libs/daotcompat-1.0.0.jar

# Clean build
./gradlew clean build -x test

# Run IDE download (IntelliJ/VSCode)
./gradlew idea  # or "gradlew eclipse" for Eclipse

# Check for dependency updates
./gradlew dependencyUpdates

# Build and get detailed output
./gradlew build -x test --info

# If build fails: clear caches
./gradlew clean
rm -rf .gradle
./gradlew build -x test
```

---

## 🐛 TROUBLESHOOTING

### Error: "JDK version mismatch" or "Java 21 not found"

**Solution:**
```bash
# Find Java 21 on your system
find /usr -name "java-21*" -type d 2>/dev/null

# Set JAVA_HOME explicitly
export JAVA_HOME=/path/to/java-21
./gradlew --version  # Verify

# Add to ~/.bashrc or ~/.zshrc for persistence:
echo 'export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64' >> ~/.bashrc
source ~/.bashrc
```

### Error: "Gradle wrapper not executable"

**Solution:**
```bash
chmod +x ./gradlew
./gradlew --version
```

### Error: "Could not find libs/create-*.jar"

**Solution:**
```bash
# Verify libs/ exists and has all 4 JARs
ls -lah libs/

# If missing, re-download manually or from GitHub:
cd ~/workspace/daot-compat
git checkout round3-stage1-fixes  # This branch has libs/
git checkout feature/stage2-keybind-system
cp ~/workspace/daot-compat/.../libs/*.jar ./libs/

# Then rebuild
./gradlew clean build -x test
```

### Error: "BUILD FAILED - compilation errors"

**Most likely:** Missing imports or syntax errors  
**Solution:**
```bash
# Check error output (last 20 lines usually have the problem)
./gradlew build -x test 2>&1 | tail -50

# Open file mentioned in error and fix import/syntax
# IntelliJ: Ctrl+Shift+O (auto-fix imports)
# Then rebuild
./gradlew build -x test
```

### Error: "Out of memory" during build

**Solution:**
```bash
# Increase Gradle heap
export GRADLE_OPTS="-Xmx2g"
./gradlew build -x test
```

---

## 📝 DEVELOPMENT WORKFLOW

### Starting a New Feature (e.g., Keybind System)

```bash
# 1. Ensure you're on main/latest
git checkout round3-stage1-fixes
git pull origin round3-stage1-fixes

# 2. Create feature branch
git checkout -b feature/stage2-keybind-system

# 3. Create Phase 1 files (see Stage 2 Dev Plan)
# Edit: src/main/java/com/armorberserk/daotcompat/input/GrappleKeybinds.java
# Edit: src/main/java/com/armorberserk/daotcompat/input/GrappleStateManager.java
# ... etc

# 4. Build to verify no syntax errors
./gradlew build -x test

# 5. Commit with clear message
git add src/
git commit -m "feat: keybind infrastructure (phase 1)" -m "
- GrappleKeybinds: register PULL_ROPE, ACCELERATE, DESCEND_ROPE, SWAP_HOTBAR
- GrappleStateManager: track active keybind states
- KeybindEventListener: hook into ClientTickEvent.Post
"

# 6. Push to GitHub
git push origin feature/stage2-keybind-system

# 7. Repeat steps 3-6 for each phase
```

### Syncing Latest Changes

```bash
# If main branch updated:
git fetch origin
git merge origin/round3-stage1-fixes
# Resolve conflicts if any
./gradlew clean build -x test
```

### Before Opening Pull Request

```bash
# 1. Verify all code compiles
./gradlew build -x test

# 2. Check code style (optional but recommended)
./gradlew spotlessCheck  # if configured

# 3. Verify no debug prints left
grep -r "System.out.println\|println\|debug" src/main/java/

# 4. Create clean commit history
git log --oneline origin/round3-stage1-fixes..HEAD
# (Should be 5-10 commits, each with clear message)

# 5. Push final changes
git push origin feature/stage2-keybind-system

# 6. Create PR on GitHub with full description
```

---

## 🎨 IDE SETUP (OPTIONAL BUT RECOMMENDED)

### IntelliJ IDEA Setup

```
1. File → Open → ~/workspace/daot-compat
2. Wait for Gradle sync (bottom right notification)
3. Trust the project if prompted
4. File → Project Structure → Project
   - SDK: JDK 21
   - Language level: 21
5. Build → Build Project (Ctrl+F9)
```

**Useful Shortcuts:**
- `Ctrl+Shift+O` — Optimize imports
- `Ctrl+Alt+L` — Reformat code
- `Shift+F6` — Rename refactoring
- `Ctrl+F` — Find in file
- `Ctrl+H` — Replace in file
- `Ctrl+Shift+F` — Find in project

### VS Code Setup

Install extensions:
- Extension Pack for Java (Microsoft)
- Gradle for Java (Microsoft)
- Minecraft Development (Earthcomputer) — optional, syntax highlighting

Then:
```
1. File → Open Folder → ~/workspace/daot-compat
2. Trust the workspace
3. Gradle will auto-sync
4. View → Command Palette (Ctrl+Shift+P)
5. Type "Java: Create New Java Project"
6. Or just open any .java file, it will index automatically
```

---

## 📋 FILE CHECKLIST FOR STAGE 2 PHASES

After implementing each phase, verify these files exist:

### Phase 1: Keybind Infrastructure
- [ ] `src/main/java/com/armorberserk/daotcompat/input/GrappleKeybinds.java`
- [ ] `src/main/java/com/armorberserk/daotcompat/input/GrappleStateManager.java`
- [ ] `src/main/java/com/armorberserk/daotcompat/input/KeybindEventListener.java`
- [ ] Updated: `src/main/resources/assets/daotcompat/lang/en_us.json` (add keybind keys)

### Phase 2: Physics Controller
- [ ] `src/main/java/com/armorberserk/daotcompat/physics/GrapplePhysicsController.java`
- [ ] `src/main/java/com/armorberserk/daotcompat/physics/RopeLengthValidator.java`
- [ ] Deleted: `src/main/java/com/armorberserk/daotcompat/input/ReelControl.java` (old code)
- [ ] Updated: `src/main/java/com/armorberserk/daotcompat/HookTransformResolver.java` (call new controller)

### Phase 3: Rope Physics
- [ ] `src/main/java/com/armorberserk/daotcompat/physics/SableRopePhysicsIntegration.java`
- [ ] Created: `src/main/resources/config/daotcompat-keybinds.toml` (default config)

### Phase 4: Hotbar
- [ ] `src/main/java/com/armorberserk/daotcompat/input/HotbarSwapHandler.java`

### Phase 5: Rendering
- [ ] Updated: `src/main/java/com/armorberserk/daotcompat/render/HookLineRenderer.java`
- [ ] Created: `src/main/java/com/armorberserk/daotcompat/render/RopeStateIndicator.java` (optional)

### Phase 6: Testing
- [ ] All unit tests pass: `./gradlew test`
- [ ] Integration test in-game: can grapple + keybinds work

---

## 🚀 DEPLOYMENT CHECKLIST

Once all phases complete:

```bash
# 1. Final build
./gradlew clean build -x test

# 2. Verify JAR size reasonable
ls -lh build/libs/daotcompat-1.0.0.jar
# Should be ~100-200 KB (not 5+ MB)

# 3. Test in actual Minecraft
# Copy to: ~/.minecraft/mods/daotcompat-1.0.0.jar
# Start game with mods + keybinds should appear in controls

# 4. Create release
git tag -a v1.1.0 -m "Release: Stage 2 keybind system"
git push origin v1.1.0

# 5. Create release on GitHub
# Go to: https://github.com/MikasaAckerrman/daot-compat/releases
# Upload: build/libs/daotcompat-1.0.0.jar
# Write changelog (reference ROUND3_STAGE2_DEV_PLAN.md)
```

---

## 📚 REFERENCE MATERIALS

### In This Repository
- **ROUND3_STAGE2_DEV_PLAN.md** — Full technical specification (READ THIS FIRST)
- **PLAN.txt** — Handoff notes from Stage 1
- **CHANGES.md** — What Stage 1 did (context)
- **README.md** — Original project overview

### NeoForge Documentation
- Keybinds: https://docs.neoforged.net/docs/input/keybinds
- Events: https://docs.neoforged.net/docs/concepts/events
- Mixins: https://docs.neoforged.net/docs/advanced/mixin

### External Mods (Reference)
- **GrappleHook 1.21.1** — Physics reference
  - Decompiled classes: `~/workspace/grapple_ref/decompiled/`
- **Sable Physics** — Rope API reference
  - GitHub: https://github.com/ryanhcode/Sable
- **Create Aeronautics** — Sublevels (moving objects)
  - GitHub: https://github.com/Belgabor/Create-Aeronautics

---

## 🎯 SUCCESS CRITERIA FOR SETUP

When environment is ready:
- [x] `./gradlew build -x test` returns BUILD SUCCESSFUL
- [x] JAR file created in `build/libs/daotcompat-1.0.0.jar`
- [x] IDE (IntelliJ/VSCode) opens project without errors
- [x] All source files appear in `src/main/java/`
- [x] Git history is clean: `git log --oneline | head -10` shows 5+ commits
- [x] Can create feature branch and make changes without conflicts

---

## 📞 IF SOMETHING GOES WRONG

1. **Check error output carefully** — last 50 lines usually have the root cause
2. **Google the error message** — e.g., "Gradle compilation error [exact error]"
3. **Try clean build:** `./gradlew clean build -x test`
4. **Verify JAVA_HOME:** `echo $JAVA_HOME && java -version`
5. **Check GitHub Issues** in the repo for similar problems
6. **Ask in code comments** — document what you tried and what failed

---

**Created by:** AI Agent (2026-07-25)  
**For:** Next developer continuing Stage 2  
**Status:** Ready for Use
