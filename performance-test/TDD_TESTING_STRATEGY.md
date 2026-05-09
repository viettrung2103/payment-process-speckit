# TDD Testing Strategy for Phase 11: Rate Limiting & Auto-Scaling

**Date**: 9 May 2026
**Phase**: Phase 11 - Rate Limiting & Auto-Scaling
**Approach**: Test-Driven Development (TDD) with comprehensive test coverage

## 🎯 **Testing Objectives**

Following TDD principles, we write tests FIRST, ensure they FAIL before implementation, then implement until tests PASS. Phase 11 requires testing:

1. **Rate Limiting**: Nginx-based request throttling and burst handling
2. **Load Balancing**: Traffic distribution across payment-bridge instances
3. **Auto-Scaling**: CPU-based instance scaling with health validation
4. **Integration**: End-to-end system behavior under load

## 🧪 **Test Categories & Frameworks**

### **Unit Tests** (JUnit 5 + Mockito)

- Test individual components in isolation
- Mock external dependencies (Docker, nginx, system metrics)
- Focus on business logic and edge cases

### **Integration Tests** (TestContainers + JUnit 5)

- Test component interactions
- Use real Docker containers for nginx and payment-bridge
- Validate rate limiting and load balancing behavior

### **System Tests** (Bash + JMeter)

- Test complete system behavior
- End-to-end auto-scaling scenarios
- Performance validation under load

### **Load Tests** (JMeter + Custom Scripts)

- Stress test rate limiting thresholds
- Validate auto-scaling triggers
- Performance regression testing

## 📋 **Test Plan by Component**

### **1. Rate Limiting Tests**

#### **Unit Tests** (`src/test/java/.../ratelimiting/`)

```java
// RateLimitConfigTest.java
@Test
void shouldGenerateCorrectNginxConfig() {
    // Given: Rate limiting configuration
    var config = new RateLimitConfig(10, 5, 100);

    // When: Generate nginx config
    String nginxConfig = config.generateNginxConfig();

    // Then: Config contains correct zones
    assertThat(nginxConfig)
        .contains("limit_req_zone $binary_remote_addr zone=api:10m rate=10r/s")
        .contains("limit_req_zone $binary_remote_addr zone=payments:10m rate=5r/s");
}

@Test
void shouldHandleBurstRequestsCorrectly() {
    // Given: Payment endpoint with burst allowance
    var limiter = new RateLimiter(5, 10); // 5 req/s, 10 burst

    // When: Send 12 requests rapidly
    for (int i = 0; i < 12; i++) {
        limiter.allowRequest("192.168.1.1");
    }

    // Then: First 10 allowed, next 2 blocked
    assertThat(limiter.getAllowedCount()).isEqualTo(10);
    assertThat(limiter.getBlockedCount()).isEqualTo(2);
}
```

#### **Integration Tests** (`src/test/java/.../ratelimiting/`)

```java
// RateLimitingIntegrationTest.java
@Testcontainers
class RateLimitingIntegrationTest {

    @Container
    static GenericContainer<?> nginx = new GenericContainer<>("nginx:alpine")
        .withCopyFileToContainer(MountableFile.forHostPath("nginx.conf"), "/etc/nginx/nginx.conf")
        .withExposedPorts(80);

    @Test
    void shouldRateLimitPaymentRequests() {
        // Given: Nginx with rate limiting active
        String nginxUrl = "http://" + nginx.getHost() + ":" + nginx.getMappedPort(80);

        // When: Send requests above limit (6 req/s for payments)
        List<HttpResponse> responses = sendRequests(nginxUrl + "/api/v1/payments", 12, 1); // 12 in 1 second

        // Then: Some requests succeed, some return 429
        long successCount = responses.stream().mapToInt(HttpResponse::statusCode).filter(code -> code == 200).count();
        long rateLimitedCount = responses.stream().mapToInt(HttpResponse::statusCode).filter(code -> code == 429).count();

        assertThat(successCount).isGreaterThan(0);
        assertThat(rateLimitedCount).isGreaterThan(0);
        assertThat(successCount + rateLimitedCount).isEqualTo(12);
    }
}
```

#### **System Tests** (`performance-test/scripts/`)

