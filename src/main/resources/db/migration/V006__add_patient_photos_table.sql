-- V006__add_patient_photos_table.sql
-- Phase 3: Patient photo upload support (SC2 - webcam photo capture)
-- Stores photo file metadata; binary files stored on filesystem (not in DB)
-- Multiple photos per patient supported; only one is_current=true at a time

CREATE TABLE patient_photos (
    id                  BIGSERIAL PRIMARY KEY,
    patient_business_id UUID NOT NULL,
    filename            VARCHAR(255) NOT NULL,       -- UUID-based safe filename (e.g. "a1b2c3-...jpg")
    content_type        VARCHAR(50) NOT NULL,         -- 'image/jpeg' or 'image/png'
    file_size_bytes     BIGINT NOT NULL,
    uploaded_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    uploaded_by         VARCHAR(255) NOT NULL,
    is_current          BOOLEAN DEFAULT true          -- only one current photo per patient
);

-- Index for lookups by patient (most common query: find current photo for a patient)
CREATE INDEX idx_patient_photos_business ON patient_photos(patient_business_id);

-- Composite index for "find current photo" query used in PhotoService
CREATE INDEX idx_patient_photos_current ON patient_photos(patient_business_id, is_current);

COMMENT ON TABLE patient_photos IS 'Patient photo metadata. Binaries stored on filesystem at app.storage.photos-dir.';
COMMENT ON COLUMN patient_photos.filename IS 'UUID-generated filename (not original upload name) to prevent path traversal.';
COMMENT ON COLUMN patient_photos.is_current IS 'True for the active/latest photo. Set to false when a new photo is uploaded.';
