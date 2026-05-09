# 💪 Payment System Resilience Tests

Comprehensive integration tests that verify system behavior under real-world outage scenarios.

## 🎯 Overview

These tests simulate realistic chaos scenarios to validate that your payment bridge can:

1. ✅ Handle temporary service outages gracefully
2. ✅ Defer payments when service is unavailable
3. ✅ Recover automatically when service restarts
4. ✅ Catch up on deferred payments without data loss
5. ✅ Maintain system stability under rapid restart cycles

## 📋 Available Tests

### Test 1: Offline Recovery Integration Test

**File:** `scripts/run-offline-recovery-test.sh`

**Scenario:**

- System running normally → processing payments
- Sudden network outage (service offline)
- Payments deferred (stay IN_PROGRESS)
- Service comes back online after ~1 second
- System triggers automatic recovery
- Deferred payments completed

**Duration:** ~2 minutes
**Max outages:** 2 cycles

### Test 2: Random Shutdown Performance Test

**File:** `scripts/run-random-shutdown-test.sh`

**Scenario:**

- System processes payments continuously
- Service **randomly shuts down** (unpredictable timing)
- Each shutdown lasts **1-5 seconds** (random duration)
- Shutdowns occur **max 2 times**
- Wait between shutdowns: **5-20 seconds** (random)
- System verifies recovery after each restart

**Duration:** ~2-3 minutes (includes random waits)
**Max shutdowns:** 2
**Outage duration:** 1-5 seconds (random)
**Shutdown intervals:** 5-20 seconds (random)

### Test 3: Quick Single Instance Shutdown Test

**File:** `run-quick-single-instance-test.sh`

**Scenario:**

- Single payment service instance
- Random shutdowns (max 4 times, up to 5 seconds each)
- Payments deferred during outages
- Automatic recovery and catch-up when service restarts

**Duration:** ~40 seconds
**Max shutdowns:** 4
**Outage duration:** 1-5 seconds (random)

### Test 4: Quick Multi Instance Shutdown Test

**File:** `run-quick-multi-instance-test.sh`

**Scenario:**

- 3 payment service instances with load balancing
- Each instance randomly shuts down independently (max 4 times, up to 5 seconds each)
- Round-robin load distribution
- Payments deferred when all instances are down
- Automatic recovery and catch-up when instances restart

**Duration:** ~40 seconds
**Max shutdowns per instance:** 4
**Outage duration:** 1-5 seconds (random)

### Test 5: Full Single Instance Shutdown Test

**File:** `run-full-single-instance-test.sh`

**Scenario:**

- Single payment service instance
- Random shutdowns (max 4 times, up to 5 seconds each)
- Extended test duration with more payment batches
- Payments deferred during outages
- Automatic recovery and catch-up when service restarts

**Duration:** ~120 seconds
**Max shutdowns:** 4
**Outage duration:** 1-5 seconds (random)

### Test 6: Full Multi Instance Shutdown Test

**File:** `run-full-multi-instance-test.sh`

**Scenario:**

- 3 payment service instances with load balancing
- Each instance randomly shuts down independently (max 4 times, up to 5 seconds each)
- Round-robin load distribution
- Extended test duration with more payment batches
- Payments deferred when all instances are down
- Automatic recovery and catch-up when instances restart

**Duration:** ~120 seconds
**Max shutdowns per instance:** 4
**Outage duration:** 1-5 seconds (random)

## 🚀 How to Run

### Option 1: Interactive Menu (Recommended)

```bash
cd /Users/mac/Programming/payment-system-speckit/performance-test

bash scripts/run-resilience-tests.sh
```

Then select:

- `1` for Offline Recovery Test
- `2` for Random Shutdown Test
- `3` for Quick Single Instance Shutdown Test
- `4` for Quick Multi Instance Shutdown Test
- `5` for Full Single Instance Shutdown Test
- `6` for Full Multi Instance Shutdown Test
- `7` for All tests sequentially

### Option 2: Run Specific Test

**Offline Recovery Test:**

```bash
cd /Users/mac/Programming/payment-system-speckit/performance-test

bash scripts/run-offline-recovery-test.sh
```

**Random Shutdown Test:**

```bash
cd /Users/mac/Programming/payment-system-speckit/performance-test

bash scripts/run-random-shutdown-test.sh
```

**Quick Single Instance Shutdown Test:**

```bash
cd /Users/mac/Programming/payment-system-speckit/performance-test

bash run-quick-single-instance-test.sh
```

**Quick Multi Instance Shutdown Test:**

```bash
cd /Users/mac/Programming/payment-system-speckit/performance-test

bash run-quick-multi-instance-test.sh
```

