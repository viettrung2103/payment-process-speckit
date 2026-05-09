#!/bin/bash

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║  🔥 Random Shutdown Performance Test - Single Instance  🔥  ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
echo

# Check if JAVA_HOME is set
if [ -z "$JAVA_HOME" ]; then
    echo -e "${YELLOW}⚠️  JAVA_HOME not set, attempting to detect...${NC}"
    JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home 2>/dev/null)
    if [ -z "$JAVA_HOME" ]; then
        echo -e "${RED}❌ ERROR: Could not detect Java home${NC}"
        exit 1
    fi
fi

export JAVA_HOME
echo -e "${GREEN}✅ Using Java: $JAVA_HOME${NC}"

cd "$(dirname "$0")/.."

echo
echo -e "${YELLOW}📊 Test Configuration:${NC}"
echo "  • Test Name: Random Shutdown Performance Test"
echo "  • Scenario: Service randomly shuts down and restarts"
echo "  • Max shutdowns: 2"
echo "  • Shutdown duration: 1-5 seconds (random)"
echo "  • Shutdown intervals: 5-20 seconds (random)"
echo

# Compile the standalone test runner
echo -e "${YELLOW}🔨 Compiling standalone test runner...${NC}"
javac -encoding UTF-8 RandomShutdownTestRunner.java 2>&1 | head -20

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Compilation failed${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Compilation successful${NC}"
echo

# Run the test
echo -e "${YELLOW}▶️  Running test (this may take 1-2 minutes)...${NC}"
echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
echo

java RandomShutdownTestRunner

TEST_EXIT_CODE=$?

echo
echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"

if [ $TEST_EXIT_CODE -eq 0 ]; then
    echo -e "${GREEN}✅ Test PASSED - System handled random shutdowns gracefully!${NC}"
else
    echo -e "${RED}❌ Test FAILED${NC}"
    exit 1
fi

echo
echo -e "${GREEN}✨ Random Shutdown Test Complete!${NC}"
