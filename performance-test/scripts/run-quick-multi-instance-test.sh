#!/bin/bash

# Quick Multi Instance Shutdown Test Script

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m'

# Detect Java
if command -v java >/dev/null 2>&1; then
    JAVA_CMD="java"
elif [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
else
    echo -e "${RED}❌ Java not found. Please install Java 21+${NC}"
    exit 1
fi

# Check Java version
echo -e "${BLUE}✅ Using Java: $($JAVA_CMD -version 2>&1 | head -1)${NC}"

echo -e "${PURPLE}⚡ Quick Multiple Instance Shutdown Test${NC}"
echo -e "${PURPLE}════════════════════════════════════════════════════════════${NC}"
echo
echo -e "${CYAN}📋 Configuration:${NC}"
echo -e "   • Scenario: Multiple instances with independent random shutdowns"
echo -e "   • Number of instances: 3"
echo -e "   • Max shutdowns per instance: 4 (up to 5 seconds each)"
echo -e "   • Test duration: ~40 seconds"
echo -e "   • Load balancing: Round-robin"
echo
echo -e "${YELLOW}🔨 Compiling standalone test runner...${NC}"
if ! $JAVA_CMD -cp . QuickMultiInstanceShutdownTestRunner.java 2>/dev/null; then
    echo -e "${GREEN}✅ Compilation successful${NC}"
else
    echo -e "${RED}❌ Compilation failed${NC}"
    exit 1
fi
echo
echo -e "${YELLOW}▶️  Running test...${NC}"
echo -e "${PURPLE}════════════════════════════════════════════════════════════${NC}"

$JAVA_CMD QuickMultiInstanceShutdownTestRunner
