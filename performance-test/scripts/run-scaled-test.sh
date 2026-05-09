#!/bin/bash

# Phase 9: Performance Testing - Scaled Deployment
# This script runs performance tests against a scaled deployment with nginx load balancer

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
PERF_DIR="$PROJECT_ROOT/performance-test"

echo "🚀 Starting Phase 9: Scaled Performance Testing"
echo "=============================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
TIMESTAMP=$(date +"%Y%m%d-%H%M%S")
RESULTS_DIR="$PERF_DIR/results/scaled-3-instances-$TIMESTAMP"
JMETER_PLAN="$PERF_DIR/jmeter/payment-load-test.jmx"
DOCKER_COMPOSE_FILE="$PERF_DIR/config/docker-compose.scaled.yml"

# Create results directory
mkdir -p "$RESULTS_DIR"

echo -e "${BLUE}📁 Results will be saved to: $RESULTS_DIR${NC}"
echo -e "${BLUE}🕐 Test started at: $(date)${NC}"
echo

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

if ! command_exists jq; then
    echo -e "${RED}❌ jq is not installed or not in PATH${NC}"
    exit 1
fi

if ! command_exists nc; then
    echo -e "${RED}❌ nc (netcat) is not installed or not in PATH${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Prerequisites check passed${NC}"
echo

