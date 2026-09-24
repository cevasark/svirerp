-- Phase 5C: retain Zeffy's stable contact identity and its link to the local Person.
-- Contact deletion only tombstones this integration record; it never deletes church data.

CREATE TABLE zeffy_contact (
    id                       CHAR(36)       PRIMARY KEY DEFAULT (UUID()),
    zeffy_contact_id         VARCHAR(100)   NOT NULL,
    person_id                CHAR(36),
    latest_webhook_event_id  CHAR(36),
    email                    VARCHAR(255),
    first_name               VARCHAR(100),
    last_name                VARCHAR(100),
    phone_number             VARCHAR(30),
    address_line1            VARCHAR(255),
    city                     VARCHAR(100),
    state                    VARCHAR(100),
    postal_code              VARCHAR(20),
    country                  VARCHAR(10),
    donor_type               VARCHAR(50),
    total_contribution       DECIMAL(15, 2),
    currency                 VARCHAR(10),
    donation_count           INT,
    first_donation_at        DATETIME,
    last_donation_at         DATETIME,
    zeffy_created_at         DATETIME,
    zeffy_updated_at         DATETIME,
    latest_payload_sha256    VARCHAR(64),
    processing_status        VARCHAR(20)    NOT NULL
                                 CHECK (processing_status IN ('PROCESSED', 'NEEDS_REVIEW',
                                                               'DELETED', 'ERROR')),
    outcome_reason           VARCHAR(1000),
    first_seen_source        VARCHAR(20)    NOT NULL
                                 CHECK (first_seen_source IN ('WEBHOOK', 'API_SYNC')),
    first_seen_at            DATETIME       NOT NULL,
    last_event_at            DATETIME,
    last_synced_at           DATETIME,
    deleted_at               DATETIME,
    created_at               DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_zeffy_contact_external_id UNIQUE (zeffy_contact_id),
    CONSTRAINT fk_zeffy_contact_person FOREIGN KEY (person_id)
        REFERENCES person (id) ON DELETE SET NULL,
    CONSTRAINT fk_zeffy_contact_latest_event FOREIGN KEY (latest_webhook_event_id)
        REFERENCES zeffy_webhook_event (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_zeffy_contact_person ON zeffy_contact (person_id);
CREATE INDEX idx_zeffy_contact_email ON zeffy_contact (email);
CREATE INDEX idx_zeffy_contact_status ON zeffy_contact (processing_status);

ALTER TABLE zeffy_webhook_event
    ADD COLUMN zeffy_contact_record_id CHAR(36),
    ADD INDEX idx_zeffy_webhook_event_contact (zeffy_contact_record_id),
    ADD CONSTRAINT fk_zeffy_webhook_event_contact FOREIGN KEY (zeffy_contact_record_id)
        REFERENCES zeffy_contact (id) ON DELETE SET NULL;
