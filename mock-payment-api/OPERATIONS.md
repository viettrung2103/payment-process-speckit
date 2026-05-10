# Mock Payment API Operations Guide

This guide describes how to run, monitor, and operate the Mock Payment API in development and local environments.

## Starting the Service

### Run with Maven

```bash
cd mock-payment-api
mvn spring-boot:run
```

### Run with Test Profile

For faster iteration and lower latency:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=test"
```

### Custom Port

If port `8081` is already in use:

```bash
mvn spring-boot:run -Dserver.port=8082
```

### Docker (Future)

A Dockerfile is planned, but not yet implemented. For now, run locally with Maven.

## Service Health

### Health Endpoint

```bash
curl http://localhost:8081/health
```

Expected response:

```json
{
  "status": "UP"
}
```

### Health Check Usage

- Use for local readiness probes
- Integrate into build pipelines and CI checks
- Validate that the service is running before sending test traffic

## Logging

### Default Logging

- `application.yml` sets root logging to `INFO`
- `com.payment.mock` package is logged at `DEBUG` in production config
- `application-test.yml` lowers logging to `WARN`

### Viewing Logs

Logs are written to console by default. No file-based logging is configured unless added in Spring Boot config.

## Configuration

### Key Properties

```yaml
mock:
  api:
    min-delay-ms: 10
    max-delay-ms: 2000
    failure-rate: 0.1
```

### Test Profile Properties

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

### Recommended Overrides

- Use `test` profile for local development and test runs
- Use production profile for integration testing with realistic delays
- Adjust `failure-rate` for deterministic behavior when debugging

## Monitoring

### Endpoints to Monitor

- `GET /health` — service health
- `GET /api/v1/payments/status/{transactionId}` — transaction status
- `GET /api/v1/transactions` — transaction history

### Monitoring Considerations

- H2 is in-memory and not exposed by default
- Transaction history grows with every request
- Use pagination to inspect state without large response payloads

## Debugging

### Common Issues

#### 1. Service Not Starting

- Check that port 8081 is available
- Verify Java 21 is installed
- Confirm Maven build succeeded

#### 2. Endpoint 404

- Confirm the request path is `/api/v1/payments`
- Confirm the server is running on the expected port
- Verify the service started without errors

#### 3. Transaction Not Found

- Ensure transactionId matches exactly
- Confirm POST request actually created the transaction
- Check that the same transactionId is not being reused incorrectly

#### 4. High Latency

- The mock API deliberately introduces delay
- Use the `test` profile to reduce delay for local debugging
- Verify configuration values in `application.yml`

### Debugging Requests

```bash
curl -v -X POST http://localhost:8081/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{"transactionId":"DEBUG-001","amount":10.0,"currency":"USD"}'
```

### Debugging Database State

The H2 database is in-memory and not accessible externally by default. Use API endpoints to inspect persisted data.

## Running with Payment Process Server

### Configuration

In the Payment Process Server application config, set:

```yaml
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

### Startup Order

1. Start Mock Payment API
2. Start Payment Process Server
3. Submit payment requests to Payment Process Server

### Example

```bash
# Start mock API
cd mock-payment-api
mvn spring-boot:run -Dspring.profiles.active=test

# Start bridge
cd ../payment-bridge
mvn spring-boot:run
```

## Performance Operations

### Load Test Support

Use the built-in JUnit load tests to validate performance:

```bash
mvn test -Dtest=LoadTest
mvn test -Dtest=ConcurrencyTest
mvn test -Dtest=FailureDistributionStatTest
```

### Recommended Test Settings

- `LoadTest`: 100 concurrent requests
- `ConcurrencyTest`: 50 threads × 20 requests = 1000 total
- `FailureDistributionStatTest`: 1000 requests, 90% ±2% success rate

### Observing Throughput

- Monitor request completion time
- Use the printed logs from test classes for timing and throughput

## Maintenance

### Resetting State

The H2 database is reset each application restart.

To clear data between tests:

- Restart the application
- Or use `transactionRepository.deleteAll()` in test setup

### Updating Failure Rate

Adjust the `failure-rate` property in configuration:

```yaml
mock:
  api:
    failure-rate: 0.2
```

Then restart the service.

## Future Operations

- Add a Dockerfile for containerized execution
- Add `docker-compose.yml` to run both Mock API and Payment Process Server together
- Add metrics endpoints for request latency and throughput
- Add external database support for persistence beyond in-memory H2

## Contacts

For questions or enhancements:

- Review `README.md` for architecture and setup
- Review `TESTING.md` for verification strategies
- Review `ENDPOINTS.md` for API contract details
 
Or contact:

- viettrung21.work@gmail.com
