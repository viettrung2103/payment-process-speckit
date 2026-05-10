package com.payment.scaling;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Random Shutdown Performance Test for Single Instance
 *
 * Simulates real-world chaos:
 * 1. System running normally, processing payments
 * 2. Service randomly shuts down (max 2 times)
 * 3. Each shutdown duration is random (1-5 seconds)
 * 4. Time between shutdowns is random
 * 5. System must recover and catch up after each restart
 *
 * This tests resilience under unpredictable outages.
 */
@DisplayName("Random Shutdown Performance Test")
class RandomShutdownPerformanceTest {

    private PaymentServiceWithRandomShutdowns serviceSimulator;
    private PaymentProcessorSimulator processor;

    @BeforeEach
    void setUp() {
        serviceSimulator = new PaymentServiceWithRandomShutdowns();
        processor = new PaymentProcessorSimulator(serviceSimulator);
    }

    @Test
    @DisplayName("System should handle random shutdowns (max 2, up to 5 seconds each) and recover")
    void shouldHandleRandomShutdownsAndRecover() throws InterruptedException {
        System.out.println("\n🔥 Starting Random Shutdown Performance Test");
        System.out.println("================================================");

        processor.startProcessing();

        long testStartTime = System.currentTimeMillis();
        long testDuration = TimeUnit.SECONDS.toMillis(60); // 60 second overall test

        // Enable random shutdowns (max 2, up to 5 seconds each)
        serviceSimulator.enableRandomShutdowns(2, 5000);

        System.out.println("\n📊 Test Configuration:");
        System.out.println("  • Max shutdowns: 2");
        System.out.println("  • Max shutdown duration: 5 seconds");
        System.out.println("  • Test duration: 60 seconds");
        System.out.println("  • Shutdown intervals: random");
        System.out.println();

        int batchNumber = 0;
        while (System.currentTimeMillis() - testStartTime < testDuration) {
            batchNumber++;

            // Try to process 10 payments in each batch
            System.out.println("📍 Batch " + batchNumber + ":");

            for (int i = 0; i < 10; i++) {
                PaymentRequest payment = new PaymentRequest(
                    "random-shutdown-" + batchNumber + "-" + i,
                    100.0 + (Math.random() * 200)
                );

                try {
                    processor.processPayment(payment);
                } catch (Exception e) {
                    processor.recordFailure(payment);
                }
            }

            // Check service status
            String status = serviceSimulator.isRunning() ? "✅ ONLINE" : "⏸️  OFFLINE";
            System.out.println("  Service Status: " + status);
            System.out.println("  Successful: " + processor.getSuccessfulPayments() +
                             " | Deferred: " + processor.getDeferredPayments() +
                             " | Recovered: " + processor.getRecoveredPayments());

            // If service just came back online, trigger recovery
            if (serviceSimulator.hasRecentlyRestarted()) {
                System.out.println("  🔄 Service restarted - triggering recovery...");
                processor.triggerRecovery();
                serviceSimulator.acknowledgeRestart();
            }

            Thread.sleep(1000); // Wait 1 second between batches
        }

        // Final recovery pass
        System.out.println("\n✨ Final Recovery Pass:");
        processor.triggerRecovery();
        Thread.sleep(2000);

        // Print final statistics
        printFinalStatistics(System.currentTimeMillis() - testStartTime);

        // Assertions
        int totalProcessed = processor.getSuccessfulPayments() + processor.getRecoveredPayments();
        assert totalProcessed > 0 : "No payments were successfully processed";
        assert processor.getShutdownCount() <= 2 : "Too many shutdowns occurred";

        System.out.println("\n✅ Test PASSED - System is resilient!");
    }

