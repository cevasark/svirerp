-- V46__relax_zeffy_import_row_category_check.sql
-- Drops the CHECK (category IN ('Donation','Ticket')) added in V45. A row whose Category is
-- neither of those (e.g. a dispute/chargeback row from Zeffy, seen in practice as Category
-- "Dispute pending" with a negative Amount) is correctly flagged by ZeffyImportService#computeOutcome
-- as outcome='error' so a human can review it — but the row still needs to be *saved* with its raw
-- category value to make that error visible, and the CHECK rejected the save outright, throwing
-- outside the per-row try/catch and aborting the whole import instead of just that one row. Same
-- treatment payment_status always had in the old Payments-format schema (never DB-constrained,
-- only validated in Java) — VALID_CATEGORIES in ZeffyImportService remains the real gate.

ALTER TABLE zeffy_import_row
    MODIFY COLUMN category VARCHAR(30);
