# Feature Specification: Resilient Distributed Payment Bridge

**Feature ID**: F-PAY-001  
**Feature Branch**: `001-resilient-payment-bridge`  
**Created**: 2026-05-07  
**Status**: Specification Phase  
**Input**: User description: "Phase 1: Feature Specification (spec.md) - Resilient Distributed Payment Bridge with high-throughput, horizontally scalable payment middleware ensuring zero data loss through message-driven architecture and persistent state management"

## Objective and Scope

### Objective
Build a high-throughput, horizontally scalable payment middleware that ensures zero data loss through a message-driven architecture and persistent state management.

### Scope
Includes ID generation, persistent ingestion, MQ task distribution, and hybrid retry logic for API/DB interactions.

**Out of Scope (Phase 1)**:
- UI/Frontend components
- Direct customer-facing APIs (gateway interface only)
- Payment method tokenization or PCI compliance auditing

---

## Functional Requirements

### FR-001: Gateway Persistence
- **Description**: Generate payment_id and save to DB in RECEIVED state before MQ enqueueing.
- **Acceptance**: Payment must exist in DB before any downstream processing begins.
- **Related Principle**: Principle 1 (The Law of Idempotency), No-Loss Persistence

### FR-002: Scalable Processing
- **Description**: Workers pull from MQ to execute external REST calls.
- **Acceptance**: Multiple worker instances can simultaneously pull unique tasks without conflicts.
- **Related Principle**: Principle 2 (MQ-Driven Statelessness)

### FR-003: State Integrity
- **Description**: Transition status to IN_PROGRESS during API call and COMPLETED/FAILED only after DB confirmation.
- **Acceptance**: State transitions follow strict RECEIVED → IN_PROGRESS → (COMPLETED or FAILED) flow.
- **Related Principle**: Explicit State Transitions

### FR-004: Exhaustive Retries
- **Description**: Implement the 5-attempt API retry and 5-attempt DB retry cycles.
- **Rules**:
  - API failures: 5 retries with **Base 1.5 exponential backoff** (formula: delay = (1.5^attempt - 1) seconds)
    - Attempt 1: 0.5s, Attempt 2: 1.25s, Attempt 3: 2.25s, Attempt 4: 3.375s, Attempt 5: 4.75s
    - Total window: ~12 seconds, then DLQ
  - DB update failures: 5 immediate retries after successful API response, then DLQ
- **Related Principle**: Principle 3 & 4 (Hybrid Retry Mechanisms)

### FR-004a: API Error Classification
- **Description**: Classify API errors by HTTP status code to optimize retry strategy.
- **Rules**:
  - **Retry with exponential backoff (5x)**: Network errors, timeouts, HTTP 429 (Rate Limited), 500 (Internal Server Error), 503 (Service Unavailable), 504 (Gateway Timeout)
  - **Immediate DLQ (no retry)**: HTTP 400 (Bad Request), 401 (Unauthorized), 403 (Forbidden), all 4xx client errors
  - **Retry with exponential backoff (5x)**: All other 5xx server errors except 501 (Not Implemented)
- **Rationale**: Client errors indicate a structural problem with the request that won't resolve with retries; server errors are often transient and will recover with backoff.

### FR-005: DLQ Governance
- **Description**: Failed payments after exhaustive retries sent to Dead Letter Queue for manual review.
- **Acceptance**: DLQ entries include full payment context, failure history, and retry attempts.
- **Related Principle**: Principle 5 (The "Hall of Shame")

---

## Non-Functional Requirements

### NFR-001: Horizontal Correctness
- **Description**: Multiple instances must not process the same payment_id (Idempotency).
- **Acceptance**: Verified through concurrent load testing across N instances.
- **Related Principle**: Principle 2 (MQ-Driven Statelessness)

### NFR-002: Restart Reliability
- **Description**: Payments stuck in RECEIVED or IN_PROGRESS must be recovered from the DB/MQ on restart.
- **Acceptance**: No payments lost due to application restart or crash.
- **Related Principle**: Principle 1 (The Law of Idempotency)

### NFR-003: Performance Baseline
- **Description**: Demonstrated throughput increase when scaling from 1 to N instances.
- **Acceptance**: Measurable linear or near-linear throughput improvement with horizontal scaling.
- **Related Principle**: Principle 2 (MQ-Driven Statelessness)

