-- Zeffy data now enters through its API and signed webhooks. Remove the obsolete spreadsheet
-- staging/audit tables and title-based mapping. Payment method values and the Zeffy clearing
-- account remain because API payments use them.

DROP TABLE zeffy_import_row;
DROP TABLE zeffy_import_batch;
DROP TABLE zeffy_campaign_mapping;
