CREATE TABLE IF NOT EXISTS dead_letter_queue (
    dlq_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID NOT NULL REFERENCES payment(payment_id),
    failed_action VARCHAR(50) NOT NULL, -- 'API_CALL', 'DB_UPDATE'
    failure_reason TEXT NOT NULL,
    payment_context JSONB NOT NULL, -- Snapshot of payment record
    api_response JSONB, -- Last API response if available
    retry_history JSONB NOT NULL, -- Array of retry attempts
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Foreign key constraint
    CONSTRAINT fk_dlq_payment FOREIGN KEY (payment_id) REFERENCES payment(payment_id)
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_dlq_payment_id ON dead_letter_queue(payment_id);
CREATE INDEX IF NOT EXISTS idx_dlq_created_at ON dead_letter_queue(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_dlq_failed_action ON dead_letter_queue(failed_action);