    @Test
    @DisplayName("Performance under rapid random restarts with mixed success rates")
    void shouldHandleRapidRandomRestartsWithRecovery() throws InterruptedException {
        System.out.println("\n⚡ Starting Rapid Random Restart Performance Test");
        System.out.println("================================================");

        processor.startProcessing();

        long testStartTime = System.currentTimeMillis();
        long testDuration = TimeUnit.SECONDS.toMillis(45); // 45 second test

        // More aggressive random shutdowns
        serviceSimulator.enableRandomShutdowns(2, 5000);

        System.out.println("\n📊 Test Configuration:");
        System.out.println("  • Scenario: Rapid, unpredictable restarts");
        System.out.println("  • Max shutdowns: 2");
        System.out.println("  • Max shutdown duration: 5 seconds");
        System.out.println();

        int batchNumber = 0;
        while (System.currentTimeMillis() - testStartTime < testDuration) {
            batchNumber++;

            // Process payments with concurrent pattern
            for (int i = 0; i < 5; i++) {
                PaymentRequest payment = new PaymentRequest(
                    "rapid-" + batchNumber + "-" + i,
                    50.0 + (Math.random() * 150)
                );

                try {
                    processor.processPayment(payment);
                } catch (Exception e) {
                    processor.recordFailure(payment);
                }

                Thread.sleep(200); // Slight delay between payments
            }

            // Recovery if needed
            if (serviceSimulator.hasRecentlyRestarted()) {
                System.out.println("🔄 Service recovered - initiating catch-up recovery");
                processor.triggerRecovery();
                serviceSimulator.acknowledgeRestart();
            }

            // Status check
            String status = serviceSimulator.isRunning() ? "✅" : "⏸️ ";
            System.out.println("  [" + status + "] Batch " + batchNumber +
                             " | Success: " + processor.getSuccessfulPayments() +
                             " | Deferred: " + processor.getDeferredPayments());

            Thread.sleep(500);
        }

        // Final stats
        printFinalStatistics(System.currentTimeMillis() - testStartTime);

        int successRate = calculateSuccessRate(
            processor.getSuccessfulPayments(),
            processor.getDeferredPayments()
        );

        System.out.println("\n📈 Success Rate: " + successRate + "%");
        assert successRate > 70 : "Success rate too low for resilient system";

        System.out.println("✅ Test PASSED - System maintained acceptable performance!");
    }

    private void printFinalStatistics(long testDurationMs) {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("📊 Final Statistics:");
        System.out.println("=".repeat(50));
        System.out.println("Test Duration: " + (testDurationMs / 1000) + " seconds");
        System.out.println("Total Shutdowns: " + serviceSimulator.getShutdownCount());
        System.out.println("Successful Payments: " + processor.getSuccessfulPayments());
        System.out.println("Deferred Payments: " + processor.getDeferredPayments());
        System.out.println("Recovered Payments: " + processor.getRecoveredPayments());
        System.out.println("Failed Payments: " + processor.getFailedPayments());

        int totalPayments = processor.getSuccessfulPayments() +
                           processor.getDeferredPayments() +
                           processor.getRecoveredPayments() +
                           processor.getFailedPayments();

        if (totalPayments > 0) {
            double successRate = ((double) (processor.getSuccessfulPayments() +
                                           processor.getRecoveredPayments()) / totalPayments) * 100;
            System.out.println("Recovery Rate: " + String.format("%.1f", successRate) + "%");
        }
    }

    private int calculateSuccessRate(int successful, int deferred) {
        int total = successful + deferred;
        if (total == 0) return 0;
        return (successful * 100) / total;
    }

    /**
     * Service that randomly shuts down
     */
    static class PaymentServiceWithRandomShutdowns {
        private AtomicBoolean isRunning = new AtomicBoolean(true);
        private AtomicBoolean hasRecentlyRestarted = new AtomicBoolean(false);
        private AtomicInteger shutdownCount = new AtomicInteger(0);
        private AtomicInteger maxShutdowns = new AtomicInteger(0);
        private AtomicLong maxShutdownDuration = new AtomicLong(0);
        private volatile Thread shutdownThread;

