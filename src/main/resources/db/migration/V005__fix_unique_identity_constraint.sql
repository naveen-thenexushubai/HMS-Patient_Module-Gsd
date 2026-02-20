-- V005__fix_unique_identity_constraint.sql
-- Fix: Remove idx_patients_unique_identity constraint that incorrectly blocks event-sourced updates.
--
-- Problem: The idx_patients_unique_identity unique index applied to all rows WHERE status = 'ACTIVE'.
-- With the event-sourced pattern, old patient version rows retain their original status value (ACTIVE).
-- When re-activating a patient (inserting a new ACTIVE version), the old ACTIVE row from
-- version 1 triggers a unique constraint violation because the demographics are identical.
--
-- Solution: Drop the constraint. Duplicate patient detection is handled at the application layer
-- by DuplicateDetectionService (fuzzy matching at registration time via the registration endpoint).
-- The DB-level constraint is redundant and incompatible with the event-sourced INSERT-only pattern.
--
-- Impact: Registration duplicate prevention still enforced by DuplicateDetectionService.
-- Phase 2 requirements STAT-01 through STAT-08 (status management) now function correctly.

DROP INDEX IF EXISTS idx_patients_unique_identity;
