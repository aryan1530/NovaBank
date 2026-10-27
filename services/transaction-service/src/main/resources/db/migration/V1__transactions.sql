CREATE TABLE transfers (
    id                  BIGSERIAL PRIMARY KEY,
    public_id           UUID NOT NULL UNIQUE,
    idempotency_key     VARCHAR(80) NOT NULL UNIQUE,
    debit_account_id    BIGINT NOT NULL,
    credit_account_id   BIGINT NOT NULL,
    user_id             BIGINT NOT NULL,
    amount              NUMERIC(18,2) NOT NULL CHECK (amount > 0),
    channel             VARCHAR(16) NOT NULL,
    status              VARCHAR(24) NOT NULL,
    narration           VARCHAR(140),
    fraud_score         NUMERIC(6,4),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMPTZ
);

CREATE TABLE scheduled_payments (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL,
    debit_account_id    BIGINT NOT NULL,
    credit_account_id   BIGINT NOT NULL,
    amount              NUMERIC(18,2) NOT NULL CHECK (amount > 0),
    channel             VARCHAR(16) NOT NULL,
    run_at              TIMESTAMPTZ NOT NULL,
    status              VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE outbox_events (
    id              BIGSERIAL PRIMARY KEY,
    topic           VARCHAR(128) NOT NULL,
    payload         TEXT NOT NULL,
    published       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unpublished ON outbox_events(published, id);

CREATE TABLE processed_events (
    event_id        VARCHAR(80) PRIMARY KEY,
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
