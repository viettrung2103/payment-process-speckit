package com.payment.bridge.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "payment-bridge"
        ));
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, String>> ready() {
        return ResponseEntity.ok(Map.of(
            "status", "READY",
            "service", "payment-bridge"
        ));
    }
}