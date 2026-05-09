-- Migration V005: Error details column type fix
-- Note: Entity now properly declares error_details as JSONB, so no database change needed
-- This migration is kept for reference but does nothing

-- Column is already JSONB in V001, entity updated to match