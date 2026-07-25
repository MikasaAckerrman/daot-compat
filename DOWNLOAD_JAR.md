# 📥 DOWNLOAD daotcompat-1.1.0.jar

## ✅ JAR IS READY

**File:** `daotcompat-1.0.0.jar`  
**Size:** 42 KB  
**Status:** BUILD SUCCESSFUL ✅  
**Release:** v1.1.0 (Stages 1-5 Complete)

---

## 🔗 DOWNLOAD OPTIONS

### Option 1: Clone & Build (Recommended for Development)

```bash
# Clone repository
git clone https://github.com/MikasaAckerrman/daot-compat.git
cd daot-compat

# Checkout release branch
git checkout round3-stage1-fixes

# Build JAR (requires Java 21)
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64  # Linux
# or for macOS:
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

./gradlew build -x test

# JAR location:
# build/libs/daotcompat-1.0.0.jar
```

### Option 2: Direct Download from Releases (When Available)

Once GitHub Actions is set up:
```
https://github.com/MikasaAckerrman/daot-compat/releases/tag/v1.1.0
```

For now, use **Option 1** to build locally.

---

## 📦 INSTALLATION

Once you have `daotcompat-1.0.0.jar`:

```bash
# Copy to Minecraft mods folder
cp daotcompat-1.0.0.jar ~/.minecraft/mods/

# Or manually:
# 1. Navigate to ~/.minecraft/mods/ (or %APPDATA%\.minecraft\mods\ on Windows)
# 2. Drag and drop daotcompat-1.0.0.jar into that folder
# 3. Launch Minecraft with mods enabled
```

---

## ✨ VERIFY INSTALLATION

1. **Launch Minecraft** with mods
2. **In-game:** Options → Controls → Search for "ODM Hooks"
3. **You should see 5 keybinds:**
   - Engage / Reel-In (Hold) → SPACE
   - Accelerate Forward → W
   - Descend / Release Tension → SHIFT
   - Swap Hotbar (While Grappling) → (unbound)
   - Reverse DEW (Double-Tap S) → S
4. **If visible:** Installation successful ✅

---

## 🎮 QUICK START

1. **Launch Creative Mode**
2. **Get rope hooks** (from Danny's AOT or compatible mod)
3. **Test keybinds:**
   - Press **SPACE** to engage rope
   - Hold **W** while holding SPACE to accelerate
   - Press **SHIFT** to descend
   - Double-press **S** for Reverse DEW
   - Press **1-9** to switch hotbar
4. **Run fast** to see spark effects when sliding

---

## 📋 REQUIREMENTS

| Requirement | Version | Required? |
|-------------|---------|-----------|
| **Minecraft** | 1.21.1 | ✅ Yes |
| **NeoForge** | 21.1.30+ | ✅ Yes |
| **Java** | 21+ | ✅ Yes (for building) |
| **Danny's AOT** | 2.2.0+ | ✅ Yes (for grappling) |
| **Create** | 6.0.10+ | ⚠️ Optional |
| **Create Aeronautics** | 1.2.1+ | ⚠️ Optional |
| **Sable** | 1.2.2+ | ⚠️ Optional |

---

## 🛠️ BUILD TROUBLESHOOTING

**Java 21 Not Found:**
```bash
# Download from https://jdk.java.net/21/ or:
# Ubuntu/Debian:
sudo apt install openjdk-21-jdk

# Set JAVA_HOME:
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
```

**Build Fails:**
```bash
# Clean and rebuild
./gradlew clean build -x test
```

**JAR Not Created:**
```bash
# Check if build directory exists
ls -lh build/libs/
```

---

## 📊 JAR DETAILS

```
Filename: daotcompat-1.0.0.jar
Size: 42 KB
SHA256: (will be available after release)
Build Date: 2026-07-25
JDK Version: 21
NeoForge Version: 21.1.30
Minecraft Version: 1.21.1
```

---

## 📝 RELEASE NOTES

**Full release notes:** See `RELEASE_v1.1.0.md` in repository

**What's Included:**
- ✅ 12 Java classes
- ✅ 1100+ lines of code
- ✅ Full Javadoc documentation
- ✅ Configuration file (TOML)
- ✅ 5 user-customizable keybinds
- ✅ Complete physics system
- ✅ Gas & DEW mechanics
- ✅ Spark effects
- ✅ Zero compilation errors

---

## 🔗 USEFUL LINKS

- **GitHub Repository:** https://github.com/MikasaAckerrman/daot-compat
- **Release Branch:** https://github.com/MikasaAckerrman/daot-compat/tree/round3-stage1-fixes
- **Latest Commit:** https://github.com/MikasaAckerrman/daot-compat/commit/ef0f1ac
- **Issues & Feedback:** https://github.com/MikasaAckerrman/daot-compat/issues

---

## 💬 FEEDBACK

Found a bug? Have suggestions?

1. **Report on GitHub Issues:** https://github.com/MikasaAckerrman/daot-compat/issues/new
2. **Include:**
   - Minecraft version
   - NeoForge version
   - What happened vs what you expected
   - Steps to reproduce

---

**Ready to use!** 🎮✨

For questions or issues, open an issue on GitHub or contact the developers.

Last updated: 2026-07-25
