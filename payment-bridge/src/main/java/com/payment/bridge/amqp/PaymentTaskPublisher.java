package com.payment.bridge.amqp;

import com.payment.bridge.model.MessageQueueTask;

public interface PaymentTaskPublisher {
    void publishPaymentTask(MessageQueueTask task);
    void publishRetryTask(MessageQueueTask task);
    void publishPaymentTaskWithDelay(MessageQueueTask task, long delayMillis);
}
