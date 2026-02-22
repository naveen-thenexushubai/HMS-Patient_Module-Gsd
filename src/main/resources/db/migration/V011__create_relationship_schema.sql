-- V011: Patient relationship and guarantor management.
-- Supports formal typed relationships (guardian, spouse, guarantor, etc.)
-- beyond the simple emergency-contact name+phone already captured.
-- A relationship can reference another patient in the system OR an external person.

CREATE TABLE patient_relationships (
    id                          BIGSERIAL    PRIMARY KEY,
    patient_business_id         UUID         NOT NULL,
    related_patient_business_id UUID,                       -- null if external person
    related_person_name         VARCHAR(100),                -- required if no relatedPatientBusinessId
    related_person_phone        VARCHAR(20),
    related_person_email        VARCHAR(255),
    relationship_type           VARCHAR(50)  NOT NULL,
    is_guarantor                BOOLEAN      NOT NULL DEFAULT false,
    guarantor_account_id        VARCHAR(100),
    notes                       TEXT,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by                  VARCHAR(255) NOT NULL
);

CREATE INDEX idx_patient_rel_patient ON patient_relationships(patient_business_id);
CREATE INDEX idx_patient_rel_related ON patient_relationships(related_patient_business_id)
    WHERE related_patient_business_id IS NOT NULL;
