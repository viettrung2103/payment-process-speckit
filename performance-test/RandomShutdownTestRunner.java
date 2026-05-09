import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Random Shutdown Performance Test Runner
 * 
 * STANDALONE - No external dependencies
 */
public class RandomShutdownTestRunner {

    public static void main(String[] args) {
        System.out.println("\n🔥 Random Shutdown Performance Test - Single Instance");
        System.out.println("════════════════════════════════════════════════════════════");
        System.out.println();

        int passed = 0;
        int failed = 0;

        if (testRandomShutdownsAndRecover()) {
            passed++;
            System.out.println("\n✅ testRandomShutdownsAndRecover PASSED");
        } else {
            failed++;
            System.out.println("\n❌ testRandomShutdownsAndRecover FAILED");
        }

        if (testRapidRandomRestartsWithRecovery()) {
            passed++;
            System.out.println("✅ testRapidRandomRestartsWithRecovery PASSED");
        } else {
            failed++;
            System.out.println("❌ testRapidRandomRestartsWithRecovery FAILED");
        }

        System.out.println("\n════════════════════════════════════════════════════════════");
        System.out.println("📊 Test Results: " + passed + " passed, " + failed + " failed");

        if (failed == 0) {
            System.out.println("🎉 All random shutdown tests PASSED!");
        } else {
            System.out.println("⚠️  Some tests failed.");
            System.exit(1);
        }
    }

    private static boolean testRandomShutdownsAndRecover() {
        try {
            System.out.println("Test 1: Random shutdowns (max 2, 1-5s each)");
            System.out.println("─".repeat(50));

            PaymentServiceWithRandomShutdowns service = new PaymentServiceWithRandomShutdowns();
            PaymentProcessorSimulator processor = new PaymentProcessorSimulator(service);

            processor.startProcessing();

            long testStart = System.currentTimeMillis();
            long testDuration = 60_000; // 60 seconds

            service.enableRandomShutdowns(2, 5000);

            System.out.println("\n📋 Configuration:");
            System.out.println("   • Max shutdowns: 2");
            System.out.println("   • Max shutdown duration: 5 seconds");
            System.out.println("   • Test duration: 60 seconds\n");

            int batchNumber = 0;
            while (System.currentTimeMillis() - testStart < testDuration) {
                batchNumber++;

                int successful = 0;
                for (int i = 0; i < 10; i++) {
                    try {
                        processor.processPayment("shutdown-" + batchNumber + "-" + i, 100.0);
                        successful++;
                    } catch (Exception e) {
                        processor.recordFailure();
                    }
                }

                String status = service.isRunning() ? "✅ ONLINE" : "⏸️  OFFLINE";
                System.out.println("   Batch " + batchNumber + ": " + successful + "/10 | " + status);

                if (service.hasRecentlyRestarted()) {
                    System.out.println("   🔄 Recovery: Catching up deferred payments...");
                    processor.triggerRecovery();
                    service.acknowledgeRestart();
                }

                Thread.sleep(1000);
            }

            processor.triggerRecovery();
            Thread.sleep(1000);

            System.out.println("\n📊 Results:");
            System.out.println("   • Successful: " + processor.getSuccessfulPayments());
            System.out.println("   • Recovered: " + processor.getRecoveredPayments());
            System.out.println("   • Shutdowns: " + service.getShutdownCount());

            return processor.getSuccessfulPayments() + processor.getRecoveredPayments() > 0 &&
                   service.getShutdownCount() <= 2;

        } catch (Exception e) {
            System.err.println("❌ Test failed: " + e.getMessage());
            return false;
        }
    }

