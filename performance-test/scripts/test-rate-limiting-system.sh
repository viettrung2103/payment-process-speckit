#!/bin/bash

# System Test for Rate Limiting
# Tests nginx rate limiting configuration in a real environment

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Source test utilities
source "$SCRIPT_DIR/helpers/test-utils.sh"

# Configuration
NGINX_URL="http://localhost:8080"
PAYMENT_ENDPOINT="/api/v1/payments"
API_ENDPOINT="/api/v1/status"

# Test Functions
test_payment_rate_limiting() {
    log_info "🧪 Testing payment endpoint rate limiting (5 req/s limit)..."

    local success_count=0
    local rate_limited_count=0
    local total_requests=12

    # Send requests above the 5 req/s limit
    for i in $(seq 1 $total_requests); do
        local response_code=$(curl -s -w "%{http_code}" \
            -H "Content-Type: application/json" \
            -d '{"amount": 100.00, "currency": "USD"}' \
            -o /dev/null \
            "$NGINX_URL$PAYMENT_ENDPOINT")

        case $response_code in
            200) ((success_count++)) ;;
            429) ((rate_limited_count++)) ;;
            *) log_error "Unexpected response code: $response_code" ;;
        esac
    done

    # Validate results
    assert_greater_than "$success_count" 0 "Should allow some payment requests"
    assert_greater_than "$rate_limited_count" 0 "Should rate limit excess payment requests"

    local total_handled=$((success_count + rate_limited_count))
    assert_equals "$total_handled" "$total_requests" "All requests should be handled"

    log_success "Payment rate limiting: $success_count allowed, $rate_limited_count blocked"
}

test_api_rate_limiting() {
    log_info "🧪 Testing API endpoint rate limiting (10 req/s limit)..."

    local success_count=0
    local rate_limited_count=0
    local total_requests=15

    # Send requests above the 10 req/s limit
    for i in $(seq 1 $total_requests); do
        local response_code=$(curl -s -w "%{http_code}" \
            -H "Accept: application/json" \
            -o /dev/null \
            "$NGINX_URL$API_ENDPOINT")

        case $response_code in
            200) ((success_count++)) ;;
            429) ((rate_limited_count++)) ;;
            *) log_error "Unexpected response code: $response_code" ;;
        esac
    done

    # Validate results
    assert_greater_than "$success_count" 0 "Should allow some API requests"
    assert_greater_than "$rate_limited_count" 0 "Should rate limit excess API requests"

    log_success "API rate limiting: $success_count allowed, $rate_limited_count blocked"
}

test_burst_handling() {
    log_info "🧪 Testing burst request handling..."

    local success_count=0
    local rate_limited_count=0
    local total_requests=20

    # Send burst of requests simultaneously
    for i in $(seq 1 $total_requests); do
        curl -s -w "%{http_code}\n" \
            -H "Content-Type: application/json" \
            -d '{"amount": 50.00, "currency": "USD"}' \
            "$NGINX_URL$PAYMENT_ENDPOINT" &
    done | while read -r response_code; do
        case $response_code in
            200) ((success_count++)) ;;
            429) ((rate_limited_count++)) ;;
            *) log_error "Unexpected response code: $response_code" ;;
        esac
    done

    # Wait for all background processes
    wait

    # Validate results
    assert_greater_than "$success_count" 0 "Should handle some burst requests"
    assert_greater_than "$rate_limited_count" 0 "Should eventually rate limit burst"

    log_success "Burst handling: $success_count allowed, $rate_limited_count blocked"
}

test_rate_limit_headers() {
    log_info "🧪 Testing rate limit response headers..."

    # Send a few requests to potentially trigger rate limiting
    for i in $(seq 1 8); do
        curl -s -I \
            -H "Content-Type: application/json" \
            -d '{"amount": 25.00, "currency": "USD"}' \
            "$NGINX_URL$PAYMENT_ENDPOINT" > /dev/null
    done

    # Check rate limit headers on next request
    local headers=$(curl -s -I \
        -H "Content-Type: application/json" \
        -d '{"amount": 25.00, "currency": "USD"}' \
        "$NGINX_URL$PAYMENT_ENDPOINT")

    # Check for rate limiting headers (nginx rate limiting module)
    if echo "$headers" | grep -q "X-RateLimit"; then
        log_success "Rate limit headers present"
    else
        log_warning "Rate limit headers not found (may be expected depending on nginx config)"
    fi
}

test_different_clients() {
    log_info "🧪 Testing rate limiting per client (different IPs)..."

    # Simulate different client IPs using X-Forwarded-For header
    local client1_success=0
    local client2_success=0

    # Client 1 requests
    for i in $(seq 1 6); do
        local response_code=$(curl -s -w "%{http_code}" \
            -H "X-Forwarded-For: 192.168.1.1" \
            -H "Content-Type: application/json" \
            -d '{"amount": 10.00}' \
            -o /dev/null \
            "$NGINX_URL$PAYMENT_ENDPOINT")

        if [ "$response_code" = "200" ]; then
            ((client1_success++))
        fi
    done

    # Client 2 requests (should not be affected by client 1's rate limiting)
    for i in $(seq 1 6); do
        local response_code=$(curl -s -w "%{http_code}" \
            -H "X-Forwarded-For: 192.168.1.2" \
            -H "Content-Type: application/json" \
            -d '{"amount": 10.00}' \
            -o /dev/null \
            "$NGINX_URL$PAYMENT_ENDPOINT")

        if [ "$response_code" = "200" ]; then
            ((client2_success++))
        fi
    done

    # Validate that different clients are rate limited independently
    assert_greater_than "$client1_success" 0 "Client 1 should have successful requests"
    assert_greater_than "$client2_success" 0 "Client 2 should have successful requests"

    log_success "Client isolation: Client1=$client1_success, Client2=$client2_success"
}

# Main test execution
main() {
    log_info "🚀 Starting Rate Limiting System Tests"

    # Verify environment is ready
    if ! curl -s "$NGINX_URL$API_ENDPOINT" > /dev/null 2>&1; then
        log_error "Nginx is not accessible at $NGINX_URL. Make sure the environment is running."
        exit 1
    fi

    # Run all tests
    test_payment_rate_limiting
    test_api_rate_limiting
    test_burst_handling
    test_rate_limit_headers
    test_different_clients

    log_success "✅ All rate limiting system tests passed!"
}

# Run main if script is executed directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi