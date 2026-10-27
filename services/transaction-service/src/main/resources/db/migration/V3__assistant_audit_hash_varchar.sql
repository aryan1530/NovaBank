ALTER TABLE assistant_audit
    ALTER COLUMN request_hash TYPE VARCHAR(64) USING request_hash::TEXT;
