#!/bin/bash

# Combine and analyze both Java simulation results and JMeter performance test results
# Usage: ./combine-results.sh [simulation-log-file] [jmeter-csv-file]

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
RESULTS_DIR="$PROJECT_ROOT/performance-test/results"
COMBINED_DIR="$RESULTS_DIR/combined-$(date +%Y%m%d-%H%M%S)"

SIMULATION_LOG="$1"
JMETER_CSV="$2"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${PURPLE}🔗 Combining Test Results${NC}"
echo -e "${PURPLE}═══════════════════════════════${NC}"
echo

mkdir -p "$COMBINED_DIR"

# Function to extract Java simulation results
extract_simulation_results() {
    local log_file="$1"
    local output_file="$2"

    if [ ! -f "$log_file" ]; then
        echo -e "${YELLOW}⚠️  Simulation log file not found: $log_file${NC}"
        return 1
    fi

    echo -e "${BLUE}📊 Extracting simulation results from: $log_file${NC}"

    # Extract key metrics from simulation output
    {
        echo "# Java Simulation Results"
        echo

        # Extract configuration
        echo "## Configuration"
        grep "Total payments submitted:" "$log_file" || echo "Total payments submitted: N/A"
        grep "Number of instances:" "$log_file" || echo "Number of instances: 1 (single instance)"
        grep "Submit interval:" "$log_file" || echo "Submit interval: N/A"
        grep "Processing time per payment:" "$log_file" || echo "Processing time per payment: N/A"
        grep "Shutdown after:" "$log_file" || echo "Shutdown after: N/A"
        echo

        # Extract final results
        echo "## Final Results"
        grep "Total submitted:" "$log_file" || echo "Total submitted: N/A"
        grep "Completed before shutdown:" "$log_file" || echo "Completed before shutdown: N/A"
        grep "Pending RECEIVED at shutdown:" "$log_file" || echo "Pending RECEIVED at shutdown: N/A"
        grep "In-progress at shutdown:" "$log_file" || echo "In-progress at shutdown: N/A"
        grep "Completed after restart:" "$log_file" || echo "Completed after restart: N/A"
        grep "Total completed:" "$log_file" || echo "Total completed: N/A"
        grep "Total failed:" "$log_file" || echo "Total failed: N/A"
        grep "Remaining pending after recovery:" "$log_file" || echo "Remaining pending after recovery: N/A"
        echo

        # Calculate recovery success
        local total_submitted=$(grep "Total submitted:" "$log_file" | sed 's/.*: //' | tr -d ',')
        local remaining_pending=$(grep "Remaining pending after recovery:" "$log_file" | sed 's/.*: //' | tr -d ',')

        if [ -n "$total_submitted" ] && [ -n "$remaining_pending" ]; then
            local recovered=$((total_submitted - remaining_pending))
            local recovery_rate=$(echo "scale=2; ($recovered * 100) / $total_submitted" | bc 2>/dev/null || echo "100.00")
            echo "## Recovery Analysis"
            echo "Payments recovered: $recovered / $total_submitted"
            echo "Recovery success rate: ${recovery_rate}%"
            if [ "$remaining_pending" = "0" ]; then
                echo "Status: ✅ FULL RECOVERY ACHIEVED"
            else
                echo "Status: ⚠️  PARTIAL RECOVERY ($remaining_pending payments lost)"
            fi
        fi

    } > "$output_file"

    echo -e "${GREEN}✅ Simulation results extracted to: $output_file${NC}"
}

# Function to analyze JMeter CSV results
analyze_jmeter_results() {
    local csv_file="$1"
    local output_file="$2"

    if [ ! -f "$csv_file" ]; then
        echo -e "${YELLOW}⚠️  JMeter CSV file not found: $csv_file${NC}"
        return 1
    fi

    echo -e "${BLUE}📊 Analyzing JMeter results from: $csv_file${NC}"

    # Use the existing analyzer script
    local analyzer_script="$SCRIPT_DIR/helpers/analyze-results.sh"

    if [ -f "$analyzer_script" ]; then
        # Convert CSV to JTL format if needed (JMeter CSV is similar to JTL)
        local jtl_file="${csv_file%.*}.jtl"
        cp "$csv_file" "$jtl_file"

        # Run the analyzer
        bash "$analyzer_script" "$jtl_file" "$output_file"

        # Clean up temporary JTL file
        rm -f "$jtl_file"
    else
        # Fallback analysis if analyzer script not found
        {
            echo "# JMeter Performance Test Results"
            echo

            # Basic CSV analysis
            local total_requests=$(tail -n +2 "$csv_file" | wc -l)
            local successful_requests=$(awk -F',' 'NR > 1 && ($4 == "200" || $4 == "202") {count++} END {print count}' "$csv_file")
            local failed_requests=$((total_requests - successful_requests))

            echo "## Summary"
            echo "Total requests: $total_requests"
            echo "Successful requests: $successful_requests"
            echo "Failed requests: $failed_requests"

            if [ "$total_requests" -gt 0 ]; then
                local error_rate=$(echo "scale=2; ($failed_requests * 100) / $total_requests" | bc 2>/dev/null || echo "0.00")
                echo "Error rate: ${error_rate}%"
            fi

            # Response time analysis
            local avg_response=$(awk -F',' 'NR > 1 && $2 > 0 {sum += $2; count++} END {if (count > 0) print sum/count; else print 0}' "$csv_file")
            local min_response=$(awk -F',' 'NR > 1 && $2 > 0 {if (min == "" || $2 < min) min = $2} END {print min}' "$csv_file")
            local max_response=$(awk -F',' 'NR > 1 && $2 > 0 {if ($2 > max) max = $2} END {print max}' "$csv_file")

            echo
            echo "## Response Times (ms)"
            echo "Average: ${avg_response%.*}"
            echo "Min: $min_response"
            echo "Max: $max_response"

        } > "$output_file"
    fi

    echo -e "${GREEN}✅ JMeter results analyzed to: $output_file${NC}"
}