**Full Single Instance Shutdown Test:**

```bash
cd /Users/mac/Programming/payment-system-speckit/performance-test

bash run-full-single-instance-test.sh
```

**Full Multi Instance Shutdown Test:**

```bash
cd /Users/mac/Programming/payment-system-speckit/performance-test

bash run-full-multi-instance-test.sh
```

### Option 3: Direct Java Execution

**Offline Recovery:**

```bash
cd /Users/mac/Programming/payment-system-speckit/performance-test

export JAVA_HOME=$(/usr/libexec/java_home -v 21)
javac OfflineRecoveryTestRunner.java
java OfflineRecoveryTestRunner
```

**Random Shutdown:**

```bash
cd /Users/mac/Programming/payment-system-speckit/performance-test

export JAVA_HOME=$(/usr/libexec/java_home -v 21)
javac RandomShutdownTestRunner.java
java RandomShutdownTestRunner
```

**Quick Single Instance Shutdown:**

```bash
cd /Users/mac/Programming/payment-system-speckit/performance-test

export JAVA_HOME=$(/usr/libexec/java_home -v 21)
javac QuickSingleInstanceShutdownTestRunner.java
java QuickSingleInstanceShutdownTestRunner
```

**Quick Multi Instance Shutdown:**

```bash
cd /Users/mac/Programming/payment-system-speckit/performance-test

export JAVA_HOME=$(/usr/libexec/java_home -v 21)
javac QuickMultiInstanceShutdownTestRunner.java
java QuickMultiInstanceShutdownTestRunner
```

**Full Single Instance Shutdown:**

```bash
cd /Users/mac/Programming/payment-system-speckit/performance-test

export JAVA_HOME=$(/usr/libexec/java_home -v 21)
javac FullSingleInstanceShutdownTestRunner.java
java FullSingleInstanceShutdownTestRunner
```

**Full Multi Instance Shutdown:**

```bash
cd /Users/mac/Programming/payment-system-speckit/performance-test

export JAVA_HOME=$(/usr/libexec/java_home -v 21)
javac FullMultiInstanceShutdownTestRunner.java
java FullMultiInstanceShutdownTestRunner
```

## 📊 Sample Output

### Offline Recovery Test Output

```
🔄 Starting System Offline → Recovery → Automatic Catch-up

📊 PHASE 1: System running normally
✅ Submitted 5 payments - Successful: 5

⚠️  PHASE 2: Sudden network outage - service OFFLINE
⏸️  Payment offline-payment deferred (service offline)

⏳ PHASE 3: Waiting ~1 second for service to come back online...

🔄 PHASE 4: Service recovery - bringing service back online

✨ PHASE 5: Automatic recovery - bridge catches up on IN_PROGRESS payments

✅ PHASE 6: Verifying recovery
📈 Recovery Statistics:
  - Deferred payments during outage: 1
  - Recovered payments: 1
  - Total recovery time: 2500ms

✅ testSystemOfflineAndAutomaticRecovery PASSED
```

### Random Shutdown Test Output

```
🔥 Test 1: Random Shutdowns (max 2, up to 5s each) + Recovery

   Batch 1: 10/10 successful | ✅ ONLINE
   Batch 2: 9/10 successful | ✅ ONLINE

   💥 SHUTDOWN #1 (duration: ~2s)
   Batch 3: 0/10 successful | ⏸️ OFFLINE

   🚀 SERVICE RESTARTED
   🔄 Recovery: Catching up deferred payments...

   Batch 4: 10/10 successful | ✅ ONLINE
   [... continues ...]

📊 Final Statistics:
   Test Duration: 60s
   Shutdowns: 2
   Successful Payments: 120
   Deferred Payments: 25
   Recovered Payments: 25
   Failed Payments: 0
   Recovery Rate: 100.0%

✅ testRandomShutdownsAndRecover PASSED
```

## ✅ Success Criteria

**Offline Recovery Test Passes When:**

- ✅ Deferred at least 1 payment during outage
- ✅ Recovered at least 1 payment after restart
- ✅ Recovery completed within 3 seconds
- ✅ Max 2 offline cycles

**Random Shutdown Test Passes When:**

- ✅ Max 2 random shutdowns occurred
- ✅ Each shutdown was 1-5 seconds
- ✅ At least 1 payment recovered
- ✅ Success rate > 70%

**Quick/Fast Single Instance Shutdown Tests Pass When:**

- ✅ Max 4 random shutdowns occurred
- ✅ Each shutdown was 1-5 seconds
- ✅ At least 1 payment recovered
- ✅ Total processed payments > 0
- ✅ Shutdown count ≤ 4