```bash
# test-rate-limiting-system.sh
#!/bin/bash

# System test for rate limiting
test_payment_rate_limiting() {
    echo "🧪 Testing payment endpoint rate limiting..."

    # Send requests above 5 req/s limit
    local success_count=0
    local rate_limited_count=0

    for i in {1..12}; do
        response=$(curl -s -w "%{http_code}" \
            -H "Content-Type: application/json" \
            -d '{"amount": 100.00}' \
            "http://localhost:8080/api/v1/payments")

        if [ "$response" = "200" ]; then
            ((success_count++))
        elif [ "$response" = "429" ]; then
            ((rate_limited_count++))
        fi
    done

    # Assert: Some requests succeed, some are rate limited
    assert_greater_than "$success_count" 0 "Should allow some requests"
    assert_greater_than "$rate_limited_count" 0 "Should rate limit excess requests"

    echo "✅ Payment rate limiting: $success_count allowed, $rate_limited_count blocked"
}

test_burst_handling() {
    echo "🧪 Testing burst request handling..."

    # Send burst of requests
    responses=$(for i in {1..15}; do
        curl -s -w "%{http_code}\n" \
            -H "Content-Type: application/json" \
            -d '{"amount": 50.00}' \
            "http://localhost:8080/api/v1/payments" &
    done | sort | uniq -c)

    # Assert: Burst handling allows some requests through
    success_count=$(echo "$responses" | grep "200" | awk '{print $1}' || echo "0")
    rate_limited_count=$(echo "$responses" | grep "429" | awk '{print $1}' || echo "0")

    assert_greater_than "$success_count" 0 "Should handle burst requests"
    assert_greater_than "$rate_limited_count" 0 "Should eventually rate limit"

    echo "✅ Burst handling: $success_count allowed, $rate_limited_count blocked"
}
```

### **2. Load Balancing Tests**

#### **Unit Tests**

```java
// LoadBalancerConfigTest.java
@Test
void shouldGenerateUpstreamConfigForMultipleInstances() {
    // Given: 3 payment-bridge instances
    var instances = List.of("pb-1:8080", "pb-2:8080", "pb-3:8080");

    // When: Generate nginx upstream config
    String upstreamConfig = LoadBalancerConfig.generateUpstream(instances);

    // Then: All instances included
    assertThat(upstreamConfig)
        .contains("server pb-1:8080;")
        .contains("server pb-2:8080;")
        .contains("server pb-3:8080;");
}

@Test
void shouldDistributeRequestsRoundRobin() {
    // Given: Load balancer with 3 instances
    var balancer = new LoadBalancer(3);

    // When: Send 9 requests
    var distribution = new HashMap<String, Integer>();
    for (int i = 0; i < 9; i++) {
        String instance = balancer.getNextInstance();
        distribution.put(instance, distribution.getOrDefault(instance, 0) + 1);
    }

    // Then: Each instance gets 3 requests
    assertThat(distribution.values()).allMatch(count -> count == 3);
}
```

#### **Integration Tests**

```java
// LoadBalancingIntegrationTest.java
@Testcontainers
class LoadBalancingIntegrationTest {

    @Container
    static GenericContainer<?> nginx = new GenericContainer<>("nginx:alpine")
        .withCopyFileToContainer(MountableFile.forHostPath("nginx.conf"), "/etc/nginx/nginx.conf");

    @Container
    static GenericContainer<?> paymentBridge1 = new GenericContainer<>("payment-bridge:latest")
        .withExposedPorts(8080);

    @Container
    static GenericContainer<?> paymentBridge2 = new GenericContainer<>("payment-bridge:latest")
        .withExposedPorts(8080);

    @Test
    void shouldDistributeRequestsAcrossInstances() {
        // Given: 2 payment-bridge instances behind nginx
        String nginxUrl = "http://" + nginx.getHost() + ":" + nginx.getMappedPort(80);

        // When: Send multiple requests
        var instanceCounts = new HashMap<String, Integer>();
        for (int i = 0; i < 20; i++) {
            var response = sendRequest(nginxUrl + "/api/v1/payments");
            String instanceId = response.getHeader("X-Instance-Id");
            instanceCounts.put(instanceId, instanceCounts.getOrDefault(instanceId, 0) + 1);
        }

        // Then: Requests distributed across instances
        assertThat(instanceCounts.size()).isEqualTo(2);
        assertThat(instanceCounts.values()).allMatch(count -> count >= 8); // Roughly equal distribution
    }
}
```

#### **System Tests**

