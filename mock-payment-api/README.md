# Mock Payment API

A realistic mock implementation of a payment processing API built with Java 21, Spring Boot 3.4, and virtual threads. This service simulates real-world payment processing including configurable delays, realistic failure scenarios, and transaction history tracking.

## Overview

The Mock Payment API is designed to:

- **Simulate real payment processing** with configurable delays (1-100ms in test, 10-2000ms in production)
- **Provide realistic failure scenarios** with ~10% failure rate and 5 different failure types
- **Demonstrate Java 21 Project Loom** via virtual thread executors for efficient concurrent processing
- **Enable end-to-end testing** of the Payment Process Server without external payment provider dependencies
- **Track transaction history** in H2 in-memory database with comprehensive transaction lifecycle

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.8+

### Running Locally

```bash
# Navigate to mock-payment-api directory
cd mock-payment-api

# Run with Maven (production profile, 10-2000ms delays)
mvn spring-boot:run

# Run with test profile (1-100ms delays for faster testing)
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=test"

# API will be available at: http://localhost:8081
```

### Running with Tests

```bash
# Run unit tests only (does not require running service)
mvn test

# Run integration tests (requires service running on port 8081)
# Terminal 1: mvn spring-boot:run
# Terminal 2: mvn verify

# Run specific test class
mvn test -Dtest=MockPaymentServiceTest
mvn test -Dtest=FailureSimulatorTest
mvn test -Dtest=DelaySimulatorTest
```

## Configuration

### Application Properties

Configure behavior via `application.yml` (production) or `application-test.yml` (testing):

```yaml
mock:
  api:
    min-delay-ms: 10 # Minimum processing delay in milliseconds
    max-delay-ms: 2000 # Maximum processing delay in milliseconds
    failure-rate: 0.1 # Failure probability (0.0 - 1.0), default 10%
```

### Profiles

- **Production** (default): 10-2000ms delays, full validation
- **Test**: 1-100ms delays for faster test execution

Start with specific profile:

```bash
mvn spring-boot:run -Dspring.profiles.active=test
```

## API Endpoints

### Process Payment

**Endpoint**: `POST /api/v1/payments`

**Request**:

```json
{
  "transactionId": "TXN-2024-001",
  "amount": 100.0,
  "currency": "USD",
  "clientReference": "CLIENT-REF-123"
}
```

**Response** (Success - 200 OK):

```json
{
  "transactionId": "TXN-2024-001",
  "status": "COMPLETED",
  "amount": 100.0,
  "currency": "USD",
  "processedAt": "2024-05-08T10:30:45Z",
  "failureReason": null
}
```

**Response** (Failure - 200 OK with FAILED status):

```json
{
  "transactionId": "TXN-2024-002",
  "status": "FAILED",
  "amount": 100.0,
  "currency": "USD",
  "processedAt": "2024-05-08T10:30:47Z",
  "failureReason": "Payment processing failed - Rate limit exceeded"
}
```

**Features**:

- Simulates processing delay based on configuration
- Randomly fails ~10% of requests
- Returns realistic failure reasons
- Automatically persists transaction to H2
- Idempotent: Same transactionId always produces same result

### Get Payment Status

**Endpoint**: `GET /api/v1/payments/status/{transactionId}`

**Response** (200 OK):

```json
{
  "transactionId": "TXN-2024-001",
  "status": "COMPLETED",
  "amount": 100.0,
  "currency": "USD",
  "clientReference": "CLIENT-REF-123",
  "createdAt": "2024-05-08T10:30:30Z",
  "processedAt": "2024-05-08T10:30:45Z",
  "failureReason": null
}
```

**Error Response** (404 Not Found):

```json
{
  "error": "Transaction not found",
  "transactionId": "UNKNOWN-TXN"
}
```

### Get Transaction History

**Endpoint**: `GET /api/v1/transactions`

**Query Parameters**:

- `offset`: Starting position (default: 0)
- `limit`: Number of transactions to return (default: 100, max: 500)

**Response** (200 OK):

