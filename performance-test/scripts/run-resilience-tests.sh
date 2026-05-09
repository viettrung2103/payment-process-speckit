#!/bin/bash

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}"
echo "╔════════════════════════════════════════════════════════════════╗"
echo "║          💪 Payment System Resilience Test Suite 💪           ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo -e "${NC}"
echo

# Menu
echo -e "${YELLOW}Select test to run:${NC}"
echo "  1) Offline Recovery Integration Test (2 phases, ~2 minutes)"
echo "  2) Random Shutdown Performance Test (2 phases, ~2-3 minutes)"
echo "  3) Quick Single Instance Shutdown Test (~40 seconds)"
echo "  4) Quick Multi Instance Shutdown Test (~40 seconds)"
echo "  5) Full Single Instance Shutdown Test (~2 minutes)"
echo "  6) Full Multi Instance Shutdown Test (~2 minutes)"
echo "  7) Run all tests sequentially"
echo
read -p "Enter choice (1-7): " choice

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

case $choice in
    1)
        echo
        echo -e "${YELLOW}Running: Offline Recovery Integration Test${NC}"
        bash "$SCRIPT_DIR/run-offline-recovery-test.sh"
        ;;
    2)
        echo
        echo -e "${YELLOW}Running: Random Shutdown Performance Test${NC}"
        bash "$SCRIPT_DIR/run-random-shutdown-test.sh"
        ;;
    3)
        echo
        echo -e "${YELLOW}Running: Quick Single Instance Shutdown Test${NC}"
        bash "$SCRIPT_DIR/run-quick-single-instance-test.sh"
        ;;
    4)
        echo
        echo -e "${YELLOW}Running: Quick Multi Instance Shutdown Test${NC}"
        bash "$SCRIPT_DIR/run-quick-multi-instance-test.sh"
        ;;
    5)
        echo
        echo -e "${YELLOW}Running: Full Single Instance Shutdown Test${NC}"
        bash "$SCRIPT_DIR/run-full-single-instance-test.sh"
        ;;
    6)
        echo
        echo -e "${YELLOW}Running: Full Multi Instance Shutdown Test${NC}"
        bash "$SCRIPT_DIR/run-full-multi-instance-test.sh"
        ;;
    7)
        echo
        echo -e "${YELLOW}Running all tests sequentially...${NC}"
        echo

        echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
        echo -e "${YELLOW}TEST 1: Offline Recovery Integration Test${NC}"
        echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
        bash "$SCRIPT_DIR/run-offline-recovery-test.sh"

        echo
        echo -e "${YELLOW}Waiting 5 seconds before next test...${NC}"
        sleep 5
        echo

        echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
        echo -e "${YELLOW}TEST 2: Random Shutdown Performance Test${NC}"
        echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
        bash "$SCRIPT_DIR/run-random-shutdown-test.sh"

        echo
        echo -e "${YELLOW}Waiting 5 seconds before next test...${NC}"
        sleep 5
        echo

        echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
        echo -e "${YELLOW}TEST 3: Quick Single Instance Shutdown Test${NC}"
        echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
        bash "$SCRIPT_DIR/run-quick-single-instance-test.sh"

        echo
        echo -e "${YELLOW}Waiting 5 seconds before next test...${NC}"
        sleep 5
        echo

        echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
        echo -e "${YELLOW}TEST 4: Quick Multi Instance Shutdown Test${NC}"
        echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
        bash "$SCRIPT_DIR/run-quick-multi-instance-test.sh"

        echo
        echo -e "${YELLOW}Waiting 5 seconds before next test...${NC}"
        sleep 5
        echo

        echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
        echo -e "${YELLOW}TEST 5: Full Single Instance Shutdown Test${NC}"
        echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
        bash "$SCRIPT_DIR/run-full-single-instance-test.sh"

        echo
        echo -e "${YELLOW}Waiting 5 seconds before next test...${NC}"
        sleep 5
        echo

        echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
        echo -e "${YELLOW}TEST 6: Full Multi Instance Shutdown Test${NC}"
        echo -e "${BLUE}════════════════════════════════════════════════════════════${NC}"
        bash "$SCRIPT_DIR/run-full-multi-instance-test.sh"

        echo
        echo -e "${GREEN}════════════════════════════════════════════════════════════${NC}"
        echo -e "${GREEN}🎉 All 6 resilience tests completed successfully!${NC}"
        echo -e "${GREEN}════════════════════════════════════════════════════════════${NC}"
        ;;
    *)
        echo -e "${RED}Invalid choice${NC}"
        exit 1
        ;;
esac

echo
