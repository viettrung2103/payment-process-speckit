# Implementation Plan: Resilient Distributed Payment Bridge

**Branch**: `001-resilient-payment-bridge` | **Date**: 2026-05-07 | **Spec**: [specs/001-resilient-payment-bridge/spec.md](specs/001-resilient-payment-bridge/spec.md)
**Input**: Feature specification from `/specs/001-resilient-payment-bridge/spec.md`

**Note**: This plan is created based on the provided tech stack and architecture details for the payment processing bridge.

## Summary

Build a high-throughput, horizontally scalable payment middleware using Java 21 with Virtual Threads, Spring Boot 3.4, PostgreSQL, and RabbitMQ. The system ensures zero data loss through message-driven architecture with persistent state management, implementing the 6 constitutional principles: idempotency, MQ-driven statelessness, hybrid retry mechanisms, DLQ governance, and latency tolerance.

## Technical Context

**Language/Version**: Java 21 (Virtual Threads for concurrency)  
**Primary Dependencies**: Spring Boot 3.4, Spring AMQP, Resilience4j  
**Storage**: PostgreSQL (ACID compliance, optimistic locking with version column)  
**Messaging**: RabbitMQ with direct exchange, retry queue, and DLQ routing  
**Deployment**: Docker / Docker Compose with Nginx load balancer and health checks  
**Testing**: JUnit 5 + Gatling (TDD + load testing for P99 latency)  
**Target Platform**: Linux server (horizontal scaling to 10 instances)  
**Project Type**: Web service (payment middleware API)  
**Performance Goals**: 1000 payments/minute per instance, P99 latency <500ms  
**Constraints**: Zero data loss, stateless instances, 10ms-2s API latency tolerance  
**Scale/Scope**: 10 instances max, 10k payments/minute total throughput

## Tech Stack

- Java 21 with Virtual Threads
- Spring Boot 3.4 for rapid service development and dependency management
- RabbitMQ for asynchronous message delivery, retries, and DLQ governance
- PostgreSQL for durable transaction state and optimistic locking support
- Docker Compose for local environment orchestration
- Nginx load balancer for upstream health checking, rate limiting, and horizontal scaling
- JUnit 5 and Gatling/JMeter for quality and performance validation

## Architecture Overview

The implementation uses a modular payment bridge design with clear separation of concerns:

- **Ingress**: A REST API endpoint accepts payment requests, validates idempotency, and persists an initial RECEIVED state.
- **Queueing**: `PaymentPublisher` enqueues payment work to RabbitMQ using a direct exchange and retry/DLQ topology.
- **Processing**: `PaymentWorker` consumes messages, calls the external payment API client under Resilience4j circuit breaker protection, and transitions payment state.
- **Resilience**: Transaction boundary synchronization, retry classification, DLQ routing, and optimistic locking handling keep the system reliable and recoverable.
- **Observability**: Health checks, metrics, audit trails, and queue depth endpoints provide runtime visibility and diagnostics.

## Constitution Check

_GATE: Must pass before Phase 0 research. Re-check after Phase 1 design._

### ✅ Principle 1: The Law of Idempotency

- **Status**: COMPLIANT
- **Implementation**: Payment_id generation at gateway, persisted in RECEIVED state before MQ enqueue
- **Evidence**: POST /api/v1/payments endpoint with X-Idempotency-Key header validation

### ✅ Principle 2: MQ-Driven Statelessness

- **Status**: COMPLIANT
- **Implementation**: RabbitMQ Direct Exchange with SimpleMessageListenerContainer
- **Evidence**: Workers pull tasks from queue, no in-memory state between instances

### ✅ Principle 3: Hybrid Retry Mechanism (API-Side)

- **Status**: COMPLIANT
- **Implementation**: Resilience4j Circuit Breaker with Base 1.5 exponential backoff (5 retries)
- **Evidence**: API error classification (4xx → immediate DLQ, 5xx/transient → retry)

### ✅ Principle 4: Hybrid Retry Mechanism (DB-Side)

- **Status**: COMPLIANT
- **Implementation**: 5 immediate retries for DB update failures after successful API response
- **Evidence**: Manual ACK only after DB commit, rollback on DB failure

### ✅ Principle 5: The "Hall of Shame" (DLQ Governance)

- **Status**: COMPLIANT
- **Implementation**: RabbitMQ TTL-based DLQ routing, manual review required
- **Evidence**: Failed payments after 10 total retries sent to DLQ with full context

### ✅ Principle 6: Latency and Failure Transparency

- **Status**: COMPLIANT
- **Implementation**: Virtual Threads + non-blocking I/O, CRITICAL level logging
- **Evidence**: Prefetch count 20, Circuit Breaker protection, no silent failures

**Gate Status**: ✅ ALL PRINCIPLES COMPLIANT - Phase 1 design validated and complete.

## Project Structure

### Documentation (this feature)

