#!/bin/bash

# System Test for Auto-Scaling
# Tests CPU-based auto-scaling of payment-bridge instances

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Source test utilities
source "$SCRIPT_DIR/helpers/test-utils.sh"

# Configuration
AUTO_SCALER_SCRIPT="$SCRIPT_DIR/auto-scaler.sh"
MANAGE_SCRIPT="$SCRIPT_DIR/manage-auto-scaling.sh"
NGINX_URL="http://localhost:8080"
LOAD_GENERATOR="$SCRIPT_DIR/generate-load.sh"

# Test Functions
test_initial_state() {
    log_info "🧪 Testing initial auto-scaling state..."

    # Ensure we start with minimum instances (1)
    $MANAGE_SCRIPT scale-to 1

    # Wait for scaling to complete
    sleep 5

    # Check instance count
    local instance_count=$(docker ps --filter "name=payment-bridge-" --format "{{.Names}}" | wc -l)
    assert_equals "$instance_count" 1 "Should start with 1 instance"

    log_success "Initial state: $instance_count instance(s)"
}

test_cpu_based_scale_up() {
    log_info "🧪 Testing CPU-based scale-up..."

    # Start with 1 instance
    $MANAGE_SCRIPT scale-to 1
    sleep 5

    # Generate high load to trigger scale-up
    log_info "Generating high load for 60 seconds..."
    local load_result=$($LOAD_GENERATOR --url="$NGINX_URL/api/v1/payments" \
                                         --requests=200 \
                                         --concurrency=20 \
                                         --duration=60 \
                                         --data='{"amount": 100.00, "currency": "USD"}')

    # Wait for auto-scaling to detect and respond
    sleep 40

    # Check if scaled up
    local instance_count=$(docker ps --filter "name=payment-bridge-" --format "{{.Names}}" | wc -l)
    assert_greater_than "$instance_count" 1 "Should have scaled up under load"

    log_success "Scale-up test: $instance_count instances after load"
}

test_scale_down() {
    log_info "🧪 Testing scale-down behavior..."

    # Start with multiple instances
    $MANAGE_SCRIPT scale-to 3
    sleep 10

    # Wait for low load period (longer than cooldown)
    log_info "Waiting for low load period (120 seconds)..."
    sleep 120

    # Check if scaled down
    local instance_count=$(docker ps --filter "name=payment-bridge-" --format "{{.Names}}" | wc -l)
    assert_less_or_equal "$instance_count" 2 "Should have scaled down during low load"

    log_success "Scale-down test: $instance_count instances after cooldown"
}

test_health_checks() {
    log_info "🧪 Testing health check integration..."

    # Start with 2 instances
    $MANAGE_SCRIPT scale-to 2
    sleep 5

    # Check that all instances are healthy
    local healthy_count=0
    local instances=$(docker ps --filter "name=payment-bridge-" --format "{{.Names}}")

    for instance in $instances; do
        # Extract container port
        local port=$(docker inspect "$instance" --format '{{(index (index .NetworkSettings.Ports "8080/tcp") 0).HostPort}}')

        if curl -s "http://localhost:$port/actuator/health" | grep -q '"status":"UP"'; then
            ((healthy_count++))
        fi
    done

    local total_instances=$(echo "$instances" | wc -l)
    assert_equals "$healthy_count" "$total_instances" "All instances should be healthy"

    log_success "Health checks: $healthy_count/$total_instances instances healthy"
}

test_nginx_config_updates() {
    log_info "🧪 Testing nginx configuration updates during scaling..."

    # Start with 1 instance
    $MANAGE_SCRIPT scale-to 1
    sleep 5

    # Check nginx config has 1 upstream server
    local config=$(docker exec payment-system-nginx cat /etc/nginx/nginx.conf)
    local server_count=$(echo "$config" | grep -c "server payment-bridge-")
    assert_equals "$server_count" 1 "Nginx should have 1 upstream server"

    # Scale to 2 instances
    $MANAGE_SCRIPT scale-to 2
    sleep 10

    # Check nginx config updated
    config=$(docker exec payment-system-nginx cat /etc/nginx/nginx.conf)
    server_count=$(echo "$config" | grep -c "server payment-bridge-")
    assert_equals "$server_count" 2 "Nginx should have 2 upstream servers after scaling"

    # Reload nginx and verify it's still working
    docker exec payment-system-nginx nginx -s reload
    sleep 2

    local response_code=$(curl -s -w "%{http_code}" -o /dev/null "$NGINX_URL/api/v1/status")
    assert_equals "$response_code" 200 "Nginx should still serve requests after reload"

    log_success "Nginx config updates: properly updated upstream servers"
}

