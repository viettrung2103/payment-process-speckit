#!/bin/bash

# Load Balancer Health Check Script
# Comprehensive health monitoring for the payment system load balancer

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LB_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PROJECT_ROOT="$(cd "$LB_DIR/.." && pwd)"

# Configuration
CONTAINER_NAME="payment-system-nginx"
LB_HOST="localhost"
LB_PORT="80"
MONITORING_PORT="8081"
TIMEOUT="10"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Exit codes
EXIT_OK=0
EXIT_WARNING=1
EXIT_CRITICAL=2
EXIT_UNKNOWN=3

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

log_critical() {
    echo -e "${RED}[CRITICAL]${NC} $1" >&2
}

# Check if container is running
check_container_running() {
    if docker ps --filter "name=$CONTAINER_NAME" --filter "status=running" --format "{{.Names}}" | grep -q "^$CONTAINER_NAME$"; then
        log_success "Container $CONTAINER_NAME is running"
        return $EXIT_OK
    else
        log_critical "Container $CONTAINER_NAME is not running"
        return $EXIT_CRITICAL
    fi
}

# Check nginx process
check_nginx_process() {
    if docker exec "$CONTAINER_NAME" pgrep -f nginx > /dev/null 2>&1; then
        log_success "Nginx process is running"
        return $EXIT_OK
    else
        log_critical "Nginx process is not running"
        return $EXIT_CRITICAL
    fi
}

# Check nginx configuration
check_nginx_config() {
    if docker exec "$CONTAINER_NAME" nginx -t > /dev/null 2>&1; then
        log_success "Nginx configuration is valid"
        return $EXIT_OK
    else
        log_error "Nginx configuration is invalid"
        return $EXIT_CRITICAL
    fi
}

# Check main load balancer endpoint
check_lb_endpoint() {
    local url="http://$LB_HOST:$LB_PORT/"
    local response_code=$(curl -s -w "%{http_code}" -o /dev/null --max-time $TIMEOUT "$url" 2>/dev/null)

    if [ "$response_code" = "404" ]; then
        log_success "Load balancer endpoint responding (404 expected for root)"
        return $EXIT_OK
    elif [ -n "$response_code" ]; then
        log_warning "Load balancer endpoint responding with unexpected code: $response_code"
        return $EXIT_WARNING
    else
        log_critical "Load balancer endpoint not responding"
        return $EXIT_CRITICAL
    fi
}

# Check nginx status endpoint
check_nginx_status() {
    local url="http://$LB_HOST:$MONITORING_PORT/nginx_status"
    local response=$(curl -s --max-time $TIMEOUT "$url" 2>/dev/null)

    if echo "$response" | grep -q "Active connections"; then
        log_success "Nginx status endpoint responding"

        # Extract some metrics
        local active_connections=$(echo "$response" | grep "Active connections" | awk '{print $3}')
        local total_accepted=$(echo "$response" | grep "accepts" | awk '{print $1}' | tr -d ';')
        local total_handled=$(echo "$response" | grep "handled" | awk '{print $1}' | tr -d ';')

        log_info "Active connections: $active_connections"
        log_info "Total accepted: $total_accepted, handled: $total_handled"

        return $EXIT_OK
    else
        log_error "Nginx status endpoint not responding or returning invalid data"
        return $EXIT_CRITICAL
    fi
}

