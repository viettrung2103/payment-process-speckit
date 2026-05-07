# Phase 1 Design: Quickstart Guide

**Feature**: F-PAY-001 Resilient Distributed Payment Bridge
**Date**: 2026-05-07
**Status**: Design Phase Complete

## Overview

This quickstart guide provides step-by-step instructions to set up a local development environment for the Resilient Distributed Payment Bridge. The setup includes PostgreSQL, RabbitMQ, and the mock payment API for end-to-end testing.

## Prerequisites

### System Requirements

- **Java**: 21+ (Virtual Threads support)
- **Docker**: 24+ (for containerized services)
- **Docker Compose**: 2.0+ (for orchestration)
- **Git**: 2.30+ (for repository management)
- **curl**: For API testing
- **jq**: For JSON response formatting (optional)

### Hardware Requirements

- **RAM**: 4GB minimum, 8GB recommended
- **CPU**: 2 cores minimum, 4 cores recommended
- **Disk**: 10GB free space

## Quick Setup (5 minutes)

### 1. Clone and Navigate

```bash
git clone <repository-url>
cd payment-system-speckit
git checkout 001-resilient-payment-bridge
```

### 2. Start Infrastructure

```bash
# Start PostgreSQL, RabbitMQ, and Mock API
docker-compose -f docker-compose.dev.yml up -d

# Wait for services to be healthy
docker-compose -f docker-compose.dev.yml ps
```

**Expected Output:**
```
NAME                          COMMAND                  SERVICE             STATUS              PORTS
payment-bridge-postgres-1     "docker-entrypoint.s…"   postgres            running             0.0.0.0:5432->5432/tcp
payment-bridge-rabbitmq-1     "docker-entrypoint.s…"   rabbitmq            running             0.0.0.0:5672->5672/tcp, 0.0.0.0:15672->15672/tcp
payment-bridge-mock-api-1     "java -jar /app/mock…"   mock-api            running             0.0.0.0:8081->8080/tcp
```

### 3. Build Application

```bash
# Build the payment bridge application
./mvnw clean compile

# Or if using Gradle
./gradlew build
```

### 4. Run Application

```bash
# Start the payment bridge
./mvnw spring-boot:run

# Or with custom profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### 5. Verify Setup

```bash
# Check application health
curl http://localhost:8080/actuator/health

# Check RabbitMQ management UI
open http://localhost:15672
# Username: admin, Password: admin
```

## Detailed Setup

### Docker Compose Configuration

Create `docker-compose.dev.yml`:

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: payment_bridge
      POSTGRES_USER: payment_user
      POSTGRES_PASSWORD: payment_pass
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./src/main/resources/schema.sql:/docker-entrypoint-initdb.d/01-schema.sql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U payment_user -d payment_bridge"]
      interval: 10s
      timeout: 5s
      retries: 5

  rabbitmq:
    image: rabbitmq:3-management-alpine
    environment:
      RABBITMQ_DEFAULT_USER: admin
      RABBITMQ_DEFAULT_PASS: admin
    ports:
      - "5672:5672"   # AMQP port
      - "15672:15672" # Management UI
    volumes:
      - rabbitmq_data:/var/lib/rabbitmq
      - ./docker/rabbitmq-init.sh:/docker-entrypoint-initdb.d/rabbitmq-init.sh
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  mock-api:
    build:
      context: .
      dockerfile: docker/mock-api.Dockerfile
    ports:
      - "8081:8080"
    environment:
      - MOCK_API_PORT=8080
      - FAILURE_RATE=0.15
      - MIN_LATENCY_MS=10
      - MAX_LATENCY_MS=2000
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  postgres_data:
  rabbitmq_data:
```

### Application Configuration

Create `src/main/resources/application-dev.yml`:

```yaml
spring:
  profiles:
    active: dev

  datasource:
    url: jdbc:postgresql://localhost:5432/payment_bridge
    username: payment_user
    password: payment_pass
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5

  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false

  rabbitmq:
    host: localhost
    port: 5672
    username: admin
    password: admin
    listener:
      simple:
        concurrency: 10-50
        prefetch: 20
        retry:
          enabled: false  # We handle retries manually

logging:
  level:
    com.payment.bridge: DEBUG
    org.springframework.amqp: INFO
    org.springframework.transaction: DEBUG

payment:
  api:
    base-url: http://localhost:8081
    timeout:
      connect: 5000
      read: 2000
    retry:
      max-attempts: 5
      backoff-multiplier: 1.5
      initial-interval: 500ms

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

### RabbitMQ Initialization

Create `docker/rabbitmq-init.sh`:

```bash
#!/bin/bash

# Wait for RabbitMQ to start
sleep 30

# Create exchanges and queues
rabbitmqadmin declare exchange name=payment-exchange type=direct
rabbitmqadmin declare queue name=payment-processing durable=true
rabbitmqadmin declare queue name=dlq-payment-failed durable=true

# Bind queues to exchange
rabbitmqadmin declare binding source=payment-exchange destination_type=queue destination=payment-processing routing_key=payment.process
rabbitmqadmin declare binding source=dlx-payment-failed destination_type=queue destination=dlq-payment-failed routing_key=#