# Function to create combined report
create_combined_report() {
    local simulation_file="$1"
    local jmeter_file="$2"
    local output_file="$3"

    {
        echo "# Combined Test Results Report"
        echo "Generated on: $(date)"
        echo

        echo "# Test Overview"
        echo
        echo "This report combines results from:"
        echo "1. Java-based shutdown/restart recovery simulations"
        echo "2. JMeter performance tests"
        echo

        if [ -f "$simulation_file" ]; then
            echo "# Java Simulation Results"
            echo
            cat "$simulation_file"
            echo
            echo "---"
            echo
        fi

        if [ -f "$jmeter_file" ]; then
            echo "# JMeter Performance Test Results"
            echo
            cat "$jmeter_file"
            echo
        fi

        echo "# Summary & Recommendations"
        echo

        # Extract key metrics for summary
        if [ -f "$simulation_file" ]; then
            local recovery_status=$(grep "Status:" "$simulation_file" | head -1)
            if [[ "$recovery_status" == *"FULL RECOVERY"* ]]; then
                echo "✅ **Recovery Test**: PASSED - Zero payment loss achieved"
            else
                echo "⚠️  **Recovery Test**: PARTIAL - Some payments may be lost"
            fi
        fi

        if [ -f "$jmeter_file" ]; then
            local error_rate=$(grep "Error Rate:" "$jmeter_file" | sed 's/.*: //' | sed 's/%//')
            local throughput=$(grep "Requests/Second:" "$jmeter_file" | sed 's/.*: //')

            if [ -n "$error_rate" ] && [ "$(echo "$error_rate < 1" | bc 2>/dev/null)" = "1" ]; then
                echo "✅ **Performance Test**: PASSED - Low error rate ($error_rate%)"
            else
                echo "⚠️  **Performance Test**: ISSUES - High error rate ($error_rate%)"
            fi

            if [ -n "$throughput" ]; then
                echo "📊 **Throughput**: $throughput RPS"
            fi
        fi

        echo
        echo "## Next Steps"
        echo "1. Review recovery mechanisms for any lost payments"
        echo "2. Optimize performance if error rates are high"
        echo "3. Consider additional chaos testing scenarios"
        echo "4. Validate results in production-like environment"

    } > "$output_file"

    echo -e "${GREEN}✅ Combined report created: $output_file${NC}"
}

# Main execution
echo -e "${BLUE}📁 Combined results will be saved to: $COMBINED_DIR${NC}"
echo

SIMULATION_RESULTS="$COMBINED_DIR/simulation-results.md"
JMETER_RESULTS="$COMBINED_DIR/jmeter-results.txt"
COMBINED_REPORT="$COMBINED_DIR/combined-report.md"

# Process simulation results if provided
if [ -n "$SIMULATION_LOG" ]; then
    extract_simulation_results "$SIMULATION_LOG" "$SIMULATION_RESULTS"
else
    echo -e "${YELLOW}ℹ️  No simulation log provided. Use: $0 <simulation-log> [jmeter-csv]${NC}"
    echo -e "${YELLOW}   To capture simulation output: java QuickSingleInstanceReceivedRecoveryTestRunner > simulation.log${NC}"
fi
echo

# Process JMeter results if provided
if [ -n "$JMETER_CSV" ]; then
    analyze_jmeter_results "$JMETER_CSV" "$JMETER_RESULTS"
else
    echo -e "${YELLOW}ℹ️  No JMeter CSV provided. Use: $0 [simulation-log] <jmeter-csv>${NC}"
    JMETER_CSV="/Users/mac/Programming/payment-system-speckit/performance-test/config/results/payment-load-test-20260510-182402.csv"
    if [ -f "$JMETER_CSV" ]; then
        echo -e "${YELLOW}   Using found JMeter CSV: $JMETER_CSV${NC}"
        analyze_jmeter_results "$JMETER_CSV" "$JMETER_RESULTS"
    fi
fi
echo

# Create combined report
create_combined_report "$SIMULATION_RESULTS" "$JMETER_RESULTS" "$COMBINED_REPORT"

echo
echo -e "${PURPLE}📋 Results Summary:${NC}"
echo -e "   📁 Combined directory: $COMBINED_DIR"
if [ -f "$SIMULATION_RESULTS" ]; then
    echo -e "   📄 Simulation results: $(basename "$SIMULATION_RESULTS")"
fi
if [ -f "$JMETER_RESULTS" ]; then
    echo -e "   📄 JMeter results: $(basename "$JMETER_RESULTS")"
fi
echo -e "   📄 Combined report: $(basename "$COMBINED_REPORT")"

echo
echo -e "${GREEN}🎉 Results combination complete!${NC}"