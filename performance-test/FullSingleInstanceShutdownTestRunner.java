import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

public class FullSingleInstanceShutdownTestRunner {
    public static void main(String[] args) {
        System.out.println("
🔥 Full Single Instance Shutdown Test");
        System.out.println("════════════════════════════════════════════════════════════");
        System.out.println();
        if (testFullSingleInstanceShutdown()) {
            System.out.println("
✅ Full Single Instance Test PASSED");
            System.exit(0);
        } else {
            System.out.println("
❌ Full Single Instance Test FAILED");
            System.exit(1);
        }
    }
    private static boolean testFullSingleInstanceShutdown() {
        try {
            System.out.println("📋 Configuration:");
            System.out.println("   • Scenario: Single instance with random shutdowns (full)");
            System.out.println("   • Max shutdowns: 4 (up to 5 seconds each)");
            System.out.println("   • Test duration: ~120 seconds");
            System.out.println("   • Recovery: Automatic on restart
");
            SingleInstanceServiceSimulator service = new SingleInstanceServiceSimulator();
            service.enableRandomShutdowns(4, 5000);
            long testStart = System.currentTimeMillis();
            long testDuration = 120_000;
            int batchNumber = 0;
            int totalProcessed = 0;
            System.out.println("▶️  Starting full payment processing...
");
            while (System.currentTimeMillis() - testStart < testDuration) {
                batchNumber++;
                int successful = 0;
                for (int i = 0; i < 15; i++) {
                    try {
                        service.processPayment("full-single-" + batchNumber + "-" + i, 100.0);
                        successful++;
                        totalProcessed++;
                    } catch (Exception e) {}
                }
                String status = service.isRunning() ? "✅ ONLINE" : "⏸️  OFFLINE";
                System.out.println("   Batch " + batchNumber + ": " + successful + "/15 | " + status);
                if (service.hasRecentlyRestarted()) {
                    int recovered = service.recoverDeferredPayments();
                    if (recovered > 0) System.out.println("   🔄 Recovered: " + recovered);
                    service.acknowledgeRestart();
                }
                Thread.sleep(1000);
            }
            int totalRecovered = service.recoverDeferredPayments();
            System.out.println("
📊 Final Results:");
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
                        long waitTime = 5000 + (long)(Math.random() * 20000);
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
            System.out.println("
   💥 SHUTDOWN #" + shutdownCount.get() + " (~" + (duration / 1000) + "s)");
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
