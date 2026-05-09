package com.payment.bridge.controller;

import com.payment.bridge.model.DLQActionRequest;
import com.payment.bridge.model.DeadLetterQueueEntry;
import com.payment.bridge.service.DLQResolutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dlq")
public class DLQController {

    private static final Logger logger = LoggerFactory.getLogger(DLQController.class);

    private final DLQResolutionService dlqResolutionService;

    public DLQController(DLQResolutionService dlqResolutionService) {
        this.dlqResolutionService = dlqResolutionService;
    }

    @GetMapping
    public List<DeadLetterQueueEntry> searchDLQEntries(
            @RequestParam(value = "paymentId", required = false) UUID paymentId,
            @RequestParam(value = "failedAction", required = false) String failedAction) {
        return dlqResolutionService.searchDLQEntries(Optional.ofNullable(paymentId), Optional.ofNullable(failedAction));
    }

    @GetMapping("/{dlqId}")
    public ResponseEntity<DeadLetterQueueEntry> getDLQEntry(@PathVariable UUID dlqId) {
        try {
            return ResponseEntity.ok(dlqResolutionService.getDLQEntry(dlqId));
        } catch (IllegalArgumentException e) {
            logger.warn("DLQ entry not found: {}", dlqId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PostMapping("/{dlqId}/retry")
    public ResponseEntity<Void> retryDLQEntry(@PathVariable UUID dlqId,
                                              @Valid @RequestBody DLQActionRequest request) {
        try {
            dlqResolutionService.retryDLQEntry(dlqId, request);
            return ResponseEntity.accepted().build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            logger.warn("Unable to retry DLQ entry {}: {}", dlqId, e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Unexpected error retrying DLQ entry {}", dlqId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{dlqId}/resolve")
    public ResponseEntity<Void> resolveDLQEntry(@PathVariable UUID dlqId,
                                                @Valid @RequestBody DLQActionRequest request) {
        try {
            dlqResolutionService.resolveDLQEntry(dlqId, request);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            logger.warn("Unable to resolve DLQ entry {}: {}", dlqId, e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            logger.error("Unexpected error resolving DLQ entry {}", dlqId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}