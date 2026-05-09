# Mock Payment API Testing Guide

This guide explains how to run the Mock Payment API tests, configure failure scenarios, and use the test profile for fast execution.

## Test Types

### Unit Tests

Purpose: Validate business logic in isolation using mocked dependencies.

- `MockPaymentServiceTest` - payment processing flow, duplicate handling, status updates
- `FailureSimulatorTest` - failure probability and scenario generation
- `DelaySimulatorTest` - configured delay behavior

### Integration Tests

Purpose: Validate HTTP request/response behavior, Spring Boot context, and H2 persistence.

- `PaymentFlowTest` - full payment lifecycle with the HTTP API
- `FailureDistributionTest` - failure rate and response validation
- `TransactionHistoryTest` - pagination and history queries
- `PersistenceTest` - H2 persistence and transaction consistency

### Load Tests

Purpose: Validate concurrency and performance under load.

- `LoadTest` - 100 concurrent requests with delays
- `ConcurrencyTest` - 50 threads × 20 requests = 1000 total requests
- `FailureDistributionStatTest` - statistical distribution consistency around 90% success rate

## Running Tests

### Run All Tests

```bash
cd mock-payment-api
mvn test
```

### Run Specific Unit Tests

```bash
mvn test -Dtest=MockPaymentServiceTest,FailureSimulatorTest,DelaySimulatorTest
```

### Run Specific Integration Tests

```bash
mvn test -Dtest=PaymentFlowTest,FailureDistributionTest,TransactionHistoryTest,PersistenceTest
```

### Run Specific Load Tests

```bash
mvn test -Dtest=LoadTest,ConcurrencyTest,FailureDistributionStatTest
```

### Run Single Test Class

```bash
mvn test -Dtest=FailureDistributionStatTest
```

### Run with Test Profile

Use the `test` Spring profile for lower delay values and faster execution.

```bash
mvn test -Dspring.profiles.active=test
```

## Test Profiles and Configuration

### application-test.yml

The `test` profile reduces delay ranges for faster, predictable execution:

```yaml
mock:
  api:
    min-delay-ms: 1
    max-delay-ms: 100
    failure-rate: 0.1
logging:
  level:
    root: WARN
```

### Production Config

The default profile uses a wider delay range and simulates more realistic latency:

```yaml
mock:
  api:
    min-delay-ms: 10
    max-delay-ms: 2000
    failure-rate: 0.1
```

## Running Tests with Maven

### Recommended: Clean then Test

```bash
mvn clean test
```

### Verify Phase 7 Load Tests

```bash
mvn test -Dtest=LoadTest
mvn test -Dtest=ConcurrencyTest
mvn test -Dtest=FailureDistributionStatTest
```

### Run Tests in IDE

Because Spring Boot context initialization may behave differently under Maven, you can run the tests directly from your IDE:

- `PaymentFlowTest`
- `FailureDistributionTest`
- `TransactionHistoryTest`
- `PersistenceTest`
- `LoadTest`
- `ConcurrencyTest`
- `FailureDistributionStatTest`

## Common Test Issues

### Spring Context Startup Failures

If tests fail due to `ApplicationContext` initialization errors, try:

1. Running tests individually instead of as a batch
2. Using the IDE runner rather than Maven
3. Verifying H2 and Spring Boot configuration in `application-test.yml`
4. Checking for cached test results or stale compiled files

### H2 Persistence Issues

- Ensure the test profile uses `jdbc:h2:mem:mockdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`
- If data is lost between tests, verify the profile and `ddl-auto` settings
- Avoid concurrent writes that modify the same transaction ID unless idempotency is expected

### Load Test Timing

- If load tests exceed the timeout, increase the JUnit timeout or adjust test delay ranges
- For `FailureDistributionStatTest`, allow up to 10 minutes for 1000 requests to complete
- Use `-Dspring.profiles.active=test` during load testing to reduce per-request delay

## Test Data

### Example Payment Payload

```json
{
  "transactionId": "TXN-TEST-001",
  "amount": 100.0,
  "currency": "USD",
  "clientReference": "CLIENT-TEST-001"
}
```

### Transaction IDs in Tests

The tests use deterministic transaction IDs for reproducibility:

- `CONCUR-<thread>-<request>`
- `STORED-0000` to `STORED-0099`
- `STAT-<timestamp>-<index>`
- `BATCH-<run>-<timestamp>-<index>`
- `FAILREASON-<timestamp>-<index>`

## Test Metrics and Validation

### Success Rate Validation

- `FailureDistributionStatTest` verifies success rate is around `90% ± 2%`
- For a 1000 request sample, expected success count is between `880` and `920`

### Failure Reason Validation

- Every failed payment must return a non-null `failureReason`
- Failure reason is persisted in the transaction record

### Concurrency and Data Integrity

- `ConcurrencyTest` verifies 1000 requests are processed correctly
- `LoadTest` validates 200 concurrent virtual thread requests complete efficiently
- `EndToEndIntegrationTest` verifies concurrent data persistence and independent payment records

## Notes on Statistical Tests

1. Large sample sizes help validate the failure distribution
2. Randomness means exact counts will vary per run
3. Use tolerance bands to avoid flaky tests
4. The configured failure rate is 10%, so tests expect approximately `90%` success

## How to Add More Tests

1. Add a new test class under `src/test/java/com/payment/mock/`
2. Use `@SpringBootTest` for full integration tests
3. Use `@ActiveProfiles("test")` for fast test setup
4. For load tests, use `Executors.newVirtualThreadPerTaskExecutor()`
5. Update `README.md` and `TESTING.md` with descriptions of new scenarios
