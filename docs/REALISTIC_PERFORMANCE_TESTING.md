# Realistic Performance Testing Update

## Problem
- The previous realistic stress test wrapper only targeted a single-instance environment.
- It could begin JMeter load before all required services were fully healthy, causing false failures and high error rates.
- It also did not clean up the Docker environment after the run, leaving stale containers and ports open.

## Solution in this iteration
- Enhanced `performance-test/scripts/realistic-performance-test.sh` to support:
  - `single` mode for the one-instance test
  - `scaled` mode for the multi-instance load-balanced deployment
  - `all` mode to run both sequentially
- Added startup orchestration and health gating for:
  - PostgreSQL readiness
  - RabbitMQ aliveness
  - Mock Payment API health
  - Payment Bridge health
  - Nginx load balancer health (scaled mode)
- Added multi-instance startup using `performance-test/config/docker-compose.scaled.yml`.
- Added RabbitMQ queue purge before the load run so the test starts from a clean state.
- Added a `cleanup()` trap to destroy Docker Compose services on completion or failure.
- Preserved configurable realistic load parameters via env vars:
  - `USERS`, `RAMP_UP`, `DURATION`, `THINK_TIME`, `RANDOM_DELAY`

## Suggestions for improvement
- Centralize JMeter command and Docker readiness helpers into a shared helper script to reduce duplication across quick, full, and realistic test flows.
- Extend scaled mode to support configurable instance counts instead of a fixed 3-instance setup.
- Add a preflight validation script for Docker Compose, health endpoints, and required tools before starting the realistic test.
- Document `realistic-performance-test.sh` usage in `performance-test/README.md` and `docs/QUICK_PERFORMANCE_TESTING.md` for easier onboarding.
- Add a dedicated results summary writer for `realistic` mode so single and scaled runs produce comparable reports automatically.

