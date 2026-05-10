#!/bin/bash

# Analyze JMeter test results and generate performance metrics
# Usage: ./analyze-results.sh <jtl_file> <output_file>

set -e

JTL_FILE=$1
OUTPUT_FILE=$2

if [ -z "$JTL_FILE" ] || [ -z "$OUTPUT_FILE" ]; then
    echo "Usage: $0 <jtl_file> <output_file>"
    echo "Example: $0 results.jtl summary.txt"
    exit 1
fi

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}📊 Analyzing JMeter results: $JTL_FILE${NC}"

# Check if file exists
if [ ! -f "$JTL_FILE" ]; then
    echo -e "${RED}❌ JTL file not found: $JTL_FILE${NC}"
    exit 1
fi

# Function to calculate percentile from JTL data (optimized)
calculate_percentile() {
    local percentile=$1
    local column=$2

    # Use external sort for better performance
    awk -F',' -v col="$column" 'NR > 1 && $col != "" && $col != " " {print $col}' "$JTL_FILE" |
    sort -n |
    awk -v p="$percentile" '
        { values[NR] = $1; count++ }
        END {
            if (count == 0) {
                print "0"
                exit
            }
            n = int(count * p / 100)
            if (n < 1) n = 1
            if (n > count) n = count
            print values[n]
        }
    '
}

# Extract all metrics in a single pass for better performance
METRICS=$(awk -F',' '
    BEGIN {
        total = 0
        successful = 0
        min_latency = 999999
        max_latency = 0
        sum_latency = 0
        start_time = 0
        end_time = 0
    }
    NR > 1 {
        total++
        latency = $2 + 0
        success = ($8 == "true" || $8 == "200") ? 1 : 0
        successful += success

        if (latency > 0) {
            if (latency < min_latency) min_latency = latency
            if (latency > max_latency) max_latency = latency
            sum_latency += latency
            latencies[total] = latency
        }

        timestamp = $1 + 0
        if (start_time == 0 || timestamp < start_time) start_time = timestamp
        if (timestamp > end_time) end_time = timestamp
    }
    END {
        avg_latency = total > 0 ? sum_latency / total : 0
        duration_ms = end_time - start_time
        duration_sec = duration_ms > 0 ? duration_ms / 1000 : 0
        throughput = duration_sec > 0 ? total / duration_sec : 0

        print total "," successful "," min_latency "," max_latency "," avg_latency "," duration_sec "," throughput
    }
' "$JTL_FILE")

# Parse the metrics
IFS=',' read -r TOTAL_REQUESTS SUCCESSFUL_REQUESTS MIN_LATENCY MAX_LATENCY AVG_LATENCY DURATION_SECONDS THROUGHPUT <<< "$METRICS"
FAILED_REQUESTS=$((TOTAL_REQUESTS - SUCCESSFUL_REQUESTS))

# Calculate error rate
if [ "$TOTAL_REQUESTS" -gt 0 ]; then
    ERROR_RATE=$(echo "scale=2; ($FAILED_REQUESTS * 100) / $TOTAL_REQUESTS" | bc 2>/dev/null || echo "0.00")
else
    ERROR_RATE="0.00"
fi

# Calculate response time percentiles (column 2 is response time in JTL)
P50_LATENCY=$(calculate_percentile 50 2)
P95_LATENCY=$(calculate_percentile 95 2)
P99_LATENCY=$(calculate_percentile 99 2)

# Generate report
cat << EOF > "$OUTPUT_FILE"
PERFORMANCE TEST RESULTS
========================

Test File: $(basename "$JTL_FILE")
Generated: $(date)

SUMMARY METRICS
---------------
Total Requests:     $TOTAL_REQUESTS
Successful Requests: $SUCCESSFUL_REQUESTS
Failed Requests:    $FAILED_REQUESTS
Error Rate:         ${ERROR_RATE}%

RESPONSE TIME (ms)
------------------
Min:     $MIN_LATENCY
P50:     $P50_LATENCY
P95:     $P95_LATENCY
P99:     $P99_LATENCY
Max:     $MAX_LATENCY

THROUGHPUT
----------
Requests/Second: $THROUGHPUT
Test Duration:   ${DURATION_SECONDS}s

PERFORMANCE ASSESSMENT
----------------------
$(if (( $(echo "$ERROR_RATE < 1" | bc -l 2>/dev/null || echo "1") )); then
    echo "✅ Error Rate: GOOD (< 1%)"
else
    echo "❌ Error Rate: HIGH (≥ 1%)"
fi)

$(if (( $(echo "$P95_LATENCY < 1000" | bc -l 2>/dev/null || echo "1") )); then
    echo "✅ P95 Latency: GOOD (< 1000ms)"
else
    echo "⚠️  P95 Latency: SLOW (≥ 1000ms)"
fi)

$(if (( $(echo "$THROUGHPUT > 10" | bc -l 2>/dev/null || echo "0") )); then
    echo "✅ Throughput: GOOD (> 10 RPS)"
else
    echo "⚠️  Throughput: LOW (≤ 10 RPS)"
fi)
EOF

echo -e "${GREEN}✅ Analysis complete: $OUTPUT_FILE${NC}"

# Display summary on console
echo
echo -e "${BLUE}📈 Quick Summary:${NC}"
echo -e "${BLUE}   Requests: $TOTAL_REQUESTS total, $SUCCESSFUL_REQUESTS successful${NC}"
echo -e "${BLUE}   Error Rate: ${ERROR_RATE}%${NC}"
echo -e "${BLUE}   P95 Latency: ${P95_LATENCY}ms${NC}"
echo -e "${BLUE}   Throughput: ${THROUGHPUT} RPS${NC}"