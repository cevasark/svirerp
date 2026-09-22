-- V50__checklist_sibling_to_project.sql
-- Reworks the checklist feature (added V49, nested under a single ProjectTask) to be a sibling of
-- ProjectTask directly under Project instead — removes the "create a task first just to attach a
-- checklist" workflow friction (confirmed with the user). A project can now hold multiple
-- independent checklists (true sibling to Task, not capped at one — also confirmed with the user),
-- each still with its own Title/Completion Date/items.
--
-- Data-preserving: production already had one real checklist created via the V49 UI by the time
-- this was requested, so project_id is backfilled from each checklist's former task before the old
-- FK is dropped, rather than dropping and recreating the tables.

ALTER TABLE task_checklist ADD COLUMN project_id CHAR(36) NULL AFTER id;

UPDATE task_checklist tc
JOIN project_task pt ON pt.id = tc.project_task_id
SET tc.project_id = pt.project_id;

ALTER TABLE task_checklist DROP FOREIGN KEY fk_task_checklist_task;
ALTER TABLE task_checklist DROP INDEX project_task_id;
ALTER TABLE task_checklist DROP COLUMN project_task_id;
ALTER TABLE task_checklist MODIFY COLUMN project_id CHAR(36) NOT NULL;
ALTER TABLE task_checklist ADD CONSTRAINT fk_project_checklist_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE;
CREATE INDEX idx_project_checklist_project ON task_checklist (project_id);

RENAME TABLE task_checklist TO project_checklist;
RENAME TABLE task_checklist_item TO project_checklist_item;

ALTER TABLE project_checklist_item DROP FOREIGN KEY fk_task_checklist_item_checklist;
ALTER TABLE project_checklist_item ADD CONSTRAINT fk_project_checklist_item_checklist
    FOREIGN KEY (checklist_id) REFERENCES project_checklist (id) ON DELETE CASCADE;
ALTER TABLE project_checklist_item RENAME INDEX idx_task_checklist_item_checklist TO idx_project_checklist_item_checklist;
