-- Remove legacy per-user exit permissions and enforce destination zone requirement

DELETE FROM access_rules
WHERE to_zone_id IS NULL;

DROP INDEX IF EXISTS ux_access_rules_user_exit;

ALTER TABLE access_rules
    ALTER COLUMN to_zone_id SET NOT NULL;