test_cooldown_period() {
    log_info "🧪 Testing cooldown period prevents rapid scaling..."

    # Start with 1 instance
    $MANAGE_SCRIPT scale-to 1
    sleep 5

    # Generate brief load spike
    log_info "Generating brief load spike..."
    $LOAD_GENERATOR --url="$NGINX_URL/api/v1/payments" \
                    --requests=50 \
                    --concurrency=10 \
                    --duration=10 \
                    --data='{"amount": 100.00, "currency": "USD"}'

    # Check immediate scaling (should not happen due to cooldown)
    sleep 10
    local instance_count=$(docker ps --filter "name=payment-bridge-" --format "{{.Names}}" | wc -l)
    assert_equals "$instance_count" 1 "Should not scale immediately due to cooldown"

    # Wait for cooldown to expire and check again
    log_info "Waiting for cooldown period to expire..."
    sleep 310

    # Generate load again
    $LOAD_GENERATOR --url="$NGINX_URL/api/v1/payments" \
                    --requests=100 \
                    --concurrency=15 \
                    --duration=30 \
                    --data='{"amount": 100.00, "currency": "USD"}'

    # Now scaling should be allowed
    sleep 40
    instance_count=$(docker ps --filter "name=payment-bridge-" --format "{{.Names}}" | wc -l)
    assert_greater_than "$instance_count" 1 "Should scale up after cooldown expires"

    log_success "Cooldown period: prevented rapid scaling oscillations"
}

test_max_instances_limit() {
    log_info "🧪 Testing maximum instances limit..."

    # Scale to maximum (5 instances)
    $MANAGE_SCRIPT scale-to 5
    sleep 10

    # Verify at maximum
    local instance_count=$(docker ps --filter "name=payment-bridge-" --format "{{.Names}}" | wc -l)
    assert_equals "$instance_count" 5 "Should be at maximum instances"

    # Generate extreme load (should not scale beyond maximum)
    log_info "Generating extreme load..."
    $LOAD_GENERATOR --url="$NGINX_URL/api/v1/payments" \
                    --requests=500 \
                    --concurrency=50 \
                    --duration=60 \
                    --data='{"amount": 100.00, "currency": "USD"}'

    # Wait and check still at maximum
    sleep 60
    instance_count=$(docker ps --filter "name=payment-bridge-" --format "{{.Names}}" | wc -l)
    assert_equals "$instance_count" 5 "Should not exceed maximum instances"

    log_success "Maximum instances limit: stayed at 5 instances under extreme load"
}

test_load_distribution() {
    log_info "🧪 Testing load distribution across scaled instances..."

    # Scale to 3 instances
    $MANAGE_SCRIPT scale-to 3
    sleep 10

    # Send requests and track which instance handles them
    local instance_requests=()
    for i in {1..30}; do
        local response=$(curl -s -H "X-Track-Request: $i" \
            -H "Content-Type: application/json" \
            -d '{"amount": 10.00}' \
            "$NGINX_URL/api/v1/payments")

        # Extract instance ID from response header (assuming instances add this)
        local instance_id=$(echo "$response" | grep -o "instance-[0-9]" || echo "unknown")
        instance_requests+=("$instance_id")
    done

    # Count requests per instance
    local unique_instances=$(printf '%s\n' "${instance_requests[@]}" | sort | uniq | wc -l)
    assert_greater_than "$unique_instances" 1 "Requests should be distributed across multiple instances"

    # Check distribution is reasonably even
    local instance_counts=$(printf '%s\n' "${instance_requests[@]}" | sort | uniq -c | sort -nr)
    local max_count=$(echo "$instance_counts" | head -1 | awk '{print $1}')
    local min_count=$(echo "$instance_counts" | tail -1 | awk '{print $1}')
    local difference=$((max_count - min_count))

    assert_less_than "$difference" 8 "Load distribution should be reasonably even"

    log_success "Load distribution: requests spread across $unique_instances instances"
}

# Main test execution
main() {
    log_info "🚀 Starting Auto-Scaling System Tests"

    # Verify environment is ready
    if ! curl -s "$NGINX_URL/api/v1/status" > /dev/null 2>&1; then
        log_error "Payment system is not accessible at $NGINX_URL. Make sure the environment is running."
        exit 1
    fi

    if [ ! -x "$AUTO_SCALER_SCRIPT" ]; then
        log_error "Auto-scaler script not found or not executable: $AUTO_SCALER_SCRIPT"
        exit 1
    fi

    # Run all tests
    test_initial_state
    test_cpu_based_scale_up
    test_scale_down
    test_health_checks
    test_nginx_config_updates
    test_cooldown_period
    test_max_instances_limit
    test_load_distribution

    log_success "✅ All auto-scaling system tests passed!"
}

# Run main if script is executed directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi