# Mock Payment API - Endpoints Reference

Complete documentation of all available endpoints in the Mock Payment API.

## Base URL

- **Production**: `http://localhost:8081`
- **Test**: `http://localhost:8081` (same, different delays)

## Endpoints Overview

| Method | Endpoint                                  | Purpose                           | Response                |
| ------ | ----------------------------------------- | --------------------------------- | ----------------------- |
| POST   | `/api/v1/payments`                        | Process a payment                 | 200 OK (with status)    |
| GET    | `/api/v1/payments/status/{transactionId}` | Get payment status                | 200 OK or 404 Not Found |
| GET    | `/api/v1/transactions`                    | List transactions with pagination | 200 OK                  |
| GET    | `/api/v1/transactions/{transactionId}`    | Get single transaction            | 200 OK or 404 Not Found |
| GET    | `/health`                                 | Health check                      | 200 OK                  |

---

## POST /api/v1/payments (or /api/v1/pay)

Process a new payment through the mock API.

### Request

**Method**: `POST`  
**Content-Type**: `application/json`  
**Authentication**: None

**Body**:

```json
{
  "transactionId": "string (required)",
  "amount": "number (required)",
  "currency": "string (required, 3-letter code)",
  "clientReference": "string (optional)"
}
```

### Request Parameters

| Parameter       | Type   | Required | Description                                                                                                                         |
| --------------- | ------ | -------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| transactionId   | string | Yes      | Unique transaction identifier. Must be unique across all requests. If duplicate, returns same result as first request (idempotent). |
| amount          | number | Yes      | Payment amount as decimal (e.g., 100.00, 99.99). Must be positive.                                                                  |
| currency        | string | Yes      | ISO 4217 currency code (USD, EUR, GBP, JPY, etc.).                                                                                  |
| clientReference | string | No       | Client-provided reference for tracking. Stored with transaction.                                                                    |

### Response

**Status Code**: `200 OK`  
**Content-Type**: `application/json`

**Success Body** (90% of requests):

```json
{
  "code": 200,
  "message": "OK",
  "transactionId": "TXN-2024-001",
  "amount": 100.0,
  "currency": "USD",
  "status": "COMPLETED",
  "clientReference": "CLIENT-123",
  "processedAt": "2024-05-08T10:30:45.123Z",
  "responseTimeMs": 145,
  "failureCode": null,
  "failureReason": null
}
```

**Failure Body** (10% of requests):

```json
{
  "code": 200,
  "message": "FAILED",
  "transactionId": "TXN-2024-002",
  "amount": 100.0,
  "currency": "USD",
  "status": "FAILED",
  "clientReference": "CLIENT-123",
  "processedAt": "2024-05-08T10:30:47.456Z",
  "responseTimeMs": 1234,
  "failureCode": "SERVICE_UNAVAILABLE",
  "failureReason": "simulated failure"
}
```

### Response Fields

| Field         | Type                | Description                                              |
| ------------- | ------------------- | -------------------------------------------------------- |
| transactionId | string              | Echo of the request transactionId                        |
| status        | string              | "COMPLETED" or "FAILED"                                  |
| amount        | number              | Echo of the request amount                               |
| currency      | string              | Echo of the request currency                             |
| processedAt   | datetime (ISO 8601) | Server timestamp of processing completion                |
| failureReason | string or null      | Reason for failure (only populated if status = "FAILED") |

### Response Status Codes

| Code | Meaning                                                     |
| ---- | ----------------------------------------------------------- |
| 200  | Successfully processed (payment may be completed or failed) |
| 400  | Bad Request (invalid JSON, missing fields, invalid types)   |
| 500  | Internal Server Error (unexpected condition)                |

### Processing Behavior

1. **Delay**: Server delays processing by 1-100ms (test) or 10-2000ms (production)
2. **Idempotency**: Same transactionId always returns same response
3. **Failure Rate**: Approximately 10% of requests fail
4. **Persistence**: All transactions stored in H2 database
5. **Async Simulation**: Delays simulate real payment processing

### Example Request

```bash
curl -X POST http://localhost:8081/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "TXN-2024-001",
    "amount": 99.99,
    "currency": "USD",
    "clientReference": "ORDER-12345"
  }'
```

### Example Response

```json
{
  "transactionId": "TXN-2024-001",
  "status": "COMPLETED",
  "amount": 99.99,
  "currency": "USD",
  "processedAt": "2024-05-08T10:30:45.789Z",
  "failureReason": null
}
```

