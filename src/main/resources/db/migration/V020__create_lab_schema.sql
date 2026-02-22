CREATE TABLE lab_orders (
    id BIGSERIAL PRIMARY KEY,
    business_id UUID NOT NULL DEFAULT gen_random_uuid(),
    patient_business_id UUID NOT NULL,
    appointment_business_id UUID,
    order_name VARCHAR(255) NOT NULL,
    ordered_by VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    priority VARCHAR(20) NOT NULL DEFAULT 'ROUTINE',
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255) NOT NULL,
    updated_at TIMESTAMPTZ,
    updated_by VARCHAR(255),
    CONSTRAINT uq_lab_orders_business_id UNIQUE (business_id),
    CONSTRAINT chk_lab_order_status CHECK (status IN ('PENDING','COLLECTED','IN_LAB','COMPLETED','CANCELLED')),
    CONSTRAINT chk_lab_priority CHECK (priority IN ('ROUTINE','URGENT','STAT'))
);

CREATE TABLE lab_results (
    id BIGSERIAL PRIMARY KEY,
    business_id UUID NOT NULL DEFAULT gen_random_uuid(),
    lab_order_business_id UUID NOT NULL,
    patient_business_id UUID NOT NULL,
    test_name VARCHAR(255) NOT NULL,
    result_value VARCHAR(255),
    unit VARCHAR(50),
    reference_range VARCHAR(100),
    is_abnormal BOOLEAN NOT NULL DEFAULT FALSE,
    abnormal_flag VARCHAR(10),
    result_text TEXT,
    document_path VARCHAR(500),
    document_filename VARCHAR(255),
    reviewed_by VARCHAR(255),
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255) NOT NULL,
    updated_at TIMESTAMPTZ,
    updated_by VARCHAR(255),
    CONSTRAINT uq_lab_results_business_id UNIQUE (business_id)
);

CREATE INDEX idx_lab_orders_patient ON lab_orders(patient_business_id, created_at DESC);
CREATE INDEX idx_lab_results_order ON lab_results(lab_order_business_id);
CREATE INDEX idx_lab_results_patient ON lab_results(patient_business_id);
CREATE INDEX idx_lab_results_abnormal ON lab_results(patient_business_id, is_abnormal) WHERE is_abnormal = true;
