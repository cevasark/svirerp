-- V67__create_app_user_and_login_history.sql
-- Track users and login timestamps

CREATE TABLE app_user (
    id            CHAR(36)       PRIMARY KEY DEFAULT (UUID()),
    email         VARCHAR(255)   NOT NULL UNIQUE,
    name          VARCHAR(255),
    provider      VARCHAR(50),
    last_login_at DATETIME       NOT NULL,
    login_count   INT            NOT NULL DEFAULT 1,
    created_at    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_app_user_last_login ON app_user (last_login_at DESC);

CREATE TABLE user_login_history (
    id            CHAR(36)       PRIMARY KEY DEFAULT (UUID()),
    app_user_id   CHAR(36),
    email         VARCHAR(255)   NOT NULL,
    login_at      DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    provider      VARCHAR(50),
    CONSTRAINT fk_user_login_app_user FOREIGN KEY (app_user_id) REFERENCES app_user (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_user_login_history_email ON user_login_history (email);
CREATE INDEX idx_user_login_history_time  ON user_login_history (login_at DESC);
