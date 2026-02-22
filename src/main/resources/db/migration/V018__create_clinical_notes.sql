CREATE TABLE clinical_notes (
    id BIGSERIAL PRIMARY KEY,
    business_id UUID NOT NULL DEFAULT gen_random_uuid(),
    patient_business_id UUID NOT NULL,
    appointment_business_id UUID,
    note_type VARCHAR(30) NOT NULL DEFAULT 'SOAP',
    subjective TEXT,
    objective TEXT,
    assessment TEXT,
    plan TEXT,
    is_finalized BOOLEAN NOT NULL DEFAULT FALSE,
    finalized_at TIMESTAMPTZ,
    finalized_by VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255) NOT NULL,
    updated_at TIMESTAMPTZ,
    updated_by VARCHAR(255),
    CONSTRAINT uq_clinical_notes_business_id UNIQUE (business_id),
    CONSTRAINT chk_note_type CHECK (note_type IN ('SOAP','PROGRESS','ADMISSION','DISCHARGE'))
);

CREATE INDEX idx_clinical_notes_patient ON clinical_notes(patient_business_id, created_at DESC);
