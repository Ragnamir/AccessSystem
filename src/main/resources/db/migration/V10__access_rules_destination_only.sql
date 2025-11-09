-- Align access rules with destination-based permissions

-- 1. Remove duplicate rules per user/destination (including NULL destination)
WITH ranked_rules AS (
    SELECT
        ctid,
        ROW_NUMBER() OVER (
            PARTITION BY user_id, to_zone_id
            ORDER BY created_at, id
        ) AS rn
    FROM access_rules
)
DELETE FROM access_rules ar
USING ranked_rules rr
WHERE ar.ctid = rr.ctid
  AND rr.rn > 1;

-- 2. Nullify legacy from_zone_id values (no longer used)
UPDATE access_rules
SET from_zone_id = NULL
WHERE from_zone_id IS NOT NULL;

-- 3. Drop obsolete unique constraint and indexes relying on from_zone_id
ALTER TABLE access_rules
    DROP CONSTRAINT IF EXISTS uk_access_rules_user_from_to;

DROP INDEX IF EXISTS idx_access_rules_from_zone;
DROP INDEX IF EXISTS idx_access_rules_user_from_to;
DROP INDEX IF EXISTS idx_access_rules_user_from_exit;
DROP INDEX IF EXISTS idx_access_rules_user_to_null;

-- 4. Enforce uniqueness per destination zone (and exit)
CREATE UNIQUE INDEX IF NOT EXISTS ux_access_rules_user_to_zone
    ON access_rules (user_id, to_zone_id)
    WHERE to_zone_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_access_rules_user_exit
    ON access_rules (user_id)
    WHERE to_zone_id IS NULL;

