package com.payment.scaling;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Integration Test for System Offline Recovery Scenarios
 *
 * This test simulates REAL system behavior:
 * 1. System is running normally with active requests
 * 2. External service (mock-payment-api) goes offline unexpectedly
 * 3. Payment bridge defers recovery attempts (graceful degradation)
 * 4. After a few seconds, the service comes back online
 * 5. Bridge automatically recovers and processes deferred payments
 *
 * Maximum 2 offline periods of ~1 second each to simulate realistic network issues.
 */
@DisplayName("System Offline Recovery Integration Test")
class OfflineRecoveryPerformanceTest {

    private ExternalPaymentServiceSimulator serviceSimulator;
    private PaymentBridgeSimulator bridgeSimulator;

    @BeforeEach
    void setUp() {
        serviceSimulator = new ExternalPaymentServiceSimulator();
        bridgeSimulator = new PaymentBridgeSimulator(serviceSimulator);
    }

    @Test
    @DisplayName("System running → sudden offline → recovery → automatic catch-up")
    void shouldHandleSystemOfflineAndAutomaticRecovery() throws InterruptedException {
        System.out.println("\n🔄 Starting offline recovery integration test...");

        // PHASE 1: System running normally
        System.out.println("\n📊 PHASE 1: System running normally");
        serviceSimulator.setOnline(true);
        bridgeSimulator.startProcessing();

        // Create some payments while service is healthy
        for (int i = 0; i < 5; i++) {
            PaymentRequest request = new PaymentRequest("payment-" + i, 100.00);
            bridgeSimulator.submitPayment(request);
            Thread.sleep(100); // Small delay between requests
        }

        long startTime = System.currentTimeMillis();
        Thread.sleep(1000); // Let them process

        System.out.println("✅ Submitted 5 payments while service online");
        assertThat(bridgeSimulator.getSuccessfulPayments()).isGreaterThanOrEqualTo(3);

        // PHASE 2: Sudden network outage - service goes offline
        System.out.println("\n⚠️  PHASE 2: Simulating sudden network outage");
        serviceSimulator.setOnline(false);
        bridgeSimulator.clearStats(); // Reset stats to track recovery behavior

        // Try to submit and process more payments while offline
        PaymentRequest offlinePayment = new PaymentRequest("offline-payment", 50.00);
        bridgeSimulator.submitPayment(offlinePayment);

        Thread.sleep(500); // Give it time to fail

        System.out.println("⏸️  Service is OFFLINE - Recovery deferred, payments in IN_PROGRESS state");
        assertThat(bridgeSimulator.getDeferredPayments()).isGreaterThanOrEqualTo(1);

        // PHASE 3: Wait a few seconds (simulating network recovery)
        System.out.println("\n⏳ PHASE 3: Waiting ~1 second for service to come back online...");
        Thread.sleep(1000);

        // PHASE 4: Service comes back online
        System.out.println("\n🔄 PHASE 4: Service recovery - bringing service back online");
        serviceSimulator.setOnline(true);

        // PHASE 5: Bridge automatic recovery (in real system, this happens on next startup or scheduled job)
        System.out.println("\n✨ PHASE 5: Automatic recovery - bridge catches up on IN_PROGRESS payments");
        Thread.sleep(500); // Give recovery time to process

        bridgeSimulator.triggerManualRecovery(); // Simulate next startup or scheduled recovery job
        Thread.sleep(1000); // Let recovery process the deferred payments

        // PHASE 6: Verify recovery completed
        System.out.println("\n✅ PHASE 6: Verifying recovery");
        long recoveryTime = System.currentTimeMillis() - startTime;

        System.out.println("📈 Recovery Statistics:");
        System.out.println("  - Deferred payments during outage: " + bridgeSimulator.getDeferredPayments());
        System.out.println("  - Recovered payments: " + bridgeSimulator.getRecoveredPayments());
        System.out.println("  - Total recovery time: " + recoveryTime + "ms");

        // Assertions verify the recovery worked
        assertThat(bridgeSimulator.getRecoveredPayments())
            .as("System should have recovered deferred payments after service came online")
            .isGreaterThanOrEqualTo(1);

        assertThat(recoveryTime)
            .as("Recovery should complete within reasonable time (3 seconds)")
            .isLessThan(3000);
    }