### NFR-004: Latency Tolerance
- **Description**: System handles 10ms-2s API response delays without blocking primary ingestion threads.
- **Acceptance**: Ingestion thread pool remains responsive during external API latency.
- **Related Principle**: Principle 6 (Latency and Failure Transparency)

### NFR-005: Observability
- **Description**: All exceptions logged at CRITICAL level; no silent failures permitted.
- **Acceptance**: Every payment state transition and error logged with context for debugging.
- **Related Principle**: Principle 6 (Latency and Failure Transparency)

---

## User Scenarios & Testing

### User Story 1: Payment Request Ingestion (Priority: P1)

As a **payment gateway**, I need to receive a payment request, generate a unique payment_id, and persist it to the database before forwarding to processing workers, so that no payment is lost even if the system crashes.

**Why this priority**: This is the foundation of the entire system. Without guaranteed persistence at entry, all downstream guarantees fail.

**Independent Test**: Can be fully tested by sending a payment request, verifying it appears in DB with RECEIVED status within 100ms, and validating no duplicate IDs are generated.

**Acceptance Scenarios**:

1. **Given** a valid payment request arrives at the gateway, **When** the gateway processes it, **Then** a unique payment_id is generated and the payment record is persisted in RECEIVED state before the response is sent to the client.

2. **Given** two identical payment requests arrive simultaneously, **When** both are processed, **Then** the second request is rejected as a duplicate using idempotency key validation.

3. **Given** the system crashes after DB persistence but before response is sent, **When** the system restarts, **Then** the payment is recoverable from the database.

---

### User Story 2: Message Queue Distribution (Priority: P1)

As a **payment worker**, I need to pull payment processing tasks from a central Message Queue, so that any number of worker instances can process payments concurrently without conflicts.

**Why this priority**: Horizontal scalability depends entirely on stateless workers pulling from a shared queue.

**Independent Test**: Can be fully tested by spawning multiple workers, enqueuing tasks, and verifying each task is processed exactly once across all workers without duplication or missing tasks.

**Acceptance Scenarios**:

1. **Given** 10 payment tasks in the Message Queue, **When** 3 worker instances are running, **Then** all 10 tasks are processed exactly once across the 3 instances without conflicts.

2. **Given** a worker instance crashes during task processing, **When** the task is pulled back from the queue, **Then** another worker can pick it up and process it (at-least-once semantics).

3. **Given** multiple workers pulling from the queue simultaneously, **When** payment requests arrive at high throughput, **Then** tasks are distributed and processed without race conditions.

---

### User Story 3: Hybrid Retry and DLQ Handling (Priority: P1)

As a **system operator**, I need to have confidence that failed payments are not silently lost but are either retried or escalated to manual review, so that I can investigate and resolve critical payment failures.

**Why this priority**: The entire resilience story depends on proper retry and escalation mechanisms.

**Independent Test**: Can be fully tested by simulating API failures, DB failures, and verifying correct retry counts and DLQ placement with full failure context.

**Acceptance Scenarios**:

1. **Given** the external Payment API returns a network error on the first attempt, **When** the worker retries with exponential backoff up to 5 times, **Then** either the API eventually succeeds or the payment is moved to DLQ with retry history intact.

2. **Given** the API call succeeds but the DB update fails, **When** the worker retries the DB update 5 times, **Then** either the DB is updated or the payment is moved to DLQ with API response and DB failure context.

3. **Given** a payment in the DLQ, **When** an operator reviews it, **Then** they can see the full payment context, API response, DB state, and all retry attempts.

---

### User Story 4: State Transition Integrity (Priority: P2)

As a **payment auditor**, I need to see clear, explicit state transitions in the database, so that I can trace exactly where any payment failed or succeeded.

**Why this priority**: Debugging and compliance require absolute clarity on payment lifecycle.

**Independent Test**: Can be fully tested by processing payments through various scenarios and verifying state transitions are recorded atomically and sequentially.

**Acceptance Scenarios**:

1. **Given** a payment is persisted in RECEIVED state, **When** a worker begins processing, **Then** the state transitions to IN_PROGRESS before any external call.

2. **Given** the API call completes successfully and DB updates, **When** the operation commits, **Then** the state transitions to COMPLETED with API response data.

