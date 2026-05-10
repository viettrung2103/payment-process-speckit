# Mock Payment API Specification

**Component**: Mock Payment API (Testing & Development Support)  
**Feature ID**: F-MOCK-API-001  
**Status**: Specification Phase  
**Created**: 2026-05-07  
**Technology Stack**: Java 21 / Spring Boot 3.4, H2 In-Memory Database, Project Loom (Virtual Threads)

---

## Objective and Scope

### Objective

Build a lightweight, realistic mock Payment API server for integration testing and local development of the Payment Process Server. The mock API simulates real-world payment processing conditions including variable latency, realistic error scenarios, and transaction persistence.

### Scope

- Realistic payment processing endpoint with simulated success/failure scenarios
- Transaction history and status lookup endpoints
- Configurable response delays (10ms - 2000ms)
- H2 in-memory transaction persistence for recovery testing
- Virtual Threads for handling concurrent requests with delays
- API response formats matching production external API expectations

**Out of Scope (Phase 1)**:

- Webhook support (deferred to later phases)
- Payment method tokenization or validation
- Recurring payment scenarios
- Multi-currency conversion logic
- PCI compliance auditing

---

## Functional Requirements

### FR-M001: Payment Processing Endpoint

- **Description**: Accept payment requests and respond with realistic success/failure distribution
- **Endpoint**: `POST /api/v1/pay`
- **Request Format**: 
  ```json
  {
    "amount": 100.50,
    "currency": "USD",
    "client_reference": "ORD-12345",
    "description": "Product purchase"
  }
  ```
- **Response Format (Success - 90% probability)**:
  ```json
  {
    "code": 200,
    "message": "Payment processed successfully",
    "transaction_id": "TXN-UUID-12345",
    "amount": 100.50,
    "currency": "USD",
    "status": "COMPLETED",
    "timestamp": "2026-05-07T23:31:49Z",
    "response_time_ms": 1250
  }
  ```
- **Response Format (Failure)**:
  ```json
  {
    "code": 400 | 429 | 500 | 503 | 504,
    "message": "Specific error message",
    "error_code": "VALIDATION_ERROR" | "RATE_LIMITED" | "TIMEOUT" | "SERVICE_UNAVAILABLE",
    "transaction_id": null | "TXN-UUID-partial"
  }
  ```
- **Failure Distribution**:
  - **90%**: Success (HTTP 200, status: COMPLETED)
  - **10%**: Failures distributed as:
    - **2%**: Timeout (HTTP 504, "Gateway Timeout")
    - **2%**: Validation Error (HTTP 400, "Invalid request format")
    - **2%**: Rate Limited (HTTP 429, "Too many requests")
    - **2%**: Internal Server Error (HTTP 500, "Internal processing error")
    - **2%**: Service Unavailable (HTTP 503, "Service temporarily unavailable")

### FR-M002: Transaction Status Lookup

- **Description**: Query previously processed transactions by transaction_id
- **Endpoint**: `GET /api/v1/transactions/{transaction_id}`
- **Response Format**:
  ```json
  {
    "transaction_id": "TXN-UUID-12345",
    "amount": 100.50,
    "currency": "USD",
    "client_reference": "ORD-12345",
    "status": "COMPLETED" | "FAILED",
    "created_at": "2026-05-07T23:31:49Z",
    "completed_at": "2026-05-07T23:31:50Z",
    "failure_reason": null | "Validation error message"
  }
  ```
- **Acceptance**: All completed/failed transactions must be retrievable for 30+ minutes

### FR-M003: Transaction History Endpoint

- **Description**: List all transactions processed during current session
- **Endpoint**: `GET /api/v1/transactions?limit=100&offset=0`
- **Response Format**:
  ```json
  {
    "total": 150,
    "limit": 100,
    "offset": 0,
    "transactions": [
      { /* transaction objects */ }
    ]
  }
  ```

### FR-M004: Health Check Endpoint

- **Description**: Simple health check for load balancer / monitoring
- **Endpoint**: `GET /health`
- **Response Format**: `{ "status": "UP", "timestamp": "2026-05-07T23:31:49Z" }`

