-- Create audit_logs table with append-only constraints
CREATE TABLE audit_logs (
    id BIGSERIAL,
    user_id VARCHAR(255) NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    action VARCHAR(50) NOT NULL,  -- CREATE, READ, UPDATE, DELETE, SEARCH
    resource_type VARCHAR(100) NOT NULL,  -- PATIENT, INSURANCE, etc.
    resource_id VARCHAR(255) NOT NULL,
    ip_address INET,
    user_agent TEXT,
    details JSONB,
    CONSTRAINT audit_logs_action_check CHECK (action IN ('CREATE', 'READ', 'UPDATE', 'DELETE', 'SEARCH')),
    PRIMARY KEY (id, timestamp)
) PARTITION BY RANGE (timestamp);

-- Prevent updates and deletes (append-only)
CREATE RULE audit_logs_no_update AS ON UPDATE TO audit_logs DO INSTEAD NOTHING;
CREATE RULE audit_logs_no_delete AS ON DELETE TO audit_logs DO INSTEAD NOTHING;

-- Create indexes for common queries
CREATE INDEX idx_audit_logs_user_timestamp ON audit_logs(user_id, timestamp DESC);
CREATE INDEX idx_audit_logs_resource ON audit_logs(resource_type, resource_id);

-- Create partition for 2026 (6-year retention requirement)
-- Note: Partitioning by year for retention management
CREATE TABLE audit_logs_2026 PARTITION OF audit_logs
    FOR VALUES FROM ('2026-01-01 00:00:00+00') TO ('2027-01-01 00:00:00+00');

CREATE TABLE audit_logs_2027 PARTITION OF audit_logs
    FOR VALUES FROM ('2027-01-01 00:00:00+00') TO ('2028-01-01 00:00:00+00');

-- Grant permissions (read-only for audit reviewer role)
CREATE ROLE audit_reviewer;
GRANT SELECT ON audit_logs TO audit_reviewer;
