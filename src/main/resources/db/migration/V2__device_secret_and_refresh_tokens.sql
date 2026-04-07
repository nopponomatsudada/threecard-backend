-- Device-secret + refresh-token rotation rollout (security review fixes #1, #3).
--
-- - users.device_secret_hash: nullable so existing users can be silently
--   re-bootstrapped on their next /auth/device call (see DeviceAuthUseCase).
-- - refresh_tokens: opaque rotation tokens, sha256 hash stored.
-- - jwt_blocklist: revoked access JWT jti claims, kept until original exp.

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS device_secret_hash VARCHAR(100);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id          VARCHAR(36) PRIMARY KEY,
    user_id     VARCHAR(36) NOT NULL REFERENCES users(id),
    token_hash  VARCHAR(64) NOT NULL,
    jti         VARCHAR(36) NOT NULL,
    expires_at  TIMESTAMP   NOT NULL,
    revoked_at  TIMESTAMP,
    created_at  TIMESTAMP   NOT NULL,
    CONSTRAINT refresh_tokens_token_hash_key UNIQUE (token_hash),
    CONSTRAINT refresh_tokens_jti_key        UNIQUE (jti)
);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id    ON refresh_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);

CREATE TABLE IF NOT EXISTS jwt_blocklist (
    jti        VARCHAR(36) PRIMARY KEY,
    expires_at TIMESTAMP   NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_jwt_blocklist_expires_at ON jwt_blocklist (expires_at);
