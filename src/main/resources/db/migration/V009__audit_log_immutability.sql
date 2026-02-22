-- V009: Enforce audit log immutability at the database layer.
--
-- Audit logs are HIPAA PHI access records and must be tamper-proof.
-- This migration adds a trigger that prevents any UPDATE or DELETE
-- on audit_logs, providing defence-in-depth beyond the application-layer
-- append-only constraint (no setters on AuditLog entity).

CREATE OR REPLACE FUNCTION prevent_audit_log_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION
        'Audit logs are immutable: % on audit_logs (id=%) is not permitted',
        TG_OP, OLD.id;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_logs_immutability
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW
    EXECUTE FUNCTION prevent_audit_log_modification();