# Check upstream servers health
check_upstream_health() {
    log_info "Checking upstream server health..."

    # Get upstream configuration
    local upstream_config=$(docker exec "$CONTAINER_NAME" cat /etc/nginx/conf.d/upstream.conf 2>/dev/null)

    if [ -z "$upstream_config" ]; then
        log_warning "No upstream configuration found"
        return $EXIT_WARNING
    fi

    # Extract server lines
    local servers=$(echo "$upstream_config" | grep "server " | grep -v "^[[:space:]]*#" | sed 's/.*server \([^;]*\).*/\1/')

    if [ -z "$servers" ]; then
        log_warning "No upstream servers configured"
        return $EXIT_WARNING
    fi

    local healthy_servers=0
    local total_servers=0

    while IFS= read -r server; do
        ((total_servers++))
        log_info "Checking upstream server: $server"

        # For docker containers, check internal health
        local container_name=$(echo "$server" | cut -d: -f1)
        local container_port=$(echo "$server" | cut -d: -f2)

        if docker exec "$container_name" curl -s -f --max-time 5 "http://localhost:$container_port/actuator/health" > /dev/null 2>&1; then
            log_success "Upstream server $server is healthy"
            ((healthy_servers++))
        else
            log_error "Upstream server $server is unhealthy"
        fi
    done <<< "$servers"

    log_info "Upstream health: $healthy_servers/$total_servers servers healthy"

    if [ $healthy_servers -eq 0 ]; then
        log_critical "No healthy upstream servers available"
        return $EXIT_CRITICAL
    elif [ $healthy_servers -lt $total_servers ]; then
        log_warning "Some upstream servers are unhealthy"
        return $EXIT_WARNING
    else
        log_success "All upstream servers are healthy"
        return $EXIT_OK
    fi
}

# Check rate limiting functionality
check_rate_limiting() {
    log_info "Testing rate limiting functionality..."

    local test_url="http://$LB_HOST:$LB_PORT/api/v1/payments"
    local success_count=0
    local rate_limited_count=0

    # Send requests above the payment endpoint limit (5 req/s)
    for i in {1..8}; do
        local response_code=$(curl -s -w "%{http_code}" -o /dev/null \
            -H "Content-Type: application/json" \
            -d '{"amount": 10.00}' \
            --max-time 5 \
            "$test_url" 2>/dev/null)

        case $response_code in
            200) ((success_count++)) ;;
            429) ((rate_limited_count++)) ;;
            *) log_warning "Unexpected response code: $response_code" ;;
        esac
    done

    log_info "Rate limiting test: $success_count allowed, $rate_limited_count blocked"

    if [ $rate_limited_count -gt 0 ]; then
        log_success "Rate limiting is working correctly"
        return $EXIT_OK
    else
        log_warning "Rate limiting may not be functioning (no requests blocked)"
        return $EXIT_WARNING
    fi
}

# Check resource usage
check_resources() {
    log_info "Checking resource usage..."

    # Get container stats
    local stats=$(docker stats --no-stream --format "table {{.Container}}\t{{.CPUPerc}}\t{{.MemUsage}}" "$CONTAINER_NAME" 2>/dev/null | tail -1)

    if [ -n "$stats" ] && [ "$stats" != "N/A" ]; then
        local cpu_usage=$(echo "$stats" | awk '{print $2}')
        local mem_usage=$(echo "$stats" | awk '{print $3}')

        log_info "CPU Usage: $cpu_usage"
        log_info "Memory Usage: $mem_usage"

        # Check for high resource usage (warning thresholds)
        local cpu_percent=$(echo "$cpu_usage" | sed 's/%//')
        if (( $(echo "$cpu_percent > 80" | bc -l 2>/dev/null || echo "0") )); then
            log_warning "High CPU usage detected: $cpu_usage"
            return $EXIT_WARNING
        fi

        log_success "Resource usage is within acceptable limits"
        return $EXIT_OK
    else
        log_warning "Could not retrieve container resource statistics"
        return $EXIT_WARNING
    fi
}

# Generate health report
generate_report() {
    local overall_status="$1"
    local checks="$2"

    echo
    echo "========================================"
    echo "LOAD BALANCER HEALTH REPORT"
    echo "========================================"
    echo "Timestamp: $(date)"
    echo "Container: $CONTAINER_NAME"
    echo "Endpoint: http://$LB_HOST:$LB_PORT"
    echo

    case $overall_status in
        $EXIT_OK)
            echo -e "${GREEN}OVERALL STATUS: HEALTHY${NC}"
            ;;
        $EXIT_WARNING)
            echo -e "${YELLOW}OVERALL STATUS: WARNING${NC}"
            ;;
        $EXIT_CRITICAL)
            echo -e "${RED}OVERALL STATUS: CRITICAL${NC}"
            ;;
        *)
            echo -e "${RED}OVERALL STATUS: UNKNOWN${NC}"
            ;;
    esac

    echo
    echo "Checks performed: $checks"
    echo "========================================"
}

