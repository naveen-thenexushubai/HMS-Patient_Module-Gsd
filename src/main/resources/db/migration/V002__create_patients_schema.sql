-- V002__create_patients_schema.sql
-- Event-sourced patient data model with immutable versioned records
-- REG-01, REG-02, REG-03, REG-06, REG-07, REG-09, REG-12

-- Create sequence for patient ID generation
CREATE SEQUENCE patient_seq START WITH 1 INCREMENT BY 1;

-- Patients table (immutable, event-sourced)
CREATE TABLE patients (
    patient_id VARCHAR(20) PRIMARY KEY,
    business_id UUID NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    date_of_birth DATE NOT NULL,
    gender VARCHAR(20) NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    email VARCHAR(255),
    address_line1 VARCHAR(255),
    address_line2 VARCHAR(255),
    city VARCHAR(100),
    state VARCHAR(50),
    zip_code VARCHAR(10),
    photo_id_verified BOOLEAN DEFAULT false,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255) NOT NULL,
    CONSTRAINT uk_patients_business_version UNIQUE (business_id, version)
);

-- Indexes for query performance
CREATE INDEX idx_patients_business_id ON patients(business_id);
CREATE INDEX idx_patients_business_version ON patients(business_id, version DESC, created_at DESC);
CREATE INDEX idx_patients_dob ON patients(date_of_birth);
CREATE INDEX idx_patients_phone ON patients(phone_number);
CREATE INDEX idx_patients_email ON patients(email);
CREATE INDEX idx_patients_status ON patients(status);
CREATE INDEX idx_patients_last_name ON patients(last_name, first_name);

-- Unique constraint for duplicate prevention (active patients only)
CREATE UNIQUE INDEX idx_patients_unique_identity ON patients(
    LOWER(TRIM(first_name)),
    LOWER(TRIM(last_name)),
    date_of_birth,
    REGEXP_REPLACE(phone_number, '[^0-9]', '', 'g')
) WHERE status = 'ACTIVE';

-- Emergency contacts table
-- Note: No FK constraint on patient_business_id because it references a non-unique column
-- (business_id has multiple versions in event-sourced patients table).
-- Referential integrity enforced at application layer via JPA @ManyToOne relationship.
CREATE TABLE emergency_contacts (
    id BIGSERIAL PRIMARY KEY,
    patient_business_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    relationship VARCHAR(50) NOT NULL,
    is_primary BOOLEAN DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255) NOT NULL
);

CREATE INDEX idx_emergency_contacts_patient ON emergency_contacts(patient_business_id);

-- Medical histories table
-- Note: No FK constraint on patient_business_id for same reason as emergency_contacts.
-- Referential integrity enforced at application layer via JPA @ManyToOne relationship.
CREATE TABLE medical_histories (
    id BIGSERIAL PRIMARY KEY,
    patient_business_id UUID NOT NULL,
    blood_group VARCHAR(10),
    allergies TEXT,
    chronic_conditions TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255) NOT NULL
);

CREATE INDEX idx_medical_histories_patient ON medical_histories(patient_business_id);

-- Trigger to prevent updates (enforce immutability)
CREATE OR REPLACE FUNCTION prevent_patient_updates() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Patient records are immutable. Create new version instead.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_prevent_patient_updates
BEFORE UPDATE ON patients
FOR EACH ROW
EXECUTE FUNCTION prevent_patient_updates();

-- View for convenience (latest versions)
CREATE OR REPLACE VIEW patients_latest AS
SELECT DISTINCT ON (business_id) *
FROM patients
ORDER BY business_id, version DESC, created_at DESC;

-- Comments for documentation
COMMENT ON TABLE patients IS 'Event-sourced patient records (immutable, versioned)';
COMMENT ON COLUMN patients.business_id IS 'Immutable identifier across all versions';
COMMENT ON COLUMN patients.version IS 'Version number for event sourcing';
COMMENT ON COLUMN patients.photo_id_verified IS 'Placeholder for REG-12 (document upload in Phase 2)';
COMMENT ON TABLE emergency_contacts IS 'Emergency contact information linked to patient business_id';
COMMENT ON TABLE medical_histories IS 'Medical history information linked to patient business_id';
COMMENT ON VIEW patients_latest IS 'Convenience view showing latest version of each patient';
