# External Mock Payment API Contract

**Contract ID**: API-EXT-001
**Version**: 1.0.0
**Date**: 2026-05-07

## Overview

The External Mock Payment API simulates a third-party payment processor with intentional failures and variable latency. This API is designed to test the resilience mechanisms of the Payment Bridge, implementing Principles 3, 4, 5, and 6 (Hybrid Retry, DLQ Governance, and Failure Transparency).

## Endpoint Specification

### POST /api/v1/process-payment

**Purpose**: Process a payment with simulated failures and latency.

**HTTP Method**: POST
**Content-Type**: application/json
**Base URL**: Configurable (e.g., http://mock-payment-api:8080)

#### Request Headers

| Header       | Required | Description               | Example                                  |
| ------------ | -------- | ------------------------- | ---------------------------------------- |
| Content-Type | Yes      | Must be application/json  | application/json                         |
| X-API-Key    | Yes      | Mock API authentication   | mock_api_key_123                         |
| X-Request-ID | Yes      | Unique request identifier | req_550e8400-e29b-41d4-a716-446655440000 |

#### Request Body Schema

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["paymentId", "amount", "currency"],
  "properties": {
    "paymentId": {
      "type": "string",
      "format": "uuid",
      "description": "Unique payment identifier from Payment Bridge"
    },
    "amount": {
      "type": "number",
      "minimum": 0.01,
      "maximum": 1000000.0,
      "description": "Payment amount in decimal format"
    },
    "currency": {
      "type": "string",
      "pattern": "^[A-Z]{3}$",
      "description": "ISO 4217 currency code"
    },
    "metadata": {
      "type": "object",
      "description": "Optional payment metadata",
      "additionalProperties": true
    }
  }
}
```

#### Request Example

```http
POST /api/v1/process-payment
Content-Type: application/json
X-API-Key: mock_api_key_123
X-Request-ID: req_550e8400-e29b-41d4-a716-446655440000

{
  "paymentId": "550e8400-e29b-41d4-a716-446655440000",
  "amount": 99.99,
  "currency": "USD",
  "metadata": {
    "customer_id": "cust_123",
    "order_type": "subscription"
  }
}
```

## Response Specifications

### Success Responses

#### 200 OK (Payment Processed)

**Description**: Payment successfully processed.

**Response Body:**

```json
{
  "transactionId": "txn_mock_1234567890",
  "status": "SUCCESS",
  "processedAt": "2026-05-07T19:37:41Z",
  "fee": 2.99,
  "metadata": {
    "processing_time_ms": 150,
    "mock_simulation": true
  }
}
```

#### 201 Created (Payment Scheduled)

**Description**: Payment accepted for asynchronous processing.

**Response Body:**

```json
{
  "transactionId": "txn_mock_scheduled_1234567890",
  "status": "SCHEDULED",
  "estimatedCompletion": "2026-05-07T19:38:11Z",
  "metadata": {
    "async_processing": true,
    "mock_simulation": true
  }
}
```

### Error Responses

#### 400 Bad Request (Invalid Request)

**Description**: Request validation failed. **Immediate DLQ** - no retry.

**Response Body:**

```json
{
  "error": "INVALID_REQUEST",
  "message": "Payment request validation failed",
  "details": [
    {
      "field": "amount",
      "message": "Amount must be positive"
    }
  ],
  "requestId": "req_550e8400-e29b-41d4-a716-446655440000"
}
```

#### 401 Unauthorized (Invalid API Key)

**Description**: Authentication failed. **Immediate DLQ** - no retry.

**Response Body:**

```json
{
  "error": "UNAUTHORIZED",
  "message": "Invalid API key",
  "requestId": "req_550e8400-e29b-41d4-a716-446655440000"
}
```

#### 429 Too Many Requests (Rate Limited)

**Description**: Request rate exceeded. **Retry with exponential backoff**.

**Response Headers:**

```
Retry-After: 30
```

**Response Body:**

```json
{
  "error": "RATE_LIMITED",
  "message": "Too many requests",
  "retryAfter": 30,
  "requestId": "req_550e8400-e29b-41d4-a716-446655440000"
}
```

#### 500 Internal Server Error (Processing Failed)

**Description**: Internal processing error. **Retry with exponential backoff**.

**Response Body:**

```json
{
  "error": "PROCESSING_ERROR",
  "message": "Payment processing failed due to internal error",
  "retryable": true,
  "requestId": "req_550e8400-e29b-41d4-a716-446655440000"
}
```

#### 502 Bad Gateway (Upstream Failure)

**Description**: External service unavailable. **Retry with exponential backoff**.

**Response Body:**

```json
{
  "error": "UPSTREAM_ERROR",
  "message": "External payment processor unavailable",
  "retryable": true,
  "requestId": "req_550e8400-e29b-41d4-a716-446655440000"
}
```

#### 503 Service Unavailable (Maintenance)

**Description**: Service temporarily unavailable. **Retry with exponential backoff**.

**Response Body:**

```json
{
  "error": "SERVICE_UNAVAILABLE",
  "message": "Service is temporarily unavailable for maintenance",
  "retryAfter": 300,
  "retryable": true,
  "requestId": "req_550e8400-e29b-41d4-a716-446655440000"
}
```

#### 504 Gateway Timeout (Timeout)

**Description**: Request timed out. **Retry with exponential backoff**.

**Response Body:**

```json
{
  "error": "TIMEOUT",
  "message": "Request timed out",
  "timeoutMs": 2000,
  "retryable": true,
  "requestId": "req_550e8400-e29b-41d4-a716-446655440000"
}
```

## Failure Simulation Behavior

### Latency Distribution

The mock API intentionally introduces variable latency to test the Payment Bridge's latency tolerance (Principle 6):

- **Fast responses**: 10-500ms (70% of requests)
- **Medium responses**: 500ms-1.5s (20% of requests)
- **Slow responses**: 1.5s-2s (8% of requests)
- **Timeouts**: >2s (2% of requests - triggers 504)

### Failure Patterns

**Success Rate**: 85% of requests succeed (200/201 responses)
**Failure Distribution**:

- 400 Bad Request: 5% (client errors - immediate DLQ)
- 401 Unauthorized: 1% (auth errors - immediate DLQ)
- 429 Rate Limited: 3% (retryable)
- 500 Internal Error: 4% (retryable)
- 502 Bad Gateway: 1% (retryable)
- 503 Unavailable: 0.5% (retryable)
- 504 Timeout: 0.5% (retryable)

### Temporal Patterns

**Failure Bursts**: Every 5-10 minutes, failure rate spikes to 50% for 30-60 seconds
**Maintenance Windows**: Random 503 responses during "maintenance hours" (2-4 AM UTC)
**Rate Limiting**: Dynamic rate limits that trigger 429 responses during peak load

## Integration Requirements

### Circuit Breaker Configuration

The Payment Bridge must configure Resilience4j Circuit Breaker:

```yaml
resilience4j.circuitbreaker:
  instances:
    mock-payment-api:
      failure-rate-threshold: 50
      slow-call-rate-threshold: 50
      slow-call-duration-threshold: 2s
      wait-duration-in-open-state: 30s
      permitted-number-of-calls-in-half-open-state: 3
      minimum-number-of-calls: 10
