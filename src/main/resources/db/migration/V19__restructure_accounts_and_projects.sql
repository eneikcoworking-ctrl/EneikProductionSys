-- V19: Global account pool and project status restructuring
-- Part A, B, and C requirements

-- 1. Add new columns to accounts
ALTER TABLE accounts ADD COLUMN api_key TEXT;
ALTER TABLE accounts ADD COLUMN github_username VARCHAR(64);
ALTER TABLE accounts ADD COLUMN enabled BOOLEAN DEFAULT TRUE NOT NULL;

-- 2. Migrate data from jules_configs to accounts as global pool records
INSERT INTO accounts (id, name, capabilities, status, api_key, enabled, github_username, created_at)
SELECT random_uuid(), name, '*', 'idle', api_key, enabled, name, created_at
FROM jules_configs;

-- 3. Cleanup projects: only one active (the latest)
-- Archive all active projects except the most recent one
UPDATE projects
SET status = 'archived'
WHERE status = 'active'
AND id NOT IN (
    SELECT id FROM (
        SELECT id FROM projects WHERE status = 'active' ORDER BY created_at DESC LIMIT 1
    ) AS latest_active
);

-- 4. Cleanup accounts: decommission project-scoped accounts and any non-target global accounts
-- We keep only the global ones for 'eneikdru' and 'eneikcoworking-ctrl'
UPDATE accounts
SET status = 'decommissioned'
WHERE project_id IS NOT NULL
   OR (name NOT IN ('eneikdru', 'eneikcoworking-ctrl'));

-- 5. Finalize schema by removing jules_configs reference
ALTER TABLE accounts DROP CONSTRAINT fk_accounts_jules_config;
ALTER TABLE accounts DROP COLUMN jules_config_id;
DROP TABLE jules_configs;

-- 6. Ensure github_username is correctly set for the target pool accounts
UPDATE accounts SET github_username = 'eneikdru' WHERE name = 'eneikdru' AND status != 'decommissioned';
UPDATE accounts SET github_username = 'eneikcoworking-ctrl' WHERE name = 'eneikcoworking-ctrl' AND status != 'decommissioned';
