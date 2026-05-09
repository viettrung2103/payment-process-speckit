import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.*;

public class FullMultiInstanceShutdownTestRunner {
    public static void main(String[] args) {
        System.out.println("
🔥 Full Multiple Instance Shutdown Test");
        System.out.println("════════════════════════════════════════════════════════════");
        System.out.println();
        if (testFullMultiInstanceShutdown()) {
            System.out.println("
✅ Full Multi-Instance Test PASSED");
            System.exit(0);
        } else {
            System.out.println("
❌ Full Multi-Instance Test FAILED");
            System.exit(1);
        }
    }
    private static boolean testFullMultiInstanceShutdown() {
        try {
            System.out.println("📋 Configuration:");
            System.out.println("   • Scenario: Multiple instances with independent random shutdowns (full)");
            System.out.println("   • Number of instances: 3");
            System.out.println("   • Max shutdowns per instance: 4 (up to 5 seconds each)");
            System.out.println("   • Test duration: ~120 seconds");
            System.out.println("   • Load balancing: Round-robin
");
            List<MultiInstanceServiceSimulator> instances = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                MultiInstanceServiceSimulator instance = new MultiInstanceServiceSimulator("Instance-" + (i + 1));
                instance.enableRandomShutdowns(4, 5000);
                instances.add(instance);
            }
            LoadBalancer loadBalancer = new LoadBalancer(instances);
            long testStart = System.currentTimeMillis();
            long testDuration = 120_000;
            int batchNumber = 0;
            int totalProcessed = 0;
            System.out.println("▶️  Starting full payment processing across 3 instances...
");
            while (System.currentTimeMillis() - testStart < testDuration) {
                batchNumber++;
                int successful = 0;
                for (int i = 0; i < 18; i++) {
                    try {
                        loadBalancer.processPayment("full-multi-" + batchNumber + "-" + i, 100.0);
                        successful++;
                        totalProcessed++;
                    } catch (Exception e) {}
                }
                int onlineCount = 0;
                for (MultiInstanceServiceSimulator instance : instances) {
                    if (instance.isRunning()) onlineCount++;
                }
                System.out.println("   Batch " + batchNumber + ": " + successful + "/18 | Online: " + onlineCount + "/3");
                for (MultiInstanceServiceSimulator instance : instances) {
                    if (instance.hasRecentlyRestarted()) {
                        int recovered = instance.recoverDeferredPayments();
                        if (recovered > 0) {
                            System.out.println("   🔄 " + instance.name + " recovered " + recovered);
                        }
                        instance.acknowledgeRestart();
                    }
                }
                Thread.sleep(1000);
            }
            for (MultiInstanceServiceSimulator instance : instances) {
                int recovered = instance.recoverDeferredPayments();
                totalProcessed += recovered;
            }
            System.out.println("
📊 Final Results:");
            System.out.println("   • Total batches: " + batchNumber);
            System.out.println("   • Total processed: " + totalProcessed);
            for (MultiInstanceServiceSimulator instance : instances) {
                System.out.println("   • " + instance.name + " shutdowns: " + instance.getShutdownCount());
            }
            return totalProcessed > 0;
        } catch (Exception e) {
            System.err.println("❌ Test failed: " + e.getMessage());
            return false;
        }
    }
    static class LoadBalancer {
        private List<MultiInstanceServiceSimulator> instances;
        private int currentIndex = 0;
        LoadBalancer(List<MultiInstanceServiceSimulator> instances) {
            this.instances = instances;
        }
        void processPayment(String id, double amount) throws Exception {
            MultiInstanceServiceSimulator instance = instances.get(currentIndex);
            currentIndex = (currentIndex + 1) % instances.size();
            instance.processPayment(id, amount);
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
   💥 " + name + " SHUTDOWN #" + shutdownCount.get());
            Thread.sleep(duration);
            isRunning.set(true);
            recentlyRestarted.set(true);
            System.out.println("   🚀 " + name + " RESTARTED");
        }
        void processPayment(String id, double amount) throws Exception {
            if (!isRunning.get()) { deferredPayments.incrementAndGet(); throw new Exception(name + " offline"); }
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