**Quick/Fast Multi Instance Shutdown Tests Pass When:**

- ✅ Max 4 random shutdowns per instance occurred
- ✅ Each shutdown was 1-5 seconds
- ✅ At least 1 payment recovered
- ✅ Total processed payments > 0
- ✅ Shutdown count per instance ≤ 4

## 🔧 Troubleshooting

### Java not found

```bash
# Set JAVA_HOME manually
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
bash scripts/run-resilience-tests.sh
```

### Compilation errors

```bash
# Check Java version
java -version

# Expected output: Java 21.x.x
```

### Tests timeout (takes too long)

This is normal! Random tests have unpredictable wait times (5-20 seconds between events). Allow 2-3 minutes for full completion.

## 📈 Interpreting Results

### Recovery Rate

- **100%:** Perfect resilience - all deferred payments recovered
- **>90%:** Excellent - system handles outages well
- **>70%:** Good - system recovers but with some data loss
- **<70%:** Needs improvement - investigate recovery logic

### Shutdown Count

- Should never exceed max limit (2 for old tests, 4 for new tests)
- Each shutdown should be 1-5 seconds

### Total Test Duration

- Offline test: ~2 minutes
- Random shutdown test: ~2-3 minutes (variable due to random waits)
- Quick tests: ~40 seconds
- Full tests: ~120 seconds

### Instance Count (Multi-Instance Tests)

- 3 instances with round-robin load balancing
- Payments deferred only when ALL instances are down

## 🎯 What These Tests Validate

✅ **Graceful Degradation:** System doesn't crash when service unavailable
✅ **Data Integrity:** No payments lost during outages
✅ **Automatic Recovery:** Recovers without manual intervention
✅ **Resilience:** Handles multiple outage cycles
✅ **Performance:** Recovery completes quickly
✅ **Real-world Scenarios:** Simulates actual network problems

## 📚 Related Files

- `OfflineRecoveryTestRunner.java` - Standalone runner for offline recovery test
- `RandomShutdownTestRunner.java` - Standalone runner for random shutdown test
- `QuickSingleInstanceShutdownTestRunner.java` - Quick single instance shutdown test
- `QuickMultiInstanceShutdownTestRunner.java` - Quick multi instance shutdown test
- `FullSingleInstanceShutdownTestRunner.java` - Full single instance shutdown test
- `FullMultiInstanceShutdownTestRunner.java` - Full multi instance shutdown test
- `run-quick-single-instance-test.sh` - Bash wrapper for quick single instance test
- `run-quick-multi-instance-test.sh` - Bash wrapper for quick multi instance test
- `run-full-single-instance-test.sh` - Bash wrapper for full single instance test
- `run-full-multi-instance-test.sh` - Bash wrapper for full multi instance test
- `scripts/run-resilience-tests.sh` - Interactive menu for all tests
- `src/test/java/com/payment/scaling/OfflineRecoveryPerformanceTest.java` - JUnit 5 test version
- `src/test/java/com/payment/scaling/RandomShutdownPerformanceTest.java` - JUnit 5 test version

## 🎓 Understanding the Tests

### How Offline Recovery Works

```
Payment Bridge         External Service
     │                      │
     ├─ Process payment ────→│ ✅ Success
     │                      │
     ├─ (Service goes down) │ ❌ Timeout
     │                      ✗ (offline)
     │ (defer recovery)     │
     │ (keep IN_PROGRESS)   │
     │                      │ (service restarts)
     │                      ✅ Back online
     │                      │
     ├─ Recovery check ─────→│ Check status
     │←─ Status: COMPLETED ─┤
     ├─ Update to COMPLETED │
     │                      │
```

### How Random Shutdown Works

```
Timeline:
0s   ├─ Batch 1: Payments succeed
5s   ├─ Batch 2: Payments succeed
10s  ├─ SHUTDOWN! (random timing triggered)
10s  ├─ Batch 3: Service offline, payments deferred
12s  ├─ SERVICE RESTARTS (random duration ~1-5s)
12s  ├─ Recovery triggered
13s  ├─ Batch 4: Payments succeed, deferred batch caught up
20s  ├─ Batch 5: Normal processing
25s  ├─ SHUTDOWN #2! (if randomness triggers it)
...
```

## 🚀 Next Steps

After running these tests successfully:

1. ✅ Verify your payment bridge handles outages gracefully
2. ✅ Review recovery metrics in the test output
3. ✅ Consider implementing scheduled recovery (T205)
4. ✅ Add health-aware recovery (T206)
5. ✅ Implement circuit breaker pattern (T207)
