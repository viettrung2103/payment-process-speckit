#!/bin/bash

# Phase 9: Performance Testing - Single Instance Baseline
# This script runs performance tests against a single payment-bridge instance

# Disable "Exit on Error" for the loop so one bad test doesn't kill the suite
# set +e 

# for users in "${LOAD_LEVELS[@]}"; do
#     echo -e "${YELLOW}--- Starting Iteration: $users Users ---${NC}"
#     run_jmeter_test "$users"
    
#     echo -e "${YELLOW}Waiting 30s for system cooldown...${NC}"
#     sleep 30
# done

set -e # Re-enable if desired



SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
PERF_DIR="$PROJECT_ROOT/performance-test"
ANALYZER="$SCRIPT_DIR/helpers/analyze-results.sh"

echo "🚀 Starting Phase 9: Single-Instance Performance Testing"
echo "======================================================"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
TIMESTAMP=$(date +"%Y%m%d-%H%M%S")
RESULTS_DIR="$PERF_DIR/results/single-instance-$TIMESTAMP"
JMETER_PLAN="$PERF_DIR/jmeter/payment-load-test.jmx"
DOCKER_COMPOSE_FILE="$PROJECT_ROOT/docker-compose.yml"

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

# Clean up Docker images and containers for fresh start
echo -e "${YELLOW}🧹 Cleaning up Docker images and containers...${NC}"
docker-compose -f "$DOCKER_COMPOSE_FILE" down --volumes --remove-orphans || true
docker system prune -f || true
docker image prune -a -f || true
echo -e "${GREEN}✅ Docker cleanup completed${NC}"
echo

# Pull fresh images
echo -e "${YELLOW}📥 Pulling fresh Docker images...${NC}"
docker-compose -f "$DOCKER_COMPOSE_FILE" pull
echo -e "${GREEN}✅ Fresh images pulled${NC}"
echo

# Build local service images so the latest Payment Bridge code is deployed
echo -e "${YELLOW}🛠️  Rebuilding local Docker images...${NC}"
docker-compose -f "$DOCKER_COMPOSE_FILE" build --no-cache payment-bridge mock-payment-api
echo -e "${GREEN}✅ Local images rebuilt${NC}"
echo

