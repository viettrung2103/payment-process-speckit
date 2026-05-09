#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
PERF_DIR="$PROJECT_ROOT/performance-test"
RESULTS_DIR="$PERF_DIR/results/quick-$(date +%Y%m%d-%H%M%S)"
JMETER_PLAN="$PERF_DIR/jmeter/payment-load-test.jmx"
TOTAL_REQUESTS=20000
LOAD_LEVELS=(5)
ONE_INSTANCE_COMPOSE="$PROJECT_ROOT/docker-compose.yml"
SCALED_COMPOSE="$PERF_DIR/config/docker-compose.scaled.yml"
ANALYZER="$SCRIPT_DIR/analyze-results.sh"
MODE="${1:-all}"

if [[ "$MODE" != "all" && "$MODE" != "single" && "$MODE" != "scaled" && "$MODE" != "single-shutdown" && "$MODE" != "scaled-shutdown" ]]; then
  echo "Usage: $0 [single|scaled|single-shutdown|scaled-shutdown|all]"
  exit 1
fi

echo "🚀 Starting quick performance test: $MODE"
echo "================================================================"

echo "📁 Results will be saved to: $RESULTS_DIR"
mkdir -p "$RESULTS_DIR"

echo "🔍 Checking prerequisites..."
command_exists() { command -v "$1" >/dev/null 2>&1; }

if ! command_exists docker; then
  echo "❌ Docker is not installed or not in PATH"
  exit 1
fi
if ! command_exists docker-compose; then
  echo "❌ Docker Compose is not installed or not in PATH"
  exit 1
fi
if ! command_exists curl; then
  echo "❌ curl is not installed or not in PATH"
  exit 1
fi
if ! command_exists jq; then
  echo "❌ jq is not installed or not in PATH"
  exit 1
fi
if ! command_exists nc; then
  echo "❌ nc (netcat) is not installed or not in PATH"
  exit 1
fi

echo "✅ Prerequisites check passed"

echo "📥 Preparing JMeter environment..."
if ! command_exists jmeter; then
  if docker images | grep -q "qainsights/jmeter"; then
    echo "✅ JMeter Docker image already available"
  else
    echo "📥 Pulling JMeter Docker image..."
    docker pull qainsights/jmeter:latest
  fi
fi

echo "✅ JMeter environment ready"

echo
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

jmeter_command() {
  if command_exists jmeter; then
    echo "jmeter"
  else
    if [[ "$(uname -s)" == "Darwin" ]]; then
      echo "docker run --rm -v \"$PROJECT_ROOT\":/workspace qainsights/jmeter:latest"
    else
      echo "docker run --rm --network host -v \"$PROJECT_ROOT\":/workspace qainsights/jmeter:latest"
    fi
  fi
}

purge_queues() {
  echo "🧹 Clearing RabbitMQ queues..."
  docker exec payment-system-rabbitmq rabbitmqctl purge_queue payment-processing || true
  docker exec payment-system-rabbitmq rabbitmqctl purge_queue payment-retry || true
  docker exec payment-system-rabbitmq rabbitmqctl purge_queue dlq-payment-failed || true
  echo "✅ Queues cleared"
}

run_jmeter_test() {
  local users="$1"
  local prefix="$2"
  local test_name="${prefix}-${users}-users"
  local jmeter_cmd
  jmeter_cmd=$(jmeter_command)

  echo "🧪 Running test: $test_name"
  echo "   Users: $users"
  echo "   Total requests: $TOTAL_REQUESTS"

  local jmeter_log="$RESULTS_DIR/jmeter-${test_name}.log"
  local jtl_file="$RESULTS_DIR/results-${test_name}.jtl"

  local plan_path="$JMETER_PLAN"
  local jtl_path="$jtl_file"
  local target_host="localhost"
  local target_port="8080"

  if [[ "$jmeter_cmd" == docker* ]]; then
    local results_subdir="${RESULTS_DIR##*/}"
    plan_path="/workspace/performance-test/jmeter/payment-load-test.jmx"
    jtl_path="/workspace/performance-test/results/$results_subdir/results-${test_name}.jtl"
    if [[ "$(uname -s)" == "Darwin" ]]; then
      target_host="host.docker.internal"
    fi
  fi

  mkdir -p "$(dirname "$jtl_file")"

  eval "$jmeter_cmd -n -t \"$plan_path\" -JTARGET_HOST=\"$target_host\" -JTARGET_PORT=\"$target_port\" -JUSERS=\"$users\" -JRAMP_UP=\"20\" -JDURATION=\"30\" -l \"$jtl_path\"" 2>&1 | tee "$jmeter_log"
  echo "✅ JMeter test completed: $test_name"
  "$ANALYZER" "$jtl_file" "$RESULTS_DIR/summary-${test_name}.txt"
}