```json
{
  "transactions": [
    {
      "transactionId": "TXN-2024-001",
      "status": "COMPLETED",
      "amount": 100.0,
      "currency": "USD",
      "createdAt": "2024-05-08T10:30:30Z",
      "processedAt": "2024-05-08T10:30:45Z"
    },
    {
      "transactionId": "TXN-2024-002",
      "status": "FAILED",
      "amount": 50.0,
      "currency": "USD",
      "createdAt": "2024-05-08T10:31:00Z",
      "processedAt": "2024-05-08T10:31:05Z"
    }
  ],
  "totalCount": 150,
  "pageCount": 2
}
```

### Get Single Transaction

**Endpoint**: `GET /api/v1/transactions/{transactionId}`

**Response** (200 OK):

```json
{
  "transactionId": "TXN-2024-001",
  "status": "COMPLETED",
  "amount": 100.0,
  "currency": "USD",
  "clientReference": "CLIENT-REF-123",
  "createdAt": "2024-05-08T10:30:30Z",
  "processedAt": "2024-05-08T10:30:45Z",
  "failureReason": null
}
```

### Health Check

**Endpoint**: `GET /health`

**Response** (200 OK):

```json
{
  "status": "UP"
}
```

## Transaction States

Transactions flow through the following lifecycle:

1. **PENDING**: Transaction created, awaiting processing
2. **PROCESSING**: Currently being processed
3. **COMPLETED**: Successfully processed
4. **FAILED**: Processing failed

## Failure Scenarios

The Mock API simulates 5 realistic failure types, each with 2% probability when a failure occurs:

| Failure Type        | HTTP Status | Description                                |
| ------------------- | ----------- | ------------------------------------------ |
| VALIDATION_ERROR    | 400         | Bad Request - Invalid input                |
| TIMEOUT             | 504         | Gateway Timeout - Processing took too long |
| RATE_LIMITED        | 429         | Too Many Requests - Rate limit exceeded    |
| INTERNAL_ERROR      | 500         | Internal Server Error                      |
| SERVICE_UNAVAILABLE | 503         | Service Unavailable - Temporary outage     |

## Project Structure

```
mock-payment-api/
├── src/main/java/com/payment/mock/
│   ├── MockPaymentApiApplication.java      # Spring Boot entry point
│   ├── config/
│   │   ├── VirtualThreadConfig.java        # Virtual thread executor setup
│   │   └── DatabaseConfig.java             # H2 configuration
│   ├── controller/
│   │   ├── PaymentController.java          # Payment processing endpoints
│   │   └── TransactionController.java      # Transaction query endpoints
│   ├── service/
│   │   ├── MockPaymentService.java         # Core payment processing logic
│   │   ├── TransactionLookupService.java   # Transaction queries
│   │   ├── DelaySimulator.java             # Configurable delay simulation
│   │   └── FailureSimulator.java           # Realistic failure injection
│   ├── entity/
│   │   ├── Transaction.java                # JPA persistence model
│   │   └── TransactionStatus.java          # Status enum
│   ├── repository/
│   │   └── TransactionRepository.java      # Spring Data JPA
│   └── exception/
│       ├── TransactionNotFoundException.java # 404 error
│       └── MockApiExceptionHandler.java     # Global error handling
├── src/main/resources/
│   ├── application.yml                     # Production config
│   └── application-test.yml                # Test config
└── src/test/java/com/payment/mock/
    ├── service/
    │   ├── MockPaymentServiceTest.java     # Unit tests
    │   ├── FailureSimulatorTest.java       # Failure distribution tests
    │   └── DelaySimulatorTest.java         # Delay accuracy tests
    ├── integration/
    │   ├── PaymentFlowTest.java            # E2E payment scenarios
    │   ├── FailureDistributionTest.java    # Failure rate validation
    │   ├── TransactionHistoryTest.java     # Pagination & querying
    │   └── PersistenceTest.java            # H2 persistence validation
    └── load/
        ├── LoadTest.java                   # Concurrent load testing
        ├── ConcurrencyTest.java            # High-concurrency scenarios
        └── FailureDistributionStatTest.java # Statistical validation
```

## Testing

### Unit Tests (9 tests)

Test business logic in isolation with mocked dependencies:

```bash
mvn test -Dtest="MockPaymentServiceTest,FailureSimulatorTest,DelaySimulatorTest"
```

**Coverage**:

- MockPaymentService: Duplicate detection, delay inclusion, status transitions
- FailureSimulator: ~10% failure rate, realistic failure types
- DelaySimulator: Configurable delays (1-100ms test, 10-2000ms production)

### Integration Tests (24 tests)