# Function to wait for service health
wait_for_service() {
    local service_name=$1
    local url=$2
    local expected_status=${3:-"UP"}
    local max_attempts=${4:-30}
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
    local max_attempts=30
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

# Start services
echo -e "${YELLOW}🐳 Starting single-instance services...${NC}"
cd "$PROJECT_ROOT"

if ! docker-compose -f "$DOCKER_COMPOSE_FILE" up -d; then
    echo -e "${RED}❌ Failed to start services${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Services started${NC}"
echo

# Wait for services to be healthy
wait_for_db "PostgreSQL" "localhost" "5432" || exit 1
wait_for_service "RabbitMQ" "http://localhost:15672/api/aliveness-test/%2F" "ok" 10 "admin" "admin" || exit 1
wait_for_service "Mock Payment API" "http://localhost:8081/actuator/health" || exit 1
wait_for_service "Payment Bridge" "http://localhost:8080/actuator/health" || exit 1

echo
echo -e "${GREEN}🎉 All services are healthy! Clearing queues before testing...${NC}"
echo

# Clear RabbitMQ queues
echo -e "${YELLOW}🧹 Clearing RabbitMQ queues...${NC}"
docker exec payment-system-rabbitmq rabbitmqctl purge_queue payment-processing || true
docker exec payment-system-rabbitmq rabbitmqctl purge_queue payment-retry || true
docker exec payment-system-rabbitmq rabbitmqctl purge_queue dlq-payment-failed || true
echo -e "${GREEN}✅ Queues cleared${NC}"
echo

echo -e "${GREEN}🎉 Starting performance tests...${NC}"
echo

# Test configurations
declare -a LOAD_LEVELS=("5" "10" "20")
DURATION=160  # ~2.5 minutes per test to target 100k total requests across 3 tests
RAMP_UP=20    # 20 seconds ramp up

# Function to run JMeter test
run_jmeter_test() {
    local users=$1
    local test_name="single-instance-${users}-users"

    echo -e "${BLUE}🧪 Running test: $test_name${NC}"
    echo -e "${BLUE}   Users: $users${NC}"
    echo -e "${BLUE}   Duration: ${DURATION}s${NC}"
    echo -e "${BLUE}   Ramp-up: ${RAMP_UP}s${NC}"
    echo

    # Check if JMeter is available
    if command_exists jmeter; then
        JMETER_CMD="jmeter"
    elif command_exists docker && docker images | grep -q "qainsights/jmeter"; then
        JMETER_CMD="docker run --rm --network host -v \"$PROJECT_ROOT\":/workspace qainsights/jmeter:latest"
    else
        if command_exists docker; then
            echo -e "${YELLOW}📥 Pulling JMeter Docker image...${NC}"
            docker pull qainsights/jmeter:latest
            if [[ "$(uname -s)" == "Darwin" ]]; then
                JMETER_CMD="docker run --rm -v \"$PROJECT_ROOT\":/workspace qainsights/jmeter:latest"
            else
                JMETER_CMD="docker run --rm --network host -v \"$PROJECT_ROOT\":/workspace qainsights/jmeter:latest"
            fi
        else
            echo -e "${RED}❌ JMeter not found and Docker not available. Please install JMeter.${NC}"
            return 1
        fi
    fi

# Run JMeter test with real-time output
    local jmeter_log="$RESULTS_DIR/jmeter-${test_name}.log"
    local jtl_file="$RESULTS_DIR/results-${test_name}.jtl"

    echo -e "${YELLOW}   Executing JMeter test (real-time output enabled)...${NC}"
    echo -e "${YELLOW}   📊 You will see live performance metrics as the test runs${NC}"

    # Adjust paths for Docker if needed
    local jmeter_plan_path="$JMETER_PLAN"
    local jtl_file_path="$jtl_file"
    local jmeter_log_path="$jmeter_log"
    local host_jmeter_log_path="$jmeter_log"

    if [[ "$JMETER_CMD" == *"docker"* ]]; then
        # Use container paths when running in Docker with /workspace mount
        # RESULTS_DIR is like /path/to/results/single-instance-20260509-022754
        local results_subdir="${RESULTS_DIR##*/}"
        jmeter_plan_path="/workspace/performance-test/jmeter/payment-load-test.jmx"
        jtl_file_path="/workspace/performance-test/results/$results_subdir/results-${test_name}.jtl"
        jmeter_log_path="/workspace/performance-test/results/$results_subdir/jmeter-${test_name}.log"
        host_jmeter_log_path="$jmeter_log"
    fi

    # Build the JMeter command (without output redirection)
    local target_host="localhost"
    local target_port="8080"
    if [[ "$JMETER_CMD" == *"docker"* && "$(uname -s)" == "Darwin" ]]; then
        target_host="host.docker.internal"
    fi
    local jmeter_cmd="$JMETER_CMD -n -t \"$jmeter_plan_path\" -JTARGET_HOST=\"$target_host\" -JTARGET_PORT=\"$target_port\" -JUSERS=\"$users\" -JRAMP_UP=\"$RAMP_UP\" -JDURATION=\"$DURATION\" -l \"$jtl_file_path\""

    # Execute the command and pipe output to tee for real-time display and logging
    if eval "$jmeter_cmd" 2>&1 | tee "$host_jmeter_log_path"; then
        echo -e "${GREEN}   ✅ JMeter test completed successfully${NC}"

        # Generate summary
        echo -e "${YELLOW}   📊 Analyzing JMeter results...${NC}"
        "$ANALYZER" "$jtl_file" "$RESULTS_DIR/summary-${test_name}.txt"
        echo -e "${GREEN}   ✅ Analysis complete for $test_name${NC}"

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
    sleep 30
done

# Collect system metrics
echo -e "${YELLOW}📊 Collecting system metrics...${NC}"
"$SCRIPT_DIR/helpers/collect-metrics.sh" "$RESULTS_DIR"

# Generate final report
echo -e "${YELLOW}📋 Generating final report...${NC}"
"$SCRIPT_DIR/helpers/generate-report.sh" "$RESULTS_DIR" "single-instance"

echo
echo -e "${GREEN}🎉 Single-instance performance testing completed!${NC}"
echo -e "${GREEN}📁 Results available in: $RESULTS_DIR${NC}"
echo -e "${GREEN}🕐 Test completed at: $(date)${NC}"
echo
echo -e "${BLUE}📈 Next: Run scaled performance test with:${NC}"
echo -e "${BLUE}   ./scripts/run-scaled-test.sh${NC}"