# Configure DLQ routing
rabbitmqadmin declare policy name=dlq-policy pattern="payment-processing" definition='{"dead-letter-exchange":"dlx-payment-failed"}' apply-to=queues
```

## Testing the Setup

### 1. Submit a Payment Request

```bash
# Submit a payment request
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "X-API-Key: dev_key_123" \
  -H "X-Idempotency-Key: test_$(date +%s)" \
  -d '{
    "amount": 99.99,
    "currency": "USD",
    "clientReference": "test_order_001"
  }'
```

**Expected Response:**
```json
{
  "paymentId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "RECEIVED",
  "message": "Payment request accepted for processing",
  "estimatedProcessingTime": "PT30S",
  "_links": {
    "status": {
      "href": "/api/v1/payments/status/550e8400-e29b-41d4-a716-446655440000"
    }
  }
}
```

### 2. Check Payment Status

```bash
# Extract payment ID from previous response
PAYMENT_ID="550e8400-e29b-41d4-a716-446655440000"

# Check status
curl http://localhost:8080/api/v1/payments/status/$PAYMENT_ID
```

**Expected Response (after processing):**
```json
{
  "paymentId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "COMPLETED",
  "amount": 99.99,
  "currency": "USD",
  "processedAt": "2026-05-07T19:37:41Z",
  "transactionId": "txn_mock_1234567890"
}
```

### 3. Monitor RabbitMQ

1. Open RabbitMQ Management UI: http://localhost:15672
2. Login with: admin / admin
3. Check queues:
   - `payment-processing`: Should show message flow
   - `dlq-payment-failed`: Should be empty (successful processing)

### 4. Simulate Failures

```bash
# Configure mock API to fail
curl -X POST http://localhost:8081/admin/failure-rate \
  -H "Content-Type: application/json" \
  -d '{"rate": 1.0}'  # 100% failure rate

# Submit payment (should eventually go to DLQ)
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "X-API-Key: dev_key_123" \
  -H "X-Idempotency-Key: failure_test_$(date +%s)" \
  -d '{"amount": 50.00, "currency": "USD"}'

# Check DLQ in RabbitMQ UI
# Should see message after 5 retries with exponential backoff
```

## Development Workflow

### Running Tests

```bash
# Unit tests
./mvnw test

# Integration tests
./mvnw verify -Dspring.profiles.active=test

# Load tests (requires Gatling)
./mvnw gatling:test
```

### Debugging

**Enable Debug Logging:**
```bash
# Set logging level
curl -X POST http://localhost:8080/actuator/loggers/com.payment.bridge \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "DEBUG"}'
```

**Check Application Metrics:**
```bash
# Prometheus metrics
curl http://localhost:8080/actuator/prometheus | grep payment

# Health check
curl http://localhost:8080/actuator/health
```

### Database Inspection

```bash
# Connect to PostgreSQL
docker exec -it payment-bridge-postgres-1 psql -U payment_user -d payment_bridge

# Check payments
SELECT payment_id, status, amount, created_at FROM payment ORDER BY created_at DESC LIMIT 10;

# Check DLQ
SELECT dlq_id, payment_id, failed_action, created_at FROM dead_letter_queue ORDER BY created_at DESC LIMIT 10;
```

## Troubleshooting

### Common Issues

**Application won't start:**
```bash
# Check if ports are available
lsof -i :8080,5432,5672,15672,8081

# Check Docker containers
docker-compose -f docker-compose.dev.yml ps
docker-compose -f docker-compose.dev.yml logs <service-name>
```

**Database connection fails:**
```bash
# Test database connectivity
docker exec payment-bridge-postgres-1 pg_isready -U payment_user -d payment_bridge

# Check connection string in application.yml
```

**RabbitMQ connection fails:**
```bash
# Test RabbitMQ connectivity
docker exec payment-bridge-rabbitmq-1 rabbitmq-diagnostics ping

# Check RabbitMQ logs
docker-compose -f docker-compose.dev.yml logs rabbitmq
```

**Mock API not responding:**
```bash
# Test mock API health
curl http://localhost:8081/health

# Check mock API logs
docker-compose -f docker-compose.dev.yml logs mock-api
```

### Performance Tuning

**For higher throughput:**
```yaml
# Increase concurrency
spring.rabbitmq.listener.simple.concurrency: 50-200

# Increase prefetch
spring.rabbitmq.listener.simple.prefetch: 50

# Increase DB pool
spring.datasource.hikari.maximum-pool-size: 50
```

**For lower latency:**
```yaml
# Reduce prefetch
spring.rabbitmq.listener.simple.prefetch: 5

# Tune timeouts
payment.api.timeout.connect: 1000
payment.api.timeout.read: 1000
```

## Next Steps

1. **Run the full test suite** to validate all components
2. **Implement monitoring dashboards** for production readiness
3. **Configure CI/CD pipeline** for automated testing
4. **Scale testing** with multiple application instances
5. **Security hardening** for production deployment

## Support

- **Documentation**: See `/specs/001-resilient-payment-bridge/` for detailed specifications
- **Logs**: Check application logs at `logs/spring.log`
- **Metrics**: Access via `/actuator/metrics`
- **Health**: Check system health at `/actuator/health`</content>
<parameter name="filePath">/Users/mac/Programming/payment-system-speckit/specs/001-resilient-payment-bridge/quickstart.md