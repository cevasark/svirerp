-- Phase 3 Zeffy payment application. One row per Zeffy payment is the business-idempotency
-- boundary shared by webhook processing and the historical API synchronization added in Phase 4.

CREATE TABLE zeffy_payment (
    id                       CHAR(36)       PRIMARY KEY DEFAULT (UUID()),
    zeffy_payment_id         VARCHAR(100)   NOT NULL,
    status                   VARCHAR(30),
    refund_status            VARCHAR(30),
    dispute_status           VARCHAR(50),
    amount                   DECIMAL(15, 2),
    eligible_amount          DECIMAL(15, 2),
    currency                 VARCHAR(10),
    payment_type             VARCHAR(30),
    payment_created_at       DATETIME,
    campaign_id              VARCHAR(100),
    campaign_title           VARCHAR(255),
    mapping_action           VARCHAR(10)
                                 CHECK (mapping_action IS NULL OR mapping_action IN ('APPLY', 'IGNORE')),
    mapped_fund_id           CHAR(36),
    mapped_account_id        CHAR(36),
    membership_credit        BOOLEAN        NOT NULL DEFAULT FALSE,
    contact_id               VARCHAR(100),
    buyer_email              VARCHAR(255),
    buyer_first_name         VARCHAR(100),
    buyer_last_name          VARCHAR(100),
    latest_payload           LONGTEXT       NOT NULL,
    processing_status        VARCHAR(20)    NOT NULL
                                 CHECK (processing_status IN ('RECEIVED', 'PROCESSING', 'PROCESSED',
                                                               'NEEDS_MAPPING', 'NEEDS_REVIEW', 'ERROR',
                                                               'IGNORED')),
    outcome_reason           VARCHAR(1000),
    first_seen_source        VARCHAR(20)    NOT NULL
                                 CHECK (first_seen_source IN ('WEBHOOK', 'API_SYNC')),
    first_seen_at            DATETIME       NOT NULL,
    last_event_at            DATETIME       NOT NULL,
    applied_at               DATETIME,
    person_id                CHAR(36),
    member_id                CHAR(36),
    member_payment_id        CHAR(36),
    journal_entry_id         CHAR(36),
    created_at               DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_zeffy_payment_external_id UNIQUE (zeffy_payment_id),
    CONSTRAINT fk_zeffy_payment_mapped_fund FOREIGN KEY (mapped_fund_id)
        REFERENCES fund (id) ON DELETE RESTRICT,
    CONSTRAINT fk_zeffy_payment_mapped_account FOREIGN KEY (mapped_account_id)
        REFERENCES account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_zeffy_payment_person FOREIGN KEY (person_id)
        REFERENCES person (id) ON DELETE SET NULL,
    CONSTRAINT fk_zeffy_payment_member FOREIGN KEY (member_id)
        REFERENCES member (id) ON DELETE SET NULL,
    CONSTRAINT fk_zeffy_payment_member_payment FOREIGN KEY (member_payment_id)
        REFERENCES member_payment (id) ON DELETE SET NULL,
    CONSTRAINT fk_zeffy_payment_journal_entry FOREIGN KEY (journal_entry_id)
        REFERENCES journal_entry (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_zeffy_payment_processing_status ON zeffy_payment (processing_status);
CREATE INDEX idx_zeffy_payment_campaign ON zeffy_payment (campaign_id);
CREATE INDEX idx_zeffy_payment_contact ON zeffy_payment (contact_id);
CREATE INDEX idx_zeffy_payment_created ON zeffy_payment (payment_created_at);

ALTER TABLE zeffy_webhook_event
    ADD COLUMN zeffy_payment_record_id CHAR(36),
    ADD INDEX idx_zeffy_webhook_event_payment (zeffy_payment_record_id),
    ADD CONSTRAINT fk_zeffy_webhook_event_payment FOREIGN KEY (zeffy_payment_record_id)
        REFERENCES zeffy_payment (id) ON DELETE SET NULL;
