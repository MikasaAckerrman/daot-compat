#!/bin/bash
#
# ODM AERONAUTICS COMPAT v1.3.5 - COMPILE INSTRUCTIONS
# Этот скрипт компилирует мод в JAR файл
#

echo "╔═══════════════════════════════════════════════════════════════╗"
echo "║  ODM AERONAUTICS COMPAT v1.3.5 - COMPILATION SCRIPT           ║"
echo "╚═══════════════════════════════════════════════════════════════╝"
echo ""

# 1. Check Java version
echo "1️⃣  Checking Java version..."
JAVA_VERSION=$(java -version 2>&1 | head -1)
echo "   Current: $JAVA_VERSION"

# Check if Java 17 or higher
JAVA_VERSION_NUM=$(java -version 2>&1 | sed 's/.*version "\([^.]*\).*/\1/')
if [ "$JAVA_VERSION_NUM" -lt 17 ]; then
    echo "   ❌ ERROR: Java 17 or higher is required!"
    echo ""
    echo "   📥 How to fix:"
    echo "   • Ubuntu/Debian: sudo apt-get install openjdk-17-jdk"
    echo "   • macOS: brew install openjdk@17"
    echo "   • Windows: Download from https://adoptopenjdk.net/"
    echo ""
    exit 1
fi

echo "   ✅ Java version OK"
echo ""

# 2. Check Gradle
echo "2️⃣  Checking Gradle..."
if ! command -v gradle &> /dev/null && [ ! -f "./gradlew" ]; then
    echo "   ❌ ERROR: Gradle wrapper not found!"
    exit 1
fi
echo "   ✅ Gradle found"
echo ""

# 3. Clean and build
echo "3️⃣  Building project..."
echo "   This may take a few minutes..."
echo ""

if [ -f "./gradlew" ]; then
    ./gradlew clean build
else
    gradle clean build
fi

BUILD_STATUS=$?

echo ""
echo "─────────────────────────────────────────────────────────────"

if [ $BUILD_STATUS -eq 0 ]; then
    echo "✅ BUILD SUCCESSFUL!"
    echo ""
    echo "📦 Output JAR:"
    ls -lh build/libs/*.jar 2>/dev/null | tail -1
    echo ""
    echo "📍 Location: build/libs/"
    echo ""
    echo "Next steps:"
    echo "1. Copy JAR to Minecraft mods folder"
    echo "2. Make sure Danny's AOT is installed"
    echo "3. Launch Minecraft and test!"
else
    echo "❌ BUILD FAILED!"
    echo ""
    echo "Troubleshooting:"
    echo "• Make sure Java 17+ is installed"
    echo "• Try: ./gradlew clean build --stacktrace"
    echo "• Check that all dependencies are available"
fi

exit $BUILD_STATUS