schedule_service_shutdown() {
  local compose_file="$1"
  local service_name="$2"
  local delay="$3"
  local duration="$4"

  (
    sleep "$delay"
    echo "⏸️  Shutting down $service_name for ${duration}s"
    docker compose -f "$compose_file" stop "$service_name" || true
    sleep "$duration"
    echo "🚀 Restarting $service_name"
    docker compose -f "$compose_file" up -d "$service_name" >/dev/null 2>&1 || true
    echo "✅ $service_name restart requested"
  ) &
  echo $!
}

run_jmeter_test_with_shutdown() {
  local users="$1"
  local prefix="$2"
  local compose_file="$3"
  local service_name="$4"
  local delay="${5:-15}"
  local downtime="${6:-10}"
  local shutdown_pid

  shutdown_pid=$(schedule_service_shutdown "$compose_file" "$service_name" "$delay" "$downtime")
  run_jmeter_test "$users" "$prefix"
  wait "$shutdown_pid" 2>/dev/null || true
}

start_one_instance() {
  echo "🐳 Starting one-instance environment..."
  cd "$PROJECT_ROOT"
  echo "🧹 Cleaning up previous containers..."
  docker-compose -f "$ONE_INSTANCE_COMPOSE" down --volumes --remove-orphans --timeout 30 || true
  # Force remove any remaining containers with the same names
  docker rm -f payment-system-postgres payment-system-rabbitmq payment-bridge mock-payment-api payment-system-nginx 2>/dev/null || true
  docker-compose -f "$ONE_INSTANCE_COMPOSE" build payment-bridge mock-payment-api
  docker-compose -f "$ONE_INSTANCE_COMPOSE" up -d --build

  wait_for_db localhost 5432 || exit 1
  wait_for_service "RabbitMQ" "http://localhost:15672/api/aliveness-test/%2F" ok 10 admin admin || exit 1
  wait_for_service "Mock Payment API" "http://localhost:8081/actuator/health" || exit 1
  wait_for_service "Payment Bridge" "http://localhost:8080/actuator/health" || exit 1

  purge_queues
  for users in "${LOAD_LEVELS[@]}"; do
    run_jmeter_test "$users" single-instance
    purge_queues
  done
  docker-compose -f "$ONE_INSTANCE_COMPOSE" down --volumes --remove-orphans --timeout 30 || true
}

start_scaled_instance() {
  echo "🐳 Starting scaled 3-instance environment..."
  cd "$PERF_DIR/config"
  echo "🧹 Cleaning up previous containers..."
  docker-compose -f "$SCALED_COMPOSE" down --volumes --remove-orphans --timeout 30 || true
  # Force remove any remaining containers with the same names
  docker rm -f payment-system-postgres payment-system-rabbitmq payment-bridge-1 payment-bridge-2 payment-bridge-3 mock-payment-api payment-system-nginx 2>/dev/null || true
  COMPOSE_BAKE=1 DOCKER_BUILDKIT=1 docker-compose -f "$SCALED_COMPOSE" build payment-bridge-1 mock-payment-api
  docker-compose -f "$SCALED_COMPOSE" up -d postgres rabbitmq mock-payment-api

  wait_for_db localhost 5432 || exit 1
  wait_for_service "RabbitMQ" "http://localhost:15672/api/aliveness-test/%2F" ok 10 admin admin || exit 1
  wait_for_service "Mock Payment API" "http://localhost:8081/actuator/health" || exit 1

  docker-compose -f "$SCALED_COMPOSE" up -d payment-bridge-1 payment-bridge-2 payment-bridge-3 nginx

  wait_for_nginx_health || exit 1

  purge_queues
  for users in "${LOAD_LEVELS[@]}"; do
    run_jmeter_test "$users" scaled-3-instances
    purge_queues
  done
  docker-compose -f "$SCALED_COMPOSE" down --volumes --remove-orphans --timeout 30 || true
}

