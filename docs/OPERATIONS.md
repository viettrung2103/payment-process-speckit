# Operations Runbook

This runbook describes operational procedures for the Payment System Speckit project.

## Health Checks

### Payment Bridge

- Health endpoint: `http://localhost:8080/actuator/health`
- Metrics endpoint: `http://localhost:8080/actuator/prometheus`

### Mock Payment API

- Health endpoint: `http://localhost:8081/actuator/health`

## Common Failure Modes

### Database Connection Issues

- Verify PostgreSQL is reachable at `jdbc:postgresql://postgres:5432/payment_bridge`
- Check PostgreSQL logs for authentication or connection errors
- Ensure the `payment_user` user exists and password is correct
- **Note**: Database name is `payment_bridge`, not `payment_user`

### RabbitMQ Delivery Issues

- Verify RabbitMQ is reachable at `rabbitmq:5672`
- Use RabbitMQ management UI at `http://localhost:15672`
- Check the `payment-processing` queue and DLQ queues for unacked messages

### External API Failures

- Confirm `payment-bridge` can resolve `mock-payment-api:8081`
- Inspect retry and circuit breaker logs in `payment-bridge`
- If the mock API returns failures, use the DLQ audit trail for failure context

## DLQ Governance

- Failed messages are routed to the DLQ after retry exhaustion
- Review DLQ entries for failure reason and retry count
- Use the audit trail to determine if a retry or manual correction is required

## Release Procedure

1. Build the services using Maven:
   ```bash
   mvn -pl payment-bridge,mock-payment-api package -DskipTests
   ```
2. Create Docker images:
   ```bash
   docker compose build
   ```
3. Deploy with Docker Compose:
   ```bash
   docker compose up -d
   ```
4. Verify service health:
   ```bash
   curl http://localhost:8080/actuator/health
   curl http://localhost:8081/actuator/health
   ```

## Monitoring & Alerts

### Recommended Metrics

- Ingestion latency percentiles (p95/p99)
- DLQ rate and retry count
- Payment processing failure rate
- Database connection pool usage
- RabbitMQ queue depth

### Alerting Rules

- **High DLQ rate**: Trigger when DLQ entries exceed 5% of processed payments over 5 minutes.
- **High ingestion latency**: Trigger when ingestion latency P99 is above 500ms for 5 minutes.
- **Worker processing failure spike**: Trigger when payment processing failures exceed 1 per minute.
- **Database connection pool exhaustion**: Trigger when active Hikari connections remain at max for 2 minutes.
- **RabbitMQ queue growth**: Trigger when `payment-processing` queue depth exceeds 100 messages for 5 minutes.

### Troubleshooting

- For slow requests, inspect the `payment-bridge` logs and latency metrics
- For DLQ escalation, inspect failed message context in the database and application logs
- For RabbitMQ backlog, ensure consumers are healthy and prefetch settings are appropriate

## Maintenance

- Rotate secrets and passwords regularly
- Update dependencies and Docker base images quarterly
- Review health checks and alerts monthly
- Archive old DLQ entries after manual review
