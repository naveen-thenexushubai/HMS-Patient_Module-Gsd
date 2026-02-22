CREATE TABLE vital_signs (
    id BIGSERIAL PRIMARY KEY,
    business_id UUID NOT NULL DEFAULT gen_random_uuid(),
    patient_business_id UUID NOT NULL,
    appointment_business_id UUID,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    temperature NUMERIC(4,1),
    systolic_bp INTEGER,
    diastolic_bp INTEGER,
    heart_rate INTEGER,
    respiratory_rate INTEGER,
    oxygen_saturation NUMERIC(4,1),
    weight_kg NUMERIC(5,2),
    height_cm NUMERIC(5,1),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255) NOT NULL,
    CONSTRAINT uq_vital_signs_business_id UNIQUE (business_id),
    CONSTRAINT chk_temperature CHECK (temperature BETWEEN 30 AND 45),
    CONSTRAINT chk_bp_systolic CHECK (systolic_bp BETWEEN 50 AND 300),
    CONSTRAINT chk_bp_diastolic CHECK (diastolic_bp BETWEEN 30 AND 200),
    CONSTRAINT chk_hr CHECK (heart_rate BETWEEN 20 AND 300),
    CONSTRAINT chk_spo2 CHECK (oxygen_saturation BETWEEN 0 AND 100)
);

CREATE INDEX idx_vital_signs_patient ON vital_signs(patient_business_id, recorded_at DESC);
