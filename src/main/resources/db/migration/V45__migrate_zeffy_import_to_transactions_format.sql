-- V45__migrate_zeffy_import_to_transactions_format.sql
-- Switches the Zeffy importer from Zeffy's "Payments" export (noisy: ~17% of real rows are
-- Incomplete/Failed attempts that had to be filtered via payment_status) to Zeffy's "Transactions"
-- export (every row is a real settled transaction; adds a Donation/Ticket category the old export
-- never had). Payments-format support is dropped entirely, not kept alongside — a one-time cleanup
-- of all prior Zeffy-derived data was run by hand against this DB before this migration (not itself
-- a migration step: a DELETE like that must never be able to run against a real database by
-- accident), so there's no historical data depending on the columns being dropped here.

ALTER TABLE zeffy_import_row
    DROP COLUMN payment_time,
    DROP COLUMN payment_status,
    DROP COLUMN address,
    DROP COLUMN city,
    DROP COLUMN postal_code,
    DROP COLUMN state,
    DROP COLUMN country,
    DROP COLUMN tax_receipt_number,
    DROP COLUMN tax_receipt_url,
    CHANGE COLUMN payment_date transaction_date DATE,
    CHANGE COLUMN payout_date available_date DATE,
    ADD COLUMN transaction_id VARCHAR(100) AFTER csv_row_number,
    ADD COLUMN category VARCHAR(30) CHECK (category IN ('Donation', 'Ticket')) AFTER amount,
    ADD COLUMN eligible_amount DECIMAL(10, 2) AFTER category;
