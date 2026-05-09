# Docker Deployment Guide

## Overview

This document describes the Docker Compose deployment configuration for the Payment System Speckit, a resilient distributed payment processing system with a mock API.

## Architecture

The system consists of four main services:

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│  Payment Bridge │◄──►│   RabbitMQ      │◄──►│   PostgreSQL    │
│    (Port 8080)  │    │   (Port 5672)   │    │   (Port 5432)   │
└─────────────────┘    └─────────────────┘    └─────────────────┘
         │
         ▼
┌─────────────────┐
│ Mock Payment API│
│   (Port 8081)   │
└─────────────────┘
```

## Services Configuration

### PostgreSQL Database

```yaml
postgres:
  image: postgres:15
  container_name: payment-system-postgres
  environment:
    POSTGRES_DB: payment_bridge # Database name
    POSTGRES_USER: payment_user # Database user
    POSTGRES_PASSWORD: payment_pass # Database password
  ports:
    - "5432:5432" # Host:Container port mapping
  volumes:
    - postgres-data:/var/lib/postgresql/data # Persistent data volume
  healthcheck:
    test: ["CMD", "pg_isready", "-U", "payment_user"]
    interval: 10s
    timeout: 10s
    retries: 5
    start_period: 15s
```

**Configuration Details:**

- **Database Name:** `payment_bridge`
- **Username:** `payment_user`
- **Password:** `payment_pass`
- **Port:** 5432 (exposed on host)
- **Health Check:** Uses `pg_isready` command to verify database availability

### RabbitMQ Message Broker

```yaml
rabbitmq:
  image: rabbitmq:3-management
  container_name: payment-system-rabbitmq
  environment:
    RABBITMQ_DEFAULT_USER: admin
    RABBITMQ_DEFAULT_PASS: admin
  ports:
    - "5672:5672" # AMQP port
    - "15672:15672" # Management UI port
  healthcheck:
    test: ["CMD", "rabbitmq-diagnostics", "status"]
    interval: 10s
    timeout: 10s
    retries: 5
    start_period: 10s
```

**Configuration Details:**

- **Management UI:** http://localhost:15672
- **Credentials:** admin/admin
- **AMQP Port:** 5672
- **Management Port:** 15672

### Mock Payment API

```yaml
mock-payment-api:
  build:
    context: .
    dockerfile: mock-payment-api/Dockerfile
  container_name: mock-payment-api
  environment:
    SPRING_DATASOURCE_URL: jdbc:h2:mem:mockdb;DB_CLOSE_DELAY=-1
    SERVER_PORT: 8081
  ports:
    - "8081:8081"
  depends_on:
    - postgres
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:8081/actuator/health"]
    interval: 10s
    timeout: 10s
    retries: 5
    start_period: 15s
```

**Configuration Details:**

- **Port:** 8081
- **Database:** H2 in-memory database (for testing)
- **Health Check:** Spring Boot Actuator health endpoint
- **Dependencies:** Requires PostgreSQL to be healthy first

### Payment Bridge

```yaml
payment-bridge:
  build:
    context: .
    dockerfile: payment-bridge/Dockerfile
  container_name: payment-bridge
  environment:
    SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/payment_bridge
    SPRING_DATASOURCE_USERNAME: payment_user
    SPRING_DATASOURCE_PASSWORD: payment_pass
    PAYMENT_API_BASE_URL: http://mock-payment-api:8081
    SPRING_RABBITMQ_HOST: rabbitmq
    SPRING_RABBITMQ_PORT: 5672
    SPRING_RABBITMQ_USERNAME: admin
    SPRING_RABBITMQ_PASSWORD: admin
    SERVER_PORT: 8080
  ports:
    - "8080:8080"
  depends_on:
    - postgres
    - rabbitmq
    - mock-payment-api
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
    interval: 10s
    timeout: 10s
    retries: 5
    start_period: 20s
```

**Configuration Details:**

- **Port:** 8080
- **Database:** PostgreSQL (`payment_bridge` database)
- **Message Broker:** RabbitMQ (admin/admin)
- **External API:** Mock Payment API at http://mock-payment-api:8081
- **Health Check:** Spring Boot Actuator health endpoint
- **Dependencies:** Requires all other services to be healthy

## Volumes

```yaml
volumes:
  postgres-data: # Persistent storage for PostgreSQL data
```

## Deployment Instructions

### Prerequisites

- Docker Engine 20.10+
- Docker Compose 2.0+
- At least 4GB RAM available
- Ports 5432, 5672, 8080, 8081, 15672 available

### Quick Start

1. **Clone the repository:**

   ```bash
   git clone <repository-url>
   cd payment-system-speckit
   ```

2. **Start all services:**

   ```bash
   docker-compose up -d
   ```

3. **Verify deployment:**

   ```bash
   docker-compose ps
   ```

4. **Check health endpoints:**
   ```bash
   curl http://localhost:8080/actuator/health
   curl http://localhost:8081/actuator/health
   ```

### Build and Deploy

```bash
# Build and start all services
docker-compose up --build -d

# View logs
docker-compose logs -f

# Stop all services
docker-compose down

