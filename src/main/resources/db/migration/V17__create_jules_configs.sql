-- V17: Create jules_configs table to support multiple tokens
CREATE TABLE jules_configs (
    id UUID DEFAULT random_uuid() PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    api_key TEXT NOT NULL,
    enabled BOOLEAN DEFAULT TRUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

ALTER TABLE accounts ADD COLUMN jules_config_id UUID;
ALTER TABLE accounts ADD CONSTRAINT fk_accounts_jules_config FOREIGN KEY (jules_config_id) REFERENCES jules_configs(id);
