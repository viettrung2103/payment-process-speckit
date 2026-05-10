#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
PERF_DIR="$PROJECT_ROOT/performance-test"
RESULTS_DIR="$PERF_DIR/results/realistic-$(date +%Y%m%d-%H%M%S)"
JMETER_PLAN="$PERF_DIR/jmeter/payment-realistic-stress-test.jmx"
ANALYZER="$SCRIPT_DIR/helpers/analyze-results.sh"
ONE_INSTANCE_COMPOSE="$PROJECT_ROOT/docker-compose.yml"
SCALED_COMPOSE="$PERF_DIR/config/docker-compose.scaled.yml"
MODE="${1:-single}"

USERS="${USERS:-50}"
RAMP_UP="${RAMP_UP:-60}"
DURATION="${DURATION:-300}"
THINK_TIME="${THINK_TIME:-400}"
RANDOM_DELAY="${RANDOM_DELAY:-300}"

mkdir -p "$RESULTS_DIR"

echo "🚀 Starting realistic stress test ($MODE mode)"
echo "================================================================"
echo "📁 Results will be saved to: $RESULTS_DIR"

default_command_exists() { command -v "$1" >/dev/null 2>&1; }

for tool in docker docker-compose curl jq nc; do
  if ! default_command_exists "$tool"; then
    echo "❌ $tool is not installed or not in PATH"
    exit 1
  fi
done

echo "✅ Prerequisites check passed"

echo "📥 Preparing JMeter environment..."
if ! default_command_exists jmeter; then
  if docker images | grep -q "qainsights/jmeter"; then
    echo "✅ JMeter Docker image already available"
  else
    echo "📥 Pulling JMeter Docker image..."
    docker pull qainsights/jmeter:latest
  fi
fi

echo "✅ JMeter environment ready"

cleanup() {
  echo "🧹 Cleaning up Docker environment..."
  docker-compose -f "$ONE_INSTANCE_COMPOSE" down --volumes --remove-orphans --timeout 30 >/dev/null 2>&1 || true
  docker-compose -f "$SCALED_COMPOSE" down --volumes --remove-orphans --timeout 30 >/dev/null 2>&1 || true
}
trap cleanup EXIT

if [[ "$MODE" != "single" && "$MODE" != "scaled" && "$MODE" != "all" ]]; then
  echo "Usage: $0 [single|scaled|all]"
  exit 1
fi

wait_for_service() {
  local service_name="$1"
  local url="$2"
  local expected_status="${3:-UP}"
  local max_attempts="${4:-30}"
  local username="${5:-}"
  local password="${6:-}"
  local attempt=1

  echo "⏳ Waiting for $service_name to be healthy..."
  while [ "$attempt" -le "$max_attempts" ]; do
    local response
    response=$(curl -s${username:+ -u $username:$password} "$url" || true)
    if echo "$response" | jq -e ".status == \"$expected_status\"" >/dev/null 2>&1; then
      echo "✅ $service_name is healthy"
      return 0
    fi
    echo "   Attempt $attempt/$max_attempts - $service_name not ready yet..."
    sleep 5
    attempt=$((attempt + 1))
  done
  echo "❌ $service_name failed to become healthy after $max_attempts attempts"
  return 1
}

wait_for_db() {
  local host="$1"
  local port="$2"
  local max_attempts="${3:-30}"
  local attempt=1

  echo "⏳ Waiting for PostgreSQL to be ready..."
  while [ "$attempt" -le "$max_attempts" ]; do
    if nc -z "$host" "$port" >/dev/null 2>&1; then
      echo "✅ PostgreSQL is ready"
      return 0
    fi
    echo "   Attempt $attempt/$max_attempts - PostgreSQL not ready yet..."
    sleep 5
    attempt=$((attempt + 1))
  done
  echo "❌ PostgreSQL failed to become ready after $max_attempts attempts"
  return 1
}