---

## GET /api/v1/payments/status/{transactionId}

Retrieve the current status of a specific payment transaction.

### Request

**Method**: `GET`  
**URL Parameters**: `transactionId` (required, URL path)  
**Query Parameters**: None  
**Authentication**: None

### URL Parameters

| Parameter     | Type   | Description                                        |
| ------------- | ------ | -------------------------------------------------- |
| transactionId | string | Unique transaction identifier from payment request |

### Response

**Status Code**: `200 OK` or `404 Not Found`  
**Content-Type**: `application/json`

**Success Body** (200 OK):

```json
{
  "code": 200,
  "message": "OK",
  "transactionId": "TXN-2024-001",
  "amount": 100.0,
  "currency": "USD",
  "status": "COMPLETED",
  "clientReference": "ORDER-12345",
  "processedAt": "2024-05-08T10:30:45.789Z",
  "responseTimeMs": 145,
  "failureCode": null,
  "failureReason": null
}
```

**Not Found Body** (404 Not Found):

```json
{
  "error": "Transaction not found",
  "transactionId": "UNKNOWN-ID"
}
```

### Response Fields (200 OK)

| Field           | Type                | Description                                  |
| --------------- | ------------------- | -------------------------------------------- |
| code            | integer             | HTTP response code (200)                     |
| message         | string              | Response message: "OK" or "FAILED"           |
| transactionId   | string              | The queried transaction ID                   |
| status          | string              | Current status: COMPLETED or FAILED          |
| amount          | number              | Payment amount                               |
| currency        | string              | Currency code                                |
| clientReference | string or null      | Client reference if provided                 |
| processedAt     | datetime (ISO 8601) | When transaction was processed               |
| responseTimeMs  | integer             | Actual response time in milliseconds         |
| failureCode     | string or null      | Error code if status = FAILED                |
| failureReason   | string or null      | Reason for failure (only if status = FAILED) |

### Response Status Codes

| Code | Meaning                                |
| ---- | -------------------------------------- |
| 200  | Transaction found and details returned |
| 404  | Transaction with given ID not found    |
| 500  | Internal Server Error                  |

### Example Request

```bash
curl http://localhost:8081/api/v1/payments/status/TXN-2024-001
```

### Example Response

```json
{
  "code": 200,
  "message": "OK",
  "transactionId": "TXN-2024-001",
  "amount": 100.0,
  "currency": "USD",
  "status": "COMPLETED",
  "clientReference": "ORDER-12345",
  "processedAt": "2024-05-08T10:30:45.789Z",
  "responseTimeMs": 145,
  "failureCode": null,
  "failureReason": null
}
```

---

## GET /api/v1/transactions

Retrieve paginated transaction history with optional filtering and sorting.

### Request

**Method**: `GET`  
**Query Parameters**: `offset`, `limit` (both optional)  
**Authentication**: None

### Query Parameters

| Parameter | Type    | Default | Max | Description                                     |
| --------- | ------- | ------- | --- | ----------------------------------------------- |
| offset    | integer | 0       | -   | Number of transactions to skip (for pagination) |
| limit     | integer | 100     | 500 | Number of transactions to return                |

### Response

**Status Code**: `200 OK`  
**Content-Type**: `application/json`

**Success Body**:

```json
{
  "transactions": [
    {
      "transactionId": "TXN-2024-001",
      "status": "COMPLETED",
      "amount": 100.0,
      "currency": "USD",
      "clientReference": "ORDER-12345",
      "createdAt": "2024-05-08T10:30:30.100Z",
      "processedAt": "2024-05-08T10:30:45.789Z",
      "failureReason": null
    },
    {
      "transactionId": "TXN-2024-002",
      "status": "FAILED",
      "amount": 50.0,
      "currency": "EUR",
      "clientReference": "ORDER-12346",
      "createdAt": "2024-05-08T10:31:00.200Z",
      "processedAt": "2024-05-08T10:31:05.300Z",
      "failureReason": "Payment processing failed - Rate limit exceeded"
    }
  ],
  "totalCount": 150,
  "pageCount": 2
}
```

### Response Fields

| Field        | Type    | Description                                     |
| ------------ | ------- | ----------------------------------------------- |
| transactions | array   | Array of transaction objects                    |
| totalCount   | integer | Total number of transactions in database        |
| pageCount    | integer | Number of pages needed to view all transactions |

