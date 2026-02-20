-- V008__refresh_patients_latest_view.sql
-- Refresh patients_latest view to include is_registration_complete column added by V007.
--
-- PostgreSQL CREATE OR REPLACE VIEW with SELECT * does NOT automatically pick up new columns
-- added after the view was created. The view definition is stored as a fixed column list.
-- This migration recreates the view to include the is_registration_complete column
-- so that DataQualityRepository's native queries can filter by it.

CREATE OR REPLACE VIEW patients_latest AS
SELECT DISTINCT ON (business_id) *
FROM patients
ORDER BY business_id, version DESC, created_at DESC;

COMMENT ON VIEW patients_latest IS 'Convenience view showing latest version of each patient (refreshed by V008 to include is_registration_complete)';