wait_for_nginx_health() {
  local max_attempts="${1:-30}"
  local attempt=1
  echo "⏳ Waiting for nginx load balancer to be healthy..."
  while [ "$attempt" -le "$max_attempts" ]; do
    if curl -s "http://localhost:8080/actuator/health" >/dev/null 2>&1; then
      echo "✅ Nginx load balancer is healthy"
      return 0
    fi
    echo "   Attempt $attempt/$max_attempts - nginx not ready yet..."
    sleep 5
    attempt=$((attempt + 1))
  done
  echo "❌ Nginx load balancer failed to become healthy after $max_attempts attempts"
  return 1
}

wait_for_container_health() {
  local container_name="$1"
  local max_attempts="${2:-30}"
  local attempt=1

  echo "⏳ Waiting for container $container_name to report healthy..."
  while [ "$attempt" -le "$max_attempts" ]; do
    local status
    status=$(docker inspect --format='{{.State.Health.Status}}' "$container_name" 2>/dev/null || echo "unknown")
    if [ "$status" = "healthy" ]; then
      echo "✅ $container_name is healthy"
      return 0
    fi
    echo "   Attempt $attempt/$max_attempts - $container_name health=$status"
    sleep 5
    attempt=$((attempt + 1))
  done
  echo "❌ $container_name failed to become healthy after $max_attempts attempts"
  return 1
}

wait_for_containers_healthy() {
  local max_attempts="${1:-30}"
  shift
  local container
  for container in "$@"; do
    wait_for_container_health "$container" "$max_attempts" || return 1
  done
}

jmeter_command() {
  if default_command_exists jmeter; then
    echo "jmeter"
  else
    if [[ "$(uname -s)" == "Darwin" ]]; then
      echo "docker run --rm -v \"$PROJECT_ROOT\":/workspace qainsights/jmeter:latest"
    else
      echo "docker run --rm --network host -v \"$PROJECT_ROOT\":/workspace qainsights/jmeter:latest"
    fi
  fi
}

run_realistic_test() {
  local mode="$1"
  local jmeter_cmd
  jmeter_cmd=$(jmeter_command)
  local prefix="single-instance"
  if [[ "$mode" == "scaled" ]]; then
    prefix="scaled-3-instances"
  fi

  local test_name="${prefix}-${USERS}-users"
  local jmeter_log="$RESULTS_DIR/jmeter-${test_name}.log"
  local jtl_file="$RESULTS_DIR/results-${test_name}.jtl"
  local plan_path="$JMETER_PLAN"
  local jtl_path="$jtl_file"
  local target_host="localhost"

  if [[ "$jmeter_cmd" == docker* ]]; then
    local results_subdir="${RESULTS_DIR##*/}"
    plan_path="/workspace/performance-test/jmeter/payment-realistic-stress-test.jmx"
    jtl_path="/workspace/performance-test/results/$results_subdir/results-${test_name}.jtl"
    if [[ "$(uname -s)" == "Darwin" ]]; then
      target_host="host.docker.internal"
    fi
  fi

  echo "🧪 Running realistic stress test: $test_name"
  echo "   Users: $USERS"
  echo "   Ramp-up: $RAMP_UP"
  echo "   Duration: $DURATION"

  eval "$jmeter_cmd -n -t \"$plan_path\" -JTARGET_HOST=\"$target_host\" -JTARGET_PORT=\"8080\" -JUSERS=\"$USERS\" -JRAMP_UP=\"$RAMP_UP\" -JDURATION=\"$DURATION\" -JTHINK_TIME=\"$THINK_TIME\" -JRANDOM_DELAY=\"$RANDOM_DELAY\" -l \"$jtl_path\"" 2>&1 | tee "$jmeter_log"
  echo "✅ Realistic stress test completed"
  "$ANALYZER" "$jtl_file" "$RESULTS_DIR/summary-${test_name}.txt"
}