```text
specs/001-resilient-payment-bridge/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
src/main/java/com/payment/bridge/
├── config/
│   ├── RabbitMQConfig.java          # Exchange, queue, DLQ configuration
│   ├── DatabaseConfig.java          # PostgreSQL connection, optimistic locking
│   ├── ResilienceConfig.java        # Circuit Breaker, retry policies
│   └── AsyncConfig.java             # Virtual Threads configuration
├── controller/
│   └── PaymentController.java       # POST /api/v1/payments endpoint
├── service/
│   ├── PaymentService.java          # Business logic, state transitions
│   ├── IdempotencyService.java      # Duplicate detection logic
│   └── PublisherConfirmService.java # RabbitMQ publisher confirms
├── worker/
│   ├── PaymentWorker.java           # RabbitMQ message listener
│   └── ExternalApiClient.java       # Mock API integration with Circuit Breaker
├── model/
│   ├── Payment.java                 # JPA entity with version column
│   ├── PaymentStatus.java           # Enum: RECEIVED, IN_PROGRESS, COMPLETED, FAILED
│   └── PaymentRequest.java          # DTO for API requests
├── repository/
│   └── PaymentRepository.java       # JPA repository with optimistic locking
└── exception/
    ├── PaymentProcessingException.java
    └── IdempotencyViolationException.java

src/test/java/com/payment/bridge/
├── unit/                            # JUnit 5 unit tests
├── integration/                     # Spring Boot integration tests
└── load/                            # Gatling load tests for P99 latency

src/main/resources/
├── application.yml                  # Spring configuration
├── schema.sql                       # Database schema
└── docker-compose.yml               # Local development stack
```

**Structure Decision**: Single Spring Boot application with clear separation of concerns. Controller handles ingestion, workers handle processing, services manage business logic. Test structure mirrors source structure for maintainability.

## Phase Execution Plan

### Phase 0: Research & Technical Validation (Output: research.md)

1. **Virtual Threads Performance Research**
   - Benchmark Virtual Threads vs traditional thread pools for payment processing
   - Validate 10ms-2s latency tolerance with concurrent workloads
   - Document optimal prefetch count settings

2. **RabbitMQ DLQ Configuration Research**
   - Test TTL-based retry mechanisms with exponential backoff
   - Validate dead letter routing for different error classifications
   - Document Management Plugin monitoring capabilities

3. **PostgreSQL Optimistic Locking Research**
   - Test version column performance under high concurrency
   - Validate ACID compliance with concurrent state transitions
   - Document locking strategy for 10-instance scaling

4. **Resilience4j Circuit Breaker Tuning**
   - Research optimal failure thresholds for payment APIs
   - Test recovery mechanisms with mock API failures
   - Document configuration for 5xx vs 4xx error handling

### Phase 1: Design & Contracts (Output: data-model.md, contracts/, quickstart.md)

1. **Data Model Design** ✅
   - Payment entity with optimistic locking
   - Message queue task structure
   - DLQ entry schema with failure context

2. **API Contracts Definition** ✅
   - Payment ingestion endpoint contract
   - External mock API integration contract
   - Monitoring/health check endpoints

3. **Quickstart Guide** ✅
   - Local development setup with Docker Compose
   - Basic payment flow demonstration
   - Load testing with Gatling

### Phase 2: Implementation Tasks (Output: tasks.md)

- Dependency-ordered task breakdown
- TDD test scenarios for each component
- Integration testing strategy

## Phase 7: Latency Resilience & Non-blocking Ingestion

1. Implement `PaymentController` as an asynchronous entry point using `CompletableFuture` and a virtual thread executor.
2. Record ingestion and processing latency with Micrometer timers and surface p99 metrics and slow-operation logs.
3. Add JUnit-based load tests in `src/test/java/com/payment/bridge/load/`, a shared load-test scaffold helper, and p95/p99 latency report formatting to validate 100+ concurrent ingestion requests under simulated API delay and mixed latency conditions.
4. Add a dedicated non-blocking controller integration test in `src/test/java/com/payment/bridge/integration/` to verify ingestion latency remains low when background publishing is slow.
5. Target p99 ingestion latency <= 500ms while maintaining non-blocking request handling.

## Complexity Tracking

**No constitution violations detected - no complexity justifications required.**

## Risk Assessment

### High Risk Items

1. **Virtual Threads Maturity**: Java 21 Virtual Threads are relatively new - Phase 0 research critical
2. **Optimistic Locking Contention**: High concurrency may cause version conflicts - monitor and tune
3. **Circuit Breaker Configuration**: Incorrect thresholds could cause false positives - extensive testing required

### Mitigation Strategies

1. **Virtual Threads**: Comprehensive benchmarking in Phase 0, fallback to traditional threads if issues
2. **Locking**: Start with conservative concurrency settings, scale up based on load testing
3. **Circuit Breaker**: Configuration-driven settings with environment-specific tuning

## Success Metrics

- **Functional**: 100% test coverage, all 5 FRs implemented with constitutional compliance
- **Performance**: 1000 payments/minute per instance, P99 latency <500ms
- **Reliability**: Zero data loss under failure scenarios, <5% DLQ rate
- **Scalability**: Linear throughput scaling from 1 to 10 instances
- **Observability**: All exceptions logged at CRITICAL level with full context

## Next Steps

1. **Execute Phase 0**: Run research tasks to validate technical assumptions
2. **Update Agent Context**: Configure Copilot with payment bridge context
3. **Re-check Constitution**: Validate Phase 0 findings against principles
4. **Proceed to Phase 1**: Create detailed design artifacts</content>
   <parameter name="filePath">/Users/mac/Programming/payment-system-speckit/specs/001-resilient-payment-bridge/plan.md
