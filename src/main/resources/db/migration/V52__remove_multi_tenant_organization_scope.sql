-- The application supports exactly one organization profile per installation.  Domain records
-- therefore do not need to repeat an organization foreign key.  Add the singleton guard first:
-- its unique constraint deliberately makes this migration fail before changing domain tables if
-- an unexpected database contains more than one organization.

ALTER TABLE organization
    ADD COLUMN singleton_key TINYINT NOT NULL DEFAULT 1,
    ADD CONSTRAINT chk_organization_singleton CHECK (singleton_key = 1),
    ADD CONSTRAINT uq_organization_singleton UNIQUE (singleton_key);

-- Replace organization-scoped business keys with installation-wide business keys.

ALTER TABLE membership_type
    DROP FOREIGN KEY fk_membership_type_org,
    DROP INDEX idx_membership_type_org,
    DROP INDEX uq_membership_type_name_org,
    DROP COLUMN org_id,
    ADD CONSTRAINT uq_membership_type_name UNIQUE (name);

ALTER TABLE member
    DROP FOREIGN KEY fk_member_org,
    DROP INDEX idx_member_org,
    DROP COLUMN org_id;

ALTER TABLE trustee
    DROP FOREIGN KEY fk_trustee_org,
    DROP INDEX idx_trustee_org,
    DROP COLUMN org_id;

ALTER TABLE volunteer
    DROP FOREIGN KEY fk_volunteer_org,
    DROP INDEX idx_volunteer_org,
    DROP COLUMN org_id;

ALTER TABLE committee
    DROP FOREIGN KEY fk_committee_org,
    DROP INDEX idx_committee_org,
    DROP INDEX uq_committee_name_org,
    DROP COLUMN org_id,
    ADD CONSTRAINT uq_committee_name UNIQUE (name);

ALTER TABLE calendar_event
    DROP FOREIGN KEY fk_calendar_event_org,
    DROP INDEX idx_calendar_event_org,
    DROP COLUMN org_id;

ALTER TABLE fund
    DROP FOREIGN KEY fk_fund_org,
    DROP INDEX idx_fund_org,
    DROP INDEX uq_fund_code_org,
    DROP COLUMN org_id,
    ADD CONSTRAINT uq_fund_code UNIQUE (fund_code);

ALTER TABLE account
    DROP FOREIGN KEY fk_account_org,
    DROP INDEX idx_account_org,
    DROP INDEX uq_account_number_org,
    DROP COLUMN org_id,
    ADD CONSTRAINT uq_account_number UNIQUE (account_number);

ALTER TABLE journal_entry
    DROP FOREIGN KEY fk_journal_entry_org,
    DROP INDEX idx_journal_entry_org,
    DROP COLUMN org_id;

ALTER TABLE budget
    DROP FOREIGN KEY fk_budget_org,
    DROP INDEX idx_budget_org,
    DROP INDEX uq_budget_account_fund_year_period,
    DROP COLUMN org_id,
    ADD CONSTRAINT uq_budget_account_fund_year_period
        UNIQUE (account_id, fund_id, fiscal_year, period);

ALTER TABLE bank_account
    DROP FOREIGN KEY fk_bank_account_org,
    DROP INDEX idx_bank_account_org,
    DROP COLUMN org_id;

ALTER TABLE meeting_minutes
    DROP FOREIGN KEY fk_meeting_minutes_org,
    DROP INDEX idx_meeting_minutes_org,
    DROP COLUMN org_id;

ALTER TABLE volunteer_area
    DROP FOREIGN KEY fk_volunteer_area_org,
    DROP INDEX idx_volunteer_area_org,
    DROP INDEX uq_volunteer_area_name_org,
    DROP COLUMN org_id,
    ADD CONSTRAINT uq_volunteer_area_name UNIQUE (name);

ALTER TABLE vendor
    DROP FOREIGN KEY fk_vendor_org,
    DROP INDEX idx_vendor_org,
    DROP COLUMN org_id;

ALTER TABLE service_request
    DROP FOREIGN KEY fk_service_request_org,
    DROP INDEX idx_service_request_org,
    DROP COLUMN org_id;

ALTER TABLE zeffy_campaign_mapping
    DROP FOREIGN KEY fk_zeffy_campaign_mapping_org,
    DROP INDEX idx_zeffy_campaign_mapping_org,
    DROP INDEX uq_zeffy_campaign_mapping_org_title,
    DROP COLUMN org_id,
    ADD CONSTRAINT uq_zeffy_campaign_mapping_title UNIQUE (campaign_title);

ALTER TABLE zeffy_import_batch
    DROP FOREIGN KEY fk_zeffy_import_batch_org,
    DROP INDEX idx_zeffy_import_batch_org,
    DROP COLUMN org_id;

ALTER TABLE zeffy_import_row
    DROP FOREIGN KEY fk_zeffy_import_row_org,
    DROP INDEX idx_zeffy_import_row_org,
    DROP INDEX idx_zeffy_import_row_dedupe_key,
    DROP COLUMN org_id,
    ADD INDEX idx_zeffy_import_row_dedupe_key (dedupe_key);

ALTER TABLE stripe_product_mapping
    DROP FOREIGN KEY fk_stripe_product_mapping_org,
    DROP INDEX idx_stripe_product_mapping_org,
    DROP INDEX uq_stripe_product_mapping_org_price,
    DROP COLUMN org_id,
    ADD CONSTRAINT uq_stripe_product_mapping_price UNIQUE (stripe_price_id);

ALTER TABLE stripe_webhook_event
    DROP FOREIGN KEY fk_stripe_webhook_event_org,
    DROP INDEX idx_stripe_webhook_event_org,
    DROP COLUMN org_id;

ALTER TABLE project
    DROP FOREIGN KEY fk_project_org,
    DROP INDEX idx_project_org,
    DROP COLUMN org_id;
