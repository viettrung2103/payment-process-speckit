import com.payment.scaling.*;
import org.mockito.Mockito;
import static org.mockito.Mockito.*;

/**
 * Simple test runner to demonstrate TDD approach
 */
public class TestRunner {

    public static void main(String[] args) {
        System.out.println("🚀 Running TDD Tests for Phase 11: Rate Limiting & Auto-Scaling");
        System.out.println();

        int passed = 0;
        int failed = 0;

        // Test 1: Scale up when CPU high
        if (testScaleUpWhenCpuHigh()) {
            passed++;
            System.out.println("✅ testScaleUpWhenCpuHigh PASSED");
        } else {
            failed++;
            System.out.println("❌ testScaleUpWhenCpuHigh FAILED");
        }

        // Test 2: Scale down when CPU low
        if (testScaleDownWhenCpuLow()) {
            passed++;
            System.out.println("✅ testScaleDownWhenCpuLow PASSED");
        } else {
            failed++;
            System.out.println("❌ testScaleDownWhenCpuLow FAILED");
        }

        // Test 3: No action when CPU optimal
        if (testNoActionWhenCpuOptimal()) {
            passed++;
            System.out.println("✅ testNoActionWhenCpuOptimal PASSED");
        } else {
            failed++;
            System.out.println("❌ testNoActionWhenCpuOptimal FAILED");
        }

        // Test 4: Cooldown period
        if (testCooldownPeriod()) {
            passed++;
            System.out.println("✅ testCooldownPeriod PASSED");
        } else {
            failed++;
            System.out.println("❌ testCooldownPeriod FAILED");
        }

        System.out.println();
        System.out.println("📊 Test Results: " + passed + " passed, " + failed + " failed");

        if (failed == 0) {
            System.out.println("🎉 All TDD tests passed! Phase 11 implementation is validated.");
        } else {
            System.out.println("⚠️  Some tests failed. Implementation needs fixes.");
            System.exit(1);
        }
    }

    private static boolean testScaleUpWhenCpuHigh() {
        try {
            // Given: High CPU usage
            MetricsProvider metricsProvider = mock(MetricsProvider.class);
            InstanceManager instanceManager = mock(InstanceManager.class);

            when(metricsProvider.getAverageCpuUsage()).thenReturn(75.0);
            when(instanceManager.getCurrentInstanceCount()).thenReturn(2);

            AutoScaler autoScaler = new AutoScaler(metricsProvider, instanceManager, 1, 5, 70.0, 30.0, 300);

            // When: Evaluate scaling decision
            ScalingDecision decision = autoScaler.evaluateScaling();

            // Then: Should recommend scale up
            return decision.getAction() == ScalingAction.SCALE_UP &&
                   decision.getTargetInstances() == 3 &&
                   decision.getReason().contains("CPU usage 75.0% > 70.0%");

        } catch (Exception e) {
            System.err.println("Test failed with exception: " + e.getMessage());
            return false;
        }
    }

    private static boolean testScaleDownWhenCpuLow() {
        try {
            // Given: Low CPU usage
            MetricsProvider metricsProvider = mock(MetricsProvider.class);
            InstanceManager instanceManager = mock(InstanceManager.class);

            when(metricsProvider.getAverageCpuUsage()).thenReturn(25.0);
            when(instanceManager.getCurrentInstanceCount()).thenReturn(3);

            AutoScaler autoScaler = new AutoScaler(metricsProvider, instanceManager, 1, 5, 70.0, 30.0, 300);

            // When: Evaluate scaling decision
            ScalingDecision decision = autoScaler.evaluateScaling();

            // Then: Should recommend scale down
            return decision.getAction() == ScalingAction.SCALE_DOWN &&
                   decision.getTargetInstances() == 2 &&
                   decision.getReason().contains("CPU usage 25.0% < 30.0%");

        } catch (Exception e) {
            System.err.println("Test failed with exception: " + e.getMessage());
            return false;
        }
    }

    private static boolean testNoActionWhenCpuOptimal() {
        try {
            // Given: CPU usage in optimal range
            MetricsProvider metricsProvider = mock(MetricsProvider.class);
            InstanceManager instanceManager = mock(InstanceManager.class);

            when(metricsProvider.getAverageCpuUsage()).thenReturn(50.0);
            when(instanceManager.getCurrentInstanceCount()).thenReturn(2);

            AutoScaler autoScaler = new AutoScaler(metricsProvider, instanceManager, 1, 5, 70.0, 30.0, 300);

            // When: Evaluate scaling decision
            ScalingDecision decision = autoScaler.evaluateScaling();

            // Then: Should not scale
            return decision.getAction() == ScalingAction.NO_ACTION &&
                   decision.getReason().contains("CPU usage 50.0% within optimal range");

        } catch (Exception e) {
            System.err.println("Test failed with exception: " + e.getMessage());
            return false;
        }
    }

    private static boolean testCooldownPeriod() {
        try {
            // Given: Recent scaling action within cooldown period
            MetricsProvider metricsProvider = mock(MetricsProvider.class);
            InstanceManager instanceManager = mock(InstanceManager.class);

            AutoScaler autoScaler = new AutoScaler(metricsProvider, instanceManager, 1, 5, 70.0, 30.0, 300);
            autoScaler.recordScalingAction(java.time.Instant.now().minusSeconds(100)); // 100s ago

            when(metricsProvider.getAverageCpuUsage()).thenReturn(80.0);
            when(instanceManager.getCurrentInstanceCount()).thenReturn(2);

            // When: Evaluate scaling decision
            ScalingDecision decision = autoScaler.evaluateScaling();

            // Then: Should not scale due to cooldown
            return decision.getAction() == ScalingAction.NO_ACTION &&
                   decision.getReason().contains("Within cooldown period");

        } catch (Exception e) {
            System.err.println("Test failed with exception: " + e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T mock(Class<T> classToMock) {
        return Mockito.mock(classToMock);
    }
}