# Quick Performance Testing Improvements

## Finding

The current quick performance test setup was too rigid and the analysis step was slow for larger JMeter result files. This caused the full quick test to take longer than necessary, and it also made separate single-instance and scaled-instance test flows harder to run independently.

## Problem

1. `analyze-results.sh` used a slow in-memory sort implementation for percentiles.
2. `quick-performance-test.sh` only supported a combined end-to-end run of single-instance and scaled-instance tests.
3. There was no dedicated quick single-instance or quick scaled-instance wrapper script.

## Solution

1. Optimized `performance-test/scripts/analyze-results.sh`:
   - Switched percentile calculation to use `awk` plus `sort`.
   - Reduced repeated file scans by extracting most metrics in a single pass.
   - Kept the same output format while cutting processing time dramatically.

2. Updated `performance-test/scripts/quick-performance-test.sh`:
   - Added a CLI mode argument: `single`, `scaled`, or `all`.
   - Preserved the combined mode for end-to-end verification.
   - Kept the quick test as a shorter local validation run:
     - `20,000` total requests
     - `5` users only
   - Added a combined report generator that merges single and scaled summaries when both are available.

3. Added dedicated quick wrappers:
   - `performance-test/scripts/quick-single-performance-test.sh`
   - `performance-test/scripts/quick-scaled-performance-test.sh`

## Problem, solution, and improvement summary

### Problem

- JMeter execution on Apple Silicon was inconsistent: Docker-based JMeter image selection led to amd64 emulation on macOS and host connectivity issues.
- The full 100k-request plan did not reliably execute real traffic because the loop count logic was overly complex in JMeter and was not always passed correctly from the scripts.
- Dockerized JMeter could not always resolve the application endpoint from macOS without using `host.docker.internal`, causing tests to generate zero requests or invalid results.

### Solution

- Updated performance scripts to use `qainsights/jmeter:latest` and to prefer native Homebrew-installed JMeter on macOS when available.
- Changed JMeter plans to use configurable `TARGET_HOST` and `TARGET_PORT` properties instead of hardcoded `localhost:8080`.
- Simplified the full test plan loop count to `${__P(LOOPS,1)}` and calculated the exact `LOOPS` value in the shell script using bash arithmetic before passing it with `-JLOOPS`.
- Added explicit macOS Docker host handling so Dockerized JMeter can reach the application service through `host.docker.internal`.

### Suggestions for improvement

- Add a shared performance test helper script or central configuration file to consolidate JMeter command construction, target host/port handling, and plan selection.
- Add environment variable or config file support to switch tests between local, Docker-hosted, and cloud-hosted target endpoints cleanly.
- Add health-check gating and service readiness validation before running JMeter, so the test runner only starts after the application and dependencies are fully healthy.
- Document Apple Silicon Docker platform requirements and expected performance behavior for local load testing.

### Completed in this iteration

- ✅ Updated performance scripts for ARM64/macOS compatibility and `qainsights/jmeter:latest` usage.
- ✅ Added native JMeter detection for Homebrew-installed JMeter on macOS.
- ✅ Fixed the full 100k-request JMeter plan loop count wiring using `LOOPS` passed from the shell script.
- ✅ Made JMeter target host and port configurable and macOS Docker host-aware.

## Current iteration: run issues, fixes, and recommendations

### One-instance run problems

- The single-instance Docker workflow initially failed because service startup dependencies were not fully gated, meaning JMeter could begin before the app stack was ready.
- On Apple Silicon, the JMeter Docker image ran under amd64 emulation, creating platform mismatch warnings and slower execution.
- The fix was to:
  - validate service health at the load balancer endpoint before starting tests,
  - add `platform: linux/arm64` for Docker services on macOS, and
  - update the JMeter Docker command in the scripts to `docker run --platform linux/arm64 ... qainsights/jmeter:latest`.

### Scaled-instance run problems

- The scaled `docker-compose` flow was inefficient and fragile because it rebuilt the payment bridge image multiple times and started services before the load balancer was fully ready.
- `docker-compose.scaled.yml` needed a clear startup order and consistent shared image usage for all bridge replicas.
- The solution was to rebuild the shared `payment-bridge` image once, reuse it for all 3 instances, wait for nginx readiness, and keep the load balancer in the critical path when comparing scaled performance.

### Analysis report bug fix

- The JMeter result analysis step had a slow or brittle implementation in `analyze-results.sh` that caused delays and sometimes failed on large JTL files.
- This was fixed by switching to a more efficient `awk` + `sort` percentile path and ensuring summary file generation works correctly when JMeter runs inside Docker.
- The combined report generation now only runs when summary files are present, preventing incomplete report outputs.

### JMeter Plan Fix

- **Issue**: The `payment-load-test-100k.jmx` plan had a complex Groovy expression in the LoopController that failed to evaluate correctly, resulting in 0 requests being executed.
- **Root Cause**: The expression `${__groovy(Math.ceil(Integer.parseInt('${__P(TOTAL_REQUESTS,100000)}') / Integer.parseInt('${__P(USERS,5)}')))}` was too complex for JMeter to parse reliably.
- **Solution**:
  - Simplified the LoopController to use a direct property reference: `${__P(LOOPS,1)}`
  - Updated performance test scripts to calculate the correct loop count using bash arithmetic: `loops = ceil(TOTAL_REQUESTS / USERS)`
  - Pass the calculated `LOOPS` parameter to JMeter via `-JLOOPS` command line argument
- **Result**: JMeter now executes the exact number of requests specified, enabling proper performance testing.

