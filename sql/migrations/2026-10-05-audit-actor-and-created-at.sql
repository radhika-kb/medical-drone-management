-- Run once on an existing MySQL database before starting the updated backend.
ALTER TABLE audit_logs
    ADD COLUMN actor_user_id BIGINT NULL AFTER id,
    CHANGE COLUMN `timestamp` created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE audit_logs
    ADD CONSTRAINT fk_audit_actor_user FOREIGN KEY (actor_user_id) REFERENCES users(id),
    ADD INDEX idx_audit_created_at (created_at),
    ADD INDEX idx_audit_action (action);
