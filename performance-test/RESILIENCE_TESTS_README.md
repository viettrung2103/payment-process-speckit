# 💪 Payment System Resilience Tests

This folder contains the quick resilience tests that validate how the payment bridge behaves under shutdown and recovery scenarios.

## 🧪 What remains

We keep two Java-based quick shutdown test runners:

- `run-quick-single-instance-test.sh` - Java quick single-instance shutdown test
- `run-quick-multi-instance-test.sh` - Java quick scaled-instance shutdown test

We also keep a shell-based performance test harness for JMeter-based execution without Java:

- `scripts/quick-performance-test.sh` - shell-based quick performance script
- `scripts/quick-single-performance-test.sh` - convenience wrapper for the single-instance quick script
- `scripts/quick-scaled-performance-test.sh` - convenience wrapper for the scaled-instance quick script

The Java-based shutdown validation helpers are now stored in `java-tests/`.

## 🔧 Available tests

### Quick Single Instance Shutdown Test (Java)

**File:** `java-tests/run-quick-single-instance-test.sh`

**Scenario:**

- Single payment bridge instance
- Random or scheduled shutdowns during execution
- Verifies failover behavior and recovery in a single-instance environment

### Quick Multi Instance Shutdown Test (Java)

**File:** `java-tests/run-quick-multi-instance-test.sh`

**Scenario:**

- 3 payment bridge instances behind load balancing
- One instance shutdown during execution
- Verifies recovery and continued processing under partial outage

### Quick Performance Test (Shell/JMeter)

**File:** `scripts/quick-performance-test.sh`

**Scenarios:**

- `single` – quick single-instance performance run
- `scaled` – quick 3-instance scaled performance run
- `single-shutdown` – single-instance run with explicit shutdown injection
- `scaled-shutdown` – scaled-instance run with explicit shutdown injection
- `all` – runs both single and scaled modes sequentially

**Usage:**

```bash
cd /Users/mac/Programming/payment-system-speckit/performance-test
bash scripts/quick-performance-test.sh single
```

Or list the other modes:

```bash
bash scripts/quick-performance-test.sh scaled
bash scripts/quick-performance-test.sh single-shutdown
bash scripts/quick-performance-test.sh scaled-shutdown
bash scripts/quick-performance-test.sh all
```

## 📌 Notes

- The shell-based performance harness is the preferred non-Java performance test entry point.
- The two retained Java tests are focused quick shutdown validation helpers only.
