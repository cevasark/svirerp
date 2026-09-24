-- Preserve the authoritative Zeffy timestamp so same-day membership payments are replayed in
-- their original order. Manual payments leave this column null and continue to use payment_date.
ALTER TABLE member_payment
    ADD COLUMN source_created_at DATETIME;

