# Tasks: Resilient Distributed Payment Bridge (F-PAY-001)

**Input**: Design documents from `/specs/001-resilient-payment-bridge/`  
**Prerequisites**: plan.md (✅ complete), spec.md (✅ complete), research.md (✅ complete), data-model.md (✅ complete), contracts/ (✅ complete), quickstart.md (✅ complete)

**Tests**: TDD approach - Write tests FIRST (JUnit 5), ensure they FAIL before implementation  
**Test Types**: Unit tests → Contract tests → Integration tests → Load tests (Gatling)

**Organization**: Tasks grouped by user story to enable independent implementation, testing, and MVP delivery  
**Technology**: Java 21 + Spring Boot 3.4 + RabbitMQ + PostgreSQL + Resilience4j

## Format Reference

- **[P]**: Can run in parallel (different files, no blocking dependencies)
- **[US#]**: User Story task (US1=Payment Ingestion, US2=MQ Distribution, US3=Retry/DLQ, US4=State Transitions, US5=Latency)
- **Paths**: `src/main/java/com/payment/bridge/`, `src/test/java/com/payment/bridge/`, `src/main/resources/`

---

## Phase 1: Setup (Shared Infrastructure) 🏗️

**Purpose**: Maven project initialization, Spring Boot configuration, and development environment

- [ ] T001 Create Maven project structure with spring-boot-starter-parent 3.4 in pom.xml
- [ ] T002 [P] Add Spring Boot dependencies (web, data-jpa, amqp, actuator, resilience4j) to pom.xml
- [ ] T003 [P] Add testing dependencies (junit-5, mockito, embedded-rabbitmq, testcontainers-postgresql) to pom.xml
- [ ] T004 [P] Configure application.yml with profiles (dev, test, prod) in src/main/resources/
- [ ] T005 Create PaymentStatus enum in src/main/java/com/payment/bridge/model/PaymentStatus.java
- [ ] T006 Create PaymentRequest DTO in src/main/java/com/payment/bridge/model/PaymentRequest.java
- [ ] T007 Create PaymentResponse DTO in src/main/java/com/payment/bridge/model/PaymentResponse.java
- [ ] T008 [P] Setup logging configuration (logback-spring.xml) in src/main/resources/

---

## Phase 2: Foundational (Blocking Prerequisites) ⚠️

**Purpose**: Core infrastructure that ALL user stories depend on  
**⚠️ CRITICAL**: No user story work begins until Phase 2 is complete

### Database Setup

- [ ] T009 Create payment table migration in src/main/resources/db/migration/V001\_\_create_payment_table.sql
- [x] T010 Create dead_letter_queue table migration in src/main/resources/db/migration/V002\_\_create_dlq_table.sql
- [x] T011 Create payment_audit table migration for state transition tracking in src/main/resources/db/migration/V003\_\_create_payment_audit.sql

### JPA Configuration & Models

- [x] T012 [P] Create Payment JPA entity with @Version annotation in src/main/java/com/payment/bridge/model/Payment.java
- [x] T013 [P] Create DeadLetterQueueEntry entity in src/main/java/com/payment/bridge/model/DeadLetterQueueEntry.java
- [x] T014 [P] Create PaymentAudit entity for state transition logging in src/main/java/com/payment/bridge/model/PaymentAudit.java
- [x] T015 [P] Create Repositories: PaymentRepository in src/main/java/com/payment/bridge/repository/PaymentRepository.java
- [x] T016 [P] Create DLQRepository in src/main/java/com/payment/bridge/repository/DeadLetterQueueRepository.java
- [x] T017 [P] Create PaymentAuditRepository in src/main/java/com/payment/bridge/repository/PaymentAuditRepository.java
- [x] T018 Create PaymentJdbcTemplate class for custom optimistic locking queries in src/main/java/com/payment/bridge/repository/PaymentJdbcTemplate.java

### RabbitMQ Configuration

- [x] T019 Create RabbitMQConfig class in src/main/java/com/payment/bridge/config/RabbitMQConfig.java
  - Direct Exchange: payment-exchange
  - Processing Queue: payment-processing with DLQ binding
  - DLQ Queue: dlq-payment-failed with TTL policy
  - Manual ACK configuration

- [x] T020 [P] Create MessageQueueTask class (message DTO) in src/main/java/com/payment/bridge/model/MessageQueueTask.java
- [x] T021 [P] Create RabbitMQ Publisher in src/main/java/com/payment/bridge/amqp/PaymentPublisher.java

### Resilience & Error Handling

- [x] T022 Create ResilienceConfig class in src/main/java/com/payment/bridge/config/ResilienceConfig.java
  - Circuit Breaker configuration for external API (5 failure threshold, 60s timeout)
  - Retry policy with Base 1.5 exponential backoff (0.5s initial, 5 attempts)
  - Timeout configuration (5s connect, 2s read)

- [x] T023 [P] Create ErrorClassifier class in src/main/java/com/payment/bridge/service/ErrorClassifier.java (4xx vs 5xx classification)
- [x] T024 [P] Create custom exceptions: PaymentProcessingException, IdempotencyViolationException in src/main/java/com/payment/bridge/exception/

### Virtual Threads & Async Configuration

- [x] T025 Create AsyncConfig class in src/main/java/com/payment/bridge/config/AsyncConfig.java
  - Virtual Threads executor for payment processing
  - Concurrency settings: 20 max concurrent tasks

- [x] T026 Create DatabaseConfig class in src/main/java/com/payment/bridge/config/DatabaseConfig.java
  - HikariCP pool configuration (20 maximum, 5 minimum)
  - Transaction management for optimistic locking

### Health & Monitoring

- [x] T027 [P] Create HealthController in src/main/java/com/payment/bridge/controller/HealthController.java (/actuator/health endpoint)
- [x] T028 [P] Create MetricsConfiguration for payment processing metrics in src/main/java/com/payment/bridge/config/MetricsConfig.java

**Checkpoint**: Foundation complete - all user story implementation can proceed in parallel

---

## Phase 3: User Story 1 - Payment Request Ingestion (Priority: P1) 🎯 MVP

**Goal**: Accept payment requests and persist them to DB in RECEIVED state before MQ forwarding  
**Independent Test**: Send request, verify DB has RECEIVED status within 100ms, no duplicate IDs

**NFR Coverage**: FR-001 (Gateway Persistence), FR-005 (Idempotency), NFR-002 (Restart Recovery)

### Tests for US1 (Write these FIRST ❌ should fail)

- [x] T029 [P] [US1] Unit test PaymentService.createPayment() idempotency in src/test/java/com/payment/bridge/service/PaymentServiceTest.java
- [x] T030 [P] [US1] Unit test duplicate payment rejection in src/test/java/com/payment/bridge/service/IdempotencyServiceTest.java
- [x] T031 [US1] Contract test POST /api/v1/payments endpoint in src/test/java/com/payment/bridge/contract/PaymentIngestTest.java
- [x] T032 [US1] Integration test payment creation and MQ publish in src/test/java/com/payment/bridge/integration/PaymentIngestIntegrationTest.java
- [x] T033 [US1] Integration test system crash recovery scenario in src/test/java/com/payment/bridge/integration/PaymentRecoveryTest.java

### Implementation for US1

- [x] T034 [P] [US1] Create IdempotencyService in src/main/java/com/payment/bridge/service/IdempotencyService.java
  - Duplicate detection using client_reference (optional) or request signature
  - Idempotency key validation (X-Idempotency-Key header)

- [x] T035 [P] [US1] Create PaymentService.createPayment() in src/main/java/com/payment/bridge/service/PaymentService.java
  - Generate UUID payment_id
  - Persist to DB in RECEIVED state (atomic transaction)
  - Return response before MQ enqueue

- [x] T036 [US1] Create PaymentController POST /api/v1/payments in src/main/java/com/payment/bridge/controller/PaymentController.java
  - Request validation (amount > 0, currency ISO 4217)
  - Call PaymentService.createPayment()
  - Return PaymentResponse with 202 Accepted status

- [x] T037 [US1] Implement after-persistence MQ publish logic in PaymentService (publishes PROCESS_PAYMENT task)
- [x] T038 [US1] Add CRITICAL level logging for payment creation in PaymentService and PaymentController
- [x] T039 [US1] Create StatusController GET /api/v1/payments/status/{paymentId} in src/main/java/com/payment/bridge/controller/StatusController.java

**Checkpoint**: User Story 1 complete - Payment ingestion works independently with DB persistence guarantee

---

## Phase 4: User Story 2 - Message Queue Distribution (Priority: P1)

**Goal**: Workers pull from MQ and execute external API calls with proper task distribution  
**Independent Test**: Spawn 3 workers, enqueue 10 tasks, verify each processed exactly once

**NFR Coverage**: FR-002 (Scalable Processing), NFR-001 (Horizontal Correctness)

### Tests for US2 (Write FIRST ❌ should fail)

- [ ] T040 [P] [US2] Unit test PaymentWorker message listener in src/test/java/com/payment/bridge/worker/PaymentWorkerTest.java
- [ ] T041 [P] [US2] Unit test ExternalApiClient with Circuit Breaker in src/test/java/com/payment/bridge/client/ExternalApiClientTest.java
- [ ] T042 [US2] Integration test MQ task distribution across multiple workers in src/test/java/com/payment/bridge/integration/MQDistributionTest.java (3 workers, 10 tasks)
- [ ] T043 [US2] Integration test worker crash recovery (at-least-once semantics) in src/test/java/com/payment/bridge/integration/WorkerRecoveryTest.java

### Implementation for US2

- [ ] T044 [P] [US2] Create ExternalApiClient in src/main/java/com/payment/bridge/client/ExternalApiClient.java
  - HTTP client for mock API integration
  - Resilience4j Circuit Breaker decorator
  - Timeout: 5s connect, 2s read

- [ ] T045 [P] [US2] Create PaymentWorker listener in src/main/java/com/payment/bridge/worker/PaymentWorker.java
  - @RabbitListener for payment-processing queue
  - Manual ACK after successful processing
  - Virtual Thread executor integration

- [ ] T046 [US2] Implement worker state transition to IN_PROGRESS in PaymentWorker (before API call)
- [ ] T047 [US2] Add CRITICAL level logging for worker task pickup and processing in PaymentWorker
- [ ] T048 [US2] Create configuration for concurrent workers (concurrency: 10-50, prefetch: 20)

**Checkpoint**: User Story 2 complete - Horizontal scalability with MQ distribution works

---

## Phase 5: User Story 3 - Hybrid Retry and DLQ Handling (Priority: P1)

**Goal**: Implement resilient retry logic with error classification and DLQ escalation  
**Independent Test**: Simulate API failures, verify 5 retries with backoff, then DLQ

**NFR Coverage**: FR-004 (Exhaustive Retries), FR-004a (API Error Classification), FR-005 (DLQ Governance), NFR-005 (Observability)

### Tests for US3 (Write FIRST ❌ should fail)

- [ ] T049 [P] [US3] Unit test ErrorClassifier for 4xx/5xx classification in src/test/java/com/payment/bridge/service/ErrorClassifierTest.java
- [ ] T050 [P] [US3] Unit test RetryHandler exponential backoff calculation in src/test/java/com/payment/bridge/service/RetryHandlerTest.java
- [ ] T051 [P] [US3] Unit test DLQHandler entry creation with failure context in src/test/java/com/payment/bridge/service/DLQHandlerTest.java
- [ ] T052 [US3] Integration test API 5xx error retry with backoff in src/test/java/com/payment/bridge/integration/APIRetryTest.java
- [ ] T053 [US3] Integration test API 4xx error immediate DLQ in src/test/java/com/payment/bridge/integration/APIErrorClassificationTest.java
- [ ] T054 [US3] Integration test DB update retry in src/test/java/com/payment/bridge/integration/DBRetryTest.java
- [ ] T055 [US3] Integration test DLQ escalation after exhaustive retries in src/test/java/com/payment/bridge/integration/DLQEscalationTest.java

### Implementation for US3

- [ ] T056 [P] [US3] Create ErrorClassifier.classify() in src/main/java/com/payment/bridge/service/ErrorClassifier.java
  - Retry: Network errors, timeouts, 429, 500, 503, 504
  - Immediate DLQ: 400, 401, 403, all 4xx
  - Retry: All other 5xx except 501

- [ ] T057 [P] [US3] Create RetryHandler class in src/main/java/com/payment/bridge/service/RetryHandler.java
  - Calculate Base 1.5 exponential backoff: delay = (1.5^attempt - 1) seconds
  - Produce backoff queue (separate RabbitMQ queue with TTL)
  - Max 5 attempts, then DLQ

- [ ] T058 [P] [US3] Create DLQHandler in src/main/java/com/payment/bridge/service/DLQHandler.java
  - Create DLQ entries with full payment context snapshot
  - Store API response and retry history
  - Manual review workflow (operator dashboard - future phase)

- [ ] T059 [US3] Implement API retry loop in PaymentWorker.processPayment()
  - On retryable error: call RetryHandler to republish with TTL delay
  - On non-retryable error: call ErrorClassifier → DLQHandler

- [ ] T060 [US3] Implement DB update retry loop in PaymentWorker.updatePaymentStatus()
  - 5 immediate retries for DB failures
  - Manual ACK only after successful DB commit
  - On failure: DLQHandler

- [ ] T061 [US3] Create DLQListener for failed message processing in src/main/java/com/payment/bridge/worker/DLQListener.java
- [ ] T062 [US3] Add CRITICAL level logging for all retry attempts, error classifications, and DLQ entries

**Checkpoint**: User Story 3 complete - Resilience mechanism fully operational

---

## Phase 6: User Story 4 - State Transition Integrity (Priority: P2)

**Goal**: Ensure all state transitions are atomic and auditable  
**Independent Test**: Process payments through various scenarios, verify state transitions in DB

**NFR Coverage**: FR-003 (State Integrity), NFR-005 (Observability)

### Tests for US4 (Write FIRST ❌ should fail)

- [ ] T063 [P] [US4] Unit test state transition validation in PaymentService in src/test/java/com/payment/bridge/service/StateTransitionTest.java
- [ ] T064 [P] [US4] Unit test audit logging for each transition in src/test/java/com/payment/bridge/service/PaymentAuditTest.java
- [ ] T065 [US4] Integration test state progression (RECEIVED → IN_PROGRESS → COMPLETED) in src/test/java/com/payment/bridge/integration/StateTransitionTest.java
- [ ] T066 [US4] Integration test failure state (RECEIVED → IN_PROGRESS → FAILED) in src/test/java/com/payment/bridge/integration/FailureStateTest.java

### Implementation for US4

- [ ] T067 [P] [US4] Create PaymentAuditService in src/main/java/com/payment/bridge/service/PaymentAuditService.java
  - Log every state transition: old_status, new_status, timestamp, reason
  - Atomic with state update (same transaction)

- [ ] T068 [US4] Update PaymentService with explicit state validation
  - Only allow valid transitions: RECEIVED → IN_PROGRESS, IN_PROGRESS → COMPLETED, IN_PROGRESS → FAILED
  - Reject invalid transitions with exception

- [ ] T069 [US4] Create StateTransitionController GET /api/v1/payments/{paymentId}/audit in src/main/java/com/payment/bridge/controller/StateTransitionController.java
  - Return all state transitions with timestamps and reasons

- [ ] T070 [US4] Add CRITICAL level logging for all state transitions in PaymentService

**Checkpoint**: User Story 4 complete - Full audit trail of payment lifecycle

---

## Phase 7: User Story 5 - Latency Resilience (Priority: P2)

**Goal**: Ensure ingestion pipeline remains responsive under API latency (10ms-2s delays)  
**Independent Test**: Simulate 2s API delays, measure ingestion thread latency p99 < 500ms

**NFR Coverage**: NFR-004 (Latency Tolerance), NFR-003 (Performance Baseline)

### Tests for US5 (Write FIRST ❌ should fail)

- [ ] T071 [P] [US5] Load test with simulated API delays in src/test/java/com/payment/bridge/load/LatencyLoadTest.java (Gatling)
  - 100 concurrent ingestion requests
  - 2s API response delay
  - Measure ingestion p99 latency < 500ms

- [ ] T072 [US5] Load test with mixed API latencies (10ms + 2s) in src/test/java/com/payment/bridge/load/MixedLatencyLoadTest.java
- [ ] T073 [US5] Integration test that ingestion threads are not blocked during worker processing in src/test/java/com/payment/bridge/integration/NonBlockingTest.java

### Implementation for US5

- [ ] T074 [P] [US5] Configure Virtual Threads executor with sufficient capacity in AsyncConfig
- [ ] T075 [P] [US5] Verify PaymentController uses non-blocking pattern (async HTTP responses)
- [ ] T076 [US5] Verify PaymentWorker uses Virtual Threads (no traditional thread blocking)
- [ ] T077 [US5] Create latency metrics collection in src/main/java/com/payment/bridge/metrics/LatencyMetrics.java
  - Track ingestion request latency percentiles
  - Track worker processing latency percentiles
- [ ] T078 [US5] Add CRITICAL level logging for slow operations (> 100ms)

**Checkpoint**: User Story 5 complete - System remains responsive under load

---

## Phase 8: Polish & Cross-Cutting Concerns 🎯 Production Ready

### Testing & Quality

- [ ] T079 [P] Setup code coverage reporting (JaCoCo) - target 80%+ coverage
- [ ] T080 [P] Configure SonarQube integration for code quality scanning
- [ ] T081 Create end-to-end test suite in src/test/java/com/payment/bridge/e2e/ that covers:
  - Payment ingestion through completion
  - Failure scenario with DLQ escalation
  - Horizontal scaling with 3 workers
  - State transition audit trail

### Documentation & Operations

- [ ] T082 [P] Create API documentation (OpenAPI/Swagger) in src/main/resources/api-docs.yml
- [ ] T083 [P] Create deployment guide in docs/DEPLOYMENT.md
- [ ] T084 Create operational runbook in docs/OPERATIONS.md covering:
  - DLQ manual review process
  - Performance tuning parameters
  - Troubleshooting guide
  - Scaling procedures

### Monitoring & Observability

- [ ] T085 [P] Setup Prometheus metrics export in MetricsConfig
- [ ] T086 [P] Create Spring Boot Actuator dashboard integration
- [ ] T087 Create alerting rules for:
  - High DLQ rate (> 5%)
  - Ingestion latency p99 > 500ms
  - Worker processing failures
  - DB connection pool exhaustion

### Performance Tuning

- [ ] T088 Performance profiling with JFR (Java Flight Recorder) to validate Virtual Threads benefit
- [ ] T089 Database query optimization - verify all payment queries use indexes
- [ ] T090 RabbitMQ consumer concurrency tuning (prefetch, concurrency limits)

### Security Hardening

- [ ] T091 [P] Add input validation for all API endpoints
- [ ] T092 [P] Add rate limiting for payment ingestion API
- [ ] T093 [P] Secure sensitive data in logs (redact payment amounts, PII)
- [ ] T094 [P] Add authentication/authorization framework (future: API keys, mTLS)

### Continuous Improvement

- [ ] T095 [P] Setup CI/CD pipeline (.github/workflows/) - build, test, security scan
- [ ] T096 [P] Create release checklist and deployment procedures
- [ ] T097 Document lessons learned and architecture decisions
- [ ] T098 Create backlog for Phase 2 enhancements:
  - Customer-facing dashboard
  - Advanced DLQ resolution workflows
  - Metrics-driven auto-scaling
  - PCI compliance framework

---

## Task Dependency Graph

```
Phase 1 (Setup)
    ↓
Phase 2 (Foundational) ⚠️ CRITICAL GATE
    ↓
    ├─→ Phase 3 (US1: Ingestion) 🎯 MVP
    │       ↓
    ├─→ Phase 4 (US2: MQ Distribution)
    │       ↓ (can run in parallel with US1)
    ├─→ Phase 5 (US3: Retry/DLQ)
    │       ↓ (depends on US1 + US2)
    ├─→ Phase 6 (US4: State Transitions)
    │       ↓ (can run after US1)
    ├─→ Phase 7 (US5: Latency Resilience)
    │       ↓ (can run in parallel, depends on all workers)
    ↓
Phase 8 (Polish & Cross-Cutting)
```

## Parallel Execution Strategy

**MVP Scope (Phase 3 only)**: US1 complete in isolation - demonstrates zero data loss persistence

**Phase 4 Start Point**: After T037 (Phase 3 MQ publish implemented)

- Tasks T040-T048 can run while Phase 3 completes
- Requires PaymentPublisher from Phase 2 (T021)

**Phase 5 Start Point**: After Phase 4 workers processing payments

- Tasks T049-T062 depend on payment status updates from worker
- Can begin after T046 (worker state transition to IN_PROGRESS)

**Phase 6 Start Point**: After T037 (Phase 3 MQ logic)

- State auditing independent of worker implementation
- Can run in parallel with Phase 4-5

**Phase 7 Start Point**: After Phase 4 workers operational

- Load testing requires active workers pulling from queue
- Should begin after T048 (worker configuration complete)

## Success Criteria per Phase

| Phase           | Task Count | Criteria                                    | Owner        |
| --------------- | ---------- | ------------------------------------------- | ------------ |
| 1. Setup        | 8          | Maven + Spring Boot + DTOs                  | Dev Team     |
| 2. Foundational | 19         | DB + RabbitMQ + Models + Resilience         | Dev Team     |
| 3. US1          | 11         | Payment creation + idempotency (100ms)      | Dev Team     |
| 4. US2          | 9          | Worker distribution (exact-once semantics)  | Dev Team     |
| 5. US3          | 19         | Retry + backoff + DLQ (5 attempts)          | Dev Team     |
| 6. US4          | 8          | State audit trail (atomic transitions)      | Dev Team     |
| 7. US5          | 5          | Latency p99 < 500ms under 2s delays         | Dev Team     |
| 8. Polish       | 24         | Coverage + docs + monitoring + CI/CD        | Dev Team     |
| **TOTAL**       | **103**    | **All constitutional principles validated** | **Dev Team** |

---

## Testing Summary

**TDD Approach**: Write tests BEFORE implementation

**Test Pyramid**:

- Unit Tests (30%): Individual service logic - T029-T030, T040-T041, T049-T051, T063-T064, T071-T073
- Contract Tests (25%): API endpoint contracts - T031, T043
- Integration Tests (35%): End-to-end flows with real DB/MQ - T032-T033, T042-T043, T052-T066
- Load Tests (10%): Performance under stress - Gatling tests in Phase 7 (T071-T073)

**Coverage Target**: 80%+ code coverage across all packages

**Validation**:

- Zero data loss: T032 + audit trail verification
- Horizontal scaling: T042 (3 workers, 10 tasks)
- Retry effectiveness: T052-T055 (error classification, backoff, DLQ)
- Latency resilience: T071-T073 (p99 < 500ms)
- Observability: T070, T078, T085-T087 (CRITICAL logging, metrics)

---

## Next Steps (After Task Completion)

1. ✅ **Phase 1-3**: MVP payment ingestion delivered and tested
2. ✅ **Phase 4-5**: Full resilience with retry + DLQ operational
3. ✅ **Phase 6-7**: Production-ready auditing and performance
4. 🔜 **Phase 8**: CI/CD + documentation + operational excellence
5. 🔜 **Phase 2 (Future)**: Customer dashboard, auto-scaling, advanced DLQ workflows
