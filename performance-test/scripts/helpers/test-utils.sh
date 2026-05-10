#!/bin/bash

# Test Utilities for System Tests
# Provides common assertion functions and logging

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1" >&2
}

# Assertion functions
assert_equals() {
    local actual="$1"
    local expected="$2"
    local message="${3:-Expected $expected, got $actual}"

    if [ "$actual" != "$expected" ]; then
        log_error "Assertion failed: $message"
        return 1
    fi
}

assert_not_equals() {
    local actual="$1"
    local unexpected="$2"
    local message="${3:-Expected not $unexpected, got $actual}"

    if [ "$actual" = "$unexpected" ]; then
        log_error "Assertion failed: $message"
        return 1
    fi
}

assert_greater_than() {
    local actual="$1"
    local threshold="$2"
    local message="${3:-Expected $actual > $threshold}"

    if ! [ "$actual" -gt "$threshold" ]; then
        log_error "Assertion failed: $message"
        return 1
    fi
}

assert_greater_or_equal() {
    local actual="$1"
    local threshold="$2"
    local message="${3:-Expected $actual >= $threshold}"

    if ! [ "$actual" -ge "$threshold" ]; then
        log_error "Assertion failed: $message"
        return 1
    fi
}

assert_less_than() {
    local actual="$1"
    local threshold="$2"
    local message="${3:-Expected $actual < $threshold}"

    if ! [ "$actual" -lt "$threshold" ]; then
        log_error "Assertion failed: $message"
        return 1
    fi
}

assert_less_or_equal() {
    local actual="$1"
    local threshold="$2"
    local message="${3:-Expected $actual <= $threshold}"

    if ! [ "$actual" -le "$threshold" ]; then
        log_error "Assertion failed: $message"
        return 1
    fi
}

assert_true() {
    local condition="$1"
    local message="${2:-Expected true, got false}"

    if [ "$condition" != "true" ] && [ "$condition" != "0" ]; then
        log_error "Assertion failed: $message"
        return 1
    fi
}

assert_false() {
    local condition="$1"
    local message="${2:-Expected false, got true}"

    if [ "$condition" = "true" ] || [ "$condition" = "0" ]; then
        log_error "Assertion failed: $message"
        return 1
    fi
}

assert_contains() {
    local haystack="$1"
    local needle="$2"
    local message="${3:-Expected '$haystack' to contain '$needle'}"

    if [[ "$haystack" != *"$needle"* ]]; then
        log_error "Assertion failed: $message"
        return 1
    fi
}

assert_not_contains() {
    local haystack="$1"
    local needle="$2"
    local message="${3:-Expected '$haystack' to not contain '$needle'}"

    if [[ "$haystack" == *"$needle"* ]]; then
        log_error "Assertion failed: $message"
        return 1
    fi
}

# Test runner functions
run_test() {
    local test_name="$1"
    local test_function="$2"

    log_info "Running test: $test_name"

    if $test_function; then
        log_success "✓ $test_name passed"
        return 0
    else
        log_error "✗ $test_name failed"
        return 1
    fi
}

run_tests() {
    local test_functions=("$@")
    local passed=0
    local failed=0

    for test_func in "${test_functions[@]}"; do
        if run_test "$test_func" "$test_func"; then
            ((passed++))
        else
            ((failed++))
        fi
    done

    echo
    log_info "Test Results: $passed passed, $failed failed"

    if [ $failed -gt 0 ]; then
        log_error "Some tests failed"
        return 1
    else
        log_success "All tests passed"
        return 0
    fi
}

# Utility functions
wait_for_service() {
    local url="$1"
    local timeout="${2:-30}"
    local interval="${3:-1}"

    log_info "Waiting for service at $url (timeout: ${timeout}s)"

    local count=0
    while [ $count -lt $timeout ]; do
        if curl -s "$url" > /dev/null 2>&1; then
            log_success "Service is ready"
            return 0
        fi

        sleep $interval
        ((count += interval))
    done

    log_error "Service did not become ready within ${timeout}s"
    return 1
}

cleanup_containers() {
    local pattern="${1:-payment-system}"

    log_info "Cleaning up containers matching: $pattern"

    local containers=$(docker ps -aq --filter "name=$pattern")
    if [ -n "$containers" ]; then
        docker rm -f $containers > /dev/null 2>&1
        log_success "Cleaned up containers"
    else
        log_info "No containers to clean up"
    fi
}

# Performance testing utilities
measure_time() {
    local command="$1"
    local start_time=$(date +%s%N)

    eval "$command"

    local end_time=$(date +%s%N)
    local duration_ns=$((end_time - start_time))
    local duration_ms=$((duration_ns / 1000000))

    echo "$duration_ms"
}

measure_memory() {
    local pid="$1"
    local memory_kb=$(ps -o rss= -p "$pid" 2>/dev/null || echo "0")
    echo "$memory_kb"
}

# Load testing utilities
generate_load() {
    local url="$1"
    local requests="${2:-100}"
    local concurrency="${3:-10}"
    local data="${4:-}"

    log_info "Generating load: $requests requests, $concurrency concurrent"

    local success_count=0
    local error_count=0

    for i in $(seq 1 $requests); do
        (
            local response_code
            if [ -n "$data" ]; then
                response_code=$(curl -s -w "%{http_code}" \
                    -H "Content-Type: application/json" \
                    -d "$data" \
                    -o /dev/null \
                    "$url")
            else
                response_code=$(curl -s -w "%{http_code}" \
                    -o /dev/null \
                    "$url")
            fi

            if [ "$response_code" = "200" ]; then
                echo "success"
            else
                echo "error:$response_code"
            fi
        ) &
    done | while read -r result; do
        if [[ "$result" == "success" ]]; then
            ((success_count++))
        else
            ((error_count++))
        fi
    done

    # Wait for all background processes
    wait

    log_info "Load test results: $success_count success, $error_count errors"
    echo "$success_count:$error_count"
}