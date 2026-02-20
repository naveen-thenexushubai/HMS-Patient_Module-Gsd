-- V007__add_registration_complete_flag.sql
-- Phase 3: Quick registration support (SC1 - "complete later" workflow flag)
-- IMPORTANT: patients table is immutable (trigger prevents UPDATE).
-- This column is carried by each version row — quick-registered patients start with false,
-- completion inserts a new version row with is_registration_complete = true.
-- DEFAULT true ensures all existing records are considered complete.

ALTER TABLE patients
    ADD COLUMN is_registration_complete BOOLEAN NOT NULL DEFAULT true;

COMMENT ON COLUMN patients.is_registration_complete IS
    'False for quick-registered patients pending full data entry. New version row with true when completed.';
