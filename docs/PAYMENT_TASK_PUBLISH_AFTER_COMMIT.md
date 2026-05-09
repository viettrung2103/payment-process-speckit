# Payment Task Publishing & Transaction Commit Race Condition

**Date:** 9 May 2026
**Area:** payment-bridge / asynchronous RabbitMQ task publishing

## Problem

When a new payment is created, the service saved the payment entity and then published a RabbitMQ processing task before the database transaction had committed.

Because task publication occurred within the same transaction, the worker could consume the message immediately and attempt to load the payment before the row was visible in the database.

This led to errors such as:

- `PaymentProcessingException: Payment not found for task: <paymentId>`
- `NACKed payment task due to processing exception`

## Root Cause

The `PaymentService.createPayment(...)` method is annotated with `@Transactional`.
Inside the transaction it:

1. creates and saves the `Payment` entity
2. publishes a `MessageQueueTask` to RabbitMQ

The payment task could be delivered to the worker before the transaction committed, causing the worker to query `paymentRepository.findById(task.getPaymentId())` and receive no result.

## Fix

Updated `payment-bridge/src/main/java/com/payment/bridge/service/PaymentService.java` so task publishing occurs only after the transaction commits.

### Key change

- `publishPaymentTask(Payment payment)` now registers a `TransactionSynchronization.afterCommit()` callback
- actual RabbitMQ publishing is deferred until after commit
- if no transaction is active, the task is published immediately as before

### Why this works

After commit, the `Payment` row is guaranteed to be visible to other transactions and consumers.
The worker will no longer process tasks for payments that are not yet committed.

## Validation

Verified with targeted unit testing:

- `mvn -q -Dtest=PaymentServiceTest test`
- Result: `EXIT:0`

This confirms the change compiles and the service logic behaves correctly under the existing unit test coverage.

## Suggestions for improvement

1. **Use `@TransactionalEventListener(phase = AFTER_COMMIT)`**
   - This is a cleaner Spring abstraction than manual synchronization registration.

2. **Consider an outbox pattern**
   - Persist pending task events in a local outbox table within the same transaction.
   - Publish them after commit from a separate process or dispatcher.
   - This improves reliability and makes messaging consistent with DB state.

3. **Add worker-side reconciliation**
   - If the worker receives a task and the payment is missing, defer or requeue instead of NACKing immediately.
   - This can prevent transient race failures if the message is delivered slightly early.

4. **Document transaction boundaries clearly**
   - Add comments and/or logs around asynchronous event publication to prevent future regressions.

## Files changed

- `payment-bridge/src/main/java/com/payment/bridge/service/PaymentService.java`

## Notes

- This fix does not depend on any externally configured `JAVA_HOME` variable.
- Local test execution can use auto-discovery for the installed JDK on macOS with `/usr/libexec/java_home`.

