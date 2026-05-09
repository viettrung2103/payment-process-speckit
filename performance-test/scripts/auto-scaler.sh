#!/bin/bash

# Auto-scaling script for payment-bridge instances
# Monitors system metrics and scales instances up/down based on load

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
PERF_DIR="$PROJECT_ROOT/performance-test"

# Configuration
DOCKER_COMPOSE_FILE="$PERF_DIR/config/docker-compose.scaled.yml"
METRICS_INTERVAL=30  # Check metrics every 30 seconds
SCALE_UP_THRESHOLD=70  # Scale up when CPU > 70%
SCALE_DOWN_THRESHOLD=30  # Scale down when CPU < 30%
MIN_INSTANCES=1
MAX_INSTANCES=5
COOLDOWN_PERIOD=300  # 5 minutes between scaling actions

# State tracking
LAST_SCALE_TIME=0
CURRENT_INSTANCES=3

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging function
log() {
    echo -e "$(date '+%Y-%m-%d %H:%M:%S') - $1"
}

# Get CPU usage for payment-bridge containers
get_cpu_usage() {
    local total_cpu=0
    local count=0

    for i in $(seq 1 $CURRENT_INSTANCES); do
        local container_name="payment-bridge-$i"
        if docker ps --format "table {{.Names}}" | grep -q "^${container_name}$"; then
            # Get CPU usage percentage
            local cpu_usage=$(docker stats --no-stream --format "table {{.CPUPerc}}" "$container_name" 2>/dev/null | tail -n 1 | sed 's/%//')
            if [[ -n "$cpu_usage" && "$cpu_usage" =~ ^[0-9]+(\.[0-9]+)?$ ]]; then
                total_cpu=$(echo "$total_cpu + $cpu_usage" | bc -l)
                ((count++))
            fi
        fi
    done

    if [ $count -gt 0 ]; then
        echo "scale=2; $total_cpu / $count" | bc -l
    else
        echo "0"
    fi
}

# Get request rate from nginx access log
get_request_rate() {
    local log_file="/tmp/nginx-access.log"
    local requests_per_minute=0

    # Mount nginx logs to a temporary location for monitoring
    if [ -f "$log_file" ]; then
        # Count requests in the last minute
        local recent_requests=$(tail -n 1000 "$log_file" | awk -v now=$(date +%s) '$4 >= (now - 60)' | wc -l)
        requests_per_minute=$((recent_requests * 60 / 60))  # Normalize to per minute
    fi

    echo "$requests_per_minute"
}

# Scale up instances
scale_up() {
    if [ $CURRENT_INSTANCES -ge $MAX_INSTANCES ]; then
        log "${YELLOW}⚠️  Already at maximum instances ($MAX_INSTANCES)${NC}"
        return 1
    fi

    local new_count=$((CURRENT_INSTANCES + 1))
    log "${BLUE}📈 Scaling UP from $CURRENT_INSTANCES to $new_count instances${NC}"

    # Add new instance to docker-compose override
    cd "$PERF_DIR/config"

    # Create temporary override file
    cat > docker-compose.override.yml << EOF
version: "3.8"
services:
  payment-bridge-$new_count:
    build:
      context: ../..
      dockerfile: payment-bridge/Dockerfile
    container_name: payment-bridge-$new_count
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/payment_bridge
      SPRING_DATASOURCE_USERNAME: payment_user
      SPRING_DATASOURCE_PASSWORD: payment_pass
      PAYMENT_API_BASE_URL: http://mock-payment-api:8081
      SPRING_RABBITMQ_HOST: rabbitmq
      SPRING_RABBITMQ_PORT: 5672
      SPRING_RABBITMQ_USERNAME: admin
      SPRING_RABBITMQ_PASSWORD: admin
      SERVER_PORT: 8080
      INSTANCE_ID: payment-bridge-$new_count
    expose:
      - "8080"
    depends_on:
      - postgres
      - rabbitmq
      - mock-payment-api
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 10s
      timeout: 10s
      retries: 5
      start_period: 30s
    restart: unless-stopped
EOF

    # Start new instance
    if docker-compose -f "$DOCKER_COMPOSE_FILE" -f docker-compose.override.yml up -d "payment-bridge-$new_count"; then
        CURRENT_INSTANCES=$new_count
        LAST_SCALE_TIME=$(date +%s)
        log "${GREEN}✅ Successfully scaled to $CURRENT_INSTANCES instances${NC}"

        # Update nginx configuration
        update_nginx_config
        return 0
    else
        log "${RED}❌ Failed to scale up${NC}"
        return 1
    fi
}