### FR-M005: Configurable Response Delays

- **Description**: Control API response latency via configuration
- **Configuration Parameters**:
  - `mock.api.min-delay-ms`: Minimum delay (default: 10)
  - `mock.api.max-delay-ms`: Maximum delay (default: 2000)
  - `mock.api.failure-rate`: Failure probability 0-1.0 (default: 0.1)
- **Implementation**: Delay applied AFTER processing, before response sent
- **Purpose**: Simulate real-world API latency conditions

### FR-M006: Persistent Transaction Store

- **Description**: Store all transactions in H2 memory database during runtime
- **Data Retained**: All transactions since last application startup
- **Use Case**: Recovery testing - Payment Process Server can query completed transactions after restart
- **Atomicity**: Transactions written immediately after processing decision

---

## Non-Functional Requirements

### NFR-M001: Concurrency Under Load

- **Description**: Handle 100+ concurrent requests with 2s delays using Virtual Threads
- **Acceptance**: No blocked threads; CPU utilization remains low (< 50%)
- **Technology**: Project Loom virtual threads for efficient I/O-bound delays
- **Related Principle**: Latency transparency without thread exhaustion

### NFR-M002: Response Time Variability

- **Description**: Simulate realistic latency distribution
- **Range**: 10ms - 2000ms (configurable)
- **Distribution**: Uniform random distribution
- **Rationale**: Mirrors production payment gateway behavior

### NFR-M003: Realistic Failure Scenarios

- **Description**: Distribute failures realistically across HTTP status codes
- **90% Success**: COMPLETED transactions
- **2% Timeout**: Simulates network timeouts (HTTP 504)
- **2% Validation**: Bad request errors (HTTP 400)
- **2% Rate Limited**: Throttling simulation (HTTP 429)
- **2% Server Errors**: Transient errors (HTTP 500, 503)

### NFR-M004: Transaction Persistence

- **Description**: In-memory H2 database retains transactions for duration of application lifetime
- **Acceptance**: Transactions retrievable via status lookup endpoint
- **Data Loss**: Expected on application restart (test/dev only - no persistence to disk)

### NFR-M005: Observability

- **Description**: Log all transactions with timing and outcome
- **Log Format**: `[TRANSACTION] TXN-UUID | client_reference | amount | status | response_time_ms`
- **Purpose**: Debugging and load test analysis

---

## Data Model

### Transaction Entity

```
transaction_id: String (UUID, PRIMARY KEY)
client_reference: String (optional, for matching with Payment Process Server)
amount: BigDecimal
currency: String (ISO 4217)
status: Enum (COMPLETED, FAILED)
failure_code: String (nullable - error code if failed)
failure_reason: String (nullable - error message if failed)
created_at: Timestamp
completed_at: Timestamp
response_time_ms: Long
```

### Transaction Table Schema

```sql
CREATE TABLE mock_transaction (
    transaction_id VARCHAR(36) PRIMARY KEY,
    client_reference VARCHAR(255),
    amount DECIMAL(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    failure_code VARCHAR(50),
    failure_reason TEXT,
    response_time_ms BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NOT NULL
);
```

---

## API Contract Examples

### Success Scenario (90%)

**Request**:
```
POST /api/v1/pay HTTP/1.1
Content-Type: application/json

{
  "amount": 100.00,
  "currency": "USD",
  "client_reference": "ORD-2026-001",
  "description": "Electronics purchase"
}
```

**Response** (HTTP 200):
```json
{
  "code": 200,
  "message": "Payment processed successfully",
  "transaction_id": "TXN-550e8400-e29b-41d4-a716-446655440000",
  "amount": 100.00,
  "currency": "USD",
  "status": "COMPLETED",
  "timestamp": "2026-05-07T23:35:10Z",
  "response_time_ms": 1542
}
```

### Validation Error Scenario (2%)

**Request**: (with invalid amount)
```json
{
  "amount": -10.00,
  "currency": "INVALID",
  "client_reference": ""
}
```

**Response** (HTTP 400):
```json
{
  "code": 400,
  "message": "Invalid payment request",
  "error_code": "VALIDATION_ERROR",
  "transaction_id": null
}
```

