-- Users
CREATE TABLE IF NOT EXISTS users (
    id              UUID PRIMARY KEY,
    code            VARCHAR(128) NOT NULL UNIQUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Zones
CREATE TABLE IF NOT EXISTS zones (
    id              UUID PRIMARY KEY,
    code            VARCHAR(128) NOT NULL UNIQUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Checkpoints
CREATE TABLE IF NOT EXISTS checkpoints (
    id              UUID PRIMARY KEY,
    code            VARCHAR(128) NOT NULL UNIQUE,
    from_zone_id    UUID,
    to_zone_id      UUID,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_checkpoint_from_zone FOREIGN KEY (from_zone_id) REFERENCES zones(id),
    CONSTRAINT fk_checkpoint_to_zone   FOREIGN KEY (to_zone_id)   REFERENCES zones(id)
);

-- Keys (issuer/user keys registry)
CREATE TABLE IF NOT EXISTS keys (
    id              UUID PRIMARY KEY,
    user_id         UUID,
    public_key_pem  TEXT         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_keys_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_users_code       ON users(code);
CREATE INDEX IF NOT EXISTS idx_zones_code       ON zones(code);
CREATE INDEX IF NOT EXISTS idx_checkpoints_code ON checkpoints(code);
CREATE INDEX IF NOT EXISTS idx_keys_user        ON keys(user_id);


