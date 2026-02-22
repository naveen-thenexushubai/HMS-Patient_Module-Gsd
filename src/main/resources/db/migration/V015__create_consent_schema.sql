-- V015: Patient consent and document management
-- Checkbox acknowledgement + optional PDF upload per consent type

CREATE TABLE consent_records (
    id                    BIGSERIAL     PRIMARY KEY,
    business_id           UUID          NOT NULL DEFAULT gen_random_uuid(),
    patient_business_id   UUID          NOT NULL,
    consent_type          VARCHAR(40)   NOT NULL,
    status                VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    signed_at             TIMESTAMPTZ,
    signed_by             VARCHAR(255),
    expires_at            TIMESTAMPTZ,
    form_version          VARCHAR(20)   NOT NULL DEFAULT '1.0',
    notes                 TEXT,
    document_path         VARCHAR(500),
    document_filename     VARCHAR(255),
    document_content_type VARCHAR(100),
    ip_address            VARCHAR(45),
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by            VARCHAR(255)  NOT NULL,
    updated_at            TIMESTAMPTZ,
    updated_by            VARCHAR(255),
    CONSTRAINT uq_consent_business_id   UNIQUE (business_id),
    CONSTRAINT uq_consent_patient_type  UNIQUE (patient_business_id, consent_type),
    CONSTRAINT chk_consent_status CHECK (
        status IN ('PENDING','SIGNED','REVOKED','EXPIRED')
    ),
    CONSTRAINT chk_consent_type CHECK (
        consent_type IN (
            'HIPAA_NOTICE',
            'TREATMENT_AUTHORIZATION',
            'FINANCIAL_RESPONSIBILITY',
            'RESEARCH_PARTICIPATION',
            'PHOTOGRAPHY_PERMISSION'
        )
    )
);

CREATE INDEX idx_consent_patient        ON consent_records(patient_business_id);
CREATE INDEX idx_consent_type           ON consent_records(consent_type);
CREATE INDEX idx_consent_status         ON consent_records(status);
CREATE INDEX idx_consent_patient_type   ON consent_records(patient_business_id, consent_type);
CREATE INDEX idx_consent_expires        ON consent_records(expires_at)
    WHERE status = 'SIGNED';

COMMENT ON TABLE consent_records IS
    'HIPAA and treatment consent records. One record per patient per consent type (upsert on re-sign).';
COMMENT ON COLUMN consent_records.expires_at IS
    'HIPAA_NOTICE: +1 year from signing. TREATMENT_AUTHORIZATION: +5 years. Others: null.';
