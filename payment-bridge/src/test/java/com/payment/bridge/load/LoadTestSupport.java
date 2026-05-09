package com.payment.bridge.load;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class LoadTestSupport {

    private LoadTestSupport() {
        // Utility class
    }

    public static List<Long> executeConcurrentRequests(int requestCount,
                                                        int executorTimeoutSeconds,
                                                        LoadTestAction action) throws Exception {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<Long>> futures = new ArrayList<>(requestCount);

        for (int i = 0; i < requestCount; i++) {
            final int index = i;
            futures.add(executor.submit(() -> action.execute(index)));
        }

        executor.shutdown();
        if (!executor.awaitTermination(executorTimeoutSeconds, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Load test did not complete within " + executorTimeoutSeconds + " seconds");
        }

        List<Long> timings = new ArrayList<>(requestCount);
        for (Future<Long> future : futures) {
            timings.add(future.get(executorTimeoutSeconds, TimeUnit.SECONDS));
        }

        return timings;
    }

    public static LoadTestResult executeConcurrentRequestMetrics(int requestCount,
                                                                  int executorTimeoutSeconds,
                                                                  LoadTestAction action) throws Exception {
        return new LoadTestResult(executeConcurrentRequests(requestCount, executorTimeoutSeconds, action));
    }

    public static long percentile(List<Long> timings, double percentile) {
        if (timings.isEmpty()) {
            throw new IllegalArgumentException("Timings list must not be empty");
        }
        List<Long> sorted = new ArrayList<>(timings);
        Collections.sort(sorted);
        int index = (int) Math.ceil(percentile / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    public static String formatLatencyReport(LoadTestResult result, String label) {
        return String.format(
                "%s latency summary: p50=%dms, p95=%dms, p99=%dms, max=%dms, mean=%dms",
                label,
                result.getP50(),
                result.getP95(),
                result.getP99(),
                result.getMax(),
                result.getMean()
        );
    }

    public static String formatLatencyReport(LoadTestResult result) {
        return formatLatencyReport(result, "Load test");
    }

    public static final class LoadTestResult {
        private final List<Long> timings;
        private final long p50;
        private final long p95;
        private final long p99;
        private final long max;
        private final long mean;

        public LoadTestResult(List<Long> timings) {
            if (timings.isEmpty()) {
                throw new IllegalArgumentException("Timings list must not be empty");
            }
            this.timings = new ArrayList<>(timings);
            this.p50 = percentile(this.timings, 50.0);
            this.p95 = percentile(this.timings, 95.0);
            this.p99 = percentile(this.timings, 99.0);
            this.max = Collections.max(this.timings);
            this.mean = this.timings.stream().mapToLong(Long::longValue).sum() / this.timings.size();
        }

        public List<Long> getTimings() {
            return new ArrayList<>(timings);
        }

        public long getP50() {
            return p50;
        }

        public long getP95() {
            return p95;
        }

        public long getP99() {
            return p99;
        }

        public long getMax() {
            return max;
        }

        public long getMean() {
            return mean;
        }

        @Override
        public String toString() {
            return formatLatencyReport(this);
        }
    }

    @FunctionalInterface
    public interface LoadTestAction {
        long execute(int index) throws Exception;
    }
}