# Main health check function
perform_health_check() {
    local checks=""
    local overall_status=$EXIT_OK

    log_info "Starting comprehensive load balancer health check..."
    echo

    # Perform all checks
    checks+="container_running,"
    if ! check_container_running; then
        overall_status=$EXIT_CRITICAL
    fi

    checks+="nginx_process,"
    if ! check_nginx_process; then
        overall_status=$EXIT_CRITICAL
    fi

    checks+="nginx_config,"
    if ! check_nginx_config; then
        overall_status=$EXIT_CRITICAL
    fi

    checks+="lb_endpoint,"
    if ! check_lb_endpoint; then
        case $? in
            $EXIT_CRITICAL) overall_status=$EXIT_CRITICAL ;;
            $EXIT_WARNING) [ $overall_status -ne $EXIT_CRITICAL ] && overall_status=$EXIT_WARNING ;;
        esac
    fi

    checks+="nginx_status,"
    if ! check_nginx_status; then
        overall_status=$EXIT_CRITICAL
    fi

    checks+="upstream_health,"
    if ! check_upstream_health; then
        case $? in
            $EXIT_CRITICAL) overall_status=$EXIT_CRITICAL ;;
            $EXIT_WARNING) [ $overall_status -ne $EXIT_CRITICAL ] && overall_status=$EXIT_WARNING ;;
        esac
    fi

    checks+="rate_limiting,"
    if ! check_rate_limiting; then
        case $? in
            $EXIT_CRITICAL) overall_status=$EXIT_CRITICAL ;;
            $EXIT_WARNING) [ $overall_status -ne $EXIT_CRITICAL ] && overall_status=$EXIT_WARNING ;;
        esac
    fi

    checks+="resources,"
    if ! check_resources; then
        case $? in
            $EXIT_CRITICAL) overall_status=$EXIT_CRITICAL ;;
            $EXIT_WARNING) [ $overall_status -ne $EXIT_CRITICAL ] && overall_status=$EXIT_WARNING ;;
        esac
    fi

    # Remove trailing comma
    checks=${checks%,}

    # Generate report
    generate_report $overall_status "$checks"

    return $overall_status
}

# Show usage
show_usage() {
    cat << EOF
Payment System Load Balancer Health Check Script

USAGE:
    $0 [options]

OPTIONS:
    -v, --verbose    Enable verbose output
    -q, --quiet      Suppress non-error output
    -h, --help       Show this help message

DESCRIPTION:
    Performs comprehensive health checks on the payment system load balancer,
    including container status, nginx configuration, upstream health, rate limiting,
    and resource usage monitoring.

EXIT CODES:
    0    OK - All checks passed
    1    WARNING - Some non-critical issues detected
    2    CRITICAL - Critical issues detected
    3    UNKNOWN - Unable to determine status

EOF
}

# Parse command line arguments
VERBOSE=true
QUIET=false

while [[ $# -gt 0 ]]; do
    case $1 in
        -v|--verbose)
            VERBOSE=true
            shift
            ;;
        -q|--quiet)
            QUIET=true
            VERBOSE=false
            shift
            ;;
        -h|--help)
            show_usage
            exit 0
            ;;
        *)
            log_error "Unknown option: $1"
            show_usage
            exit $EXIT_UNKNOWN
            ;;
    esac
done

# Suppress output if quiet mode
if [ "$QUIET" = true ]; then
    exec > /dev/null 2>&1
fi

# Run health check
perform_health_check
exit $?