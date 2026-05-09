#!/bin/bash
# Java Environment Setup Script for Payment System Speckit
# This script sets up the correct Java environment for the project

echo "🔧 Setting up Java environment..."

# Check if Java 21 is available
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d\" -f2 | cut -d. -f1)
    echo "✅ Java found: version $JAVA_VERSION"
    
    if [ "$JAVA_VERSION" = "21" ]; then
        echo "✅ Java 21 detected"
    else
        echo "⚠️  Warning: Java version $JAVA_VERSION detected, but Java 21 is recommended"
    fi
else
    echo "❌ Java not found. Please install Java 21"
    exit 1
fi

# Set JAVA_HOME for macOS
if [[ "$OSTYPE" == "darwin"* ]]; then
    export JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null)
    if [ $? -eq 0 ]; then
        echo "✅ JAVA_HOME set to: $JAVA_HOME"
    else
        echo "⚠️  Could not set JAVA_HOME automatically"
    fi
fi

# Verify Maven can run
if command -v mvn &> /dev/null; then
    echo "✅ Maven found"
    mvn --version | head -n 1
else
    echo "❌ Maven not found. Please install Maven"
    exit 1
fi

echo "🎉 Environment setup complete!"
echo "You can now run: mvn clean test"

