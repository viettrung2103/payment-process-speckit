package com.payment.bridge.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class PaymentJdbcTemplate {

    private final JdbcTemplate jdbcTemplate;

    public PaymentJdbcTemplate(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Atomically update payment status with optimistic locking.
     * Returns true if update succeeded, false if version conflict.
     */
    public boolean updateStatusWithLock(UUID paymentId, int expectedVersion, String newStatus) {
        String sql = """
            UPDATE payment
            SET status = ?, version = version + 1, updated_at = NOW()
            WHERE payment_id = ? AND version = ?
            """;

        int updated = jdbcTemplate.update(sql, newStatus, paymentId, expectedVersion);
        return updated > 0;
    }

    /**
     * Update payment with API response data and optimistic locking.
     */
    public boolean updateWithApiResponse(UUID paymentId, int expectedVersion, String newStatus,
                                       String apiResponse, Integer apiStatusCode, String externalTransactionId) {
        String sql = """
            UPDATE payment
            SET status = ?, version = version + 1, updated_at = NOW(),
                api_response = ?::jsonb, api_status_code = ?, external_transaction_id = ?
            WHERE payment_id = ? AND version = ?
            """;

        int updated = jdbcTemplate.update(sql, newStatus, apiResponse, apiStatusCode,
                                        externalTransactionId, paymentId, expectedVersion);
        return updated > 0;
    }

    /**
     * Update payment with error details and optimistic locking.
     */
    public boolean updateWithError(UUID paymentId, int expectedVersion, String newStatus,
                                 String errorReason, String errorDetails) {
        String sql = """
            UPDATE payment
            SET status = ?, version = version + 1, updated_at = NOW(),
                error_reason = ?, error_details = ?::jsonb
            WHERE payment_id = ? AND version = ?
            """;

        int updated = jdbcTemplate.update(sql, newStatus, errorReason, errorDetails,
                                        paymentId, expectedVersion);
        return updated > 0;
    }
}