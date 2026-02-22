-- V016: Document storage directory note
-- The consent PDF storage directory is configured via app.storage.consents-dir in application.yml

COMMENT ON TABLE consent_records IS
    'HIPAA and treatment consent records. PDF documents stored at app.storage.consents-dir filesystem path.';