### Transaction Object Fields

| Field           | Type                | Description                                               |
| --------------- | ------------------- | --------------------------------------------------------- |
| transactionId   | string              | Unique transaction identifier                             |
| status          | string              | Current status: PENDING, PROCESSING, COMPLETED, or FAILED |
| amount          | number              | Payment amount                                            |
| currency        | string              | Currency code                                             |
| clientReference | string or null      | Client reference if provided                              |
| createdAt       | datetime (ISO 8601) | When transaction was created                              |
| processedAt     | datetime (ISO 8601) | When transaction was processed                            |
| failureReason   | string or null      | Reason for failure (only if status = FAILED)              |

### Response Status Codes

| Code | Meaning                             |
| ---- | ----------------------------------- |
| 200  | Transactions retrieved successfully |
| 400  | Invalid query parameters            |
| 500  | Internal Server Error               |

### Sorting

- Results sorted by `createdAt` in **descending order** (newest first)
- Use `offset` and `limit` to paginate through results

### Example Requests

```bash
# Get first 100 transactions (default)
curl "http://localhost:8081/api/v1/transactions"

# Get next 100 transactions (pagination)
curl "http://localhost:8081/api/v1/transactions?offset=100&limit=100"

# Get last 50 transactions
curl "http://localhost:8081/api/v1/transactions?offset=0&limit=50"

# Get transactions with custom pagination
curl "http://localhost:8081/api/v1/transactions?offset=200&limit=25"
```

### Example Response

```json
{
  "transactions": [
    {
      "transactionId": "TXN-2024-150",
      "status": "COMPLETED",
      "amount": 1500.0,
      "currency": "USD",
      "clientReference": "ORDER-99999",
      "createdAt": "2024-05-08T10:50:00.000Z",
      "processedAt": "2024-05-08T10:50:15.500Z",
      "failureReason": null
    }
  ],
  "totalCount": 150,
  "pageCount": 2
}
```

---

## GET /api/v1/transactions/{transactionId}

Retrieve details for a single transaction.

### Request

**Method**: `GET`  
**URL Parameters**: `transactionId` (required)  
**Authentication**: None

### URL Parameters

| Parameter     | Type   | Description                   |
| ------------- | ------ | ----------------------------- |
| transactionId | string | Unique transaction identifier |

### Response

**Status Code**: `200 OK` or `404 Not Found`  
**Content-Type**: `application/json`

**Success Body** (200 OK):

```json
{
  "transactionId": "TXN-2024-001",
  "status": "COMPLETED",
  "amount": 100.0,
  "currency": "USD",
  "clientReference": "ORDER-12345",
  "createdAt": "2024-05-08T10:30:30.100Z",
  "processedAt": "2024-05-08T10:30:45.789Z",
  "failureReason": null
}
```

**Not Found Body** (404 Not Found):

```json
{
  "error": "Transaction not found",
  "transactionId": "UNKNOWN-ID"
}
```

### Response Status Codes

| Code | Meaning               |
| ---- | --------------------- |
| 200  | Transaction found     |
| 404  | Transaction not found |
| 500  | Internal Server Error |

### Example Request

```bash
curl http://localhost:8081/api/v1/transactions/TXN-2024-001
```

---

## GET /health

Health check endpoint for monitoring service availability.

### Request

**Method**: `GET`  
**Query Parameters**: None  
**Authentication**: None

### Response

**Status Code**: `200 OK`  
**Content-Type**: `application/json`

**Success Body**:

```json
{
  "status": "UP"
}
```

### Response Status Codes

| Code | Meaning                        |
| ---- | ------------------------------ |
| 200  | Service is healthy and running |
| 503  | Service is down or unavailable |

### Example Request

```bash
curl http://localhost:8081/health
```

### Use Cases

- **Docker health checks**: Configure Docker to call this endpoint
- **Load balancer health checks**: Verify backend availability
- **Monitoring**: Periodically check service status
- **Startup verification**: Wait for health check to pass before sending traffic

---

## Error Responses

### Global Error Format

All error responses follow this format:

```json
{
  "error": "Human-readable error message",
  "timestamp": "2024-05-08T10:30:45.789Z",
  "status": "HTTP_STATUS_CODE"
}
```

### Common HTTP Status Codes

