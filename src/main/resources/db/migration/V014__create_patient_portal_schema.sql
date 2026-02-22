-- V014: Patient portal self-service authentication
-- Patients activate portal with patientId+DOB, then log in with email+PIN

CREATE TABLE patient_credentials (
    id                  BIGSERIAL     PRIMARY KEY,
    patient_business_id UUID          NOT NULL,
    email               VARCHAR(255)  NOT NULL,
    pin_hash            VARCHAR(255)  NOT NULL,
    is_active           BOOLEAN       NOT NULL DEFAULT true,
    last_login_at       TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_patient_cred_business_id UNIQUE (patient_business_id),
    CONSTRAINT uq_patient_cred_email       UNIQUE (email)
);

CREATE INDEX idx_patient_credentials_email   ON patient_credentials(email);
CREATE INDEX idx_patient_credentials_patient ON patient_credentials(patient_business_id);

COMMENT ON TABLE patient_credentials IS
    'Portal authentication credentials for patient self-service. PIN-based login with JWT.';
COMMENT ON COLUMN patient_credentials.pin_hash IS
    'BCrypt-hashed PIN (4–8 chars). Never stored in plaintext.';