start_one_instance_shutdown() {
  echo "🐳 Starting one-instance shutdown resilience test..."
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

  purge_queues
  for users in "${LOAD_LEVELS[@]}"; do
    run_jmeter_test_with_shutdown "$users" single-instance-shutdown "$ONE_INSTANCE_COMPOSE" payment-bridge 15 10
    purge_queues
  done
  docker-compose -f "$ONE_INSTANCE_COMPOSE" down --volumes --remove-orphans --timeout 30 || true
}

start_scaled_instance_shutdown() {
  echo "🐳 Starting scaled 3-instance shutdown resilience test..."
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

  purge_queues
  for users in "${LOAD_LEVELS[@]}"; do
    run_jmeter_test_with_shutdown "$users" scaled-3-instances-shutdown "$SCALED_COMPOSE" payment-bridge-1 15 10
    purge_queues
  done
  docker-compose -f "$SCALED_COMPOSE" down --volumes --remove-orphans --timeout 30 || true
}

generate_combined_report() {
  echo "📊 Generating combined performance report..."
  local combined_report="$RESULTS_DIR/combined-summary.txt"

  cat << EOF > "$combined_report"
QUICK PERFORMANCE TEST RESULTS
==============================

Test Date: $(date)
Results Directory: $(basename "$RESULTS_DIR")

SINGLE INSTANCE RESULTS
-----------------------
$(for users in "${LOAD_LEVELS[@]}"; do
  summary_file="$RESULTS_DIR/summary-single-instance-${users}-users.txt"
  if [ -f "$summary_file" ]; then
    echo "--- ${users} users ---"
    cat "$summary_file" | grep -A 20 "SUMMARY METRICS"
  else
    echo "No single instance results found for ${users} users"
  fi
  echo
 done)

SCALED INSTANCE RESULTS (3 instances)
-------------------------------------
$(for users in "${LOAD_LEVELS[@]}"; do
  summary_file="$RESULTS_DIR/summary-scaled-3-instances-${users}-users.txt"
  if [ -f "$summary_file" ]; then
    echo "--- ${users} users ---"
    cat "$summary_file" | grep -A 20 "SUMMARY METRICS"
  else
    echo "No scaled instance results found for ${users} users"
  fi
  echo
 done)

OVERALL ASSESSMENT
------------------
$(for users in "${LOAD_LEVELS[@]}"; do
  single_file="$RESULTS_DIR/summary-single-instance-${users}-users.txt"
  scaled_file="$RESULTS_DIR/summary-scaled-3-instances-${users}-users.txt"
  if [ -f "$single_file" ] && [ -f "$scaled_file" ]; then
    SINGLE_ERRORS=$(grep "Error Rate:" "$single_file" | awk '{print $3}' | tr -d '%')
    SCALED_ERRORS=$(grep "Error Rate:" "$scaled_file" | awk '{print $3}' | tr -d '%')
    SINGLE_THROUGHPUT=$(grep "Requests/Second:" "$single_file" | awk '{print $2}')
    SCALED_THROUGHPUT=$(grep "Requests/Second:" "$scaled_file" | awk '{print $2}')
    echo "Load ${users} users"
    echo "  Single Instance: ${SINGLE_ERRORS}% errors, ${SINGLE_THROUGHPUT} RPS"
    echo "  Scaled 3 instances: ${SCALED_ERRORS}% errors, ${SCALED_THROUGHPUT} RPS"
    echo "  Efficiency: $(echo "scale=2; ($SCALED_THROUGHPUT / $SINGLE_THROUGHPUT) * 100 / 3" | bc 2>/dev/null || echo "N/A")% per instance"
    echo
  else
    echo "Load ${users} users: cannot compare results - missing summary files"
    echo
  fi
 done)
EOF

  echo "✅ Combined report generated: $combined_report"
}

if [[ "$MODE" == "all" || "$MODE" == "single" ]]; then
  start_one_instance
fi

if [[ "$MODE" == "single-shutdown" ]]; then
  start_one_instance_shutdown
fi

if [[ "$MODE" == "all" || "$MODE" == "scaled" ]]; then
  start_scaled_instance
fi

if [[ "$MODE" == "scaled-shutdown" ]]; then
  start_scaled_instance_shutdown
fi

generate_combined_report

echo "🎉 Quick performance test completed"