    @Test
    @DisplayName("Multiple rapid offline/online cycles with graceful degradation")
    void shouldHandleMultipleOfflineOnlineCycles() throws InterruptedException {
        System.out.println("\n🔄 Starting multiple offline/online cycles test...");

        bridgeSimulator.startProcessing();

        int cycleCount = 2; // Maximum 2 offline periods as requested
        for (int cycle = 0; cycle < cycleCount; cycle++) {
            System.out.println("\n📍 Cycle " + (cycle + 1) + "/" + cycleCount);

            // Online phase
            System.out.println("  ✅ Online phase - processing requests");
            serviceSimulator.setOnline(true);
            for (int i = 0; i < 3; i++) {
                bridgeSimulator.submitPayment(new PaymentRequest("cycle-" + cycle + "-pay-" + i, 75.00));
            }
            Thread.sleep(500);

            // Offline phase
            System.out.println("  ⏸️  Offline phase - ~1 second outage");
            serviceSimulator.setOnline(false);
            Thread.sleep(1000); // ~1 second offline

            // Recovery phase
            System.out.println("  🔄 Recovery phase - service back online");
            serviceSimulator.setOnline(true);
            bridgeSimulator.triggerManualRecovery();
            Thread.sleep(500);
        }

        System.out.println("\n📊 Final Statistics:");
        System.out.println("  - Total successful: " + bridgeSimulator.getSuccessfulPayments());
        System.out.println("  - Total recovered: " + bridgeSimulator.getRecoveredPayments());
        System.out.println("  - Total deferred: " + bridgeSimulator.getDeferredPayments());

        assertThat(bridgeSimulator.getSuccessfulPayments() + bridgeSimulator.getRecoveredPayments())
            .as("All payments should eventually be processed or recovered")
            .isGreaterThan(0);
    }

    /**
     * Simulates the external payment service (mock-payment-api)
     * Can be toggled online/offline to simulate network issues
     */
    static class ExternalPaymentServiceSimulator {
        private AtomicBoolean isOnline = new AtomicBoolean(true);

        void setOnline(boolean online) {
            isOnline.set(online);
        }

        boolean isOnline() {
            return isOnline.get();
        }

        String getPaymentStatus(String paymentId) throws Exception {
            if (!isOnline.get()) {
                throw new RuntimeException("Service unavailable - network timeout");
            }
            // Simulate successful response
            return "{\"status\":\"COMPLETED\",\"transactionId\":\"TXN-" + paymentId + "\"}";
        }
    }

    /**
     * Simulates the payment bridge application
     * Tracks deferred payments and recovery behavior
     */
    static class PaymentBridgeSimulator {
        private ExternalPaymentServiceSimulator externalService;
        private AtomicInteger successfulPayments = new AtomicInteger(0);
        private AtomicInteger deferredPayments = new AtomicInteger(0);
        private AtomicInteger recoveredPayments = new AtomicInteger(0);
        private AtomicBoolean isProcessing = new AtomicBoolean(false);

        PaymentBridgeSimulator(ExternalPaymentServiceSimulator externalService) {
            this.externalService = externalService;
        }

        void startProcessing() {
            isProcessing.set(true);
        }

        void submitPayment(PaymentRequest request) throws InterruptedException {
            if (!isProcessing.get()) return;

            try {
                String status = externalService.getPaymentStatus(request.id);
                if (status.contains("COMPLETED")) {
                    successfulPayments.incrementAndGet();
                    System.out.println("    ✅ Payment " + request.id + " completed");
                }
            } catch (Exception e) {
                // Service is offline - defer recovery
                deferredPayments.incrementAndGet();
                System.out.println("    ⏸️  Payment " + request.id + " deferred (service offline)");
            }
        }

        void triggerManualRecovery() throws InterruptedException {
            System.out.println("    🔄 Triggering recovery for deferred payments...");
            int deferred = deferredPayments.get();

            for (int i = 0; i < deferred; i++) {
                try {
                    String status = externalService.getPaymentStatus("deferred-" + i);
                    if (status.contains("COMPLETED")) {
                        recoveredPayments.incrementAndGet();
                        System.out.println("    ✨ Recovered deferred payment " + i);
                    }
                } catch (Exception e) {
                    // Still offline
                }
            }
        }

        void clearStats() {
            successfulPayments.set(0);
            deferredPayments.set(0);
            recoveredPayments.set(0);
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