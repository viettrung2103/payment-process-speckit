# Deployment Guide

This guide describes how to deploy the Payment System Speckit project locally using Docker and Docker Compose.

## Prerequisites

- Docker Engine (version 24+)
- Docker Compose (version 2+)
- Java 21 (for local builds if you are building from source)
- Maven 3.9+ (for building service images)

## Local Docker Deployment

From the repository root:

```bash
docker compose up --build
```

This starts:
- `postgres` database on port `5432`
- `rabbitmq` broker on ports `5672` and `15672`
- `mock-payment-api` service on port `8081`
- `payment-bridge` service on port `8080`

## Service Endpoints

- Payment Bridge API: `http://localhost:8080/api/v1/payments`
- Mock Payment API: `http://localhost:8081/api/v1/payments`
- RabbitMQ Management UI: `http://localhost:15672` (user: `admin`, pass: `admin`)
- Postgres: `jdbc:postgresql://localhost:5432/payment_bridge`

## Build Artifacts

Each service builds using its module Dockerfile:
- `payment-bridge/Dockerfile`
- `mock-payment-api/Dockerfile`

## Environment Variables

The containers use the following environment variables:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `PAYMENT_API_BASE_URL`
- `SPRING_RABBITMQ_HOST`
- `SPRING_RABBITMQ_PORT`
- `SPRING_RABBITMQ_USERNAME`
- `SPRING_RABBITMQ_PASSWORD`

## Notes

- The `mock-payment-api` service runs with an in-memory H2 database.
- The `payment-bridge` service connects to PostgreSQL and RabbitMQ.
- If you only need the API services without Docker, use Maven locally:
  - `mvn -pl payment-bridge spring-boot:run`
  - `mvn -pl mock-payment-api spring-boot:run`