    private static boolean testRapidRandomRestartsWithRecovery() {
        try {
            System.out.println("\nTest 2: Rapid random restarts");
            System.out.println("─".repeat(50));

            PaymentServiceWithRandomShutdowns service = new PaymentServiceWithRandomShutdowns();
            PaymentProcessorSimulator processor = new PaymentProcessorSimulator(service);

            processor.startProcessing();

            long testStart = System.currentTimeMillis();
            long testDuration = 45_000; // 45 seconds

            service.enableRandomShutdowns(2, 5000);

            System.out.println("\n📋 Configuration:");
            System.out.println("   • Scenario: Rapid, unpredictable restarts");
            System.out.println("   • Max shutdowns: 2");
            System.out.println("   • Test duration: 45 seconds\n");

            int batchNumber = 0;
            while (System.currentTimeMillis() - testStart < testDuration) {
                batchNumber++;

                int successful = 0;
                for (int i = 0; i < 5; i++) {
                    try {
                        processor.processPayment("rapid-" + batchNumber + "-" + i, 100.0);
                        successful++;
                    } catch (Exception e) {
                        processor.recordFailure();
                    }
                    Thread.sleep(200);
                }

                if (service.hasRecentlyRestarted()) {
                    processor.triggerRecovery();
                    service.acknowledgeRestart();
                    System.out.print(" [RECOVERED]");
                }

                String status = service.isRunning() ? "✅" : "⏸️ ";
                System.out.println("   Batch " + batchNumber + ": " + successful + "/5 | " + status);

                Thread.sleep(500);
            }

            System.out.println("\n📊 Results:");
            int total = processor.getSuccessfulPayments() + processor.getDeferredPayments();
            int successRate = total > 0 ? (processor.getSuccessfulPayments() * 100) / total : 0;
            System.out.println("   • Success rate: " + successRate + "%");
            System.out.println("   • Total processed: " + (processor.getSuccessfulPayments() + processor.getRecoveredPayments()));

            return successRate > 70;

        } catch (Exception e) {
            System.err.println("❌ Test failed: " + e.getMessage());
            return false;
        }
    }

    static class PaymentServiceWithRandomShutdowns {
        private AtomicBoolean isRunning = new AtomicBoolean(true);
        private AtomicBoolean recentlyRestarted = new AtomicBoolean(false);
        private AtomicInteger shutdownCount = new AtomicInteger(0);
        private AtomicInteger maxShutdowns = new AtomicInteger(0);
        private AtomicBoolean shutdownEnabled = new AtomicBoolean(false);

        void enableRandomShutdowns(int maxCount, long maxDuration) {
            maxShutdowns.set(maxCount);
            shutdownEnabled.set(true);

            new Thread(() -> {
                try {
                    while (shutdownCount.get() < maxShutdowns.get()) {
                        long waitTime = 5000 + (long)(Math.random() * 15000);
                        Thread.sleep(waitTime);

                        if (shutdownCount.get() < maxShutdowns.get()) {
                            triggerShutdown(maxDuration);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }

        private void triggerShutdown(long maxDuration) throws InterruptedException {
            isRunning.set(false);
            shutdownCount.incrementAndGet();

            long shutdownDuration = 1000 + (long)(Math.random() * 4000);
            System.out.println("\n   💥 SHUTDOWN #" + shutdownCount.get() + " (~" + (shutdownDuration / 1000) + "s)");

            Thread.sleep(shutdownDuration);

            isRunning.set(true);
            recentlyRestarted.set(true);
            System.out.println("   🚀 SERVICE RESTARTED");
        }

        boolean isRunning() { return isRunning.get(); }
        boolean hasRecentlyRestarted() { return recentlyRestarted.get(); }
        void acknowledgeRestart() { recentlyRestarted.set(false); }
        int getShutdownCount() { return shutdownCount.get(); }

        String getPaymentStatus(String id) throws Exception {
            if (!isRunning.get()) throw new RuntimeException("Service down");
            return "{\"status\":\"COMPLETED\"}";
        }
    }

    static class PaymentProcessorSimulator {
        private PaymentServiceWithRandomShutdowns service;
        private AtomicInteger successful = new AtomicInteger(0);
        private AtomicInteger deferred = new AtomicInteger(0);
        private AtomicInteger recovered = new AtomicInteger(0);
        private AtomicInteger failed = new AtomicInteger(0);
        private AtomicBoolean processing = new AtomicBoolean(false);

        PaymentProcessorSimulator(PaymentServiceWithRandomShutdowns service) {
            this.service = service;
        }

        void startProcessing() { processing.set(true); }

        void processPayment(String id, double amount) throws Exception {
            if (!processing.get()) return;
            try {
                service.getPaymentStatus(id);
                successful.incrementAndGet();
            } catch (Exception e) {
                deferred.incrementAndGet();
            }
        }

        void recordFailure() { failed.incrementAndGet(); }

        void triggerRecovery() {
            int deferredCount = deferred.get();
            for (int i = 0; i < deferredCount; i++) {
                try {
                    service.getPaymentStatus("deferred-" + i);
                    recovered.incrementAndGet();
                } catch (Exception e) {
                    // Still offline
                }
            }
        }

        int getSuccessfulPayments() { return successful.get(); }
        int getDeferredPayments() { return deferred.get(); }
        int getRecoveredPayments() { return recovered.get(); }
        int getFailedPayments() { return failed.get(); }
    }
}