Test full HTTP request/response flow with H2 persistence:

```bash
# Note: May encounter Spring context caching in Maven; run in IDE or with workaround
mvn verify -Dtest="*Test" -pl mock-payment-api
```

**Test Classes**:

- PaymentFlowTest: Full payment lifecycle, idempotency, delay honoring
- FailureDistributionTest: 90% success rate validation, failure reasons
- TransactionHistoryTest: Pagination, sorting, limit enforcement
- PersistenceTest: H2 persistence, concurrent transactions

### Load Tests (8 tests)

Test performance under sustained concurrent load:

```bash
# 100 concurrent requests with delays
mvn test -Dtest="LoadTest"

# 1000 concurrent requests (50 threads × 20 requests)
mvn test -Dtest="ConcurrencyTest"

# Statistical validation over large sample
mvn test -Dtest="FailureDistributionStatTest"
```

**Scenarios**:

- 100 concurrent requests with actual delays
- 50 threads × 20 requests (1000 total) with data integrity validation
- 1000+ request statistical distribution (90% ±2% success rate)
- 500+ request performance testing (>10 req/s throughput)

## Virtual Threads (Project Loom)

This service demonstrates Java 21 virtual threads for efficient concurrent processing:

```java
// Virtual thread executor configuration (see VirtualThreadConfig.java)
@Bean
public Executor asyncExecutor() {
    return Executors.newVirtualThreadPerTaskExecutor();
}

// Usage in services - no explicit thread pooling needed
// Each request gets its own lightweight virtual thread
// Can handle 1000s of concurrent requests without exhaustion
```

**Benefits**:

- Simplified concurrency model - no need to manage thread pools
- Better resource utilization - can spawn millions of virtual threads
- Simpler debugging - stack traces remain readable
- Natural exception handling - errors don't break thread pools

## Performance Characteristics

### Throughput

- Production: 10-100 requests/second (depending on configured delays)
- Test: 100-500 requests/second (with 1-100ms delays)
- Load test validated: >10 requests/second under sustained load

### Response Time

- 90th percentile: Configured min-delay + processing
- 99th percentile: Configured max-delay + processing
- Production: ~15-2030ms typical
- Test: ~2-150ms typical

### Concurrency

- Tested up to 1000 concurrent requests
- No thread exhaustion errors
- H2 consistency maintained across concurrent access
- Virtual threads ensure optimal resource utilization

## Known Limitations

1. **In-Memory Database**: Data does not persist between restarts
2. **Single Instance**: No clustering or multi-instance deployment
3. **Mock Data**: Simulated failures may not match all real-world scenarios
4. **Performance**: Virtual thread overhead minimal but not zero-cost
5. **Error Types**: Only 5 failure scenarios implemented

## Troubleshooting

### High CPU Usage

- Reduce concurrent request load
- Increase min-delay-ms to reduce busy-waiting
- Check transaction history size (may grow large)

### Out of Memory

- H2 in-memory database grows with transaction history
- Configure periodic cleanup or restart service
- Monitor transaction count with GET /api/v1/transactions

### Port Already in Use

- Default port is 8081
- Change with: `-Dserver.port=8082`
- Or in application.yml: `server: port: 8082`

### Transactions Not Persisting

- Verify H2 configuration in application.yml
- Check database-platform: `org.hibernate.dialect.H2Dialect`
- Ensure ddl-auto is set correctly (usually `create-drop` for tests)

## Integration with Payment Process Server

To use this Mock API with the Payment Process Server:

```yaml
# In payment-bridge/application.yml
payment:
  api:
    base-url: http://localhost:8081

external:
  api:
    connect:
      timeout: 5s
    read:
      timeout: 2s
```

Then launch both services:

```bash
# Terminal 1: Mock API
cd mock-payment-api
mvn spring-boot:run -Dspring.profiles.active=test

# Terminal 2: Payment Bridge
cd payment-bridge
mvn spring-boot:run

# Terminal 3: Send payment
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"amount":100,"currency":"USD"}'
```

## License

Part of the Payment System specification and Spec Kit tutorial.

## Support

For issues or questions, refer to:

- Spec Kit documentation: `specs/mock-payment-api/`
- API documentation: `ENDPOINTS.md`
- Testing guide: `TESTING.md`
- Operations guide: `OPERATIONS.md`
