-- Phase 5A: retain payment lifecycle changes, refunds, and disputes without applying financial
-- corrections. Refund/dispute rows already include nullable correction links for Phase 5B.

ALTER TABLE zeffy_payment
    ADD COLUMN latest_payload_sha256 CHAR(64),
    ADD COLUMN last_fetched_at DATETIME,
    ADD COLUMN deleted_at DATETIME;

ALTER TABLE zeffy_webhook_event
    ADD COLUMN processing_summary VARCHAR(1000);

CREATE TABLE zeffy_payment_change (
    id                       CHAR(36)      PRIMARY KEY DEFAULT (UUID()),
    zeffy_payment_record_id  CHAR(36)      NOT NULL,
    webhook_event_id         CHAR(36)      NOT NULL,
    change_kind              VARCHAR(30)   NOT NULL
                                 CHECK (change_kind IN ('CREATED', 'UPDATED', 'DELETED',
                                                        'FETCH_NOT_FOUND')),
    changed_fields           VARCHAR(1000),
    previous_payload_sha256  CHAR(64),
    current_payload_sha256   CHAR(64),
    payment_snapshot         LONGTEXT      NOT NULL,
    summary                  VARCHAR(1000) NOT NULL,
    observed_at              DATETIME      NOT NULL,
    created_at               DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_zeffy_payment_change_event UNIQUE (webhook_event_id),
    CONSTRAINT fk_zeffy_payment_change_payment FOREIGN KEY (zeffy_payment_record_id)
        REFERENCES zeffy_payment (id) ON DELETE CASCADE,
    CONSTRAINT fk_zeffy_payment_change_event FOREIGN KEY (webhook_event_id)
        REFERENCES zeffy_webhook_event (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_zeffy_payment_change_payment
    ON zeffy_payment_change (zeffy_payment_record_id, observed_at);

CREATE TABLE zeffy_refund (
    id                           CHAR(36)      PRIMARY KEY DEFAULT (UUID()),
    zeffy_refund_id              VARCHAR(100)  NOT NULL,
    zeffy_payment_record_id      CHAR(36)      NOT NULL,
    latest_webhook_event_id      CHAR(36),
    amount                       DECIMAL(15, 2) NOT NULL,
    currency                     VARCHAR(10)    NOT NULL,
    status                       VARCHAR(20)    NOT NULL
                                     CHECK (status IN ('pending', 'succeeded', 'failed')),
    refund_created_at            DATETIME       NOT NULL,
    correction_status            VARCHAR(30)    NOT NULL
                                     CHECK (correction_status IN ('NOT_REQUIRED',
                                                                  'AWAITING_CORRECTION',
                                                                  'CORRECTED', 'NEEDS_REVIEW')),
    correction_journal_entry_id  CHAR(36),
    first_seen_at                DATETIME       NOT NULL,
    last_seen_at                 DATETIME       NOT NULL,
    created_at                   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_zeffy_refund_external_id UNIQUE (zeffy_refund_id),
    CONSTRAINT fk_zeffy_refund_payment FOREIGN KEY (zeffy_payment_record_id)
        REFERENCES zeffy_payment (id) ON DELETE CASCADE,
    CONSTRAINT fk_zeffy_refund_event FOREIGN KEY (latest_webhook_event_id)
        REFERENCES zeffy_webhook_event (id) ON DELETE SET NULL,
    CONSTRAINT fk_zeffy_refund_correction FOREIGN KEY (correction_journal_entry_id)
        REFERENCES journal_entry (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_zeffy_refund_payment ON zeffy_refund (zeffy_payment_record_id);
CREATE INDEX idx_zeffy_refund_correction_status ON zeffy_refund (correction_status);

CREATE TABLE zeffy_dispute (
    id                           CHAR(36)      PRIMARY KEY DEFAULT (UUID()),
    zeffy_dispute_id             VARCHAR(100)  NOT NULL,
    zeffy_payment_record_id      CHAR(36)      NOT NULL,
    latest_webhook_event_id      CHAR(36),
    amount                       DECIMAL(15, 2) NOT NULL,
    currency                     VARCHAR(10)    NOT NULL,
    status                       VARCHAR(30)    NOT NULL
                                     CHECK (status IN ('needs_response', 'won', 'lost')),
    reason                       VARCHAR(255),
    dispute_created_at           DATETIME       NOT NULL,
    correction_status            VARCHAR(30)    NOT NULL
                                     CHECK (correction_status IN ('NOT_REQUIRED',
                                                                  'AWAITING_CORRECTION',
                                                                  'CORRECTED', 'NEEDS_REVIEW')),
    correction_journal_entry_id  CHAR(36),
    first_seen_at                DATETIME       NOT NULL,
    last_seen_at                 DATETIME       NOT NULL,
    created_at                   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_zeffy_dispute_external_id UNIQUE (zeffy_dispute_id),
    CONSTRAINT fk_zeffy_dispute_payment FOREIGN KEY (zeffy_payment_record_id)
        REFERENCES zeffy_payment (id) ON DELETE CASCADE,
    CONSTRAINT fk_zeffy_dispute_event FOREIGN KEY (latest_webhook_event_id)
        REFERENCES zeffy_webhook_event (id) ON DELETE SET NULL,
    CONSTRAINT fk_zeffy_dispute_correction FOREIGN KEY (correction_journal_entry_id)
        REFERENCES journal_entry (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_zeffy_dispute_payment ON zeffy_dispute (zeffy_payment_record_id);
CREATE INDEX idx_zeffy_dispute_correction_status ON zeffy_dispute (correction_status);
