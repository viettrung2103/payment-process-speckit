import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Quick Single Instance Shutdown Test
 * 
 * Simulates a quick performance test for a single payment service instance
 * with random shutdowns (max 4, up to 5 seconds each).
 * Duration: ~40 seconds
 */
public class QuickSingleInstanceShutdownTestRunner {

    public static void main(String[] args) {
        System.out.println("\n⚡ Quick Single Instance Shutdown Test");
        System.out.println("════════════════════════════════════════════════════════════");
        System.out.println();

        if (testQuickSingleInstanceShutdown()) {
            System.out.println("\n✅ Quick Single Instance Test PASSED");
            System.exit(0);
        } else {
            System.out.println("\n❌ Quick Single Instance Test FAILED");
            System.exit(1);
        }
    }

    private static boolean testQuickSingleInstanceShutdown() {
        try {
            System.out.println("📋 Configuration:");
            System.out.println("   • Scenario: Single instance with random shutdowns");
            System.out.println("   • Max shutdowns: 4 (up to 5 seconds each)");
            System.out.println("   • Test duration: ~40 seconds\n");

            SingleInstanceServiceSimulator service = new SingleInstanceServiceSimulator();
            service.enableRandomShutdowns(4, 5000);

            long testStart = System.currentTimeMillis();
            long testDuration = 40_000; // 40 seconds
            int batchNumber = 0;
            int totalProcessed = 0;

            System.out.println("▶️  Starting payment processing...\n");

            while (System.currentTimeMillis() - testStart < testDuration) {
                batchNumber++;

                int successful = 0;
                for (int i = 0; i < 8; i++) {
                    try {
                        service.processPayment("quick-" + batchNumber + "-" + i, 100.0);
                        successful++;
                        totalProcessed++;
                    } catch (Exception e) {}
                }

                String status = service.isRunning() ? "✅ ONLINE" : "⏸️  OFFLINE";
                System.out.println("   Batch " + batchNumber + ": " + successful + "/8 | " + status);

                if (service.hasRecentlyRestarted()) {
                    int recovered = service.recoverDeferredPayments();
                    if (recovered > 0) System.out.println("   🔄 Recovered: " + recovered);
                    service.acknowledgeRestart();
                }

                Thread.sleep(1000);
            }

            int totalRecovered = service.recoverDeferredPayments();
            System.out.println("\n📊 Final Results:");
            System.out.println("   • Total batches: " + batchNumber);
            System.out.println("   • Total processed: " + totalProcessed);
            System.out.println("   • Total recovered: " + totalRecovered);
            System.out.println("   • Shutdowns: " + service.getShutdownCount());

            return totalProcessed > 0 && service.getShutdownCount() <= 4;

        } catch (Exception e) {
            System.err.println("❌ Test failed: " + e.getMessage());
            return false;
        }
    }

    static class SingleInstanceServiceSimulator {
        private AtomicBoolean isRunning = new AtomicBoolean(true);
        private AtomicBoolean recentlyRestarted = new AtomicBoolean(false);
        private AtomicInteger shutdownCount = new AtomicInteger(0);
        private AtomicInteger deferredPayments = new AtomicInteger(0);

        void enableRandomShutdowns(int maxCount, long maxDuration) {
            new Thread(() -> {
                try {
                    while (shutdownCount.get() < maxCount) {
                        long waitTime = 3000 + (long)(Math.random() * 10000);
                        Thread.sleep(waitTime);
                        if (shutdownCount.get() < maxCount) triggerShutdown();
                    }
                } catch (InterruptedException e) {}
            }).start();
        }

        private void triggerShutdown() throws InterruptedException {
            isRunning.set(false);
            shutdownCount.incrementAndGet();
            long duration = 1000 + (long)(Math.random() * 4000);
            System.out.println("\n   💥 SHUTDOWN #" + shutdownCount.get());
            Thread.sleep(duration);
            isRunning.set(true);
            recentlyRestarted.set(true);
            System.out.println("   🚀 RESTARTED");
        }

        void processPayment(String id, double amount) throws Exception {
            if (!isRunning.get()) { deferredPayments.incrementAndGet(); throw new Exception("Offline"); }
        }

        int recoverDeferredPayments() {
            int deferred = deferredPayments.get();
            if (deferred > 0 && isRunning.get()) { deferredPayments.set(0); return deferred; }
            return 0;
        }

        boolean isRunning() { return isRunning.get(); }
        boolean hasRecentlyRestarted() { return recentlyRestarted.get(); }
        void acknowledgeRestart() { recentlyRestarted.set(false); }
        int getShutdownCount() { return shutdownCount.get(); }
    }
}
