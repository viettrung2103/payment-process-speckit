#!/bin/bash

# Collect system metrics during performance testing
# This script gathers CPU, memory, and container statistics

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RESULTS_DIR=${1:-"$SCRIPT_DIR/../results"}

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}📊 Collecting system metrics...${NC}"

# Create metrics directory
METRICS_DIR="$RESULTS_DIR/metrics"
mkdir -p "$METRICS_DIR"

# Function to collect Docker stats
collect_docker_stats() {
    local output_file="$METRICS_DIR/docker-stats-$(date +%s).json"

    echo -e "${YELLOW}   Collecting Docker container statistics...${NC}"

    # Get stats for all payment-system containers
    docker stats --no-stream --format json payment-system-postgres payment-system-rabbitmq mock-payment-api payment-bridge nginx 2>/dev/null | \
    jq -s '.' > "$output_file" 2>/dev/null || echo "[]" > "$output_file"

    echo -e "${GREEN}   ✅ Docker stats saved to: $output_file${NC}"
}

# Function to collect container logs
collect_container_logs() {
    echo -e "${YELLOW}   Collecting container logs...${NC}"

    # Get logs from all containers
    docker-compose logs --tail=1000 > "$METRICS_DIR/container-logs-$(date +%s).log" 2>/dev/null || \
    echo "Failed to collect container logs" > "$METRICS_DIR/container-logs-error.log"
}

# Function to collect system information
collect_system_info() {
    local sysinfo_file="$METRICS_DIR/system-info.json"

    echo -e "${YELLOW}   Collecting system information...${NC}"

    # Get system information
    cat << EOF > "$sysinfo_file"
{
    "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
    "system": {
        "cpu_cores": $(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo "4"),
        "memory_total": "$(free -h 2>/dev/null | awk 'NR==2{print $2}' || echo "unknown")",
        "docker_version": "$(docker --version 2>/dev/null || echo "unknown")",
        "docker_compose_version": "$(docker-compose --version 2>/dev/null || echo "unknown")"
    },
    "test_environment": {
        "results_directory": "$RESULTS_DIR",
        "collection_timestamp": "$(date +%s)"
    }
}
EOF

    echo -e "${GREEN}   ✅ System info saved to: $sysinfo_file${NC}"
}

# Function to collect health check data
collect_health_data() {
    local health_file="$METRICS_DIR/health-checks-$(date +%s).json"

    echo -e "${YELLOW}   Collecting health check data...${NC}"

    # Test various endpoints
    cat << EOF > "$health_file"
{
    "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
    "health_checks": {
        "postgres": $(curl -s http://localhost:5432 2>/dev/null | jq . 2>/dev/null || echo "null"),
        "rabbitmq": $(curl -s http://localhost:15672/api/healthchecks/node 2>/dev/null | jq . 2>/dev/null || echo "null"),
        "mock_payment_api": $(curl -s http://localhost:8081/actuator/health 2>/dev/null | jq . 2>/dev/null || echo "null"),
        "payment_bridge": $(curl -s http://localhost:8080/actuator/health 2>/dev/null | jq . 2>/dev/null || echo "null"),
        "nginx": $(curl -s http://localhost:8080/actuator/health 2>/dev/null | jq . 2>/dev/null || echo "null")
    }
}
EOF

    echo -e "${GREEN}   ✅ Health data saved to: $health_file${NC}"
}

# Function to collect load distribution (for scaled tests)
collect_load_distribution() {
    local dist_file="$METRICS_DIR/load-distribution-$(date +%s).json"

    echo -e "${YELLOW}   Collecting load distribution data...${NC}"

    # Check nginx upstream status if available
    cat << EOF > "$dist_file"
{
    "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
    "load_distribution": {
        "nginx_upstreams": $(curl -s http://localhost:8080/nginx_status 2>/dev/null || echo "null"),
        "container_count": $(docker ps --filter "name=payment-bridge" --format json 2>/dev/null | jq length 2>/dev/null || echo "0"),
        "active_containers": $(docker ps --filter "name=payment-bridge" --filter "status=running" --format json 2>/dev/null | jq length 2>/dev/null || echo "0")
    }
}
EOF

    echo -e "${GREEN}   ✅ Load distribution data saved to: $dist_file${NC}"
}

# Main collection
echo -e "${YELLOW}Starting metrics collection at $(date)${NC}"

collect_system_info
collect_docker_stats
collect_health_data
collect_load_distribution
collect_container_logs

echo -e "${GREEN}✅ Metrics collection completed${NC}"
echo -e "${GREEN}📁 Metrics saved to: $METRICS_DIR${NC}"

# Create summary
cat << EOF > "$METRICS_DIR/README.md"
# Performance Test Metrics

Collected at: $(date)

## Files
- \`system-info.json\` - System and environment information
- \`docker-stats-*.json\` - Container resource usage statistics
- \`health-checks-*.json\` - Service health status at collection time
- \`load-distribution-*.json\` - Load balancing distribution data
- \`container-logs-*.log\` - Recent container logs

## Analysis
Use these metrics to correlate with JMeter results for:
- Resource bottlenecks
- Service health during load
- Load distribution effectiveness
- System capacity limits
EOF

echo -e "${GREEN}📋 Metrics summary created${NC}"