### Timeout Scenario (2%)

**Response** (HTTP 504):
```json
{
  "code": 504,
  "message": "Payment processing timeout",
  "error_code": "TIMEOUT",
  "transaction_id": null
}
```

### Rate Limit Scenario (2%)

**Response** (HTTP 429):
```json
{
  "code": 429,
  "message": "Too many requests, please retry after 60 seconds",
  "error_code": "RATE_LIMITED",
  "retry_after": 60
}
```

---

## Technical Stack

### Technology Choices

| Component | Technology | Reason |
|-----------|-----------|--------|
| Runtime | Java 21 | Matched with Payment Process Server |
| Framework | Spring Boot 3.4 | Same as main application |
| Database | H2 In-Memory | Zero setup, test-friendly, auto-clears on restart |
| Concurrency | Virtual Threads (Project Loom) | Handle 100+ concurrent connections with delays without thread exhaustion |
| HTTP Server | Embedded Tomcat | Spring Boot default, lightweight |
| JSON Serialization | Jackson | Spring Boot default |

### Key Architectural Decisions

1. **In-Memory H2**: Transient by design - transactions lost on restart
   - Pro: Zero external dependencies, fast setup
   - Pro: Perfect for dev/test environments
   - Pro: Supports recovery testing scenarios
   - Con: Not suitable for production (expected)

2. **Virtual Threads**: Efficient handling of high concurrency with delays
   - Pro: 1000s of concurrent connections per thread
   - Pro: Simplified async programming (no callbacks)
   - Pro: Native Java 21 feature

3. **Failure Distribution**: Realistic randomness
   - Pro: Exposes edge cases in Payment Process Server
   - Pro: Tests retry logic, circuit breaker, error classification

---

## Integration Points with Payment Process Server

### Request Flow

```
Payment Process Server
    ↓
    │ ExternalApiClient calls configured payment.api.base-url
    ↓
Mock Payment API: POST /api/v1/pay
    ↓
    │ Process payment with 10ms-2s delay
    │ Randomly determine success/failure
    │ Store transaction in H2
    ↓
Mock Payment API returns response
    ↓
    │ ExternalApiClient handles response
    │ ErrorClassifier categorizes result
    │ PaymentWorker retries or escalates
    ↓
Payment Process Server updates payment status
```

### Configuration Integration

**Payment Process Server** (`application.yml`):
```yaml
payment:
  api:
    base-url: http://localhost:8081  # Points to Mock Payment API
    timeout:
      connect: 5000
      read: 2000
```

**Mock Payment API** (`application.yml`):
```yaml
server:
  port: 8081
mock:
  api:
    min-delay-ms: 10
    max-delay-ms: 2000
    failure-rate: 0.1
```

---

## Success Criteria

### Functional Validation

1. ✅ Payment endpoint processes requests with correct success/failure distribution
2. ✅ Response formats match contract specifications exactly
3. ✅ Transactions are queryable via status lookup endpoint
4. ✅ Transaction history endpoint returns paginated results
5. ✅ Configurable delays are honored (10ms - 2000ms range)

### Non-Functional Validation

1. ✅ Handles 100+ concurrent requests without blocking
2. ✅ Failure distribution (90/2/2/2/2) is accurate
3. ✅ Transactions persist in H2 for 30+ minutes per session
4. ✅ Response times reflect configured delay (±50ms tolerance)
5. ✅ All transactions logged with timing information

### Integration Validation

1. ✅ Payment Process Server successfully calls Mock API
2. ✅ ExternalApiClient correctly handles all response types
3. ✅ ErrorClassifier properly categorizes mock failures
4. ✅ Retry logic activates for retryable failures (5xx, timeout)
5. ✅ DLQ escalation works for non-retryable failures (4xx)

---

## Next Steps

1. **Project Setup**: Create Maven module with Spring Boot 3.4 starter
2. **Core Implementation**: Transaction endpoint, H2 entity, business logic
3. **Integration**: Configure as separate application, test with Payment Process Server
4. **Load Testing**: Verify concurrent handling and failure distribution
5. **Documentation**: Create operational runbook for local development

