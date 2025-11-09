-- Checkpoint Public Keys Registry
CREATE TABLE IF NOT EXISTS checkpoint_keys (
    id              UUID PRIMARY KEY,
    checkpoint_code VARCHAR(128) NOT NULL UNIQUE,
    public_key_pem  TEXT         NOT NULL,
    key_type        VARCHAR(20)  NOT NULL DEFAULT 'RSA', -- 'RSA' or 'ECDSA'
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Index for fast lookup by checkpoint code
CREATE INDEX IF NOT EXISTS idx_checkpoint_keys_code ON checkpoint_keys(checkpoint_code);


