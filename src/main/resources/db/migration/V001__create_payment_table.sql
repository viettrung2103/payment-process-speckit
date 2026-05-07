CREATE TABLE IF NOT EXISTS payment (
    payment_id UUID PRIMARY KEY,
    version INTEGER NOT NULL DEFAULT 0,
    client_reference VARCHAR(255),
    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    api_response JSONB,
    api_status_code INTEGER,
    external_transaction_id VARCHAR(255),
    retry_count_api INTEGER NOT NULL DEFAULT 0,
    retry_count_db INTEGER NOT NULL DEFAULT 0,
    error_reason TEXT,
    error_details JSONB
);

CREATE INDEX IF NOT EXISTS idx_payment_status ON payment(status);
CREATE INDEX IF NOT EXISTS idx_payment_client_reference ON payment(client_reference);
CREATE UNIQUE INDEX IF NOT EXISTS idx_payment_idempotency ON payment(client_reference) WHERE client_reference IS NOT NULL;
