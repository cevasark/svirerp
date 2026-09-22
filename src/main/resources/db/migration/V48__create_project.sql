-- V48__create_project.sql
-- Governance "Projects" — lightweight task tracking (distinct from Finance's Fund/"Project"
-- restricted-fund-accounting concept). Project has many Tasks; both Project and Task carry their
-- own comment thread. Comment authorship is always the logged-in user (Google or local-admin),
-- stamped server-side — there is no separate `User` table in this app, so author_name is a plain
-- denormalized string, not a FK (see AuthController#me for the same {email, name} shape).

CREATE TABLE project (
    id                  CHAR(36)            PRIMARY KEY DEFAULT (UUID()),
    org_id              CHAR(36)            NOT NULL,
    name                VARCHAR(255)        NOT NULL,
    description         TEXT,
    status              VARCHAR(20)         NOT NULL DEFAULT 'planning'
                             CHECK (status IN ('planning', 'in_progress', 'on_hold', 'completed', 'cancelled')),
    due_date            DATE,
    assignee_person_id  CHAR(36),
    created_at          DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_project_org      FOREIGN KEY (org_id)             REFERENCES organization (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_assignee FOREIGN KEY (assignee_person_id) REFERENCES person (id)        ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_project_org      ON project (org_id);
CREATE INDEX idx_project_status   ON project (status);
CREATE INDEX idx_project_assignee ON project (assignee_person_id);

CREATE TABLE project_task (
    id                  CHAR(36)            PRIMARY KEY DEFAULT (UUID()),
    project_id          CHAR(36)            NOT NULL,
    name                VARCHAR(255)        NOT NULL,
    description         TEXT,
    status              VARCHAR(20)         NOT NULL DEFAULT 'todo'
                             CHECK (status IN ('todo', 'in_progress', 'blocked', 'done')),
    assignee_person_id  CHAR(36),
    created_at          DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_project_task_project  FOREIGN KEY (project_id)         REFERENCES project (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_task_assignee FOREIGN KEY (assignee_person_id) REFERENCES person (id)  ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_project_task_project  ON project_task (project_id);
CREATE INDEX idx_project_task_status   ON project_task (status);
CREATE INDEX idx_project_task_assignee ON project_task (assignee_person_id);

CREATE TABLE project_comment (
    id          CHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    project_id  CHAR(36)     NOT NULL,
    comment     TEXT         NOT NULL,
    author_name VARCHAR(255) NOT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_project_comment_project FOREIGN KEY (project_id) REFERENCES project (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_project_comment_project ON project_comment (project_id);

CREATE TABLE project_task_comment (
    id              CHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    project_task_id CHAR(36)     NOT NULL,
    comment         TEXT         NOT NULL,
    author_name     VARCHAR(255) NOT NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_project_task_comment_task FOREIGN KEY (project_task_id) REFERENCES project_task (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_project_task_comment_task ON project_task_comment (project_task_id);
