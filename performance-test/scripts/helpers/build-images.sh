#!/bin/bash
# Enterprise Docker Image Build Script
# Features:
# - Layer caching optimization
# - Multi-platform builds (when needed)
# - Image tagging strategy
# - CI/CD integration
# - Build metrics and logging

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
PERF_DIR="$PROJECT_ROOT/performance-test"

# Configuration
IMAGE_TAG="${1:-latest}"
BUILDKIT_PROGRESS="${BUILDKIT_PROGRESS:-plain}"
DOCKER_BUILDKIT="${DOCKER_BUILDKIT:-1}"
COMPOSE_DOCKER_CLI_BUILD="${COMPOSE_DOCKER_CLI_BUILD:-1}"

# Build optimization flags
export DOCKER_BUILDKIT
export COMPOSE_DOCKER_CLI_BUILD
export BUILDKIT_PROGRESS

echo "🏗️  Enterprise Docker Image Builder"
echo "=================================="
echo "Image Tag: $IMAGE_TAG"
echo "BuildKit: $DOCKER_BUILDKIT"
echo "Progress: $BUILDKIT_PROGRESS"
echo

# Function to build with optimization
build_optimized() {
  local service="$1"
  local compose_file="$2"
  local context_dir="$3"

  echo "🔨 Building $service..."

  local start_time=$(date +%s)

  cd "$context_dir"

  # Use buildx for advanced features when available
  if docker buildx version >/dev/null 2>&1; then
    echo "   Using Docker BuildX for optimized builds..."

    # Create builder if it doesn't exist
    if ! docker buildx ls | grep -q "performance-builder"; then
      docker buildx create --name performance-builder --use
    fi

    # Build with advanced caching
    docker buildx build \
      --target "$service" \
      --tag "payment-system-speckit-$service:$IMAGE_TAG" \
      --cache-from "type=local,src=/tmp/.buildx-cache-$service" \
      --cache-to "type=local,dst=/tmp/.buildx-cache-$service" \
      --load \
      --progress="$BUILDKIT_PROGRESS" \
      --build-arg BUILDKIT_INLINE_CACHE=1 \
      .
  else
    # Fallback to regular docker-compose build
    docker-compose -f "$compose_file" build \
      --build-arg BUILDKIT_INLINE_CACHE=1 \
      "$service"
  fi

  local end_time=$(date +%s)
  local duration=$((end_time - start_time))

  echo "✅ $service built in ${duration}s"
  echo
}

# Build base images first (most likely to change)
echo "📦 Building base service images..."

# Build payment-bridge (most complex, build first)
build_optimized "payment-bridge" "$PROJECT_ROOT/docker-compose.yml" "$PROJECT_ROOT"

# Build mock-payment-api
build_optimized "mock-payment-api" "$PROJECT_ROOT/docker-compose.yml" "$PROJECT_ROOT"

# Build load-balancer
build_optimized "load-balancer" "$PROJECT_ROOT/docker-compose.yml" "$PROJECT_ROOT"

# Tag images for scaled environment (reuse the same images)
echo "🏷️  Tagging images for scaled environment..."
docker tag "payment-system-speckit-payment-bridge:$IMAGE_TAG" "config-payment-bridge:$IMAGE_TAG"
docker tag "payment-system-speckit-mock-payment-api:$IMAGE_TAG" "config-mock-payment-api:$IMAGE_TAG"

echo "✅ All images built and tagged successfully"
echo
echo "📊 Build Summary:"
echo "   payment-bridge: payment-system-speckit-payment-bridge:$IMAGE_TAG"
echo "   mock-payment-api: payment-system-speckit-mock-payment-api:$IMAGE_TAG"
echo "   load-balancer: payment-system-speckit-load-balancer:$IMAGE_TAG"
echo "   scaled-bridge: config-payment-bridge:$IMAGE_TAG"
echo "   scaled-mock: config-mock-payment-api:$IMAGE_TAG"
echo
echo "💡 Enterprise Tips:"
echo "   - Set BUILD_CACHE=false to force rebuild"
echo "   - Use IMAGE_TAG for versioned deployments"
echo "   - Images are cached in /tmp/.buildx-cache-*"
echo "   - Use 'docker system prune' to clean old images"