CREATE TABLE fraud_alerts (
    id              BIGSERIAL PRIMARY KEY,
    transfer_id     BIGINT,
    user_id         BIGINT,
    score           NUMERIC(6,4) NOT NULL,
    reasons         TEXT NOT NULL,
    status          VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    reviewer_id     BIGINT,
    review_notes    TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
