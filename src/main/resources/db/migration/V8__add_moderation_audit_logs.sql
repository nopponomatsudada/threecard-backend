CREATE TABLE moderation_audit_logs (
    id VARCHAR(36) PRIMARY KEY,
    reviewer VARCHAR(100) NOT NULL,
    action VARCHAR(20) NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id VARCHAR(36) NOT NULL,
    target_title VARCHAR(140) NOT NULL,
    note VARCHAR(500) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_moderation_audit_logs_created_at
    ON moderation_audit_logs (created_at DESC);

CREATE INDEX idx_moderation_audit_logs_action_created_at
    ON moderation_audit_logs (action, created_at DESC);

CREATE INDEX idx_moderation_audit_logs_target
    ON moderation_audit_logs (target_type, target_id, created_at DESC);
