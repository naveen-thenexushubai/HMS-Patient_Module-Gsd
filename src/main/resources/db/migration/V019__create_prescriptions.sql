CREATE TABLE prescriptions (
    id BIGSERIAL PRIMARY KEY,
    business_id UUID NOT NULL DEFAULT gen_random_uuid(),
    patient_business_id UUID NOT NULL,
    appointment_business_id UUID,
    medication_name VARCHAR(255) NOT NULL,
    generic_name VARCHAR(255),
    dosage VARCHAR(100) NOT NULL,
    frequency VARCHAR(100) NOT NULL,
    duration_days INTEGER,
    quantity_dispensed INTEGER,
    refills_remaining INTEGER NOT NULL DEFAULT 0,
    instructions TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    prescribed_by VARCHAR(255) NOT NULL,
    prescribed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at DATE,
    discontinue_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255) NOT NULL,
    updated_at TIMESTAMPTZ,
    updated_by VARCHAR(255),
    CONSTRAINT uq_prescriptions_business_id UNIQUE (business_id),
    CONSTRAINT chk_prescription_status CHECK (status IN ('ACTIVE','COMPLETED','DISCONTINUED','EXPIRED')),
    CONSTRAINT chk_refills CHECK (refills_remaining >= 0)
);

CREATE INDEX idx_prescriptions_patient ON prescriptions(patient_business_id, prescribed_at DESC);
CREATE INDEX idx_prescriptions_active ON prescriptions(patient_business_id, status) WHERE status = 'ACTIVE';
