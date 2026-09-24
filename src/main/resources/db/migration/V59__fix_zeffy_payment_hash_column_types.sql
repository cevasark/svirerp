-- Align the Phase 5A SHA-256 columns with the JPA String mappings. V58 may already be
-- recorded in Flyway history, so this correction is a new forward migration.

ALTER TABLE zeffy_payment
    MODIFY COLUMN latest_payload_sha256 VARCHAR(64) NULL;

ALTER TABLE zeffy_payment_change
    MODIFY COLUMN previous_payload_sha256 VARCHAR(64) NULL,
    MODIFY COLUMN current_payload_sha256 VARCHAR(64) NULL;
