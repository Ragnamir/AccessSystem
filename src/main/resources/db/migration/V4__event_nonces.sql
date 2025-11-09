-- Event Nonces table for anti-replay protection
-- Stores eventId/nonce with TTL to prevent replay attacks
CREATE TABLE IF NOT EXISTS event_nonces (
    event_id        VARCHAR(512) PRIMARY KEY,
    checkpoint_id   VARCHAR(128) NOT NULL,
    event_timestamp TIMESTAMPTZ  NOT NULL,
    expires_at      TIMESTAMPTZ  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Index for fast lookup by checkpoint and expiration cleanup
CREATE INDEX IF NOT EXISTS idx_event_nonces_checkpoint ON event_nonces(checkpoint_id);
CREATE INDEX IF NOT EXISTS idx_event_nonces_expires_at ON event_nonces(expires_at);

