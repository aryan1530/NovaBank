CREATE TABLE accounts (
    id              BIGSERIAL PRIMARY KEY,
    account_number  VARCHAR(18) NOT NULL UNIQUE,
    user_id         BIGINT NOT NULL,
    customer_id     BIGINT NOT NULL,
    type            VARCHAR(24) NOT NULL,
    currency        CHAR(3) NOT NULL DEFAULT 'INR',
    balance         NUMERIC(18,2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version         BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_accounts_user ON accounts(user_id);

CREATE TABLE fixed_deposits (
    id              BIGSERIAL PRIMARY KEY,
    account_id      BIGINT NOT NULL REFERENCES accounts(id),
    principal       NUMERIC(18,2) NOT NULL CHECK (principal > 0),
    rate            NUMERIC(5,2) NOT NULL,
    tenure_months   INT NOT NULL CHECK (tenure_months > 0),
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    matured_amount  NUMERIC(18,2) NOT NULL,
    start_date      DATE NOT NULL,
    maturity_date   DATE NOT NULL
);

CREATE TABLE loan_applications (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    account_id      BIGINT NOT NULL REFERENCES accounts(id),
    amount          NUMERIC(18,2) NOT NULL CHECK (amount > 0),
    tenure_months   INT NOT NULL,
    purpose         VARCHAR(128) NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'SUBMITTED',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
