CREATE TABLE design_shop_cycles (
    id UUID NOT NULL PRIMARY KEY,
    project_id UUID NOT NULL UNIQUE,
    last_was_ready BOOLEAN NOT NULL DEFAULT FALSE,
    stage VARCHAR(32) NOT NULL DEFAULT 'IDLE',
    draft_path VARCHAR(512),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_design_shop_cycles_stage ON design_shop_cycles (stage);
