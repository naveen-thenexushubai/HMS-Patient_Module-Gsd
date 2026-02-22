-- V013: Appointment scheduling schema
-- Full scheduling with doctor availability, time slots, and booking

CREATE TABLE doctor_availability (
    id                    BIGSERIAL     PRIMARY KEY,
    doctor_id             VARCHAR(100)  NOT NULL,
    day_of_week           VARCHAR(15)   NOT NULL,
    start_time            TIME          NOT NULL,
    end_time              TIME          NOT NULL,
    slot_duration_minutes INT           NOT NULL DEFAULT 30,
    is_active             BOOLEAN       NOT NULL DEFAULT true,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by            VARCHAR(255)  NOT NULL,
    CONSTRAINT chk_availability_times CHECK (end_time > start_time),
    CONSTRAINT chk_slot_duration CHECK (slot_duration_minutes > 0)
);

CREATE UNIQUE INDEX idx_doctor_avail_unique
    ON doctor_availability(doctor_id, day_of_week)
    WHERE is_active = true;

CREATE INDEX idx_doctor_avail_doctor ON doctor_availability(doctor_id);

CREATE TABLE appointments (
    id                  BIGSERIAL     PRIMARY KEY,
    business_id         UUID          NOT NULL DEFAULT gen_random_uuid(),
    patient_business_id UUID          NOT NULL,
    doctor_id           VARCHAR(100)  NOT NULL,
    appointment_date    DATE          NOT NULL,
    start_time          TIME          NOT NULL,
    end_time            TIME          NOT NULL,
    type                VARCHAR(30)   NOT NULL,
    status              VARCHAR(20)   NOT NULL DEFAULT 'SCHEDULED',
    notes               TEXT,
    cancel_reason       VARCHAR(500),
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(255)  NOT NULL,
    updated_at          TIMESTAMPTZ,
    updated_by          VARCHAR(255),
    CONSTRAINT uq_appointments_business_id UNIQUE (business_id),
    CONSTRAINT chk_appointment_times CHECK (end_time > start_time),
    CONSTRAINT chk_appointment_status CHECK (
        status IN ('SCHEDULED','CONFIRMED','IN_PROGRESS','COMPLETED','CANCELLED','NO_SHOW')
    ),
    CONSTRAINT chk_appointment_type CHECK (
        type IN ('CONSULTATION','FOLLOW_UP','PROCEDURE','LAB_TEST','EMERGENCY')
    )
);

-- Partial unique index: only one active slot per doctor+date+time
-- CANCELLED and NO_SHOW appointments do not block the slot for rebooking
CREATE UNIQUE INDEX idx_appointments_slot_unique
    ON appointments(doctor_id, appointment_date, start_time)
    WHERE status NOT IN ('CANCELLED', 'NO_SHOW');

CREATE INDEX idx_appointments_patient       ON appointments(patient_business_id);
CREATE INDEX idx_appointments_doctor        ON appointments(doctor_id);
CREATE INDEX idx_appointments_date          ON appointments(appointment_date);
CREATE INDEX idx_appointments_status        ON appointments(status);
CREATE INDEX idx_appointments_patient_date  ON appointments(patient_business_id, appointment_date);

COMMENT ON TABLE appointments         IS 'Patient appointment bookings with full scheduling lifecycle';
COMMENT ON TABLE doctor_availability  IS 'Weekly recurring availability windows for each doctor';
COMMENT ON COLUMN appointments.business_id IS 'Public-facing stable UUID for appointment references';
