-- V010: Patient family/household linking.
-- Patients can be grouped into households for shared demographics/insurance lookups.
-- A patient can belong to at most one household (UNIQUE constraint on patient_business_id).

CREATE TABLE patient_families (
    id                   BIGSERIAL PRIMARY KEY,
    household_id         UUID         NOT NULL,
    patient_business_id  UUID         NOT NULL,
    relationship_to_head VARCHAR(50)  NOT NULL DEFAULT 'MEMBER',
    is_head              BOOLEAN      NOT NULL DEFAULT false,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by           VARCHAR(255) NOT NULL,
    CONSTRAINT uq_patient_family UNIQUE (patient_business_id)
);

CREATE INDEX idx_patient_families_household ON patient_families(household_id);
CREATE INDEX idx_patient_families_patient   ON patient_families(patient_business_id);