```

### Retry Configuration

**API-Side Retry (Principle 3)**:

- Max attempts: 5
- Backoff: Base 1.5 exponential (0.5s, 1.25s, 2.25s, 3.375s, 4.75s)
- Total window: ~12 seconds
- Retryable errors: 429, 500, 502, 503, 504
- Non-retryable errors: 400, 401, 403

### Timeout Configuration

**Request Timeouts**:

- Connect timeout: 5 seconds
- Read timeout: 2 seconds (matches latency tolerance)
- Total timeout: 10 seconds (allows for retries)

### Error Classification Logic

```java
public boolean isRetryable(HttpStatusCode statusCode) {
    return switch (statusCode) {
        case TOO_MANY_REQUESTS,     // 429
             INTERNAL_SERVER_ERROR, // 500
             BAD_GATEWAY,          // 502
             SERVICE_UNAVAILABLE,   // 503
             GATEWAY_TIMEOUT       // 504
             -> true;
        case BAD_REQUEST,           // 400
             UNAUTHORIZED,          // 401
             FORBIDDEN              // 403
             -> false;
        default -> statusCode.is5xxServerError();
    };
}
```

## Testing Scenarios

### Resilience Testing

**Circuit Breaker Test**:

1. Simulate 50% failure rate for 60 seconds
2. Verify circuit breaker opens after threshold
3. Confirm fast-fail behavior during open state
4. Test half-open recovery after wait duration

**Retry Logic Test**:

1. Force 503 responses for first 4 attempts
2. Verify exponential backoff timing
3. Confirm success on 5th attempt
4. Test DLQ routing after 5 failed retries

**Timeout Test**:

1. Configure mock API to delay 2.5 seconds
2. Verify timeout triggers 504 response
3. Confirm retry logic handles timeout errors

### Load Testing

**Concurrent Request Test**:

1. Send 1000 concurrent requests
2. Verify latency distribution (P99 < 2s)
3. Check for thread exhaustion or blocking
4. Validate Virtual Thread efficiency

**Failure Burst Test**:

1. Normal operation (85% success)
2. Inject 50% failure rate for 60 seconds
3. Monitor circuit breaker behavior
4. Verify recovery after burst ends

## Monitoring & Observability

### Metrics to Collect

**API Integration Metrics**:

- Request rate and latency distribution
- Success/failure rates by status code
- Circuit breaker state transitions
- Retry attempt distribution

**Error Classification Metrics**:

- Count of retryable vs non-retryable errors
- DLQ insertion rate
- Recovery time after failures

### Log Aggregation

**Structured Logging**:

- All API requests with request ID and payment ID
- Circuit breaker state changes
- Retry attempts with backoff timing
- DLQ insertions with full context

**Critical Error Logging**:

- Circuit breaker open events
- DLQ insertions
- Timeout events
- Authentication failures

## Version Compatibility

### API Evolution

**Backward Compatibility**:

- New optional fields can be added to request/response
- Existing field types cannot change
- New error codes can be added (treated as retryable by default)

**Breaking Changes**:

- Require new API version in URL path
- Document migration timeline
- Maintain old version during transition period

### Failure Mode Evolution

**Failure Pattern Updates**:

- Success rate can be adjusted via configuration
- Latency distribution can be modified
- New error codes can be introduced
- All changes must be backward compatible with retry logic</content>
  <parameter name="filePath">/Users/mac/Programming/payment-system-speckit/specs/001-resilient-payment-bridge/contracts/external-mock-api.md
