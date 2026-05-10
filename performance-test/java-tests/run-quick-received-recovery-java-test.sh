#!/bin/bash
set -e

if command -v java >/dev/null 2>&1; then
    JAVA_CMD=java
elif [[ -n "$JAVA_HOME" && -x "$JAVA_HOME/bin/java" ]]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
else
    echo "Java not found. Install Java 21+ or set JAVA_HOME."
    exit 1
fi

if command -v javac >/dev/null 2>&1; then
    JAVAC_CMD=javac
elif [[ -n "$JAVA_HOME" && -x "$JAVA_HOME/bin/javac" ]]; then
    JAVAC_CMD="$JAVA_HOME/bin/javac"
else
    echo "Javac not found. Install JDK 21+ or set JAVA_HOME."
    exit 1
fi

echo "Compiling QuickSingleInstanceReceivedRecoveryTestRunner.java..."
$JAVAC_CMD QuickSingleInstanceReceivedRecoveryTestRunner.java

echo "Running simulation..."
$JAVA_CMD QuickSingleInstanceReceivedRecoveryTestRunner
