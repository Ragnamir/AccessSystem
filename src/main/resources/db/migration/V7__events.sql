-- Events table for logging successful zone transitions
-- Records all successful access events after transaction commits
CREATE TABLE IF NOT EXISTS events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        VARCHAR(512) NOT NULL UNIQUE,  -- Links to event_nonces.event_id
    checkpoint_id   UUID NOT NULL,
    user_id         UUID NOT NULL,
    from_zone_id    UUID,  -- NULL means OUT zone
    to_zone_id      UUID,  -- NULL means OUT zone
    event_timestamp TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_events_checkpoint FOREIGN KEY (checkpoint_id) REFERENCES checkpoints(id),
    CONSTRAINT fk_events_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_events_from_zone FOREIGN KEY (from_zone_id) REFERENCES zones(id),
    CONSTRAINT fk_events_to_zone FOREIGN KEY (to_zone_id) REFERENCES zones(id)
);

-- Indexes for efficient queries
CREATE INDEX IF NOT EXISTS idx_events_event_id ON events(event_id);
CREATE INDEX IF NOT EXISTS idx_events_checkpoint ON events(checkpoint_id);
CREATE INDEX IF NOT EXISTS idx_events_user ON events(user_id);
CREATE INDEX IF NOT EXISTS idx_events_user_timestamp ON events(user_id, event_timestamp);
CREATE INDEX IF NOT EXISTS idx_events_created_at ON events(created_at);