start_single_environment() {
  echo "🐳 Starting single-instance environment for realistic stress test..."
  cd "$PROJECT_ROOT"
  echo "🧹 Cleaning up previous containers..."
  docker-compose -f "$ONE_INSTANCE_COMPOSE" down --volumes --remove-orphans --timeout 30 || true
  docker rm -f payment-system-postgres payment-system-rabbitmq payment-bridge mock-payment-api payment-system-nginx 2>/dev/null || true
  docker-compose -f "$ONE_INSTANCE_COMPOSE" build payment-bridge mock-payment-api
  docker-compose -f "$ONE_INSTANCE_COMPOSE" up -d --build

  wait_for_db localhost 5432 || exit 1
  wait_for_service "RabbitMQ" "http://localhost:15672/api/aliveness-test/%2F" ok 10 admin admin || exit 1
  wait_for_service "Mock Payment API" "http://localhost:8081/actuator/health" || exit 1
  wait_for_service "Payment Bridge" "http://localhost:8080/actuator/health" || exit 1
  wait_for_containers_healthy 20 payment-system-postgres payment-system-rabbitmq mock-payment-api payment-bridge payment-system-nginx || exit 1

  echo "✅ Single-instance environment is healthy"
}

start_scaled_environment() {
  echo "🐳 Starting scaled multi-instance environment for realistic stress test..."
  cd "$PERF_DIR/config"
  echo "🧹 Cleaning up previous containers..."
  docker-compose -f "$SCALED_COMPOSE" down --volumes --remove-orphans --timeout 30 || true
  docker rm -f payment-system-postgres payment-system-rabbitmq payment-bridge-1 payment-bridge-2 payment-bridge-3 mock-payment-api payment-system-nginx 2>/dev/null || true
  COMPOSE_BAKE=1 DOCKER_BUILDKIT=1 docker-compose -f "$SCALED_COMPOSE" build payment-bridge-1 mock-payment-api
  docker-compose -f "$SCALED_COMPOSE" up -d postgres rabbitmq mock-payment-api

  wait_for_db localhost 5432 || exit 1
  wait_for_service "RabbitMQ" "http://localhost:15672/api/aliveness-test/%2F" ok 10 admin admin || exit 1
  wait_for_service "Mock Payment API" "http://localhost:8081/actuator/health" || exit 1

  docker-compose -f "$SCALED_COMPOSE" up -d payment-bridge-1 payment-bridge-2 payment-bridge-3 nginx

  wait_for_nginx_health || exit 1
  wait_for_containers_healthy 20 payment-system-postgres payment-system-rabbitmq mock-payment-api payment-bridge-1 payment-bridge-2 payment-bridge-3 payment-system-nginx || exit 1

  echo "✅ Scaled multi-instance environment is healthy"
}

purge_queues() {
  echo "🧹 Clearing RabbitMQ queues..."
  docker exec payment-system-rabbitmq rabbitmqctl purge_queue payment-processing || true
  docker exec payment-system-rabbitmq rabbitmqctl purge_queue payment-retry || true
  docker exec payment-system-rabbitmq rabbitmqctl purge_queue dlq-payment-failed || true
  echo "✅ Queues cleared"
}

run_single_realistic() {
  start_single_environment
  purge_queues
  run_realistic_test single
  docker-compose -f "$ONE_INSTANCE_COMPOSE" down --volumes --remove-orphans --timeout 30 || true
}

run_scaled_realistic() {
  start_scaled_environment
  purge_queues
  run_realistic_test scaled
  docker-compose -f "$SCALED_COMPOSE" down --volumes --remove-orphans --timeout 30 || true
}

if [[ "$MODE" == "single" ]]; then
  run_single_realistic
elif [[ "$MODE" == "scaled" ]]; then
  run_scaled_realistic
else
  run_single_realistic
  run_scaled_realistic
fi

echo "🎉 Realistic stress test finished"