```bash
# test-load-balancing.sh
#!/bin/bash

test_request_distribution() {
    echo "🧪 Testing request distribution across instances..."

    # Send requests and track which instance handled them
    instance_counts=$(for i in {1..30}; do
        curl -s -H "X-Track-Request: $i" \
            "http://localhost:8080/api/v1/payments" \
            -H "Content-Type: application/json" \
            -d '{"amount": 10.00}' &
    done | grep -o "instance-[0-9]" | sort | uniq -c)

    # Assert: Requests distributed across available instances
    instance_count=$(echo "$instance_counts" | wc -l)
    assert_greater_than "$instance_count" 1 "Should use multiple instances"

    # Check distribution is roughly even
    min_requests=$(echo "$instance_counts" | awk '{print $1}' | sort -n | head -1)
    max_requests=$(echo "$instance_counts" | awk '{print $1}' | sort -n | tail -1)
    difference=$((max_requests - min_requests))

    assert_less_than "$difference" 5 "Distribution should be fairly even"

    echo "✅ Load balancing: $instance_count instances, requests distributed"
}
```

### **3. Auto-Scaling Tests**

#### **Unit Tests**

```java
// AutoScalerTest.java
@Test
void shouldScaleUpWhenCpuHigh() {
    // Given: CPU usage at 75%
    var scaler = new AutoScaler(1, 5, 70, 30);
    when(mockMetrics.getCpuUsage()).thenReturn(75.0);

    // When: Check scaling decision
    ScalingDecision decision = scaler.evaluateScaling();

    // Then: Should scale up
    assertThat(decision.getAction()).isEqualTo(ScalingAction.SCALE_UP);
    assertThat(decision.getTargetInstances()).isEqualTo(2);
}

@Test
void shouldNotScaleDuringCooldown() {
    // Given: Recent scaling action (within cooldown)
    var scaler = new AutoScaler(1, 5, 70, 30);
    scaler.recordScalingAction(Instant.now().minusSeconds(100)); // 100s ago, cooldown is 300s

    // When: Check scaling decision
    ScalingDecision decision = scaler.evaluateScaling();

    // Then: Should not scale
    assertThat(decision.getAction()).isEqualTo(ScalingAction.NO_ACTION);
}

@Test
void shouldUpdateNginxConfigAfterScaling() {
    // Given: Scaling from 2 to 3 instances
    var configManager = new NginxConfigManager();
    var newInstances = List.of("pb-1:8080", "pb-2:8080", "pb-3:8080");

    // When: Update configuration
    configManager.updateUpstreamConfig(newInstances);

    // Then: Nginx config updated
    String config = configManager.getCurrentConfig();
    assertThat(config).contains("server pb-3:8080;");
    verify(mockNginxService).reloadConfiguration();
}
```

#### **Integration Tests**

```java
// AutoScalingIntegrationTest.java
@Testcontainers
class AutoScalingIntegrationTest {

    @Container
    static GenericContainer<?> paymentBridge = new GenericContainer<>("payment-bridge:latest")
        .withExposedPorts(8080);

    @Test
    void shouldScaleBasedOnLoad() throws Exception {
        // Given: Auto-scaler monitoring system
        var autoScaler = new AutoScalerProcess();
        autoScaler.start();

        // When: Generate high load
        var loadGenerator = new LoadGenerator("http://localhost:8080");
        loadGenerator.generateLoad(100, 30); // 100 req/s for 30 seconds

        // Wait for scaling decision
        Thread.sleep(35000); // Wait past cooldown

        // Then: System should have scaled up
        int currentInstances = autoScaler.getCurrentInstanceCount();
        assertThat(currentInstances).isGreaterThan(1);

        autoScaler.stop();
    }
}
```

#### **System Tests**

```bash
# test-auto-scaling.sh
#!/bin/bash

test_cpu_based_scaling() {
    echo "🧪 Testing CPU-based auto-scaling..."

    # Start with 1 instance
    ./scripts/manage-auto-scaling.sh scale-to 1

    # Generate high CPU load
    echo "Generating high load to trigger scale-up..."
    for i in {1..10}; do
        # Send many concurrent requests
        for j in {1..50}; do
            curl -s "http://localhost:8080/api/v1/payments" \
                -H "Content-Type: application/json" \
                -d '{"amount": 100.00}' &
        done
        wait
    done

    # Wait for scaling decision
    sleep 35

    # Check if scaled up
    instance_count=$(docker ps --filter "name=payment-bridge-" --format "{{.Names}}" | wc -l)
    assert_greater_than "$instance_count" 1 "Should have scaled up under load"

    echo "✅ Auto-scaling: Scaled to $instance_count instances under load"
}

test_scale_down() {
    echo "🧪 Testing scale-down behavior..."

    # Start with multiple instances
    ./scripts/manage-auto-scaling.sh scale-to 3

    # Wait for low load period
    echo "Waiting for low load period..."
    sleep 120  # Wait longer than cooldown

    # Check if scaled down
    instance_count=$(docker ps --filter "name=payment-bridge-" --format "{{.Names}}" | wc -l)
    assert_less_or_equal "$instance_count" 2 "Should have scaled down during low load"

    echo "✅ Scale-down: Reduced to $instance_count instances"
}
```

