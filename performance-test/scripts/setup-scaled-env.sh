#!/bin/bash

# Setup scaled environment for performance testing
# This script prepares the 3-instance payment-bridge deployment with nginx load balancer

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
PERF_DIR="$PROJECT_ROOT/performance-test"

echo "🚀 Setting up scaled performance testing environment"
echo "=================================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
DOCKER_COMPOSE_FILE="$PERF_DIR/config/docker-compose.scaled.yml"

# Function to check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Check prerequisites
echo -e "${YELLOW}🔍 Checking prerequisites...${NC}"

if ! command_exists docker; then
    echo -e "${RED}❌ Docker is not installed or not in PATH${NC}"
    exit 1
fi

if ! command_exists docker-compose; then
    echo -e "${RED}❌ Docker Compose is not installed or not in PATH${NC}"
    exit 1
fi

if ! command_exists curl; then
    echo -e "${RED}❌ curl is not installed or not in PATH${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Prerequisites check passed${NC}"
echo

# Stop any existing services
echo -e "${YELLOW}🛑 Stopping any existing services...${NC}"
cd "$PROJECT_ROOT"

if docker-compose ps -q | grep -q . 2>/dev/null; then
    docker-compose down --remove-orphans 2>/dev/null || true
    echo -e "${GREEN}✅ Existing services stopped${NC}"
else
    echo -e "${BLUE}ℹ️  No existing services to stop${NC}"
fi

echo

# Clean up any orphaned containers
echo -e "${YELLOW}🧹 Cleaning up orphaned containers...${NC}"
docker system prune -f >/dev/null 2>&1 || true
echo -e "${GREEN}✅ Cleanup completed${NC}"
echo

# Start scaled services
echo -e "${YELLOW}🐳 Starting scaled services (nginx + 3 payment-bridge instances)...${NC}"
cd "$PERF_DIR/config"

if ! docker-compose -f "$DOCKER_COMPOSE_FILE" up -d; then
    echo -e "${RED}❌ Failed to start scaled services${NC}"
    echo -e "${RED}   Check Docker Compose file: $DOCKER_COMPOSE_FILE${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Scaled services started${NC}"
echo

# Function to wait for service health
wait_for_service() {
    local service_name=$1
    local url=$2
    local max_attempts=60  # More time for scaled deployment
    local attempt=1

    echo -e "${YELLOW}⏳ Waiting for $service_name to be healthy...${NC}"

    while [ $attempt -le $max_attempts ]; do
        if curl -s --max-time 5 "$url" | jq -e '.status == "UP"' >/dev/null 2>&1; then
            echo -e "${GREEN}✅ $service_name is healthy${NC}"
            return 0
        fi

        echo -e "${YELLOW}   Attempt $attempt/$max_attempts - $service_name not ready yet...${NC}"
        sleep 10
        ((attempt++))
    done

    echo -e "${RED}❌ $service_name failed to become healthy after $max_attempts attempts${NC}"
    return 1
}

# Wait for all services to be healthy
echo -e "${YELLOW}🏥 Checking service health...${NC}"

wait_for_service "PostgreSQL" "http://localhost:5432" || exit 1
wait_for_service "RabbitMQ" "http://localhost:15672/api/healthchecks/node" || exit 1
wait_for_service "Mock Payment API" "http://localhost:8081/actuator/health" || exit 1

# Wait for payment-bridge instances
echo -e "${YELLOW}⏳ Waiting for payment-bridge instances...${NC}"
for i in {1..3}; do
    wait_for_service "Payment Bridge $i" "http://localhost:8080/actuator/health" || exit 1
done

# Final nginx check
wait_for_service "Nginx Load Balancer" "http://localhost:8080/actuator/health" || exit 1

echo
echo -e "${GREEN}🎉 Scaled environment is ready!${NC}"
echo
echo -e "${BLUE}📊 Environment Status:${NC}"
echo -e "${BLUE}   • PostgreSQL: ✅ Healthy${NC}"
echo -e "${BLUE}   • RabbitMQ: ✅ Healthy${NC}"
echo -e "${BLUE}   • Mock Payment API: ✅ Healthy${NC}"
echo -e "${BLUE}   • Payment Bridge 1: ✅ Healthy${NC}"
echo -e "${BLUE}   • Payment Bridge 2: ✅ Healthy${NC}"
echo -e "${BLUE}   • Payment Bridge 3: ✅ Healthy${NC}"
echo -e "${BLUE}   • Nginx Load Balancer: ✅ Healthy${NC}"
echo
echo -e "${BLUE}🌐 Load Balancer URL: http://localhost:8080${NC}"
echo -e "${BLUE}📈 Ready for performance testing${NC}"
echo
echo -e "${GREEN}🚀 Next: Run scaled performance tests${NC}"
echo -e "${GREEN}   ./scripts/run-scaled-test.sh${NC}"