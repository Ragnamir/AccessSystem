-- Issuer Public Keys Registry
-- Центры выдачи токенов (issuers) имеют публичные ключи для проверки подписей токенов
CREATE TABLE IF NOT EXISTS issuer_keys (
    id              UUID PRIMARY KEY,
    issuer_code     VARCHAR(128) NOT NULL UNIQUE,
    public_key_pem  TEXT         NOT NULL,
    key_type        VARCHAR(20)  NOT NULL DEFAULT 'RSA', -- 'RSA' or 'ECDSA'
    algorithm       VARCHAR(50)  NOT NULL DEFAULT 'RS256', -- 'RS256', 'ES256', etc.
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Index for fast lookup by issuer code
CREATE INDEX IF NOT EXISTS idx_issuer_keys_code ON issuer_keys(issuer_code);

