-- V49__create_task_checklist.sql
-- Optional structured checklist attached to a ProjectTask, alongside (not replacing) the task's
-- own free-text `description`. One checklist per task (UNIQUE project_task_id). Checklist items
-- are deliberately 3-state (new/done/skipped), not a boolean checkbox — both "done" and "skipped"
-- are terminal/resolved outcomes reachable from "new", and either can be walked back to "new" via
-- a single "Re-open" action. `completion_date` is a user-set target date, not auto-derived from
-- item state (confirmed with the user — see GovernanceService).

CREATE TABLE task_checklist (
    id                  CHAR(36)            PRIMARY KEY DEFAULT (UUID()),
    project_task_id     CHAR(36)            NOT NULL UNIQUE,
    title               VARCHAR(255)        NOT NULL,
    completion_date     DATE,
    created_at          DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_task_checklist_task FOREIGN KEY (project_task_id) REFERENCES project_task (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE task_checklist_item (
    id              CHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    checklist_id    CHAR(36)     NOT NULL,
    text            VARCHAR(500) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'new'
                         CHECK (status IN ('new', 'done', 'skipped')),
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_task_checklist_item_checklist FOREIGN KEY (checklist_id) REFERENCES task_checklist (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_task_checklist_item_checklist ON task_checklist_item (checklist_id);
