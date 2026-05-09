#!/bin/bash

# Auto-scaling management script
# Start, stop, and monitor the auto-scaling process

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
PERF_DIR="$PROJECT_ROOT/performance-test"

# Configuration
AUTO_SCALER_SCRIPT="$SCRIPT_DIR/auto-scaler.sh"
PID_FILE="/tmp/payment-system-auto-scaler.pid"
LOG_FILE="/tmp/payment-system-auto-scaler.log"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Logging function
log() {
    echo -e "$(date '+%Y-%m-%d %H:%M:%S') - $1"
}

# Check if auto-scaler is running
is_running() {
    if [ -f "$PID_FILE" ]; then
        local pid=$(cat "$PID_FILE")
        if kill -0 "$pid" 2>/dev/null; then
            return 0  # Running
        else
            rm -f "$PID_FILE"  # Stale PID file
        fi
    fi
    return 1  # Not running
}

# Start auto-scaler
start() {
    if is_running; then
        log "${YELLOW}⚠️  Auto-scaler is already running (PID: $(cat "$PID_FILE"))${NC}"
        return 1
    fi

    log "${GREEN}🚀 Starting auto-scaler...${NC}"

    # Start in background
    nohup "$AUTO_SCALER_SCRIPT" >> "$LOG_FILE" 2>&1 &
    local pid=$!

    # Save PID
    echo $pid > "$PID_FILE"

    # Wait a moment and verify it's running
    sleep 2
    if kill -0 "$pid" 2>/dev/null; then
        log "${GREEN}✅ Auto-scaler started successfully (PID: $pid)${NC}"
        log "${BLUE}📝 Logs: $LOG_FILE${NC}"
        return 0
    else
        log "${RED}❌ Failed to start auto-scaler${NC}"
        rm -f "$PID_FILE"
        return 1
    fi
}

# Stop auto-scaler
stop() {
    if ! is_running; then
        log "${YELLOW}⚠️  Auto-scaler is not running${NC}"
        return 1
    fi

    local pid=$(cat "$PID_FILE")
    log "${BLUE}🛑 Stopping auto-scaler (PID: $pid)...${NC}"

    # Send SIGTERM first
    kill "$pid" 2>/dev/null || true

    # Wait for graceful shutdown
    local count=0
    while [ $count -lt 10 ] && kill -0 "$pid" 2>/dev/null; do
        sleep 1
        ((count++))
    done

    # Force kill if still running
    if kill -0 "$pid" 2>/dev/null; then
        log "${YELLOW}⚠️  Force stopping auto-scaler...${NC}"
        kill -9 "$pid" 2>/dev/null || true
        sleep 1
    fi

    if ! kill -0 "$pid" 2>/dev/null; then
        rm -f "$PID_FILE"
        log "${GREEN}✅ Auto-scaler stopped${NC}"
        return 0
    else
        log "${RED}❌ Failed to stop auto-scaler${NC}"
        return 1
    fi
}

# Show status
status() {
    if is_running; then
        local pid=$(cat "$PID_FILE")
        log "${GREEN}✅ Auto-scaler is running (PID: $pid)${NC}"

        # Show recent log entries
        if [ -f "$LOG_FILE" ]; then
            log "${BLUE}📝 Recent logs:${NC}"
            tail -n 5 "$LOG_FILE" | while read -r line; do
                echo "  $line"
            done
        fi

        # Show current scaling status
        show_scaling_status
    else
        log "${RED}❌ Auto-scaler is not running${NC}"
    fi
}

# Show current scaling status
show_scaling_status() {
    log "${BLUE}📊 Current scaling status:${NC}"

    # Count running payment-bridge instances
    local running_instances=$(docker ps --filter "name=payment-bridge-" --format "{{.Names}}" | wc -l)
    echo "  Running instances: $running_instances"

    # Show instance details
    docker ps --filter "name=payment-bridge-" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" | while read -r line; do
        echo "  $line"
    done

    # Show CPU usage
    echo ""
    log "${BLUE}📈 Current CPU usage:${NC}"
    docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemPerc}}" | grep "payment-bridge-" || echo "  No payment-bridge containers found"
}

# Show logs
logs() {
    local lines=${1:-50}
    if [ -f "$LOG_FILE" ]; then
        log "${BLUE}📝 Showing last $lines lines of auto-scaler logs:${NC}"
        tail -n "$lines" "$LOG_FILE"
    else
        log "${YELLOW}⚠️  No log file found at $LOG_FILE${NC}"
    fi
}

# Manual scaling commands
scale_to() {
    local target_instances=$1

    if ! [[ "$target_instances" =~ ^[0-9]+$ ]] || [ "$target_instances" -lt 1 ] || [ "$target_instances" -gt 5 ]; then
        log "${RED}❌ Invalid number of instances. Must be between 1 and 5.${NC}"
        return 1
    fi

    log "${BLUE}🎯 Manually scaling to $target_instances instances${NC}"

    # This would need to be implemented to override the auto-scaler
    # For now, just show current status
    show_scaling_status
    log "${YELLOW}⚠️  Manual scaling not yet implemented. Use auto-scaler or modify docker-compose directly.${NC}"
}

# Show help
help() {
    echo "Auto-Scaling Management Script"
    echo ""
    echo "Usage: $0 <command> [options]"
    echo ""
    echo "Commands:"
    echo "  start          Start the auto-scaler"
    echo "  stop           Stop the auto-scaler"
    echo "  status         Show auto-scaler status and current scaling"
    echo "  logs [lines]   Show auto-scaler logs (default: 50 lines)"
    echo "  scale-to <n>   Manually scale to N instances (1-5)"
    echo "  help           Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0 start"
    echo "  $0 status"
    echo "  $0 logs 100"
    echo "  $0 scale-to 4"
}

# Main command handling
case "${1:-help}" in
    start)
        start
        ;;
    stop)
        stop
        ;;
    status)
        status
        ;;
    logs)
        logs "${2:-50}"
        ;;
    scale-to)
        if [ -z "$2" ]; then
            log "${RED}❌ Please specify number of instances${NC}"
            echo ""
            help
            exit 1
        fi
        scale_to "$2"
        ;;
    help|--help|-h)
        help
        ;;
    *)
        log "${RED}❌ Unknown command: $1${NC}"
        echo ""
        help
        exit 1
        ;;
esac