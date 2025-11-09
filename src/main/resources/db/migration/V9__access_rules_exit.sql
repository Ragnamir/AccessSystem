-- Allow exit from zones to outside (OUT)
-- Changes to_zone_id to allow NULL values for exit rules
-- NULL to_zone_id means exit to outside (OUT zone)

-- Drop the NOT NULL constraint on to_zone_id
ALTER TABLE access_rules ALTER COLUMN to_zone_id DROP NOT NULL;

-- Update comment to reflect that to_zone_id can be NULL for exits
COMMENT ON COLUMN access_rules.to_zone_id IS 'Destination zone (NULL means exit to outside/OUT zone)';

-- Add index for fast lookup of exit rules (where to_zone_id IS NULL)
CREATE INDEX IF NOT EXISTS idx_access_rules_user_from_exit ON access_rules(user_id, from_zone_id) WHERE to_zone_id IS NULL;

