CREATE TABLE IF NOT EXISTS agent_accounts (
    id UUID PRIMARY KEY,
    account_code VARCHAR(32) NOT NULL UNIQUE,
    display_name VARCHAR(120) NOT NULL,
    focus_tags VARCHAR(500) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'IDLE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_claimed_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS agent_tasks (
    id UUID PRIMARY KEY,
    requirement_id UUID NOT NULL,
    requirement_title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    agent_tag VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'TODO',
    claimed_by_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    claimed_at TIMESTAMP WITH TIME ZONE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_agent_tasks_claimed_by
        FOREIGN KEY (claimed_by_id)
        REFERENCES agent_accounts(id)
);

CREATE INDEX IF NOT EXISTS idx_agent_tasks_status ON agent_tasks(status);
CREATE INDEX IF NOT EXISTS idx_agent_tasks_agent_tag ON agent_tasks(agent_tag);
CREATE INDEX IF NOT EXISTS idx_agent_tasks_requirement_id ON agent_tasks(requirement_id);
