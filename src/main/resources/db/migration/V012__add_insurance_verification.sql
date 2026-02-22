-- V012: Add insurance verification tracking columns
-- Supports P4.4 Bulk Insurance Verification feature

ALTER TABLE insurance
    ADD COLUMN verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN last_verified_at TIMESTAMPTZ;

-- Index for efficient queries by verification_status
CREATE INDEX idx_insurance_verification_status ON insurance(verification_status);

COMMENT ON COLUMN insurance.verification_status IS
    'Insurance verification status: PENDING, VERIFIED, INCOMPLETE, STALE';
COMMENT ON COLUMN insurance.last_verified_at IS
    'Timestamp of the most recent verification check';