| Status | Code                  | Meaning                              |
| ------ | --------------------- | ------------------------------------ |
| 200    | OK                    | Request successful                   |
| 400    | Bad Request           | Invalid request format or parameters |
| 404    | Not Found             | Resource not found                   |
| 500    | Internal Server Error | Unexpected server error              |

### Specific Errors

#### 400 Bad Request

**Cause**: Invalid JSON, missing required fields, invalid field types

**Example Response**:

```json
{
  "error": "Invalid request body",
  "timestamp": "2024-05-08T10:30:45.789Z",
  "status": 400
}
```

#### 404 Not Found

**Cause**: Transaction ID not found in database

**Example Response**:

```json
{
  "error": "Transaction not found",
  "transactionId": "UNKNOWN-ID",
  "timestamp": "2024-05-08T10:30:45.789Z",
  "status": 404
}
```

#### 500 Internal Server Error

**Cause**: Unexpected server error (database connection, etc.)

**Example Response**:

```json
{
  "error": "An unexpected error occurred",
  "timestamp": "2024-05-08T10:30:45.789Z",
  "status": 500
}
```

---

## Rate Limiting & Throttling

**Current Implementation**: No explicit rate limiting

**Behavior**:

- Service can handle 100+ concurrent requests
- Delayed responses (1-100ms test, 10-2000ms production) naturally throttle
- Virtual threads efficiently manage concurrent connections

**Future Enhancement**: Consider implementing:

- Rate limiting per client IP
- Token bucket algorithm
- Per-endpoint rate limits

---

## Data Types

### ISO 8601 Datetime Format

All timestamps are in ISO 8601 format with UTC timezone:

```
2024-05-08T10:30:45.789Z
```

### Currency Codes

3-letter ISO 4217 codes:

- USD (US Dollar)
- EUR (Euro)
- GBP (British Pound)
- JPY (Japanese Yen)
- CAD (Canadian Dollar)
- AUD (Australian Dollar)
- etc.

### Amount Format

Decimal numbers with up to 2 decimal places:

- 100.00
- 99.99
- 1000.50
- 0.01 (minimum)

### Transaction Status

- **PENDING**: Created but not yet processed
- **PROCESSING**: Currently being processed
- **COMPLETED**: Successfully processed
- **FAILED**: Processing failed

### Failure Reasons

Common failure reasons:

- "Payment processing failed - Service unavailable"
- "Payment processing failed - Rate limit exceeded"
- "Payment processing failed - Timeout"
- "Payment processing failed - Validation error"
- "Payment processing failed - Internal error"

---

## Testing the Endpoints

### Using cURL

```bash
# Process payment
curl -X POST http://localhost:8081/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"transactionId":"TEST-001","amount":100.00,"currency":"USD"}'

# Check status
curl http://localhost:8081/api/v1/payments/status/TEST-001

# List transactions
curl "http://localhost:8081/api/v1/transactions?offset=0&limit=10"

# Get single transaction
curl http://localhost:8081/api/v1/transactions/TEST-001

# Health check
curl http://localhost:8081/health
```

### Using Postman

1. Create new requests for each endpoint
2. Set method (POST/GET)
3. Copy URL from above examples
4. For POST /api/v1/payments:
   - Set Body → raw → JSON
   - Paste request body from examples
5. Send request and view response

### Using Java (RestTemplate)

```java
RestTemplate restTemplate = new RestTemplate();

// Process payment
Map<String, Object> request = new HashMap<>();
request.put("transactionId", "TXN-001");
request.put("amount", 100.00);
request.put("currency", "USD");

ResponseEntity<Map> response = restTemplate.postForEntity(
    "http://localhost:8081/api/v1/payments",
    request,
    Map.class
);
```

---

## Performance Considerations

### Request Timeout

- Recommended client timeout: 3-5 seconds
- Processing delay: 1-100ms (test) or 10-2000ms (production)
- Total response time: processing delay + network latency

### Throughput

- Production: 10-100 requests/second (depending on configured delays)
- Test: 100-500 requests/second
- Concurrent limit: 1000+ requests (limited by database and memory)

### Pagination

- Default limit: 100 transactions
- Maximum limit: 500 transactions
- Total history: Limited by available memory

---

## API Versioning

Current API version: **v1** (in URL path `/api/v1/`)

**Future versions** may be released at `/api/v2/`, `/api/v3/`, etc.

---

## Support

For issues or questions about these endpoints:

- See README.md for general information
- See TESTING.md for test scenarios
- See OPERATIONS.md for deployment info
