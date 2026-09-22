-- V51__checklist_item_detail.sql
-- Optional one-line detail captured when a checklist item is marked Done or Skipped (e.g. why it
-- was skipped, or a completion note) — nullable, not required. Re-opening an item always clears
-- it (enforced in GovernanceService#reopenChecklistItem, not just a frontend-side clear), since a
-- reopened item is "new" again and a stale detail from a previous Done/Skip would be misleading.

ALTER TABLE project_checklist_item ADD COLUMN detail VARCHAR(500) NULL AFTER text;
