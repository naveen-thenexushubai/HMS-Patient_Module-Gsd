-- Initialize pgAudit extension
-- Note: pgAudit may not be available in Alpine images
-- Application-level audit logging provides HIPAA compliance
-- Database-level pgAudit would be added in production via custom image

-- Verify PostgreSQL version
SELECT version();