3. **Given** the API fails after retries, **When** all retries are exhausted, **Then** the state transitions to FAILED with reason code and retry count.

---

### User Story 5: Latency Resilience (Priority: P2)

As a **payment processor**, I need the system to remain responsive even when the external Payment API is slow (10ms-2s delays), so that the ingestion pipeline doesn't get blocked and other payments can continue processing.

**Why this priority**: System responsiveness under adverse conditions is critical for reliability.

**Independent Test**: Can be fully tested by simulating API delays and measuring ingestion thread latency distribution.

**Acceptance Scenarios**:

1. **Given** the Payment API is responding slowly (2s delays), **When** workers are processing payments, **Then** primary ingestion threads remain responsive and can accept new payments.

2. **Given** high concurrency with mixed 10ms and 2s API responses, **When** the system is under load, **Then** no ingestion threads are blocked indefinitely.

---

## Data Model

### Payment Record
```
payment_id: String (UUID, PRIMARY KEY, UNIQUE)
version: Integer (default 0, for optimistic locking)
client_reference: String (optional, for de-duplication)
amount: Decimal
currency: String (ISO 4217)
status: Enum (RECEIVED, IN_PROGRESS, COMPLETED, FAILED)
created_at: Timestamp
updated_at: Timestamp
api_response: JSON (nullable, API response data)
api_status_code: Integer (nullable)
retry_count_api: Integer (default 0)
retry_count_db: Integer (default 0)
error_reason: String (nullable)
external_transaction_id: String (nullable, from external API)
```

### Message Queue Task
```
task_id: String (UUID)
payment_id: String (foreign key to Payment)
action: Enum (PROCESS_PAYMENT, RETRY_DB_UPDATE)
retry_attempt: Integer
enqueued_at: Timestamp
dequeue_count: Integer
```

### Dead Letter Queue Entry
```
dlq_id: String (UUID)
payment_id: String (foreign key to Payment)
failed_action: Enum (API_CALL, DB_UPDATE)
failure_reason: String
payment_context: JSON (snapshot of payment record at failure)
api_response: JSON (nullable)
retry_history: Array of objects
created_at: Timestamp
```

---

## Technical Assumptions & Constraints

- **Message Queue**: RabbitMQ with manual ACK mode and TTL-based DLQ routing for at-least-once delivery semantics
- **Database**: PostgreSQL with ACID compliance and distributed locking support
- **External API**: Mock REST API with intentional failures and 10ms-2s random delays for testing
- **Worker Concurrency**: Non-blocking I/O using async/await or thread pooling
- **Idempotency**: Payment_id uniqueness enforced at DB constraint level

---

## Success Criteria

### Measurable Outcomes
1. **Zero Data Loss**: All payment requests persisted to DB before any downstream processing (100% coverage audit).
2. **Horizontal Scalability**: Linear throughput improvement when scaling from 1 to 10 worker instances.
3. **Retry Effectiveness**: 95%+ of payments succeed within retry window; <5% require manual DLQ intervention.
4. **Latency Resilience**: Ingestion thread latency p99 remains under 500ms even with 2s API delays.
5. **Observability**: 100% of exceptions logged at CRITICAL level with payment context.

---

## Clarifications Recorded

### Session 2026-05-07

- **Q1: Scale Target and Performance Baseline** → A: Dynamic scaling optimization (Option D) with baseline target of 10 instances max, 1000 payments/minute per instance. Scaling improvements deferred to later phases.
- **Q2: Message Queue Technology** → A: RabbitMQ with manual ACK mode and TTL-based DLQ routing for at-least-once delivery semantics and native dead letter support.
- **Q3: Exponential Backoff Strategy** → B: Base 1.5 exponential backoff for faster failure escalation; total ~12 second retry window, then DLQ.
- **Q4: Database Concurrency & Locking** → A: Optimistic locking with version column; no row locks, prevents duplicate processing with high concurrency.
- **Q5: API Error Classification** → A: Retry transient errors (network, timeouts, 429, 5xx); immediate DLQ for client errors (4xx).

---

## Next Steps

1. **Clarification Phase**: Identify and resolve ambiguities in this specification.
2. **Implementation Planning**: Create detailed design and task breakdown.
3. **Development**: Build payment bridge components with TDD discipline.
4. **Testing**: Integration testing across scale scenarios and failure modes.
