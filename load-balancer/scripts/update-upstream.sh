#!/bin/bash

# Update Upstream Configuration Script
# Dynamically updates nginx upstream servers based on running payment-bridge instances

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LB_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PROJECT_ROOT="$(cd "$LB_DIR/.." && pwd)"

# Configuration
CONTAINER_NAME="payment-system-nginx"
DEFAULT_PORT="8080"

# Source common functions
source "$SCRIPT_DIR/manage-load-balancer.sh"

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

# Discover running payment-bridge instances
discover_instances() {
    log_info "Discovering running payment-bridge instances..."

    # Find all running payment-bridge containers
    local instances=$(docker ps --filter "name=payment-bridge-" --format "{{.Names}}" | sort)

    if [ -z "$instances" ]; then
        log_warning "No payment-bridge instances found, using default configuration"
        echo "payment-bridge-1:$DEFAULT_PORT"
        return 0
    fi

    local upstream_servers=""
    for instance in $instances; do
        # Get the host port mapping for port 8080
        local port=$(docker inspect "$instance" --format '{{(index (index .NetworkSettings.Ports "8080/tcp") 0).HostPort}}' 2>/dev/null)

        if [ -n "$port" ] && [ "$port" != "null" ]; then
            upstream_servers+="${instance}:$DEFAULT_PORT,"
        else
            log_warning "Could not determine port for instance: $instance"
        fi
    done

    # Remove trailing comma
    upstream_servers=${upstream_servers%,}

    if [ -z "$upstream_servers" ]; then
        log_warning "No valid upstream servers found, using default"
        echo "payment-bridge-1:$DEFAULT_PORT"
    else
        log_info "Found upstream servers: $upstream_servers"
        echo "$upstream_servers"
    fi
}

# Validate instance health before adding to upstream
validate_instance() {
    local instance="$1"
    local max_retries="${2:-3}"
    local retry_count=0

    log_info "Validating health of instance: $instance"

    while [ $retry_count -lt $max_retries ]; do
        # Extract host and port
        local host=$(echo "$instance" | cut -d: -f1)
        local port=$(echo "$instance" | cut -d: -f2)

        # For docker containers, we need to check the internal health
        # This is a simplified check - in production, use proper health checks
        if docker exec "$host" curl -s -f "http://localhost:$port/actuator/health" > /dev/null 2>&1; then
            log_success "Instance $instance is healthy"
            return 0
        fi

        ((retry_count++))
        log_warning "Health check failed for $instance (attempt $retry_count/$max_retries)"
        sleep 2
    done

    log_error "Instance $instance failed health check after $max_retries attempts"
    return 1
}

# Update nginx upstream configuration
update_nginx_upstream() {
    local instances="$1"

    log_info "Updating nginx upstream configuration..."

    # Validate all instances before updating
    local valid_instances=""
    IFS=',' read -ra INSTANCE_ARRAY <<< "$instances"
    for instance in "${INSTANCE_ARRAY[@]}"; do
        if validate_instance "$instance"; then
            valid_instances+="$instance,"
        else
            log_warning "Skipping unhealthy instance: $instance"
        fi
    done

    # Remove trailing comma
    valid_instances=${valid_instances%,}

    if [ -z "$valid_instances" ]; then
        log_error "No healthy instances available for upstream configuration"
        return 1
    fi

    # Use the manage-load-balancer.sh script to update upstream
    if "$SCRIPT_DIR/manage-load-balancer.sh" update-upstream "$valid_instances"; then
        log_success "Nginx upstream configuration updated successfully"
        log_info "Active upstream servers: $valid_instances"
        return 0
    else
        log_error "Failed to update nginx upstream configuration"
        return 1
    fi
}

# Main function
main() {
    local auto_discover="${1:-true}"

    log_info "Payment System Load Balancer - Upstream Update Script"
    log_info "=================================================="

    local instances=""

    if [ "$auto_discover" = "true" ] || [ "$auto_discover" = "auto" ]; then
        instances=$(discover_instances)
    else
        # Manual instance specification
        instances="$1"
        if [ -z "$instances" ]; then
            log_error "No instances specified. Usage: $0 [auto|<instance1:port,instance2:port,...>]"
            exit 1
        fi
    fi

    if [ -z "$instances" ]; then
        log_error "No instances found or specified"
        exit 1
    fi

    log_info "Target upstream instances: $instances"

    if update_nginx_upstream "$instances"; then
        log_success "Upstream update completed successfully"

        # Show final status
        echo
        "$SCRIPT_DIR/manage-load-balancer.sh" status
    else
        log_error "Upstream update failed"
        exit 1
    fi
}

# Show usage if requested
if [ "${1:-}" = "help" ] || [ "${1:-}" = "--help" ] || [ "${1:-}" = "-h" ]; then
    cat << EOF
Payment System Load Balancer - Upstream Update Script

USAGE:
    $0 [auto|<instances>]

DESCRIPTION:
    Updates the nginx upstream configuration with healthy payment-bridge instances.

MODES:
    auto                Automatically discover running payment-bridge containers (default)
    <instances>         Manually specify instances as comma-separated list
                        Format: instance1:port,instance2:port,...

EXAMPLES:
    $0                    # Auto-discover instances
    $0 auto              # Same as above
    $0 payment-bridge-1:8080,payment-bridge-2:8080  # Manual specification

EOF
    exit 0
fi

# Run main function
main "$@"