# Scale down instances
scale_down() {
    if [ $CURRENT_INSTANCES -le $MIN_INSTANCES ]; then
        log "${YELLOW}⚠️  Already at minimum instances ($MIN_INSTANCES)${NC}"
        return 1
    fi

    local new_count=$((CURRENT_INSTANCES - 1))
    log "${BLUE}📉 Scaling DOWN from $CURRENT_INSTANCES to $new_count instances${NC}"

    cd "$PERF_DIR/config"

    # Stop and remove the highest numbered instance
    if docker-compose -f "$DOCKER_COMPOSE_FILE" stop "payment-bridge-$CURRENT_INSTANCES" && \
       docker-compose -f "$DOCKER_COMPOSE_FILE" rm -f "payment-bridge-$CURRENT_INSTANCES"; then

        CURRENT_INSTANCES=$new_count
        LAST_SCALE_TIME=$(date +%s)
        log "${GREEN}✅ Successfully scaled to $CURRENT_INSTANCES instances${NC}"

        # Update nginx configuration
        update_nginx_config
        return 0
    else
        log "${RED}❌ Failed to scale down${NC}"
        return 1
    fi
}

# Update nginx configuration with current instances
update_nginx_config() {
    log "${BLUE}🔄 Updating nginx configuration for $CURRENT_INSTANCES instances${NC}"

    # Generate upstream servers list
    local upstream_servers=""
    for i in $(seq 1 $CURRENT_INSTANCES); do
        upstream_servers="${upstream_servers}        server payment-bridge-$i:8080;\n"
    done

    # Update nginx config
    sed -i.bak "/upstream payment_bridge_backend {/,/}/c\\
    upstream payment_bridge_backend {\\
        # Round-robin load balancing by default\\
$upstream_servers\\
        # Health checks with faster intervals for auto-scaling\\
        health_check interval=10s fails=2 passes=1;\\
    }" "$PERF_DIR/config/nginx.conf"

    # Reload nginx configuration
    docker-compose -f "$DOCKER_COMPOSE_FILE" exec -T nginx nginx -s reload 2>/dev/null || true

    log "${GREEN}✅ Nginx configuration updated${NC}"
}

# Check if we're in cooldown period
is_in_cooldown() {
    local current_time=$(date +%s)
    local time_since_last_scale=$((current_time - LAST_SCALE_TIME))

    if [ $time_since_last_scale -lt $COOLDOWN_PERIOD ]; then
        return 0  # In cooldown
    else
        return 1  # Not in cooldown
    fi
}

# Main monitoring loop
main() {
    log "${GREEN}🚀 Starting auto-scaler with $CURRENT_INSTANCES instances${NC}"
    log "${BLUE}📊 Monitoring every $METRICS_INTERVAL seconds${NC}"
    log "${BLUE}📈 Scale up threshold: ${SCALE_UP_THRESHOLD}% CPU${NC}"
    log "${BLUE}📉 Scale down threshold: ${SCALE_DOWN_THRESHOLD}% CPU${NC}"
    log "${BLUE}🔄 Cooldown period: ${COOLDOWN_PERIOD} seconds${NC}"

    while true; do
        # Get current metrics
        local cpu_usage=$(get_cpu_usage)
        local request_rate=$(get_request_rate)

        log "${BLUE}📊 Current metrics - CPU: ${cpu_usage}%, Requests/min: ${request_rate}${NC}"

        # Check if we should scale (and not in cooldown)
        if ! is_in_cooldown; then
            if (( $(echo "$cpu_usage > $SCALE_UP_THRESHOLD" | bc -l) )); then
                log "${YELLOW}🔥 High CPU usage detected (${cpu_usage}%), considering scale up${NC}"
                scale_up
            elif (( $(echo "$cpu_usage < $SCALE_DOWN_THRESHOLD" | bc -l) )) && [ $CURRENT_INSTANCES -gt $MIN_INSTANCES ]; then
                log "${YELLOW}🧊 Low CPU usage detected (${cpu_usage}%), considering scale down${NC}"
                scale_down
            fi
        else
            local remaining_cooldown=$((COOLDOWN_PERIOD - ($(date +%s) - LAST_SCALE_TIME)))
            log "${YELLOW}⏳ In cooldown period (${remaining_cooldown}s remaining)${NC}"
        fi

        sleep $METRICS_INTERVAL
    done
}

# Handle script termination
trap 'log "${RED}🛑 Auto-scaler stopped${NC}"; exit 0' INT TERM

# Run main function
main "$@"