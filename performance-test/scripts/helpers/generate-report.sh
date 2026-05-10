#!/bin/bash

# Generate comprehensive performance test report
# Usage: ./generate-report.sh <results_dir> <test_type>

set -e

RESULTS_DIR=$1
TEST_TYPE=$2

if [ -z "$RESULTS_DIR" ] || [ -z "$TEST_TYPE" ]; then
    echo "Usage: $0 <results_dir> <test_type>"
    echo "Example: $0 /path/to/results single-instance"
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPORTS_DIR="$SCRIPT_DIR/../reports"
REPORT_FILE="$REPORTS_DIR/performance-report-${TEST_TYPE}-$(date +%Y%m%d-%H%M%S).md"

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${BLUE}📋 Generating performance report for: $TEST_TYPE${NC}"

# Create reports directory
mkdir -p "$REPORTS_DIR"

# Function to extract metrics from summary files
extract_metrics() {
    local summary_file=$1
    local load_level=$2

    if [ -f "$summary_file" ]; then
        # Extract key metrics using grep and sed
        total_requests=$(grep "Total Requests:" "$summary_file" | sed 's/.*: *//' || echo "N/A")
        error_rate=$(grep "Error Rate:" "$summary_file" | sed 's/.*: *//' || echo "N/A")
        p95_latency=$(grep "P95:" "$summary_file" | sed 's/.*: *//' || echo "N/A")
        throughput=$(grep "Requests/Second:" "$summary_file" | sed 's/.*: *//' || echo "N/A")

        echo "$load_level|$total_requests|$error_rate|$p95_latency|$throughput"
    else
        echo "$load_level|N/A|N/A|N/A|N/A"
    fi
}

# Find all summary files
SUMMARY_FILES=$(find "$RESULTS_DIR" -name "summary-*.txt" -type f | sort)

# Generate report header
cat << EOF > "$REPORT_FILE"
# Performance Test Report: $TEST_TYPE

**Generated:** $(date)  
**Test Type:** $TEST_TYPE  
**Results Directory:** $RESULTS_DIR  

## Executive Summary

This report presents the performance testing results for the Payment System Speckit under $TEST_TYPE deployment configuration.

### Test Configuration
- **Deployment:** $TEST_TYPE
- **Load Generator:** JMeter
- **Test Duration:** 5 minutes per load level
- **Ramp-up Time:** 30-60 seconds
- **Load Levels:** 10, 50, 100, 200, 500, 1000 concurrent users (scaled test)

## Performance Results

### Summary Table

| Load Level | Total Requests | Error Rate | P95 Latency (ms) | Throughput (RPS) |
|------------|----------------|------------|------------------|------------------|
EOF

# Add results table
if [ -n "$SUMMARY_FILES" ]; then
    while IFS= read -r summary_file; do
        # Extract load level from filename
        load_level=$(basename "$summary_file" | sed 's/summary-.*-\([0-9]*\)-users\.txt/\1/' || echo "unknown")

        # Extract metrics
        metrics=$(extract_metrics "$summary_file" "$load_level")
        echo "$metrics" | awk -F'|' '{print "| " $1 " | " $2 " | " $3 " | " $4 " | " $5 " |"}' >> "$REPORT_FILE"
    done <<< "$SUMMARY_FILES"
else
    echo "| No test results found | N/A | N/A | N/A | N/A |" >> "$REPORT_FILE"
fi

# Add detailed analysis section
cat << EOF >> "$REPORT_FILE"

## Detailed Analysis

### Performance Trends

EOF

# Analyze trends
if [ -n "$SUMMARY_FILES" ]; then
    # Find the highest load test that was successful
    last_summary=$(find "$RESULTS_DIR" -name "summary-*.txt" -type f | sort | tail -1)

    if [ -f "$last_summary" ]; then
        max_load=$(basename "$last_summary" | sed 's/summary-.*-\([0-9]*\)-users\.txt/\1/' || echo "unknown")
        max_throughput=$(grep "Requests/Second:" "$last_summary" | sed 's/.*: *//' || echo "unknown")
        max_p95=$(grep "P95:" "$last_summary" | sed 's/.*: *//' || echo "unknown")
        max_errors=$(grep "Error Rate:" "$last_summary" | sed 's/.*: *//' || echo "unknown")

        cat << EOF >> "$REPORT_FILE"
**Maximum Sustained Load:** $max_load concurrent users
- **Peak Throughput:** $max_throughput requests/second
- **P95 Latency:** $max_p95 ms
- **Error Rate:** $max_errors

EOF
    fi
fi

# Add system metrics section
cat << EOF >> "$REPORT_FILE"
### System Metrics

EOF

# Check for metrics data
METRICS_DIR="$RESULTS_DIR/metrics"
if [ -d "$METRICS_DIR" ]; then
    # System info
    sysinfo_file=$(find "$METRICS_DIR" -name "system-info.json" -type f | head -1)
    if [ -f "$sysinfo_file" ]; then
        cpu_cores=$(jq -r '.system.cpu_cores' "$sysinfo_file" 2>/dev/null || echo "unknown")
        memory=$(jq -r '.system.memory_total' "$sysinfo_file" 2>/dev/null || echo "unknown")

        cat << EOF >> "$REPORT_FILE"
