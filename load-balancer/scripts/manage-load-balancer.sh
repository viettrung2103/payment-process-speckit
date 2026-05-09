#!/bin/bash

# Load Balancer Management Script
# Handles configuration updates, upstream management, and monitoring

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LB_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PROJECT_ROOT="$(cd "$LB_DIR/.." && pwd)"

# Configuration
CONTAINER_NAME="payment-system-nginx"
NGINX_CONFIG_DIR="/etc/nginx"
UPSTREAM_CONFIG_FILE="$NGINX_CONFIG_DIR/conf.d/upstream.conf"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

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

# Check if load balancer is running
is_running() {
    docker ps --filter "name=$CONTAINER_NAME" --filter "status=running" | grep -q "$CONTAINER_NAME"
}

# Wait for load balancer to be ready
wait_for_lb() {
    local timeout="${1:-30}"
    local count=0

    log_info "Waiting for load balancer to be ready..."
    while [ $count -lt $timeout ]; do
        if curl -s -f "http://localhost/nginx_status" > /dev/null 2>&1; then
            log_success "Load balancer is ready"
            return 0
        fi

        sleep 2
        ((count += 2))
    done

    log_error "Load balancer did not become ready within ${timeout}s"
    return 1
}

# Update upstream configuration
update_upstream() {
    local instances="$1"

    if [ -z "$instances" ]; then
        log_error "No instances provided"
        echo "Usage: $0 update-upstream <instance1:port,instance2:port,...>"
        exit 1
    fi

    log_info "Updating upstream configuration with instances: $instances"

    # Generate upstream configuration
    local upstream_config="upstream payment_bridge_backend {\n"
    upstream_config+="    least_conn;\n"
    upstream_config+="    health_check interval=5s fails=2 passes=2 uri=/actuator/health;\n\n"

    IFS=',' read -ra INSTANCE_ARRAY <<< "$instances"
    for instance in "${INSTANCE_ARRAY[@]}"; do
        upstream_config+="    server $instance max_fails=3 fail_timeout=30s;\n"
    done

    upstream_config+="}\n"

    # Update configuration in container
    echo -e "$upstream_config" | docker exec -i "$CONTAINER_NAME" tee "$UPSTREAM_CONFIG_FILE" > /dev/null

    if [ $? -eq 0 ]; then
        log_success "Upstream configuration updated"
        reload_nginx
    else
        log_error "Failed to update upstream configuration"
        exit 1
    fi
}

# Reload nginx configuration
reload_nginx() {
    log_info "Reloading nginx configuration..."

    if docker exec "$CONTAINER_NAME" nginx -s reload; then
        log_success "Nginx configuration reloaded successfully"
        return 0
    else
        log_error "Failed to reload nginx configuration"
        return 1
    fi
}

# Test nginx configuration
test_config() {
    log_info "Testing nginx configuration..."

    if docker exec "$CONTAINER_NAME" nginx -t; then
        log_success "Nginx configuration is valid"
        return 0
    else
        log_error "Nginx configuration has errors"
        return 1
    fi
}

# Show current upstream configuration
show_upstream() {
    log_info "Current upstream configuration:"
    docker exec "$CONTAINER_NAME" cat "$UPSTREAM_CONFIG_FILE" 2>/dev/null || log_warning "No upstream configuration found"
}

# Show nginx status
show_status() {
    log_info "Nginx status:"
    if is_running; then
        log_success "Load balancer is running"

        echo "Active connections:"
        curl -s "http://localhost:8081/nginx_status" 2>/dev/null || echo "Status endpoint not available"

        echo -e "\nUpstream servers:"
        show_upstream
    else
        log_warning "Load balancer is not running"
    fi
}

# Show rate limiting statistics
show_rate_limits() {
    log_info "Rate limiting statistics (requires nginx-plus for detailed stats):"
    log_info "Basic request statistics from access logs:"

    # Show recent rate limiting from logs (last 100 lines)
    docker exec "$CONTAINER_NAME" tail -100 /var/log/nginx/access.log 2>/dev/null | grep -c "429" | xargs echo "Rate limited requests (last 100):" || echo "No access logs available"
}

# Health check
health_check() {
    log_info "Performing health check..."

    # Check if container is running
    if ! is_running; then
        log_error "Load balancer container is not running"
        return 1
    fi

    # Check nginx status endpoint
    if ! curl -s -f "http://localhost:8081/nginx_status" > /dev/null; then
        log_error "Nginx status endpoint is not responding"
        return 1
    fi

    # Check main proxy endpoint (should return 404 for root)
    if curl -s "http://localhost/" | grep -q "Not Found"; then
        log_success "Load balancer is healthy"
        return 0
    else
        log_error "Load balancer health check failed"
        return 1
    fi
}

# Restart load balancer
restart_lb() {
    log_info "Restarting load balancer..."

    if docker restart "$CONTAINER_NAME"; then
        log_success "Load balancer restarted"
        wait_for_lb
    else
        log_error "Failed to restart load balancer"
        exit 1
    fi
}

# Show help
show_help() {
    cat << EOF
Payment System Load Balancer Management Script

USAGE:
    $0 <command> [options]

COMMANDS:
    status              Show load balancer status and configuration
    health              Perform health check
    test-config         Test nginx configuration validity
    reload              Reload nginx configuration
    restart             Restart load balancer container
    update-upstream     Update upstream server configuration
                        Usage: $0 update-upstream <instance1:port,instance2:port,...>
    show-upstream       Show current upstream configuration
    rate-limits         Show rate limiting statistics
    help                Show this help message

EXAMPLES:
    $0 status
    $0 health
    $0 update-upstream payment-bridge-1:8080,payment-bridge-2:8080
    $0 reload

EOF
}

# Main command handling
main() {
    case "${1:-help}" in
        status)
            show_status
            ;;
        health)
            health_check && log_success "Health check passed" || log_error "Health check failed"
            ;;
        test-config)
            test_config
            ;;
        reload)
            reload_nginx
            ;;
        restart)
            restart_lb
            ;;
        update-upstream)
            update_upstream "$2"
            ;;
        show-upstream)
            show_upstream
            ;;
        rate-limits)
            show_rate_limits
            ;;
        help|--help|-h)
            show_help
            ;;
        *)
            log_error "Unknown command: $1"
            echo
            show_help
            exit 1
            ;;
    esac
}

# Run main function with all arguments
main "$@"