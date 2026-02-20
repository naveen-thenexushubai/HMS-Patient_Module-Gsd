-- V003__create_insurance_schema.sql
-- INS-01, INS-03: Patient insurance information linked via patient_business_id
-- No FK to patients table (event-sourced pattern; same as emergency_contacts)
-- policy_number and group_number are AES-256-GCM encrypted by SensitiveDataConverter

CREATE TABLE insurance (
    id BIGSERIAL PRIMARY KEY,
    patient_business_id UUID NOT NULL,

    -- Non-PHI fields (not encrypted, safe to index)
    provider_name VARCHAR(255) NOT NULL,
    coverage_type VARCHAR(50) NOT NULL,
    is_active BOOLEAN DEFAULT true,

    -- PHI fields: encrypted at rest by SensitiveDataConverter (AES-256-GCM + 12-byte IV + base64)
    -- Column width 512 accommodates IV (12 bytes) + ciphertext + base64 overhead
    -- DO NOT add indexes to these columns: ciphertext is non-deterministic (random IV each time)
    policy_number VARCHAR(512) NOT NULL,
    group_number  VARCHAR(512),

    -- Audit fields: mutable table uses both created + last-modified tracking
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(255) NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE,
    updated_by  VARCHAR(255)
);

CREATE INDEX idx_insurance_patient ON insurance(patient_business_id);
CREATE INDEX idx_insurance_active  ON insurance(patient_business_id, is_active);

COMMENT ON TABLE insurance IS 'Patient insurance. policy_number and group_number are AES-256-GCM encrypted PHI.';
COMMENT ON COLUMN insurance.policy_number IS 'AES-256-GCM encrypted via SensitiveDataConverter — do not add index';
COMMENT ON COLUMN insurance.group_number  IS 'AES-256-GCM encrypted via SensitiveDataConverter — do not add index';