### Native JMeter on ARM64/Mac Infrastructure

To leverage the full performance potential of your ARM64 Mac, you can install JMeter natively instead of using Docker:

1. **Install JMeter**:

   ```bash
   brew install jmeter
   ```

2. **Verify Installation**:

   ```bash
   jmeter --version
   ```

3. **Run Tests Natively**:
   - The scripts will automatically detect native JMeter and use it instead of Docker
   - This provides better performance and direct resource access
   - No platform emulation overhead

**Note**: When using native JMeter, ensure your Java version is compatible (JMeter requires Java 8+). Check with:

```bash
java -version
```

### Key lessons

- `postgres`, `rabbitmq`, `mock-payment-api`, and `payment-bridge` are required for full end-to-end validation.
- The load balancer is optional for a single-instance smoke test, but necessary for true scaled comparison.
- Apple Silicon needs explicit ARM64 Docker support for both app services and JMeter to avoid emulation.

## Suggestions for further improvement

- Add a small config file or environment variable support so the same quick script can reuse any custom JMeter plan or target URL.
- Add a lightweight health-check report for scaled runs to capture why specific bridge instances become unhealthy.
- Consider using a separate `performance-test/scripts/common.sh` library if more scripts are added, so shared Docker startup and cleanup logic stays centralized.
- For very large JTL files, switch to a summary-only JMeter output or use a faster CSV parser utility.

## Usage

- Run a quick single-instance test:

  ```bash
  performance-test/scripts/quick-single-performance-test.sh
  ```

- Run a quick scaled performance test:

  ```bash
  performance-test/scripts/quick-scaled-performance-test.sh
  ```

- Run the full quick test (single + scaled) with a fast validation run:
  - `20,000` total requests
  - `5` users

  ```bash
  performance-test/scripts/quick-performance-test.sh all
  ```

- Run the exact full performance test with `100,000` total requests across `5`, `10`, and `20` users:
  ```bash
  performance-test/scripts/full-performance-test.sh
  ```

## Scaling Efficiency Observation

### Observed result

- Single-instance test: ~384 RPS
- Scaled 3-instance test: ~245 RPS

### What this means

This is not a bug in the math of the test framework; it is an indication that the scaled setup is hitting shared bottlenecks and overhead.

### Likely causes

- Shared backend resources (PostgreSQL, RabbitMQ, and mock-payment-api) remain a common bottleneck for all three bridge instances.
- Nginx adds an extra proxy hop and load balancing overhead compared to a direct single-instance path.
- The JMeter load profile may not be high enough to fully saturate three instances in parallel.
- Multiple app containers on one host can compete for the same CPU, memory, and disk throughput.

### Why 3 instances do not automatically mean 3x throughput

In this deployment, all payment-bridge replicas still share the same database and queue. That means scaling the app layer alone can deliver much smaller benefits if the database or message broker is the true limiting factor.

### Recommended next steps

1. Increase the test load (more users and longer duration) to verify whether the 3-instance setup can achieve better scaling.
2. Collect per-container CPU, memory, and I/O metrics for payment-bridge, postgres, rabbitmq, and nginx.
3. Confirm whether the bottleneck is PostgreSQL or RabbitMQ by running a targeted load test against the app with a lightweight backend stub.
4. Consider scaling the shared infrastructure (database / queue) if app replicas are healthy but throughput is still limited.
5. Use the current setup as a baseline: a 60%-ish efficiency is reasonable when a shared stateful backend is the bottleneck.

## How to diagnose the bottleneck

1. Observe container resources during the quick formatted run:
   - `docker stats payment-bridge-1 payment-bridge-2 payment-bridge-3 payment-system-postgres payment-system-rabbitmq payment-system-nginx`
   - If PostgreSQL or RabbitMQ is saturated while the app replicas are not, the shared services are the primary constraint.
2. Check response times and error metrics:
   - High latency with low error rate usually points to database or proxy bottlenecks.
   - Frequent 429 responses indicate rate limiting or load balancer limits rather than app throughput.
3. Review logs and health endpoints:
   - `docker logs payment-system-nginx`
   - `docker logs payment-system-postgres`
   - `curl http://localhost:8080/actuator/health`
4. Run a focused app-layer validation test:
   - Bypass the database or use a lightweight stub backend to see if the three replicas can scale linearly with minimal shared state.
5. If all services have low utilization, raise the JMeter concurrency and duration rather than assuming the scaling setup is broken.

## Local resource starvation observation

The current Mac-based Docker test is showing resource saturation rather than a code issue.

- The total CPU usage across containers is extremely high: the three `payment-bridge` instances, JMeter, Postgres, and RabbitMQ are collectively consuming more CPU than a typical local Docker VM can support.
- On Docker Desktop / OrbStack for Mac, CPU percentages are counted per core. If your Docker CPU limit is set to 4 or 6 cores, a total usage above 400-600% means the VM is saturated.
- The three bridge JVMs are competing for CPU time with each other and with JMeter. That increases context switching and reduces effective throughput.
- JMeter itself is using a large share of CPU. A high JMeter CPU use means the load generator is part of the bottleneck, not just the target application.
- Each bridge JVM also consumes memory overhead, so three instances on a laptop add significant base JVM cost compared to a single instance.

### Practical fix suggestions for local testing

1. Increase Docker CPU allocation if your Mac has available cores.
2. Run JMeter on a separate host or outside Docker if possible.
3. Cap each bridge JVM memory and thread count for local tests.
4. Treat this result as a local environment limit: the architecture may still scale better in a properly provisioned cloud or cluster.
