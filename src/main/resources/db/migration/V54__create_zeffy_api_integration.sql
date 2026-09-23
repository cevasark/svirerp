-- Phase 1 Zeffy API foundation. app_setting holds configuration only; every synchronization
-- attempt is retained in zeffy_sync_run as operational history.

INSERT INTO app_setting (setting_key, value, value_type, description) VALUES
    ('zeffy.api-key', NULL, 'SECRET', 'Zeffy API bearer key'),
    ('zeffy.webhook-signing-secret', NULL, 'SECRET', 'Zeffy webhook signing secret (whsec_...)'),
    ('zeffy.integration-mode', 'DISABLED', 'STRING', 'Zeffy event-processing mode');

CREATE TABLE zeffy_campaign (
    id                       CHAR(36)      PRIMARY KEY DEFAULT (UUID()),
    zeffy_campaign_id        VARCHAR(100)  NOT NULL,
    title                    VARCHAR(255)  NOT NULL,
    campaign_type            VARCHAR(50)   NOT NULL,
    category                 VARCHAR(100),
    status                   VARCHAR(50),
    description              TEXT,
    locale                   VARCHAR(20),
    public_url               VARCHAR(1000),
    currency                 VARCHAR(10),
    is_archived              BOOLEAN       NOT NULL DEFAULT FALSE,
    zeffy_created_at         DATETIME,
    zeffy_updated_at         DATETIME,
    zeffy_deleted_at         DATETIME,
    last_synced_at           DATETIME       NOT NULL,

    mapping_confirmed        BOOLEAN        NOT NULL DEFAULT FALSE,
    processing_action        VARCHAR(10)
                                 CHECK (processing_action IS NULL OR processing_action IN ('APPLY', 'IGNORE')),
    fund_id                  CHAR(36),
    category_account_id      CHAR(36),
    grants_membership_credit BOOLEAN        NOT NULL DEFAULT FALSE,
    mapping_note             VARCHAR(500),

    created_at               DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_zeffy_campaign_external_id UNIQUE (zeffy_campaign_id),
    CONSTRAINT fk_zeffy_campaign_fund FOREIGN KEY (fund_id)
        REFERENCES fund (id) ON DELETE RESTRICT,
    CONSTRAINT fk_zeffy_campaign_account FOREIGN KEY (category_account_id)
        REFERENCES account (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_zeffy_campaign_title ON zeffy_campaign (title);
CREATE INDEX idx_zeffy_campaign_mapping_confirmed ON zeffy_campaign (mapping_confirmed);
CREATE INDEX idx_zeffy_campaign_status ON zeffy_campaign (status, is_archived);

CREATE TABLE zeffy_sync_run (
    id                  CHAR(36)      PRIMARY KEY DEFAULT (UUID()),
    sync_type           VARCHAR(20)   NOT NULL
                            CHECK (sync_type IN ('CAMPAIGNS', 'PAYMENTS', 'CONTACTS')),
    status              VARCHAR(20)   NOT NULL
                            CHECK (status IN ('RUNNING', 'COMPLETED', 'PARTIAL', 'FAILED')),
    trigger_type        VARCHAR(20)   NOT NULL
                            CHECK (trigger_type IN ('MANUAL', 'SCHEDULED')),
    initiated_by        VARCHAR(255),
    started_at          DATETIME      NOT NULL,
    completed_at        DATETIME,
    requested_from      DATETIME,
    requested_to        DATETIME,
    starting_cursor     VARCHAR(500),
    ending_cursor       VARCHAR(500),
    fetched_count       INT           NOT NULL DEFAULT 0,
    inserted_count      INT           NOT NULL DEFAULT 0,
    updated_count       INT           NOT NULL DEFAULT 0,
    ignored_count       INT           NOT NULL DEFAULT 0,
    failed_count        INT           NOT NULL DEFAULT 0,
    error_summary       VARCHAR(1000),
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_zeffy_sync_run_type_started ON zeffy_sync_run (sync_type, started_at);
CREATE INDEX idx_zeffy_sync_run_status ON zeffy_sync_run (status);
