CREATE TABLE transfer_operations (
    transfer_id BIGINT PRIMARY KEY,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
