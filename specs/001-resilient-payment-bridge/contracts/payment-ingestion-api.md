# Payment Ingestion API Contract

**Contract ID**: API-PAY-001
**Version**: 1.0.0
**Date**: 2026-05-07

## Overview

The Payment Ingestion API provides the entry point for payment requests into the Resilient Distributed Payment Bridge. This API implements Principle 1 (The Law of Idempotency) by ensuring payments are persisted to the database before any downstream processing.

## Endpoint Specification

### POST /api/v1/payments

**Purpose**: Accept and validate payment requests with guaranteed persistence.

**HTTP Method**: POST
**Content-Type**: application/json
**Authentication**: API Key (X-API-Key header)
**Idempotency**: Required (X-Idempotency-Key header)

#### Request Headers

| Header             | Required | Description                    | Example                                  |
| ------------------ | -------- | ------------------------------ | ---------------------------------------- |
| Content-Type       | Yes      | Must be application/json       | application/json                         |
| X-API-Key          | Yes      | API authentication key         | pk_live_1234567890                       |
| X-Idempotency-Key  | Yes      | Unique request identifier      | req_550e8400-e29b-41d4-a716-446655440000 |
| X-Client-Reference | No       | Optional client-side reference | order_12345                              |

#### Request Body Schema

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["amount", "currency"],
  "properties": {
    "amount": {
      "type": "number",
      "minimum": 0.01,
      "maximum": 1000000.0,
      "description": "Payment amount in decimal format"
    },
    "currency": {
      "type": "string",
      "pattern": "^[A-Z]{3}$",
      "description": "ISO 4217 currency code",
      "examples": ["USD", "EUR", "GBP"]
    },
    "clientReference": {
      "type": "string",
      "maxLength": 255,
      "description": "Optional client-side reference for duplicate detection"
    },
    "metadata": {
      "type": "object",
      "description": "Optional key-value metadata",
      "additionalProperties": {
        "type": "string"
      }
    }
  }
}
```

#### Request Examples

**Valid Request:**

```http
POST /api/v1/payments
Content-Type: application/json
X-API-Key: pk_live_1234567890
X-Idempotency-Key: req_550e8400-e29b-41d4-a716-446655440000

{
  "amount": 99.99,
  "currency": "USD",
  "clientReference": "order_12345",
  "metadata": {
    "customer_id": "cust_123",
    "order_type": "subscription"
  }
}
```

**Duplicate Request (Idempotency Test):**

```http
POST /api/v1/payments
X-Idempotency-Key: req_550e8400-e29b-41d4-a716-446655440000

{
  "amount": 99.99,
  "currency": "USD"
}
```

#### Response Specifications

##### 202 Accepted (Success)

**Description**: Payment request accepted and persisted. Processing continues asynchronously.

**Response Headers:**

```
Location: /api/v1/payments/status/550e8400-e29b-41d4-a716-446655440000
Content-Type: application/json
X-Payment-ID: 550e8400-e29b-41d4-a716-446655440000
```

**Response Body:**

```json
{
  "paymentId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "RECEIVED",
  "message": "Payment request accepted for processing",
  "estimatedProcessingTime": "PT30S",
  "_links": {
    "self": {
      "href": "/api/v1/payments/status/550e8400-e29b-41d4-a716-446655440000"
    },
    "status": {
      "href": "/api/v1/payments/status/550e8400-e29b-41d4-a716-446655440000"
    }
  }
}
```

##### 400 Bad Request (Validation Error)

**Description**: Request validation failed.

**Response Body:**

```json
{
  "error": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "details": [
    {
      "field": "amount",
      "message": "Amount must be greater than 0"
    },
    {
      "field": "currency",
      "message": "Currency must be a valid ISO 4217 code"
    }
  ]
}
```

##### 409 Conflict (Duplicate Idempotency Key)

**Description**: Idempotency key already used for a different request.

**Response Body:**

```json
{
  "error": "DUPLICATE_REQUEST",
  "message": "Idempotency key already used",
  "existingPaymentId": "550e8400-e29b-41d4-a716-446655440000",
  "_links": {
    "existing": {
      "href": "/api/v1/payments/status/550e8400-e29b-41d4-a716-446655440000"
    }
  }
}
```

##### 429 Too Many Requests (Rate Limited)

**Description**: Request rate exceeds configured limits.

**Response Headers:**

```
Retry-After: 60
```

**Response Body:**

```json
{
  "error": "RATE_LIMITED",
  "message": "Too many requests",
  "retryAfter": 60
}
```

##### 500 Internal Server Error (System Error)

**Description**: Unexpected system error occurred.

**Response Body:**

```json
{
  "error": "INTERNAL_ERROR",
  "message": "An unexpected error occurred",
  "requestId": "req_1234567890"
}
```

## Processing Guarantees

### Persistence Guarantee

- Payment record is committed to database before 202 response is sent
- System restart will not lose accepted payments
- Implements Principle 1: No-Loss Persistence

### Idempotency Guarantee

- Same idempotency key always returns same payment ID
- Duplicate requests are rejected, not processed twice
- Client references provide additional duplicate detection

### Asynchronous Processing

- Response indicates acceptance, not completion
- Processing continues in background via message queue
- Status can be polled via Location header

## Error Handling

### Validation Errors

- All input validation occurs before persistence
- Detailed error messages for client correction
- No partial processing or side effects

### System Errors

- Database connection failures return 500
- Message queue failures return 500
- All errors logged at CRITICAL level

### Recovery Behavior

- Failed requests can be safely retried with same idempotency key
- No duplicate processing due to idempotency guarantees
- System recovers automatically from transient failures

## Performance Characteristics

### Latency Targets

- P95 response time: <200ms
- P99 response time: <500ms
- Database commit time: <50ms

### Throughput Targets

- Sustained: 1000 requests/second
- Burst capacity: 2000 requests/second (30 seconds)

### Rate Limiting

- Per API key: 100 requests/second
- Global: 5000 requests/second
- Burst allowance: 200 requests

## Monitoring & Observability

### Metrics Collected

- Request rate by endpoint and API key
- Response time distribution (P50, P95, P99)
- Error rate by error type
- Idempotency hit rate
- Database commit latency

### Logs Generated

- All requests logged with payment ID and idempotency key
- Validation failures logged with detailed reasons
- System errors logged at CRITICAL level with full context
- Performance metrics logged every 30 seconds

## Testing Scenarios

### Happy Path

1. Valid request → 202 Accepted with payment ID
2. Payment persisted in RECEIVED state
3. Message queued for processing
4. Status endpoint returns RECEIVED

### Idempotency Test

1. Same idempotency key used twice → Second request returns 409
2. Both responses reference same payment ID
3. No duplicate processing occurs

### Validation Test

1. Invalid amount (< 0) → 400 Bad Request
2. Invalid currency → 400 Bad Request
3. Missing required fields → 400 Bad Request

### Error Recovery Test

1. Database temporarily unavailable → 500 Internal Server Error
2. Request can be safely retried with same idempotency key
3. No duplicate processing after recovery</content>
   <parameter name="filePath">/Users/mac/Programming/payment-system-speckit/specs/001-resilient-payment-bridge/contracts/payment-ingestion-api.md
