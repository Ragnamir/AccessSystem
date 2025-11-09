-- Denials table for logging access denials with categorized reasons
-- Records all denied access attempts with specific reason codes
CREATE TABLE IF NOT EXISTS denials (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        VARCHAR(512),  -- Optional: links to event_nonces.event_id if available
    checkpoint_id   UUID,  -- Optional: checkpoint UUID if available
    checkpoint_code VARCHAR(128),  -- Checkpoint code for easier querying
    user_id         UUID,  -- Optional: user UUID if available
    user_code       VARCHAR(128),  -- User code for easier querying
    from_zone_id    UUID,  -- Optional: source zone UUID
    from_zone_code  VARCHAR(128),  -- Source zone code
    to_zone_id      UUID,  -- Optional: destination zone UUID
    to_zone_code    VARCHAR(128),  -- Destination zone code
    reason          VARCHAR(64) NOT NULL,  -- Denial reason: SIGNATURE_INVALID, TOKEN_INVALID, REPLAY, ACCESS_DENIED, STATE_MISMATCH, INTERNAL_ERROR
    details         TEXT,  -- Additional details about the denial
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_denials_checkpoint FOREIGN KEY (checkpoint_id) REFERENCES checkpoints(id),
    CONSTRAINT fk_denials_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_denials_from_zone FOREIGN KEY (from_zone_id) REFERENCES zones(id),
    CONSTRAINT fk_denials_to_zone FOREIGN KEY (to_zone_id) REFERENCES zones(id),
    CONSTRAINT chk_denials_reason CHECK (reason IN (
        'SIGNATURE_INVALID',
        'TOKEN_INVALID',
        'REPLAY',
        'ACCESS_DENIED',
        'STATE_MISMATCH',
        'INTERNAL_ERROR'
    ))
);

-- Indexes for efficient queries
CREATE INDEX IF NOT EXISTS idx_denials_reason ON denials(reason);
CREATE INDEX IF NOT EXISTS idx_denials_checkpoint ON denials(checkpoint_id);
CREATE INDEX IF NOT EXISTS idx_denials_checkpoint_code ON denials(checkpoint_code);
CREATE INDEX IF NOT EXISTS idx_denials_user ON denials(user_id);
CREATE INDEX IF NOT EXISTS idx_denials_user_code ON denials(user_code);
CREATE INDEX IF NOT EXISTS idx_denials_created_at ON denials(created_at);
CREATE INDEX IF NOT EXISTS idx_denials_event_id ON denials(event_id) WHERE event_id IS NOT NULL;

