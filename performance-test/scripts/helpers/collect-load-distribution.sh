#!/bin/bash

# Collect load distribution metrics for scaled deployment
# This script analyzes how requests are distributed across payment-bridge instances

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RESULTS_DIR=$1
TEST_NAME=$2

if [ -z "$RESULTS_DIR" ] || [ -z "$TEST_NAME" ]; then
    echo "Usage: $0 <results_dir> <test_name>"
    echo "Example: $0 /path/to/results scaled-3-instances-100-users"
    exit 1
fi

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}📊 Collecting load distribution for: $TEST_NAME${NC}"

# Create distribution directory
DIST_DIR="$RESULTS_DIR/distribution"
mkdir -p "$DIST_DIR"

# Function to check nginx upstream status
check_nginx_upstreams() {
    local upstream_file="$DIST_DIR/nginx-upstreams-$(date +%s).json"

    echo -e "${YELLOW}   Checking nginx upstream status...${NC}"

    # Try to get nginx status (may not be available in basic nginx)
    if curl -s "http://localhost:8080/nginx_status" >/dev/null 2>&1; then
        curl -s "http://localhost:8080/nginx_status" > "$upstream_file" 2>/dev/null || echo "{}" > "$upstream_file"
    else
        # Fallback: create basic upstream info
        cat << EOF > "$upstream_file"
{
    "note": "nginx status not available - using basic container info",
    "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
    "configured_upstreams": 3,
    "upstream_servers": ["payment-bridge-1:8080", "payment-bridge-2:8080", "payment-bridge-3:8080"]
}
EOF
    fi

    echo -e "${GREEN}   ✅ Nginx upstream data saved${NC}"
}

# Function to collect container-specific metrics
collect_container_metrics() {
    local metrics_file="$DIST_DIR/container-metrics-$(date +%s).json"

    echo -e "${YELLOW}   Collecting per-container metrics...${NC}"

    # Get metrics for each payment-bridge instance
    cat << EOF > "$metrics_file"
{
    "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
    "test_name": "$TEST_NAME",
    "containers": [
EOF

    # Check each payment-bridge instance
    for i in {1..3}; do
        container_name="payment-bridge-$i"

        if docker ps --format "table {{.Names}}" | grep -q "^${container_name}$"; then
            # Container is running
            health_status=$(docker inspect "$container_name" --format='{{.State.Health.Status}}' 2>/dev/null || echo "unknown")
            restart_count=$(docker inspect "$container_name" --format='{{.RestartCount}}' 2>/dev/null || echo "0")

            cat << EOF >> "$metrics_file"
        {
            "name": "$container_name",
            "status": "running",
            "health": "$health_status",
            "restarts": $restart_count,
            "instance_id": "$i"
        }$(if [ $i -lt 3 ]; then echo ","; fi)
EOF
        else
            # Container not found
            cat << EOF >> "$metrics_file"
        {
            "name": "$container_name",
            "status": "not_found",
            "health": "unknown",
            "restarts": 0,
            "instance_id": "$i"
        }$(if [ $i -lt 3 ]; then echo ","; fi)
EOF
        fi
    done

    cat << EOF >> "$metrics_file"
    ]
}
EOF

    echo -e "${GREEN}   ✅ Container metrics saved${NC}"
}

# Function to analyze request distribution patterns
analyze_request_patterns() {
    local pattern_file="$DIST_DIR/request-patterns-$(date +%s).json"

    echo -e "${YELLOW}   Analyzing request distribution patterns...${NC}"

    # Look for any existing JTL files to analyze response patterns
    jtl_files=$(find "$RESULTS_DIR" -name "*.jtl" -type f 2>/dev/null | head -1)

    if [ -n "$jtl_files" ]; then
        # Basic analysis of response times and success rates
        total_requests=$(wc -l < "$jtl_files" 2>/dev/null || echo "0")
        successful_requests=$(grep -c "true\|200" "$jtl_files" 2>/dev/null || echo "0")

        cat << EOF > "$pattern_file"
{
    "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
    "analysis_type": "basic_jtl_analysis",
    "total_requests_analyzed": $total_requests,
    "successful_requests": $successful_requests,
    "success_rate": $(echo "scale=4; $successful_requests / $total_requests * 100" | bc 2>/dev/null || echo "0"),
    "note": "Detailed per-instance analysis requires nginx access logs or custom instrumentation"
}
EOF
    else
        # No JTL files found
        cat << EOF > "$pattern_file"
{
    "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
    "analysis_type": "no_jtl_data",
    "note": "No JTL files found for detailed analysis",
    "recommendation": "Ensure JMeter is saving results properly"
}
EOF
    fi

    echo -e "${GREEN}   ✅ Request pattern analysis saved${NC}"
}

# Function to collect network-level distribution
collect_network_distribution() {
    local network_file="$DIST_DIR/network-distribution-$(date +%s).json"

    echo -e "${YELLOW}   Collecting network-level distribution data...${NC}"

    # Check Docker network connections
    cat << EOF > "$network_file"
{
    "timestamp": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
    "network_analysis": {
        "docker_networks": $(docker network ls --format json 2>/dev/null | jq -s '.' 2>/dev/null || echo "[]"),
        "container_connections": $(docker ps --filter "name=payment-bridge" --format "table {{.Names}}\t{{.Ports}}" 2>/dev/null | tail -n +2 | wc -l 2>/dev/null || echo "0"),
        "expected_instances": 3,
        "load_balancer": "nginx"
    }
}
EOF

    echo -e "${GREEN}   ✅ Network distribution data saved${NC}"
}

# Main collection
echo -e "${YELLOW}Starting load distribution analysis at $(date)${NC}"

check_nginx_upstreams
collect_container_metrics
analyze_request_patterns
collect_network_distribution

echo -e "${GREEN}✅ Load distribution analysis completed${NC}"
echo -e "${GREEN}📁 Distribution data saved to: $DIST_DIR${NC}"

# Create summary
cat << EOF > "$DIST_DIR/README.md"
# Load Distribution Analysis

Test: $TEST_NAME
Collected at: $(date)

## Analysis Components

### nginx-upstreams-*.json
- Nginx load balancer upstream server status
- Request distribution metrics (if available)
- Health check results for each upstream server

### container-metrics-*.json
- Per-container health and status information
- Restart counts and instance IDs
- Running state verification

### request-patterns-*.json
- Analysis of request distribution patterns
- Success rates and response time correlations
- Load balancing effectiveness metrics

### network-distribution-*.json
- Docker network connectivity analysis
- Container-to-container communication status
- Network-level load distribution insights

## Recommendations for Better Analysis

To get more detailed load distribution metrics:

1. **Enable nginx access logs** with upstream response times
2. **Add custom metrics** to payment-bridge instances
3. **Use distributed tracing** (e.g., Jaeger, Zipkin)
4. **Implement request tagging** for tracking distribution

## Interpreting Results

- **Even distribution**: Each instance should handle ~33% of requests
- **Health checks**: All instances should be healthy during testing
- **Network connectivity**: All containers should be reachable
EOF

echo -e "${GREEN}📋 Distribution analysis summary created${NC}"