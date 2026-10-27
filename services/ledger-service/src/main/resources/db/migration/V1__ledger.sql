CREATE TABLE journal_entries (
    id              BIGSERIAL PRIMARY KEY,
    transfer_id     BIGINT NOT NULL UNIQUE,
    description     VARCHAR(255) NOT NULL,
    posted_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE journal_lines (
    id              BIGSERIAL PRIMARY KEY,
    entry_id        BIGINT NOT NULL REFERENCES journal_entries(id),
    account_id      BIGINT NOT NULL,
    debit           NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (debit >= 0),
    credit          NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (credit >= 0),
    CONSTRAINT chk_line_side CHECK (
        (debit > 0 AND credit = 0) OR (credit > 0 AND debit = 0)
    )
);
CREATE INDEX idx_journal_lines_account ON journal_lines(account_id);

CREATE TABLE reconciliation_runs (
    id              BIGSERIAL PRIMARY KEY,
    ran_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    balanced        BOOLEAN NOT NULL,
    debit_total     NUMERIC(18,2) NOT NULL,
    credit_total    NUMERIC(18,2) NOT NULL,
    notes           TEXT
);