## 🚀 **Test Execution Strategy**

### **1. Red-Green-Refactor Cycle**

```bash
# 1. Write failing test (RED)
./mvnw test -Dtest=RateLimitConfigTest#shouldGenerateCorrectNginxConfig

# 2. Implement minimal code to pass (GREEN)
# Edit RateLimitConfig.java

# 3. Refactor while keeping tests green
./mvnw test

# 4. Repeat for each component
```

### **2. Test Execution Order**

```bash
# Unit Tests (Fast, isolated)
./mvnw test -Dtest="*UnitTest"

# Integration Tests (Container-based)
./mvnw test -Dtest="*IntegrationTest"

# System Tests (Full environment)
cd performance-test
./scripts/setup-scaled-env.sh
./scripts/test-rate-limiting-system.sh
./scripts/test-load-balancing.sh
./scripts/test-auto-scaling.sh

# Load Tests (Performance validation)
./scripts/run-scaled-test.sh
```

### **3. CI/CD Integration**

```yaml
# .github/workflows/phase11-tests.yml
name: Phase 11 - Rate Limiting & Auto-Scaling Tests

on: [push, pull_request]

jobs:
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Run Unit Tests
        run: ./mvnw test -Dtest="*UnitTest"

  integration-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Run Integration Tests
        run: ./mvnw test -Dtest="*IntegrationTest"

  system-tests:
    runs-on: ubuntu-latest
    services:
      docker:
        image: docker:dind
    steps:
      - uses: actions/checkout@v3
      - name: Run System Tests
        run: |
          cd performance-test
          ./scripts/setup-scaled-env.sh
          ./scripts/test-rate-limiting-system.sh
          ./scripts/test-load-balancing.sh
```

## 📊 **Test Coverage Metrics**

### **Target Coverage**

- **Unit Tests**: 80%+ code coverage
- **Integration Tests**: All component interactions
- **System Tests**: All critical user journeys
- **Load Tests**: Performance regression detection

### **Coverage Areas**

- ✅ Rate limiting logic and configuration
- ✅ Load balancing algorithms and distribution
- ✅ Auto-scaling triggers and decision logic
- ✅ Health check validation and instance management
- ✅ Nginx configuration generation and updates
- ✅ Error handling and edge cases
- ✅ Performance under various load patterns

## 🔧 **Testing Tools & Dependencies**

### **Java Testing Stack**

```xml
<!-- pom.xml -->
<dependencies>
    <!-- JUnit 5 -->
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>5.10.0</version>
        <scope>test</scope>
    </dependency>

    <!-- Mockito -->
    <dependency>
        <groupId>org.mockito</groupId>
        <artifactId>mockito-core</artifactId>
        <version>5.14.2</version>
        <scope>test</scope>
    </dependency>

    <!-- TestContainers -->
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>1.19.0</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### **System Testing Tools**

- **curl/wget**: HTTP request testing
- **jq**: JSON response validation
- **docker**: Container lifecycle testing
- **JMeter**: Load testing and performance validation

## 🎯 **Success Criteria**

### **Test Results**

- ✅ All unit tests pass (RED → GREEN → REFACTOR cycle completed)
- ✅ Integration tests validate component interactions
- ✅ System tests confirm end-to-end functionality
- ✅ Load tests verify performance under stress

### **Coverage Validation**

- ✅ Rate limiting works at specified thresholds
- ✅ Load balancing distributes requests correctly
- ✅ Auto-scaling responds to CPU triggers
- ✅ Health checks prevent traffic to unhealthy instances

### **Quality Gates**

- ✅ No test regressions in CI/CD pipeline
- ✅ Performance benchmarks maintained
- ✅ Code coverage requirements met
- ✅ Documentation updated with test scenarios

## 📝 **Implementation Notes**

1. **Test First**: Always write failing tests before implementation
2. **Isolate Components**: Use mocks for external dependencies
3. **Realistic Scenarios**: Test with production-like data and load
4. **Edge Cases**: Cover boundary conditions and error scenarios
5. **Performance**: Tests should run efficiently in CI/CD
6. **Maintainability**: Tests serve as documentation and regression protection

This TDD strategy ensures Phase 11 features are thoroughly tested, reliable, and maintainable. Each test category builds confidence in the implementation while following agile development practices.
