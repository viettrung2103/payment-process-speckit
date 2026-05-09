#!/bin/bash

# Test script to demonstrate rate limiting functionality
# Sends requests to test rate limiting thresholds

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
PERF_DIR="$PROJECT_ROOT/performance-test"

# Configuration
BASE_URL="http://localhost:8080"
PAYMENT_ENDPOINT="/api/v1/payments"
HEALTH_ENDPOINT="/actuator/health"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging function
log() {
    echo -e "$(date '+%H:%M:%S') - $1"
}

# Test health endpoint (should not be rate limited)
test_health_endpoint() {
    log "${BLUE}🏥 Testing health endpoint (no rate limiting)...${NC}"

    local success_count=0
    local total_requests=20

    for i in $(seq 1 $total_requests); do
        if curl -s -w "%{http_code}" "$BASE_URL$HEALTH_ENDPOINT" -o /dev/null | grep -q "200"; then
            ((success_count++))
        fi
        echo -n "."
        sleep 0.1
    done
    echo ""

    log "${GREEN}✅ Health endpoint: $success_count/$total_requests successful${NC}"
}

# Test payment endpoint rate limiting
test_payment_rate_limiting() {
    log "${BLUE}💳 Testing payment endpoint rate limiting (5 req/s limit)...${NC}"

    local success_count=0
    local rate_limited_count=0
    local total_requests=15

    for i in $(seq 1 $total_requests); do
        local response=$(curl -s -w "%{http_code}" \
            -H "Content-Type: application/json" \
            -d '{"amount": 100.00, "currency": "USD", "description": "Rate limit test"}' \
            "$BASE_URL$PAYMENT_ENDPOINT" -o /dev/null)

        if [ "$response" = "200" ]; then
            ((success_count++))
            echo -n "✓"
        elif [ "$response" = "429" ]; then
            ((rate_limited_count++))
            echo -n "🚫"
        else
            echo -n "❌"
        fi

        sleep 0.1  # ~10 requests per second (above 5/s limit)
    done
    echo ""

    log "${GREEN}✅ Payment endpoint: $success_count allowed, $rate_limited_count rate limited${NC}"
}

# Test API endpoint rate limiting
test_api_rate_limiting() {
    log "${BLUE}🔗 Testing general API endpoint rate limiting (10 req/s limit)...${NC}"

    local success_count=0
    local rate_limited_count=0
    local total_requests=25

    for i in $(seq 1 $total_requests); do
        local response=$(curl -s -w "%{http_code}" \
            "$BASE_URL/api/v1/some-endpoint" -o /dev/null)

        if [ "$response" = "200" ] || [ "$response" = "404" ]; then
            ((success_count++))
            echo -n "✓"
        elif [ "$response" = "429" ]; then
            ((rate_limited_count++))
            echo -n "🚫"
        else
            echo -n "❌"
        fi

        sleep 0.05  # ~20 requests per second (above 10/s limit)
    done
    echo ""

    log "${GREEN}✅ API endpoint: $success_count allowed, $rate_limited_count rate limited${NC}"
}

# Test burst handling
test_burst_handling() {
    log "${BLUE}💥 Testing burst handling (sending burst of requests)...${NC}"

    local success_count=0
    local rate_limited_count=0

    # Send a burst of 15 requests rapidly
    for i in $(seq 1 15); do
        local response=$(curl -s -w "%{http_code}" \
            -H "Content-Type: application/json" \
            -d '{"amount": 50.00, "currency": "USD", "description": "Burst test"}' \
            "$BASE_URL$PAYMENT_ENDPOINT" -o /dev/null)

        if [ "$response" = "200" ]; then
            ((success_count++))
        elif [ "$response" = "429" ]; then
            ((rate_limited_count++))
        fi
    done

    log "${GREEN}✅ Burst test: $success_count allowed, $rate_limited_count rate limited${NC}"
    log "${BLUE}ℹ️  Burst handling allows some requests through even under high load${NC}"
}

# Main test function
main() {
    log "${GREEN}🧪 Starting Rate Limiting Tests${NC}"
    log "${BLUE}📊 Testing against scaled environment at $BASE_URL${NC}"
    echo

    # Check if services are running
    if ! curl -s "$BASE_URL$HEALTH_ENDPOINT" > /dev/null; then
        log "${RED}❌ Services not available at $BASE_URL${NC}"
        log "${YELLOW}💡 Make sure to run: ./scripts/setup-scaled-env.sh${NC}"
        exit 1
    fi

    log "${GREEN}✅ Services are running${NC}"
    echo

    # Run tests
    test_health_endpoint
    echo
    test_payment_rate_limiting
    echo
    test_api_rate_limiting
    echo
    test_burst_handling
    echo

    log "${GREEN}🎉 Rate limiting tests completed!${NC}"
    log "${BLUE}💡 Rate limiting protects your API from abuse while allowing legitimate traffic${NC}"
    log "${BLUE}🔄 Auto-scaling ensures you have enough capacity to handle the allowed traffic${NC}"
}

# Run main function
main "$@"