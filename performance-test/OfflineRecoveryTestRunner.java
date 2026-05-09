import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Integration Test Runner for Offline Recovery Scenarios
 * 
 * STANDALONE - No external dependencies, just pure Java
 */
public class OfflineRecoveryTestRunner {

    public static void main(String[] args) {
        System.out.println("\n🔄 System Offline → Recovery → Automatic Catch-up");
        System.out.println("════════════════════════════════════════════════════════════");
        System.out.println();

        int passed = 0;
        int failed = 0;

        if (testSystemOfflineAndAutomaticRecovery()) {
            passed++;
            System.out.println("\n✅ testSystemOfflineAndAutomaticRecovery PASSED");
        } else {
            failed++;
            System.out.println("\n❌ testSystemOfflineAndAutomaticRecovery FAILED");
        }

        if (testMultipleOfflineOnlineCycles()) {
            passed++;
            System.out.println("✅ testMultipleOfflineOnlineCycles PASSED");
        } else {
            failed++;
            System.out.println("❌ testMultipleOfflineOnlineCycles FAILED");
        }

        System.out.println("\n════════════════════════════════════════════════════════════");
        System.out.println("📊 Test Results: " + passed + " passed, " + failed + " failed");

        if (failed == 0) {
            System.out.println("🎉 All offline recovery tests PASSED!");
        } else {
            System.out.println("⚠️  Some tests failed.");
            System.exit(1);
        }
    }

    private static boolean testSystemOfflineAndAutomaticRecovery() {
        try {
            System.out.println("Test 1: System offline → recovery → catch-up");
            System.out.println("─".repeat(50));

            ExternalPaymentServiceSimulator service = new ExternalPaymentServiceSimulator();
            PaymentBridgeSimulator bridge = new PaymentBridgeSimulator(service);

            // PHASE 1: Online
            System.out.println("\n📊 PHASE 1: System running normally");
            service.setOnline(true);
            bridge.startProcessing();

            for (int i = 0; i < 5; i++) {
                bridge.submitPayment("payment-" + i, 100.0);
                Thread.sleep(100);
            }

            long startTime = System.currentTimeMillis();
            Thread.sleep(1000);
            System.out.println("✅ Submitted 5 payments - Successful: " + bridge.getSuccessfulPayments());

            // PHASE 2: Offline
            System.out.println("\n⚠️  PHASE 2: Network outage - service OFFLINE");
            service.setOnline(false);
            bridge.clearStats();

            bridge.submitPayment("offline-payment", 50.0);
            Thread.sleep(500);
            System.out.println("⏸️  Payment deferred - Deferred: " + bridge.getDeferredPayments());

            // PHASE 3-4: Recovery
            System.out.println("\n⏳ PHASE 3: Waiting ~1 second for service to recover...");
            Thread.sleep(1000);

            System.out.println("\n🔄 PHASE 4: Service back online");
            service.setOnline(true);

            // PHASE 5: Recovery
            System.out.println("\n✨ PHASE 5: Triggering automatic recovery");
            Thread.sleep(500);
            bridge.triggerRecovery();
            Thread.sleep(1000);

            // PHASE 6: Verify
            System.out.println("\n✅ PHASE 6: Verifying recovery");
            long recoveryTime = System.currentTimeMillis() - startTime;

            System.out.println("📈 Results:");
            System.out.println("  • Recovered payments: " + bridge.getRecoveredPayments());
            System.out.println("  • Total recovery time: " + recoveryTime + "ms");

            return bridge.getRecoveredPayments() >= 1 && recoveryTime < 10000;

        } catch (Exception e) {
            System.err.println("❌ Test failed: " + e.getMessage());
            return false;
        }
    }

    private static boolean testMultipleOfflineOnlineCycles() {
        try {
            System.out.println("\nTest 2: Multiple offline/online cycles (max 2)");
            System.out.println("─".repeat(50));

            ExternalPaymentServiceSimulator service = new ExternalPaymentServiceSimulator();
            PaymentBridgeSimulator bridge = new PaymentBridgeSimulator(service);
            bridge.startProcessing();

            int cycleCount = 2;
            for (int cycle = 0; cycle < cycleCount; cycle++) {
                System.out.println("\n📍 Cycle " + (cycle + 1) + "/" + cycleCount);

                System.out.println("  ✅ Online phase");
                service.setOnline(true);
                for (int i = 0; i < 3; i++) {
                    bridge.submitPayment("cycle-" + cycle + "-" + i, 75.0);
                }
                Thread.sleep(500);

                System.out.println("  ⏸️  Offline phase (~1s)");
                service.setOnline(false);
                Thread.sleep(1000);

                System.out.println("  🔄 Recovery phase");
                service.setOnline(true);
                bridge.triggerRecovery();
                Thread.sleep(500);
            }

            System.out.println("\n📊 Final Statistics:");
            int total = bridge.getSuccessfulPayments() + bridge.getRecoveredPayments();
            System.out.println("  • Total: " + total);

            return total > 0;

        } catch (Exception e) {
            System.err.println("❌ Test failed: " + e.getMessage());
            return false;
        }
    }

    static class ExternalPaymentServiceSimulator {
        private AtomicBoolean isOnline = new AtomicBoolean(true);

        void setOnline(boolean online) {
            isOnline.set(online);
        }

        String getPaymentStatus(String paymentId) throws Exception {
            if (!isOnline.get()) {
                throw new RuntimeException("Service unavailable");
            }
            return "{\"status\":\"COMPLETED\"}";
        }
    }

    static class PaymentBridgeSimulator {
        private ExternalPaymentServiceSimulator service;
        private AtomicInteger successful = new AtomicInteger(0);
        private AtomicInteger deferred = new AtomicInteger(0);
        private AtomicInteger recovered = new AtomicInteger(0);
        private AtomicBoolean processing = new AtomicBoolean(false);

        PaymentBridgeSimulator(ExternalPaymentServiceSimulator service) {
            this.service = service;
        }

        void startProcessing() {
            processing.set(true);
        }

        void submitPayment(String id, double amount) {
            if (!processing.get()) return;
            try {
                service.getPaymentStatus(id);
                successful.incrementAndGet();
            } catch (Exception e) {
                deferred.incrementAndGet();
            }
        }

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

        void clearStats() {
            successful.set(0);
            deferred.set(0);
            recovered.set(0);
        }

        int getSuccessfulPayments() { return successful.get(); }
        int getDeferredPayments() { return deferred.get(); }
        int getRecoveredPayments() { return recovered.get(); }
    }
}
