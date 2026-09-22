-- V47__add_membership_flag_to_zeffy_campaign_mapping.sql
-- Zeffy's checkout implements fixed-price membership registration as a "Ticket"-category product,
-- not "Donation" — the importer treats all Ticket rows as non-membership (correct for genuine
-- event/service tickets, wrong for a campaign that's actually collecting membership dues). Rather
-- than guess from campaign-title keywords (fragile, language-dependent), the admin marks a
-- campaign as a membership payment explicitly, once, in the same screen already used to map each
-- campaign to a Fund — see ZeffyImportRowApplier#applyRow.

ALTER TABLE zeffy_campaign_mapping
    ADD COLUMN is_membership_payment BOOLEAN NOT NULL DEFAULT FALSE;