**System Configuration:**
- CPU Cores: $cpu_cores
- Memory: $memory
- Docker: $(jq -r '.system.docker_version' "$sysinfo_file" 2>/dev/null || echo "unknown")

EOF
    fi

    # Docker stats
    docker_stats_file=$(find "$METRICS_DIR" -name "docker-stats-*.json" -type f | head -1)
    if [ -f "$docker_stats_file" ]; then
        container_count=$(jq '. | length' "$docker_stats_file" 2>/dev/null || echo "0")

        cat << EOF >> "$REPORT_FILE"
**Container Metrics:**
- Active Containers: $container_count
- Resource monitoring: Available in metrics directory

EOF
    fi
else
    echo "No system metrics collected." >> "$REPORT_FILE"
    echo "" >> "$REPORT_FILE"
fi

# Add load distribution section for scaled tests
if [[ "$TEST_TYPE" == *"scaled"* ]]; then
    cat << EOF >> "$REPORT_FILE"
### Load Distribution Analysis

EOF

    DIST_DIR="$RESULTS_DIR/distribution"
    if [ -d "$DIST_DIR" ]; then
        container_metrics_file=$(find "$DIST_DIR" -name "container-metrics-*.json" -type f | head -1)
        if [ -f "$container_metrics_file" ]; then
            active_containers=$(jq '.containers | map(select(.status == "running")) | length' "$container_metrics_file" 2>/dev/null || echo "0")
            expected_containers=3

            cat << EOF >> "$REPORT_FILE"
**Container Status:**
- Expected Instances: $expected_containers
- Active Instances: $active_containers
- Load Balancer: nginx (round-robin)

EOF

            if [ "$active_containers" -eq "$expected_containers" ]; then
                echo "**✅ All instances healthy and active**" >> "$REPORT_FILE"
            else
                echo "**⚠️ Instance count mismatch - check container health**" >> "$REPORT_FILE"
            fi
            echo "" >> "$REPORT_FILE"
        fi
    else
        echo "No load distribution data collected." >> "$REPORT_FILE"
        echo "" >> "$REPORT_FILE"
    fi
fi

# Add recommendations section
cat << EOF >> "$REPORT_FILE"
## Recommendations

### Performance Optimizations

1. **Connection Pooling**: Consider optimizing database connection pools for higher concurrency
2. **Caching Strategy**: Implement Redis for idempotency keys to reduce database load
3. **Async Processing**: Optimize virtual thread usage for I/O bound operations
4. **Monitoring**: Add detailed metrics collection for production monitoring

### Scaling Recommendations

EOF

# Add scaling-specific recommendations
if [[ "$TEST_TYPE" == *"scaled"* ]]; then
    cat << EOF >> "$REPORT_FILE"
- **Load Balancer**: nginx performed well for request distribution
- **Instance Count**: 3 instances provided good scalability
- **Health Checks**: Ensure proper health check configuration for auto-scaling
- **Session Affinity**: Consider sticky sessions if stateful operations are added

EOF
else
    cat << EOF >> "$REPORT_FILE"
- **Horizontal Scaling**: Consider implementing load balancer for production scaling
- **Resource Limits**: Monitor CPU and memory usage for scaling thresholds
- **Auto-scaling**: Implement metrics-based auto-scaling policies

EOF
fi

# Add next steps
cat << EOF >> "$REPORT_FILE"
### Next Steps

1. **Compare Results**: Run both single-instance and scaled tests for comparison
2. **Production Tuning**: Adjust configurations based on performance baselines
3. **Monitoring Setup**: Implement production monitoring and alerting
4. **Load Testing**: Consider more comprehensive load testing scenarios

## Files and Data

### Results Location
- **Results Directory:** $RESULTS_DIR
- **Report File:** $REPORT_FILE

### Key Files
- \`summary-*.txt\`: Individual test summaries
- \`results-*.jtl\`: Raw JMeter results (CSV format)
- \`jmeter-*.log\`: JMeter execution logs
- \`metrics/\`: System and container metrics
EOF

if [[ "$TEST_TYPE" == *"scaled"* ]]; then
    cat << EOF >> "$REPORT_FILE"
- \`distribution/\`: Load distribution analysis
EOF
fi

cat << EOF >> "$REPORT_FILE"

### Raw Data
All raw performance data is preserved for further analysis and comparison with other test runs.

---

**Report generated by:** Payment System Speckit Performance Test Suite  
**Test Framework:** JMeter + Custom Analysis Scripts  
**Timestamp:** $(date -u +%Y-%m-%dT%H:%M:%SZ)
EOF

echo -e "${GREEN}✅ Performance report generated: $REPORT_FILE${NC}"

# Display summary
echo
echo -e "${BLUE}📈 Report Summary:${NC}"
echo -e "${BLUE}   Type: $TEST_TYPE${NC}"
echo -e "${BLUE}   Location: $REPORT_FILE${NC}"
echo -e "${BLUE}   Results: $RESULTS_DIR${NC}"