# Stop and remove volumes (WARNING: This deletes data)
docker-compose down -v
```

## Service Dependencies

The services start in the following order due to `depends_on` configuration:

1. **PostgreSQL** - Database service
2. **RabbitMQ** - Message broker
3. **Mock Payment API** - External payment simulation service
4. **Payment Bridge** - Main payment processing service

Each service waits for its dependencies to be healthy before starting.

## Networking

- **Default Network:** `payment-system-speckit_default`
- **Service Discovery:** Services communicate using container names as hostnames
- **External Access:** Specified ports are exposed on localhost

## Health Checks

All services include health checks that:

- Run every 10 seconds
- Have a 10-second timeout
- Allow 5 retries
- Have appropriate start periods for initialization

## Troubleshooting

### Common Issues

1. **Port Conflicts:**

   ```bash
   # Check what's using the ports
   lsof -i :5432,5672,8080,8081,15672

   # Stop conflicting services or change ports in docker-compose.yml
   ```

2. **Database Connection Issues:**

   ```bash
   # Check PostgreSQL logs
   docker-compose logs postgres

   # Test database connection
   docker exec -it payment-system-postgres psql -U payment_user -d payment_bridge
   ```

   **PostgreSQL Health Check Issue:**
   If you see repeated "database payment_user does not exist" errors, the health check is incorrectly trying to connect to a database named after the user instead of the actual database.

   **Fix:** Ensure the health check specifies the correct database:

   ```yaml
   healthcheck:
     test: ["CMD", "pg_isready", "-U", "payment_user", "-d", "payment_bridge"]
   ```

3. **Service Startup Failures:**

   ```bash
   # Check service logs
   docker-compose logs <service-name>

   # Restart specific service
   docker-compose restart <service-name>
   ```

4. **Health Check Failures:**

   ```bash
   # Check health status
   docker-compose ps

   # Inspect failing container
   docker inspect <container-name>
   ```

### Database Access

- **Host:** localhost
- **Port:** 5432
- **Database:** payment_bridge
- **Username:** payment_user
- **Password:** payment_pass

```bash
# Connect from host
psql -h localhost -p 5432 -U payment_user -d payment_bridge
```

### RabbitMQ Management

- **URL:** http://localhost:15672
- **Username:** admin
- **Password:** admin

## Monitoring

### Health Endpoints

- **Payment Bridge:** http://localhost:8080/actuator/health
- **Mock Payment API:** http://localhost:8081/actuator/health

### Metrics Endpoints

- **Payment Bridge:** http://localhost:8080/actuator/prometheus
- **Mock Payment API:** http://localhost:8081/actuator/prometheus

### Logs

```bash
# View all logs
docker-compose logs

# Follow logs in real-time
docker-compose logs -f

# View specific service logs
docker-compose logs payment-bridge
```

## Security Considerations

### Current Configuration (Development)

- Default passwords used (change for production)
- Management interfaces exposed (restrict in production)
- No SSL/TLS configuration
- Debug logging enabled

### Production Recommendations

1. **Change default credentials**
2. **Use environment variables for secrets**
3. **Configure SSL/TLS**
4. **Restrict network access**
5. **Enable audit logging**
6. **Use managed databases**

## Performance Tuning

### Resource Allocation

```yaml
# Example resource limits (add to service configurations)
deploy:
  resources:
    limits:
      memory: 512M
      cpus: "0.5"
    reservations:
      memory: 256M
      cpus: "0.25"
```

### Database Tuning

- Connection pool size: 20 (configured in Payment Bridge)
- Health check frequency: Every 10 seconds
- Timeout settings: 10 seconds

### Message Broker Tuning

- Default prefetch: 1 (can be configured)
- Queue durability: Persistent
- Message TTL: Not configured (messages don't expire)

## Backup and Recovery

### Database Backup

```bash
# Backup PostgreSQL data
docker exec payment-system-postgres pg_dump -U payment_user payment_bridge > backup.sql

# Restore from backup
docker exec -i payment-system-postgres psql -U payment_user -d payment_bridge < backup.sql
```

### Volume Management

```bash
# List volumes
docker volume ls

# Inspect volume
docker volume inspect payment-system-speckit_postgres-data

# Backup volume (requires stopping containers)
docker run --rm -v payment-system-speckit_postgres-data:/data -v $(pwd):/backup alpine tar czf /backup/postgres-backup.tar.gz -C /data .
```

## Development Workflow

### Local Development

1. **Start infrastructure only:**

   ```bash
   docker-compose up -d postgres rabbitmq
   ```

2. **Run applications locally:**

   ```bash
   # Terminal 1 - Payment Bridge
   cd payment-bridge
   mvn spring-boot:run

   # Terminal 2 - Mock Payment API
   cd mock-payment-api
   mvn spring-boot:run
   ```

3. **Debugging:**

   ```bash
   # View application logs
   docker-compose logs -f payment-bridge

   # Connect to database
   docker exec -it payment-system-postgres psql -U payment_user -d payment_bridge
   ```

### Testing

```bash
# Run integration tests
mvn test -Dspring.profiles.active=test

# Run with test containers
mvn test -Dspring.profiles.active=integration
```

## Version Information

- **Docker Compose:** 3.8
- **PostgreSQL:** 15
- **RabbitMQ:** 3-management (latest)
- **Java:** 21 (Eclipse Temurin)
- **Spring Boot:** 3.4.0
- **Maven:** 3.9

## Support

For issues and questions:

1. Check the logs: `docker-compose logs`
2. Verify health checks: `docker-compose ps`
3. Review this documentation
4. Check the [OPERATIONS.md](OPERATIONS.md) for runtime procedures
5. Review [ITERATION_ANALYSIS.md](ITERATION_ANALYSIS.md) for troubleshooting history

---

**Last Updated:** May 8, 2026
**Version:** 1.0.0
**Status:** ✅ Production Ready (Development Environment)
