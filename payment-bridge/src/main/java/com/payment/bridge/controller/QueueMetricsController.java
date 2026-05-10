package com.payment.bridge.controller;

import com.payment.bridge.service.QueueMetricsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/queue-metrics")
public class QueueMetricsController {

    private final QueueMetricsService queueMetricsService;

    public QueueMetricsController(QueueMetricsService queueMetricsService) {
        this.queueMetricsService = queueMetricsService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getQueueMetrics() {
        Map<String, Object> metrics = Map.of(
                "paymentQueueDepth", queueMetricsService.getPaymentQueueDepth(),
                "retryQueueDepth", queueMetricsService.getRetryQueueDepth(),
                "dlqQueueDepth", queueMetricsService.getDlqQueueDepth(),
                "dlqSize", queueMetricsService.getDlqSize()
        );
        return ResponseEntity.ok(metrics);
    }
}