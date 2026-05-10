#!/bin/bash

# Consolidate all test results into a single results directory
# Usage: ./consolidate-results.sh [target-directory]
# Default target: performance-test/results/consolidated

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEFAULT_TARGET="$SCRIPT_DIR/performance-test/results/consolidated"
TARGET_DIR="${1:-$DEFAULT_TARGET}"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${PURPLE}🔗 Consolidating Test Results${NC}"
echo -e "${PURPLE}═══════════════════════════════${NC}"
echo

echo -e "${BLUE}📁 Target directory: $TARGET_DIR${NC}"
mkdir -p "$TARGET_DIR"

# Function to copy results with categorization
copy_results() {
    local source_dir="$1"
    local category="$2"

    if [ ! -d "$source_dir" ]; then
        echo -e "${YELLOW}⚠️  Source directory not found: $source_dir${NC}"
        return
    fi

    echo -e "${BLUE}📋 Processing $category results from: $source_dir${NC}"

    local count=0
    while IFS= read -r -d '' file; do
        local filename=$(basename "$file")
        local target_file="$TARGET_DIR/${category}_${filename}"

        # Avoid overwriting files with same name
        local counter=1
        while [ -f "$target_file" ]; do
            target_file="$TARGET_DIR/${category}_${counter}_${filename}"
            counter=$((counter + 1))
        done

        cp "$file" "$target_file"
        echo -e "   📄 Copied: $filename → ${category}_${filename}"
        count=$((count + 1))
    done < <(find "$source_dir" \( -path "$TARGET_DIR" -o -path "$TARGET_DIR/*" -o -path "$source_dir/consolidated" -o -path "$source_dir/consolidated/*" \) -prune -o -type f \( -name "*.csv" -o -name "*.jtl" -o -name "*.txt" -o -name "*.md" -o -name "*.log" \) -print0)

    echo -e "${GREEN}✅ Copied $count $category files${NC}"
    echo
}

# Copy from different result locations
copy_results "$SCRIPT_DIR/results" "root"
copy_results "$SCRIPT_DIR/performance-test/results" "performance"
copy_results "$SCRIPT_DIR/performance-test/config/results" "config"

# Copy any JMeter results that might be elsewhere
if [ -d "$SCRIPT_DIR/jmeter.log" ]; then
    cp "$SCRIPT_DIR/jmeter.log" "$TARGET_DIR/jmeter_root.log"
    echo -e "${GREEN}✅ Copied root JMeter log${NC}"
fi

# Create a summary of all results
create_summary() {
    local summary_file="$TARGET_DIR/results-summary.md"

    {
        echo "# Consolidated Test Results Summary"
        echo "Generated on: $(date)"
        echo

        echo "# Results Locations Consolidated"
        echo
        echo "The following result locations were consolidated:"
        echo "- Root results: \`results/\`"
        echo "- Performance results: \`performance-test/results/\`"
        echo "- Config results: \`performance-test/config/results/\`"
        echo

        echo "# Files Found"
        echo

        echo "## Performance Test Results"
        find "$TARGET_DIR" -name "performance_*.csv" -o -name "performance_*.jtl" -o -name "performance_*.txt" | sed 's|.*/||' | sort || echo "None found"
        echo

        echo "## Configuration Test Results"
        find "$TARGET_DIR" -name "config_*.csv" -o -name "config_*.jtl" | sed 's|.*/||' | sort || echo "None found"
        echo

        echo "## Combined Analysis Results"
        find "$TARGET_DIR" -name "*combined*" -o -name "*analysis*" | sed 's|.*/||' | sort || echo "None found"
        echo

        echo "# Recommendations"
        echo
        echo "1. **Use \`performance-test/scripts/combine-results.sh\`** for future test analysis"
        echo "2. **Centralize results** in \`performance-test/results/consolidated/\` for consistency"
        echo "3. **Clean up old results** periodically to avoid confusion"
        echo "4. **Use clear target directories** such as \`performance-test/results/consolidated/\` or \`performance-test/results/\`"
        echo

        echo "# File Inventory"
        echo
        echo "\`\`\`"
        find "$TARGET_DIR" -type f -name "*.csv" -o -name "*.jtl" -o -name "*.txt" -o -name "*.md" | sort
        echo "\`\`\`"

    } > "$summary_file"

    echo -e "${GREEN}✅ Created summary: $(basename "$summary_file")${NC}"
}

create_summary

echo
echo -e "${PURPLE}📋 Consolidation Complete!${NC}"
echo -e "   📁 Consolidated directory: $TARGET_DIR"
echo -e "   📄 Summary file: results-summary.md"
echo

# Count total files
total_files=$(find "$TARGET_DIR" -type f | wc -l)
echo -e "   📊 Total files consolidated: $total_files"

echo
echo -e "${GREEN}🎉 All test results have been consolidated!${NC}"