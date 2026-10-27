CREATE TABLE assistant_audit (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    intent VARCHAR(32) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_assistant_audit_user_created ON assistant_audit(user_id, created_at DESC);
