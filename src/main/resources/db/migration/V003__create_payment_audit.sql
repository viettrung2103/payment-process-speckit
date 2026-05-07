CREATE TABLE IF NOT EXISTS payment_audit (
    audit_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID NOT NULL REFERENCES payment(payment_id),
    old_status VARCHAR(20),
    new_status VARCHAR(20) NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    changed_by VARCHAR(100), -- worker instance or 'system'
    reason TEXT,

    -- Foreign key constraint
    CONSTRAINT fk_audit_payment FOREIGN KEY (payment_id) REFERENCES payment(payment_id)
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_payment_audit_payment_id ON payment_audit(payment_id);
CREATE INDEX IF NOT EXISTS idx_payment_audit_changed_at ON payment_audit(changed_at DESC);