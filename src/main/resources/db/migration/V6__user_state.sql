-- User State table for tracking current zone of each user
-- Uses optimistic locking with version field to prevent race conditions
CREATE TABLE IF NOT EXISTS user_state (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL UNIQUE,
    current_zone_id UUID,  -- NULL means user is outside (OUT zone)
    version         BIGINT NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_user_state_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_user_state_zone FOREIGN KEY (current_zone_id) REFERENCES zones(id)
);

-- Index for fast lookup by user_id
CREATE INDEX IF NOT EXISTS idx_user_state_user ON user_state(user_id);

-- Index for zone-based queries
CREATE INDEX IF NOT EXISTS idx_user_state_zone ON user_state(current_zone_id);

