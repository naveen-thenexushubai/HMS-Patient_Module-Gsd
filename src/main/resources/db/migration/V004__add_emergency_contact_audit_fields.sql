-- V004__add_emergency_contact_audit_fields.sql
-- Add audit fields to emergency_contacts for EMR-04 update audit trail
ALTER TABLE emergency_contacts
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(255);
