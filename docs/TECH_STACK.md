# Tech Stack and Rationale

This project uses a carefully chosen stack to support a resilient, horizontally scalable payment bridge with strong operational visibility and robust failure handling.

## Core Stack

- **Java 21**
  - Chosen for long-term platform stability, widespread ecosystem support, and access to Virtual Threads for highly concurrent I/O-bound workloads.
  - Enables efficient handling of payment traffic with a familiar language and mature JVM tooling.

- **Spring Boot 3.4**
  - Provides convention-over-configuration for rapid service development and production-ready defaults.
  - Integrates well with Spring Data, Spring AMQP, and Spring Actuator for observability, messaging, and persistence.

- **PostgreSQL**
  - Selected for ACID-compliant transactional consistency, strong SQL support, and reliable durability.
  - Ideal for payment state management, optimistic locking, and audit trail persistence.

- **RabbitMQ**
  - Used for asynchronous workflow orchestration, retry handling, and dead-letter queue governance.
  - Fits the system's design goals of stateless processing and reliable message delivery.

- **Docker / Docker Compose**
  - Simplifies local development, dependency orchestration, and deployment reproducibility.
  - Enables consistent environment setup for the payment bridge, RabbitMQ, PostgreSQL, and mock services.

- **Nginx**
  - Serves as the load balancer and request gateway for scaled deployments.
  - Supports upstream health checks, rate limiting, and service-level routing.

## Testing and Validation

- **JUnit 5**
  - Chosen for unit and integration test coverage with strong support for Spring Boot testing.

- **Gatling / JMeter**
  - Used for performance and load validation to verify throughput, latency, and scalability goals.

## Architecture Rationale

- **Modular service design** keeps responsibilities separate: API ingestion, persistence, queuing, and worker processing.
- **Message-driven workflow** enables horizontal scaling by decoupling request intake from payment processing.
- **Resilience patterns** like circuit breakers, retry classification, and DLQ handling ensure the bridge can tolerate transient failures and recover without data loss.
- **Observability focus** with health checks, metrics, and audit trails makes the system easier to operate and troubleshoot.

## Why This Stack

- The combination of Java, Spring Boot, PostgreSQL, and RabbitMQ is well-suited to transactional middleware with durability and asynchronous processing needs.
- Docker and Nginx provide a clear path from local development to scaled deployment with consistent behavior across environments.
- These choices balance speed of development, operational reliability, and the ability to meet strict payment-processing requirements.
