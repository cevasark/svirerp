-- Phase 5B: link posted corrections to their original journal entry and retain the outcome of
-- refund, dispute, and payment-amount corrections.

ALTER TABLE journal_entry
    ADD COLUMN corrects_journal_entry_id CHAR(36),
    ADD CONSTRAINT fk_journal_entry_corrects
        FOREIGN KEY (corrects_journal_entry_id) REFERENCES journal_entry (id) ON DELETE SET NULL;

CREATE INDEX idx_journal_entry_corrects ON journal_entry (corrects_journal_entry_id);

ALTER TABLE zeffy_refund
    ADD COLUMN corrected_amount DECIMAL(15, 2),
    ADD COLUMN correction_summary VARCHAR(1000),
    ADD COLUMN corrected_at DATETIME;

ALTER TABLE zeffy_dispute
    ADD COLUMN corrected_amount DECIMAL(15, 2),
    ADD COLUMN correction_summary VARCHAR(1000),
    ADD COLUMN corrected_at DATETIME;

ALTER TABLE zeffy_payment_change
    ADD COLUMN previous_amount DECIMAL(15, 2),
    ADD COLUMN current_amount DECIMAL(15, 2),
    ADD COLUMN correction_status VARCHAR(30),
    ADD COLUMN correction_journal_entry_id CHAR(36),
    ADD COLUMN correction_summary VARCHAR(1000),
    ADD COLUMN corrected_at DATETIME,
    ADD CONSTRAINT fk_zeffy_payment_change_correction
        FOREIGN KEY (correction_journal_entry_id) REFERENCES journal_entry (id) ON DELETE SET NULL;

CREATE INDEX idx_zeffy_payment_change_correction_status
    ON zeffy_payment_change (correction_status);
