-- Phase 4 historical payment synchronization. Preview and apply runs are durable, apply runs
-- point back to the preview they were approved from, and every fetched payment has an outcome.

ALTER TABLE zeffy_sync_run
    ADD COLUMN execution_mode VARCHAR(10)
        CHECK (execution_mode IS NULL OR execution_mode IN ('PREVIEW', 'APPLY')),
    ADD COLUMN preview_run_id CHAR(36),
    ADD COLUMN already_applied_count INT NOT NULL DEFAULT 0,
    ADD COLUMN eligible_count INT NOT NULL DEFAULT 0,
    ADD COLUMN needs_mapping_count INT NOT NULL DEFAULT 0,
    ADD COLUMN needs_review_count INT NOT NULL DEFAULT 0,
    ADD COLUMN processed_count INT NOT NULL DEFAULT 0,
    ADD INDEX idx_zeffy_sync_run_preview (preview_run_id),
    ADD CONSTRAINT fk_zeffy_sync_run_preview FOREIGN KEY (preview_run_id)
        REFERENCES zeffy_sync_run (id) ON DELETE SET NULL;

ALTER TABLE zeffy_payment
    MODIFY COLUMN last_event_at DATETIME NULL,
    ADD COLUMN last_synced_at DATETIME;

CREATE TABLE zeffy_sync_payment_result (
    id                       CHAR(36)      PRIMARY KEY DEFAULT (UUID()),
    sync_run_id              CHAR(36)      NOT NULL,
    zeffy_payment_id         VARCHAR(100)  NOT NULL,
    zeffy_payment_record_id  CHAR(36),
    outcome                  VARCHAR(30)   NOT NULL
                                 CHECK (outcome IN ('ELIGIBLE', 'ALREADY_APPLIED', 'PROCESSED',
                                                    'IGNORED', 'NEEDS_MAPPING', 'NEEDS_REVIEW',
                                                    'CHANGED_AFTER_PREVIEW', 'ERROR')),
    payload_sha256           VARCHAR(64)   NOT NULL,
    detail                   VARCHAR(1000),
    observed_at              DATETIME      NOT NULL,
    created_at               DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_zeffy_sync_payment_result UNIQUE (sync_run_id, zeffy_payment_id),
    CONSTRAINT fk_zeffy_sync_payment_result_run FOREIGN KEY (sync_run_id)
        REFERENCES zeffy_sync_run (id) ON DELETE CASCADE,
    CONSTRAINT fk_zeffy_sync_payment_result_payment FOREIGN KEY (zeffy_payment_record_id)
        REFERENCES zeffy_payment (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_zeffy_sync_payment_result_outcome
    ON zeffy_sync_payment_result (sync_run_id, outcome);
CREATE INDEX idx_zeffy_sync_payment_result_payment
    ON zeffy_sync_payment_result (zeffy_payment_id);
