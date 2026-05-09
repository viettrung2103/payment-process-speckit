#!/bin/bash

# Comprehensive test script for both payment-bridge and mock-payment-api
# This script runs all tests and validates functionality

set -e

echo "🧪 Starting comprehensive testing for Payment System SpekIT"
echo "======================================================"

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_ROOT"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print status
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

# Test Mock Payment API
test_mock_api() {
    print_status "Testing Mock Payment API..."
    cd "$PROJECT_ROOT/mock-payment-api"

    print_status "Running Mock Payment API tests..."
    if mvn clean test -Dspring.profiles.active=test -q; then
        print_success "Mock Payment API tests passed"
        return 0
    else
        print_error "Mock Payment API tests failed"
        return 1
    fi
}

# Test Payment Bridge
test_payment_bridge() {
    print_status "Testing Payment Bridge..."
    cd "$PROJECT_ROOT/payment-bridge"

    print_status "Running Payment Bridge unit tests..."
    if mvn clean test -Dspring.profiles.active=test -Dtest=!*IntegrationTest -q; then
        print_success "Payment Bridge unit tests passed"
    else
        print_error "Payment Bridge unit tests failed"
        return 1
    fi

    print_status "Running Payment Bridge integration tests..."
    if mvn test -Dspring.profiles.active=test -Dtest=*IntegrationTest -q; then
        print_success "Payment Bridge integration tests passed"
        return 0
    else
        print_error "Payment Bridge integration tests failed"
        return 1
    fi
}

# Test Docker services (if available)
test_docker_services() {
    print_status "Testing Docker services..."

    if ! command -v docker &> /dev/null; then
        print_warning "Docker not available, skipping Docker tests"
        return 0
    fi

    if ! command -v docker-compose &> /dev/null; then
        print_warning "Docker Compose not available, skipping Docker tests"
        return 0
    fi

    print_status "Starting Docker services..."
    cd "$PROJECT_ROOT"

    # Clean up any existing containers (but keep volume for existing data)
    print_status "Cleaning up existing containers..."
    docker-compose down --remove-orphans 2>/dev/null || true

    # Start services
    if docker-compose up -d --build; then
        print_success "Docker services started"
    else
        print_error "Failed to start Docker services"
        return 1
    fi

    # Wait for services to be healthy
    print_status "Waiting for services to be healthy..."
    sleep 30

    # Check service health
    if curl -f http://localhost:8080/actuator/health &>/dev/null; then
        print_success "Payment Bridge health check passed"
    else
        print_error "Payment Bridge health check failed"
    fi

    if curl -f http://localhost:8081/actuator/health &>/dev/null; then
        print_success "Mock Payment API health check passed"
    else
        print_error "Mock Payment API health check failed"
    fi

    # Test end-to-end functionality
    print_status "Testing end-to-end payment flow..."
    
    # First check database schema
    print_status "Checking database schema..."
    docker-compose exec postgres psql -U payment_user -d payment_bridge -c "\d payment" 2>/dev/null || echo "Could not check schema"
    
    RESPONSE=$(curl -s -w "\nHTTP_STATUS:%{http_code}" \
        -X POST http://localhost:8080/api/v1/payments \
        -H "Content-Type: application/json" \
        -H "X-Idempotency-Key: test-123" \
        -d '{
            "amount": 100.00,
            "currency": "USD",
            "clientReference": "TEST-001"
        }' 2>/dev/null)

    # Extract HTTP status and response body
    HTTP_STATUS=$(echo "$RESPONSE" | grep "HTTP_STATUS:" | cut -d: -f2)
    RESPONSE_BODY=$(echo "$RESPONSE" | sed '/HTTP_STATUS:/d')

    if [ "$HTTP_STATUS" = "202" ]; then
        print_success "End-to-end payment flow test passed (HTTP 202)"
        
        # Check if payment was actually created in database
        PAYMENT_COUNT=$(docker-compose exec postgres psql -U payment_user -d payment_bridge -t -c "SELECT COUNT(*) FROM payment;" 2>/dev/null | tr -d ' ')
        if [ "$PAYMENT_COUNT" -gt 0 ]; then
            print_success "Payment record created in database ($PAYMENT_COUNT records)"
        else
            print_warning "No payment records found in database"
        fi
        
    else
        print_error "End-to-end payment flow test failed (HTTP $HTTP_STATUS)"
        echo "Response: $RESPONSE_BODY"
        
        # Additional debugging
        print_status "Checking application logs..."
        docker-compose logs payment-bridge --tail=30 2>/dev/null || echo "Could not retrieve logs"
        
        print_status "Checking database migration status..."
        docker-compose exec postgres psql -U payment_user -d payment_bridge -c "SELECT * FROM flyway_schema_history ORDER BY installed_rank;" 2>/dev/null || echo "Could not check migrations"
        
        print_status "Checking database table structure..."
        docker-compose exec postgres psql -U payment_user -d payment_bridge -c "\d payment" 2>/dev/null || echo "Could not check table structure"
        
        return 1
    fi

    # Clean up
    print_status "Stopping Docker services..."
    docker-compose down -v

    return 0
}

# Main test execution
main() {
    local mock_api_passed=0
    local payment_bridge_passed=0
    local docker_passed=0

    print_status "Starting comprehensive application testing..."

    # Test Mock Payment API
    if test_mock_api; then
        mock_api_passed=1
    fi

    # Test Payment Bridge
    if test_payment_bridge; then
        payment_bridge_passed=1
    fi

    # Test Docker services
    if test_docker_services; then
        docker_passed=1
    fi

    # Summary
    echo
    echo "======================================================"
    echo "🧪 Test Results Summary"
    echo "======================================================"

    if [ $mock_api_passed -eq 1 ]; then
        print_success "Mock Payment API: PASSED"
    else
        print_error "Mock Payment API: FAILED"
    fi

    if [ $payment_bridge_passed -eq 1 ]; then
        print_success "Payment Bridge: PASSED"
    else
        print_error "Payment Bridge: FAILED"
    fi

    if [ $docker_passed -eq 1 ]; then
        print_success "Docker Services: PASSED"
    else
        print_error "Docker Services: FAILED"
    fi

    if [ $mock_api_passed -eq 1 ] && [ $payment_bridge_passed -eq 1 ] && [ $docker_passed -eq 1 ]; then
        print_success "All tests passed! 🎉"
        return 0
    else
        print_error "Some tests failed. Please check the output above."
        return 1
    fi
}

# Run main function
main "$@"