# Combined Test Results Report
Generated on: Sun May 10 18:35:52 +07 2026

# Test Overview

This report combines results from:
1. Java-based shutdown/restart recovery simulations
2. JMeter performance tests

# JMeter Performance Test Results

PERFORMANCE TEST RESULTS
========================

Test File: payment-load-test-20260510-182402.jtl
Generated: Sun May 10 18:35:52 +07 2026

SUMMARY METRICS
---------------
Total Requests:     489
Successful Requests: 489
Failed Requests:    0
Error Rate:         0%

RESPONSE TIME (ms)
------------------
Min:     13
P50:     33
P95:     74
P99:     173
Max:     321

THROUGHPUT
----------
Requests/Second: 46.2937
Test Duration:   10.563s

PERFORMANCE ASSESSMENT
----------------------
✅ Error Rate: GOOD (< 1%)

✅ P95 Latency: GOOD (< 1000ms)

✅ Throughput: GOOD (> 10 RPS)

# Summary & Recommendations

⚠️  **Performance Test**: ISSUES - High error rate (        0
GOOD (< 1)%)
📊 **Throughput**: 46.2937 RPS

## Next Steps
1. Review recovery mechanisms for any lost payments
2. Optimize performance if error rates are high
3. Consider additional chaos testing scenarios
4. Validate results in production-like environment
