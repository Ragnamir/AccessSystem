-- Access Rules table for user-zone access permissions
-- Defines which users can transit from one zone to another
CREATE TABLE IF NOT EXISTS access_rules (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL,
    from_zone_id    UUID,  -- NULL means entry from outside (no previous zone)
    to_zone_id      UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_access_rules_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_access_rules_from_zone FOREIGN KEY (from_zone_id) REFERENCES zones(id),
    CONSTRAINT fk_access_rules_to_zone FOREIGN KEY (to_zone_id) REFERENCES zones(id),
    CONSTRAINT uk_access_rules_user_from_to UNIQUE (user_id, from_zone_id, to_zone_id)
);

-- Indexes for fast lookup
CREATE INDEX IF NOT EXISTS idx_access_rules_user ON access_rules(user_id);
CREATE INDEX IF NOT EXISTS idx_access_rules_from_zone ON access_rules(from_zone_id);
CREATE INDEX IF NOT EXISTS idx_access_rules_to_zone ON access_rules(to_zone_id);
CREATE INDEX IF NOT EXISTS idx_access_rules_user_from_to ON access_rules(user_id, from_zone_id, to_zone_id) WHERE from_zone_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_access_rules_user_to_null ON access_rules(user_id, to_zone_id) WHERE from_zone_id IS NULL;

