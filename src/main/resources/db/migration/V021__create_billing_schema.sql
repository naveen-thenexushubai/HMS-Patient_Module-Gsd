CREATE SEQUENCE invoice_number_seq START WITH 100001 INCREMENT BY 1;

CREATE TABLE invoices (
    id BIGSERIAL PRIMARY KEY,
    business_id UUID NOT NULL DEFAULT gen_random_uuid(),
    patient_business_id UUID NOT NULL,
    appointment_business_id UUID,
    invoice_number VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    total_amount NUMERIC(10,2) NOT NULL DEFAULT 0,
    paid_amount NUMERIC(10,2) NOT NULL DEFAULT 0,
    insurance_covered NUMERIC(10,2) NOT NULL DEFAULT 0,
    patient_due NUMERIC(10,2) NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    due_date DATE,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255) NOT NULL,
    updated_at TIMESTAMPTZ,
    updated_by VARCHAR(255),
    CONSTRAINT uq_invoices_business_id UNIQUE (business_id),
    CONSTRAINT uq_invoice_number UNIQUE (invoice_number),
    CONSTRAINT chk_invoice_status CHECK (status IN ('DRAFT','ISSUED','PARTIALLY_PAID','PAID','OVERDUE','WRITTEN_OFF','CANCELLED')),
    CONSTRAINT chk_amounts CHECK (total_amount >= 0 AND paid_amount >= 0 AND insurance_covered >= 0 AND patient_due >= 0)
);

CREATE TABLE invoice_line_items (
    id BIGSERIAL PRIMARY KEY,
    business_id UUID NOT NULL DEFAULT gen_random_uuid(),
    invoice_business_id UUID NOT NULL,
    description VARCHAR(500) NOT NULL,
    service_code VARCHAR(20),
    quantity INTEGER NOT NULL DEFAULT 1,
    unit_price NUMERIC(10,2) NOT NULL,
    total_price NUMERIC(10,2) NOT NULL,
    CONSTRAINT uq_line_items_business_id UNIQUE (business_id),
    CONSTRAINT chk_quantity CHECK (quantity > 0),
    CONSTRAINT chk_price CHECK (unit_price >= 0 AND total_price >= 0)
);

CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    business_id UUID NOT NULL DEFAULT gen_random_uuid(),
    invoice_business_id UUID NOT NULL,
    patient_business_id UUID NOT NULL,
    amount NUMERIC(10,2) NOT NULL,
    payment_method VARCHAR(30) NOT NULL,
    payment_date DATE NOT NULL,
    reference_number VARCHAR(100),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by VARCHAR(255) NOT NULL,
    CONSTRAINT uq_payments_business_id UNIQUE (business_id),
    CONSTRAINT chk_payment_method CHECK (payment_method IN ('CASH','CREDIT_CARD','INSURANCE','CHECK','OTHER')),
    CONSTRAINT chk_payment_amount CHECK (amount > 0)
);

CREATE INDEX idx_invoices_patient ON invoices(patient_business_id, created_at DESC);
CREATE INDEX idx_invoices_status ON invoices(patient_business_id, status);
CREATE INDEX idx_line_items_invoice ON invoice_line_items(invoice_business_id);
CREATE INDEX idx_payments_invoice ON payments(invoice_business_id);
