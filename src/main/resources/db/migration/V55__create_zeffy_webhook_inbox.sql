-- Phase 2 Zeffy webhook inbox. The raw payload is retained for later processing/recovery but is
-- never returned by routine list APIs. zeffy_event_id is the delivery idempotency boundary.

CREATE TABLE zeffy_webhook_event (
    id                       CHAR(36)      PRIMARY KEY DEFAULT (UUID()),
    zeffy_event_id           VARCHAR(100)  NOT NULL,
    event_type               VARCHAR(100)  NOT NULL,
    schema_version           INT           NOT NULL,
    resource_type            VARCHAR(20)    NOT NULL
                                 CHECK (resource_type IN ('PAYMENT', 'CONTACT', 'UNKNOWN')),
    zeffy_resource_id        VARCHAR(100),
    dispatched_at            DATETIME       NOT NULL,
    signature_timestamp      DATETIME       NOT NULL,
    raw_payload              LONGTEXT       NOT NULL,
    payload_sha256           VARCHAR(64)       NOT NULL,
    status                   VARCHAR(20)    NOT NULL
                                 CHECK (status IN ('RECEIVED', 'PROCESSING', 'PROCESSED',
                                                   'NEEDS_MAPPING', 'NEEDS_REVIEW', 'ERROR',
                                                   'IGNORED', 'UNSUPPORTED')),
    delivery_count           INT            NOT NULL DEFAULT 1,
    received_at              DATETIME       NOT NULL,
    last_received_at         DATETIME       NOT NULL,
    processing_attempt_count INT            NOT NULL DEFAULT 0,
    last_attempted_at        DATETIME,
    processed_at             DATETIME,
    error_summary            VARCHAR(1000),
    created_at               DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_zeffy_webhook_event_external_id UNIQUE (zeffy_event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_zeffy_webhook_event_received ON zeffy_webhook_event (received_at);
CREATE INDEX idx_zeffy_webhook_event_type ON zeffy_webhook_event (event_type);
CREATE INDEX idx_zeffy_webhook_event_status ON zeffy_webhook_event (status);
CREATE INDEX idx_zeffy_webhook_event_resource ON zeffy_webhook_event (zeffy_resource_id);
