CREATE TABLE customers (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL UNIQUE,
    full_name     VARCHAR(128) NOT NULL,
    email         VARCHAR(255) NOT NULL UNIQUE,
    phone         VARCHAR(20),
    kyc_status    VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    address_line  VARCHAR(255),
    city          VARCHAR(64),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_customers_user ON customers(user_id);