        void enableRandomShutdowns(int maxShutdownCount, long maxDuration) {
            maxShutdowns.set(maxShutdownCount);
            maxShutdownDuration.set(maxDuration);

            // Background thread that triggers random shutdowns
            shutdownThread = new Thread(() -> {
                try {
                    while (shutdownCount.get() < maxShutdowns.get()) {
                        // Random wait before shutdown (5-20 seconds)
                        long waitTime = 5000 + (long)(Math.random() * 15000);
                        Thread.sleep(waitTime);

                        if (shutdownCount.get() < maxShutdowns.get()) {
                            triggerShutdown();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            shutdownThread.setDaemon(true);
            shutdownThread.start();
        }

        private void triggerShutdown() throws InterruptedException {
            isRunning.set(false);
            shutdownCount.incrementAndGet();

            // Random shutdown duration (1-5 seconds)
            long shutdownDuration = 1000 + (long)(Math.random() * 4000);

            System.out.println("\n💥 Service SHUTDOWN #" + shutdownCount.get() +
                             " (duration: " + (shutdownDuration / 1000) + "s)");

            Thread.sleep(shutdownDuration);

            // Service restarts
            isRunning.set(true);
            hasRecentlyRestarted.set(true);

            System.out.println("🚀 Service RESTARTED - Ready for recovery");
        }

        boolean isRunning() {
            return isRunning.get();
        }

        boolean hasRecentlyRestarted() {
            return hasRecentlyRestarted.get();
        }

        void acknowledgeRestart() {
            hasRecentlyRestarted.set(false);
        }

        int getShutdownCount() {
            return shutdownCount.get();
        }

        String getPaymentStatus(String paymentId) throws Exception {
            if (!isRunning.get()) {
                throw new RuntimeException("Service unavailable - server is down");
            }

            // Simulate successful response
            return "{\"status\":\"COMPLETED\",\"transactionId\":\"TXN-" + paymentId + "\"}";
        }
    }

    /**
     * Payment processor that handles shutdowns
     */
    static class PaymentProcessorSimulator {
        private PaymentServiceWithRandomShutdowns service;
        private AtomicInteger successfulPayments = new AtomicInteger(0);
        private AtomicInteger deferredPayments = new AtomicInteger(0);
        private AtomicInteger recoveredPayments = new AtomicInteger(0);
        private AtomicInteger failedPayments = new AtomicInteger(0);
        private AtomicBoolean isProcessing = new AtomicBoolean(false);

        PaymentProcessorSimulator(PaymentServiceWithRandomShutdowns service) {
            this.service = service;
        }

        void startProcessing() {
            isProcessing.set(true);
        }

        void processPayment(PaymentRequest payment) throws Exception {
            if (!isProcessing.get()) return;

            try {
                String status = service.getPaymentStatus(payment.id);
                if (status.contains("COMPLETED")) {
                    successfulPayments.incrementAndGet();
                }
            } catch (Exception e) {
                deferredPayments.incrementAndGet();
            }
        }

        void recordFailure(PaymentRequest payment) {
            failedPayments.incrementAndGet();
        }

        void triggerRecovery() throws InterruptedException {
            int deferred = deferredPayments.get();

            for (int i = 0; i < deferred; i++) {
                try {
                    String status = service.getPaymentStatus("deferred-" + i);
                    if (status.contains("COMPLETED")) {
                        recoveredPayments.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Still having issues
                }
            }
        }

        int getSuccessfulPayments() {
            return successfulPayments.get();
        }

        int getDeferredPayments() {
            return deferredPayments.get();
        }

        int getRecoveredPayments() {
            return recoveredPayments.get();
        }

        int getFailedPayments() {
            return failedPayments.get();
        }

        int getShutdownCount() {
            return service.getShutdownCount();
        }
    }

    static class PaymentRequest {
        String id;
        double amount;

        PaymentRequest(String id, double amount) {
            this.id = id;
            this.amount = amount;
        }
    }
}