# Function to wait for service health
wait_for_service() {
    local service_name=$1
    local url=$2
    local expected_status=${3:-"UP"}
    local max_attempts=${4:-60}
    local username=${5:-""}
    local password=${6:-""}
    local attempt=1

    echo -e "${YELLOW}⏳ Waiting for $service_name to be healthy...${NC}"

    while [ $attempt -le $max_attempts ]; do
        local curl_cmd="curl -s"
        if [ -n "$username" ]; then
            curl_cmd="$curl_cmd -u $username:$password"
        fi
        curl_cmd="$curl_cmd \"$url\""
        
        if eval "$curl_cmd" | jq -e ".status == \"$expected_status\"" >/dev/null 2>&1; then
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

# Function to wait for database port
wait_for_db() {
    local service_name=$1
    local host=$2
    local port=$3
    local max_attempts=60
    local attempt=1

    echo -e "${YELLOW}⏳ Waiting for $service_name to be ready...${NC}"

    while [ $attempt -le $max_attempts ]; do
        if nc -z "$host" "$port" >/dev/null 2>&1; then
            echo -e "${GREEN}✅ $service_name is ready${NC}"
            return 0
        fi

        echo -e "${YELLOW}   Attempt $attempt/$max_attempts - $service_name not ready yet...${NC}"
        sleep 10
        ((attempt++))
    done

    echo -e "${RED}❌ $service_name failed to become ready after $max_attempts attempts${NC}"
    return 1
}

# Function to wait for nginx upstream health
wait_for_nginx_health() {
    local max_attempts=30
    local attempt=1

    echo -e "${YELLOW}⏳ Waiting for nginx load balancer to be healthy...${NC}"

    while [ $attempt -le $max_attempts ]; do
        if curl -s "http://localhost:8080/actuator/health" >/dev/null 2>&1; then
            echo -e "${GREEN}✅ Nginx load balancer is healthy${NC}"
            return 0
        fi

        echo -e "${YELLOW}   Attempt $attempt/$max_attempts - nginx not ready yet...${NC}"
        sleep 10
        ((attempt++))
    done

    echo -e "${RED}❌ Nginx load balancer failed to become healthy after $max_attempts attempts${NC}"
    return 1
}

# Start scaled services
echo -e "${YELLOW}🐳 Starting scaled services (3 payment-bridge instances + nginx)...${NC}"
cd "$PERF_DIR/config"

docker-compose -f "$DOCKER_COMPOSE_FILE" down --volumes --remove-orphans || true

echo -e "${YELLOW}🛠️  Rebuilding local scaled Docker images...${NC}"
COMPOSE_BAKE=1 DOCKER_BUILDKIT=1 docker-compose -f "$DOCKER_COMPOSE_FILE" build payment-bridge-1 mock-payment-api

echo -e "${GREEN}✅ Local scaled images rebuilt${NC}"

echo

docker-compose -f "$DOCKER_COMPOSE_FILE" up -d postgres rabbitmq mock-payment-api

echo

if ! wait_for_db "PostgreSQL" "localhost" "5432"; then
    echo -e "${RED}❌ PostgreSQL did not become ready${NC}"
    exit 1
fi
if ! wait_for_service "RabbitMQ" "http://localhost:15672/api/aliveness-test/%2F" ok 10 admin admin; then
    echo -e "${RED}❌ RabbitMQ did not become ready${NC}"
    exit 1
fi
if ! wait_for_service "Mock Payment API" "http://localhost:8081/actuator/health"; then
    echo -e "${RED}❌ Mock Payment API did not become ready${NC}"
    exit 1
fi

docker-compose -f "$DOCKER_COMPOSE_FILE" up -d payment-bridge-1 payment-bridge-2 payment-bridge-3 nginx

if ! docker-compose -f "$DOCKER_COMPOSE_FILE" ps; then
    echo -e "${RED}❌ Failed to start scaled services after dependency initialization${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Scaled services started${NC}"
echo

# Wait for services to be healthy
wait_for_db "PostgreSQL" "localhost" "5432" || exit 1
wait_for_service "RabbitMQ" "http://localhost:15672/api/aliveness-test/%2F" "ok" 10 "admin" "admin" || exit 1
wait_for_service "Mock Payment API" "http://localhost:8081/actuator/health" || exit 1

# Wait for nginx load balancer to be healthy

    echo -e "${BLUE}🧪 Running scaled test: $test_name${NC}"
    echo -e "${BLUE}   Users: $users${NC}"
    echo -e "${BLUE}   Duration: ${DURATION}s${NC}"
    echo -e "${BLUE}   Ramp-up: ${RAMP_UP}s${NC}"
    echo

    # Check if JMeter is available
    if command_exists jmeter; then
        JMETER_CMD="jmeter"
    elif command_exists docker; then
        if ! docker images | grep -q "qainsights/jmeter"; then
            echo "📥 Pulling JMeter Docker image..."
            docker pull qainsights/jmeter:latest
        fi
        if [[ "$(uname -s)" == "Darwin" ]]; then
            JMETER_CMD="docker run --rm -v \"$PROJECT_ROOT\":/workspace qainsights/jmeter:latest"
        else
            JMETER_CMD="docker run --rm --network host -v \"$PROJECT_ROOT\":/workspace qainsights/jmeter:latest"
        fi
    else
        echo -e "${RED}❌ JMeter not found and Docker is not available. Please install JMeter.${NC}"
        return 1
    fi

    # Run JMeter test
    local jmeter_log="$RESULTS_DIR/jmeter-${test_name}.log"
    local jtl_file="$RESULTS_DIR/results-${test_name}.jtl"

    echo -e "${YELLOW}   Executing JMeter test...${NC}"

    # Adjust paths for Docker if needed
    local jmeter_plan_path="$JMETER_PLAN"
    local jtl_file_path="$jtl_file"
    local jmeter_log_path="$jmeter_log"
    
    if [[ "$JMETER_CMD" == *"docker"* ]]; then
        # Use container paths when running in Docker with /workspace mount
        # RESULTS_DIR is like /path/to/results/scaled-<timestamp>
        local results_subdir="${RESULTS_DIR##*/}"
        jmeter_plan_path="/workspace/performance-test/jmeter/payment-load-test.jmx"
        jtl_file_path="/workspace/performance-test/results/$results_subdir/results-${test_name}.jtl"
        jmeter_log_path="/workspace/performance-test/results/$results_subdir/jmeter-${test_name}.log"
    fi

    local target_host="localhost"
    local target_port="8080"
    if [[ "$JMETER_CMD" == *"docker"* && "$(uname -s)" == "Darwin" ]]; then
        target_host="host.docker.internal"
    fi

    # Build the command properly
    local cmd="$JMETER_CMD -n -t \"$jmeter_plan_path\" -JTARGET_HOST=\"$target_host\" -JTARGET_PORT=\"$target_port\" -JUSERS=\"$users\" -JRAMP_UP=\"$RAMP_UP\" -JDURATION=\"$DURATION\" -l \"$jtl_file_path\" -j \"$jmeter_log_path\""

    if eval "$cmd"; then
        echo -e "${GREEN}   ✅ JMeter test completed successfully${NC}"

        # Generate summary
        echo -e "${YELLOW}   📊 Generating summary...${NC}"
        "$SCRIPT_DIR/analyze-results.sh" "$jtl_file" "$RESULTS_DIR/summary-${test_name}.txt"

        # Collect load distribution metrics
        echo -e "${YELLOW}   📊 Collecting load distribution metrics...${NC}"
        "$SCRIPT_DIR/collect-load-distribution.sh" "$RESULTS_DIR" "$test_name"

    else
        echo -e "${RED}   ❌ JMeter test failed${NC}"
        echo -e "${RED}   Check logs: $jmeter_log${NC}"
        return 1
    fi

    echo
}

# Run tests for each load level
for users in "${LOAD_LEVELS[@]}"; do
    if ! run_jmeter_test "$users"; then
        echo -e "${RED}❌ Test failed for $users users. Continuing with next test...${NC}"
    fi

    # Brief pause between tests
    sleep 60
done

# Collect system metrics
echo -e "${YELLOW}📊 Collecting system metrics...${NC}"
"$SCRIPT_DIR/collect-metrics.sh" "$RESULTS_DIR"

# Generate final report
echo -e "${YELLOW}📋 Generating final report...${NC}"
"$SCRIPT_DIR/generate-report.sh" "$RESULTS_DIR" "scaled-3-instances"

echo
echo -e "${GREEN}🎉 Scaled performance testing completed!${NC}"
echo -e "${GREEN}📁 Results available in: $RESULTS_DIR${NC}"
echo -e "${GREEN}🕐 Test completed at: $(date)${NC}"
echo
echo -e "${BLUE}📈 Next: Compare results with single-instance test${NC}"
echo -e "${BLUE}   ./scripts/compare-results.sh [single-results-dir] [scaled-results-dir]${NC}"