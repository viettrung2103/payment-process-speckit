import java.util.LinkedList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simulates a single-instance server processing payments with both RECEIVED and IN_PROGRESS tasks.
 */
public class QuickSingleInstanceReceivedRecoveryTestRunner {

    public static void main(String[] args) {
        System.out.println("\n⚡ Quick Single Instance Recovery Simulation");
        System.out.println("════════════════════════════════════════════════════════════");
        System.out.println();

        if (runSimulation()) {
            System.out.println("\n✅ Simulation completed successfully");
            System.exit(0);
        } else {
            System.out.println("\n❌ Simulation failed");
            System.exit(1);
        }
    }

    private static boolean runSimulation() {
        try {
            int totalSubmissions = 24;
            int submitDelayMs = 200;
            int processingTimeMs = 800;
            int shutdownAfterMs = 4000;
            int downtimeMs = 4000;
            int maxWaitAfterRestartMs = 30000;

            System.out.println("📋 Configuration:");
            System.out.println("   • Total payments submitted: " + totalSubmissions);
            System.out.println("   • Submit interval: " + submitDelayMs + "ms");
            System.out.println("   • Processing time per payment: " + processingTimeMs + "ms");
            System.out.println("   • Shutdown after: " + shutdownAfterMs + "ms");
            System.out.println("   • Downtime: " + downtimeMs + "ms");
            System.out.println();

            SingleInstanceServiceSimulator service = new SingleInstanceServiceSimulator(processingTimeMs);
            service.start();

            Thread shutdownManager = new Thread(() -> {
                try {
                    Thread.sleep(shutdownAfterMs);
                    service.shutdown();
                    Thread.sleep(downtimeMs);
                    service.restart();
                    service.recoverPendingTasks();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            shutdownManager.start();

            for (int i = 1; i <= totalSubmissions; i++) {
                String paymentId = String.format("PAY-%03d", i);
                service.submitPayment(paymentId);
                Thread.sleep(submitDelayMs);
            }

            long waitDeadline = System.currentTimeMillis() + maxWaitAfterRestartMs;
            while (!service.isIdle() && System.currentTimeMillis() < waitDeadline) {
                Thread.sleep(200);
            }

            service.stop();
            shutdownManager.join();

            System.out.println();
            System.out.println("📊 Simulation Results:");
            System.out.println("   • Total submitted: " + totalSubmissions);
            System.out.println("   • Completed before shutdown: " + service.getCompletedBeforeShutdown());
            System.out.println("   • Pending RECEIVED at shutdown: " + service.getReceivedCountAtShutdown());
            System.out.println("   • In-progress at shutdown: " + service.getInProgressCountAtShutdown());
            System.out.println("   • Completed after restart: " + service.getCompletedAfterRestart());
            System.out.println("   • Total completed: " + service.getTotalCompleted());
            System.out.println("   • Total failed: " + service.getTotalFailed());
            System.out.println("   • Remaining pending after recovery: " + service.getPendingCount());

            return service.getPendingCount() == 0;
        } catch (Exception e) {
            System.err.println("Simulation error: " + e.getMessage());
            e.printStackTrace(System.err);
            return false;
        }
    }

    static class SingleInstanceServiceSimulator {
        private final LinkedList<PaymentTask> receivedQueue = new LinkedList<>();
        private final LinkedList<PaymentTask> inProgressQueue = new LinkedList<>();
        private final AtomicBoolean isRunning = new AtomicBoolean(true);
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final AtomicInteger totalCompleted = new AtomicInteger(0);
        private final AtomicInteger totalFailed = new AtomicInteger(0);
        private final AtomicInteger completedBeforeShutdown = new AtomicInteger(0);
        private volatile int receivedCountAtShutdown = 0;
        private volatile int inProgressCountAtShutdown = 0;
        private final int processingTimeMs;
        private Thread workerThread;
        private volatile PaymentTask currentTask = null;
        private volatile boolean shutdownOccurred = false;
        private volatile boolean restartOccurred = false;

        SingleInstanceServiceSimulator(int processingTimeMs) {
            this.processingTimeMs = processingTimeMs;
        }

        synchronized void submitPayment(String paymentId) {
            PaymentTask task = new PaymentTask(paymentId, TaskStatus.RECEIVED, processingTimeMs);
            receivedQueue.add(task);
            System.out.println("   ➕ Received " + paymentId + " (RECEIVED)");
            notifyAll();
        }

        void start() {
            workerThread = new Thread(this::runWorker, "simulation-worker");
            workerThread.start();
        }

        void shutdown() {
            isRunning.set(false);
            shutdownOccurred = true;
            synchronized (this) {
                receivedCountAtShutdown = receivedQueue.size();
                inProgressCountAtShutdown = inProgressQueue.size() + (currentTask != null ? 1 : 0);
                completedBeforeShutdown.set(totalCompleted.get());
                System.out.println("\n⏸️  Server shutdown triggered");
                System.out.println("   • RECEIVED pending at shutdown: " + receivedCountAtShutdown);
                System.out.println("   • IN_PROGRESS tasks at shutdown: " + inProgressCountAtShutdown);
            }
        }

        void restart() {
            isRunning.set(true);
            restartOccurred = true;
            System.out.println("\n🚀 Server restarted");
            synchronized (this) {
                notifyAll();
            }
        }

        synchronized void recoverPendingTasks() {
            if (!restartOccurred) {
                return;
            }

            int pendingReceived = receivedQueue.size();
            int pendingInProgress = inProgressQueue.size();
            System.out.println("\n🔄 Recovery starting");
            System.out.println("   • Pending RECEIVED tasks: " + pendingReceived);
            System.out.println("   • Pending IN_PROGRESS tasks: " + pendingInProgress);
            notifyAll();
        }

        void stop() throws InterruptedException {
            active.set(false);
            synchronized (this) {
                notifyAll();
            }
            if (workerThread != null) {
                workerThread.join();
            }
        }

        private void runWorker() {
            while (active.get()) {
                PaymentTask task;
                synchronized (this) {
                    task = nextTask();
                    if (task == null) {
                        try {
                            wait(200);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        continue;
                    }
                    currentTask = task;
                }
                processTask(task);
                synchronized (this) {
                    currentTask = null;
                }
            }
        }

        private synchronized PaymentTask nextTask() {
            if (!isRunning.get()) {
                return null;
            }
            if (!inProgressQueue.isEmpty()) {
                return inProgressQueue.removeFirst();
            }
            if (!receivedQueue.isEmpty()) {
                PaymentTask task = receivedQueue.removeFirst();
                task.setStatus(TaskStatus.IN_PROGRESS);
                System.out.println("   ▶️  Started " + task.getId() + " (IN_PROGRESS)");
                return task;
            }
            return null;
        }

        private void processTask(PaymentTask task) {
            try {
                while (task.remainingWorkMs > 0 && active.get()) {
                    if (!isRunning.get()) {
                        synchronized (this) {
                            task.setStatus(TaskStatus.IN_PROGRESS);
                            inProgressQueue.addFirst(task);
                        }
                        System.out.println("   ⏸️  Pausing " + task.getId() + " mid-processing (IN_PROGRESS)");
                        return;
                    }
                    int chunk = Math.min(100, task.remainingWorkMs);
                    Thread.sleep(chunk);
                    task.remainingWorkMs -= chunk;
                }
                if (task.remainingWorkMs <= 0) {
                    completeTask(task);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        private void completeTask(PaymentTask task) {
            task.setStatus(TaskStatus.COMPLETED);
            int total = totalCompleted.incrementAndGet();
            String stage = shutdownOccurred && !restartOccurred ? "before shutdown" : "after restart";
            System.out.println("   ✅ Completed " + task.getId() + " (" + stage + ")");
            if (!shutdownOccurred) {
                completedBeforeShutdown.set(total);
            }
        }

        synchronized boolean isIdle() {
            return receivedQueue.isEmpty() && inProgressQueue.isEmpty() && isRunning.get();
        }

        int getTotalCompleted() {
            return totalCompleted.get();
        }

        int getTotalFailed() {
            return totalFailed.get();
        }

        int getCompletedBeforeShutdown() {
            return completedBeforeShutdown.get();
        }

        int getCompletedAfterRestart() {
            if (!shutdownOccurred) {
                return 0;
            }
            return Math.max(0, totalCompleted.get() - completedBeforeShutdown.get());
        }

        int getReceivedCountAtShutdown() {
            return receivedCountAtShutdown;
        }

        int getInProgressCountAtShutdown() {
            return inProgressCountAtShutdown;
        }

        synchronized int getPendingCount() {
            return receivedQueue.size() + inProgressQueue.size();
        }
    }

    static class PaymentTask {
        private final String id;
        private TaskStatus status;
        private int remainingWorkMs;

        PaymentTask(String id, TaskStatus status, int workMs) {
            this.id = id;
            this.status = status;
            this.remainingWorkMs = workMs;
        }

        String getId() {
            return id;
        }

        TaskStatus getStatus() {
            return status;
        }

        void setStatus(TaskStatus status) {
            this.status = status;
        }
    }

    enum TaskStatus {
        RECEIVED,
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }
}
