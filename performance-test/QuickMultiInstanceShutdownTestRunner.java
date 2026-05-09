import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.*;

/**
 * Quick Multiple Instance Shutdown Test
 * 
 * Simulates a quick performance test for multiple payment service instances
 * with independent random shutdowns (max 4 per instance, up to 5 seconds each).
 * Duration: ~40 seconds
 */
public class QuickMultiInstanceShutdownTestRunner {

    public static void main(String[] args) {
        System.out.println("\n⚡ Quick Multiple Instance Shutdown Test");
        System.out.println("════════════════════════════════════════════════════════════");
        System.out.println();

        if (testQuickMultiInstanceShutdown()) {
            System.out.println("\n✅ Quick Multi Instance Test PASSED");
            System.exit(0);
        } else {
            System.out.println("\n❌ Quick Multi Instance Test FAILED");
            System.exit(1);
        }
    }

    private static boolean testQuickMultiInstanceShutdown() {
        try {
            System.out.println("📋 Configuration:");
            System.out.println("   • Scenario: Multiple instances with independent random shutdowns");
            System.out.println("   • Number of instances: 3");
            System.out.println("   • Max shutdowns per instance: 4 (up to 5 seconds each)");
            System.out.println("   • Test duration: ~40 seconds");
            System.out.println("   • Load balancing: Round-robin\n");

            MultiInstanceServiceSimulator[] instances = new MultiInstanceServiceSimulator[3];
            for (int i = 0; i < 3; i++) {
                instances[i] = new MultiInstanceServiceSimulator("Instance-" + (i + 1));
                instances[i].enableRandomShutdowns(4, 5000);
            }

            LoadBalancer loadBalancer = new LoadBalancer(instances);

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
                        loadBalancer.processPayment("quick-" + batchNumber + "-" + i, 100.0);
                        successful++;
                        totalProcessed++;
                    } catch (Exception e) {}
                }

                String status = loadBalancer.getStatus();
                System.out.println("   Batch " + batchNumber + ": " + successful + "/8 | " + status);

                if (loadBalancer.hasRecentRecovery()) {
                    int recovered = loadBalancer.recoverDeferredPayments();
                    if (recovered > 0) System.out.println("   🔄 Recovered: " + recovered);
                    loadBalancer.acknowledgeRecovery();
                }

                Thread.sleep(1000);
            }

            int totalRecovered = loadBalancer.recoverDeferredPayments();
            System.out.println("\n📊 Final Results:");
            System.out.println("   • Total batches: " + batchNumber);
            System.out.println("   • Total processed: " + totalProcessed);
            System.out.println("   • Total recovered: " + totalRecovered);
            System.out.println("   • Total shutdowns: " + loadBalancer.getTotalShutdowns());

            return totalProcessed > 0;

        } catch (Exception e) {
            System.err.println("❌ Test failed: " + e.getMessage());
            return false;
        }
    }

    static class LoadBalancer {
        private MultiInstanceServiceSimulator[] instances;
        private AtomicInteger currentIndex = new AtomicInteger(0);
        private AtomicBoolean recentRecovery = new AtomicBoolean(false);

        LoadBalancer(MultiInstanceServiceSimulator[] instances) {
            this.instances = instances;
        }

        void processPayment(String id, double amount) throws Exception {
            int attempts = 0;
            while (attempts < instances.length) {
                int index = currentIndex.getAndIncrement() % instances.length;
                try {
                    instances[index].processPayment(id, amount);
                    return;
                } catch (Exception e) {
                    attempts++;
                }
            }
            throw new Exception("All instances offline");
        }

        String getStatus() {
            int online = 0;
            for (MultiInstanceServiceSimulator instance : instances) {
                if (instance.isRunning()) online++;
            }
            if (online == instances.length) return "✅ ALL ONLINE";
            if (online == 0) return "⏸️  ALL OFFLINE";
            return "⚠️  " + online + "/" + instances.length + " ONLINE";
        }

        boolean hasRecentRecovery() {
            for (MultiInstanceServiceSimulator instance : instances) {
                if (instance.hasRecentlyRestarted()) {
                    recentRecovery.set(true);
                    return true;
                }
            }
            return false;
        }

        void acknowledgeRecovery() {
            recentRecovery.set(false);
            for (MultiInstanceServiceSimulator instance : instances) {
                instance.acknowledgeRestart();
            }
        }

        int recoverDeferredPayments() {
            int total = 0;
            for (MultiInstanceServiceSimulator instance : instances) {
                total += instance.recoverDeferredPayments();
            }
            return total;
        }

        int getTotalShutdowns() {
            int total = 0;
            for (MultiInstanceServiceSimulator instance : instances) {
                total += instance.getShutdownCount();
            }
            return total;
        }
    }

    static class MultiInstanceServiceSimulator {
        String name;
        private AtomicBoolean isRunning = new AtomicBoolean(true);
        private AtomicBoolean recentlyRestarted = new AtomicBoolean(false);
        private AtomicInteger shutdownCount = new AtomicInteger(0);
        private AtomicInteger deferredPayments = new AtomicInteger(0);

        MultiInstanceServiceSimulator(String name) {
            this.name = name;
        }

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
            System.out.println("\n   💥 " + name + " SHUTDOWN #" + shutdownCount.get());
            Thread.sleep(duration);
            isRunning.set(true);
            recentlyRestarted.set(true);
            System.out.println("   🚀 " + name + " RESTARTED");
        }

        void processPayment(String id, double amount) throws Exception {
            if (!isRunning.get()) { 
                deferredPayments.incrementAndGet(); 
                throw new Exception(name + " offline"); 
            }
        }

        int recoverDeferredPayments() {
            int deferred = deferredPayments.get();
            if (deferred > 0 && isRunning.get()) { 
                deferredPayments.set(0); 
                return deferred; 
            }
            return 0;
        }

        boolean isRunning() { return isRunning.get(); }
        boolean hasRecentlyRestarted() { return recentlyRestarted.get(); }
        void acknowledgeRestart() { recentlyRestarted.set(false); }
        int getShutdownCount() { return shutdownCount.get(); }
    }
}
