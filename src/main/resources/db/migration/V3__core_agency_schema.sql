-- V3: Core Agency Schema for Production OS
-- Target: PostgreSQL (compatible with H2 for testing)

-- 1. Roles Table
CREATE TABLE roles (
    tag VARCHAR(16) NOT NULL,
    description TEXT,
    rules_path VARCHAR(256) NOT NULL,
    active BOOLEAN DEFAULT TRUE NOT NULL,
    PRIMARY KEY (tag)
);

-- 2. Accounts Table
CREATE TABLE accounts (
    id UUID NOT NULL,
    name VARCHAR(64) NOT NULL,
    status VARCHAR(16) DEFAULT 'idle' NOT NULL,
    capabilities TEXT NOT NULL, -- Comma-separated TAG-XX strings
    last_heartbeat TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (id)
);

-- 3. Tasks Table
CREATE TABLE tasks (
    id UUID NOT NULL,
    tag VARCHAR(16) NOT NULL,
    description TEXT NOT NULL,
    payload JSON, -- Using JSON instead of JSONB for H2 compatibility in Postgres Mode
    status VARCHAR(16) DEFAULT 'queued' NOT NULL,
    linear_issue_id VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_tasks_role FOREIGN KEY (tag) REFERENCES roles(tag)
);

CREATE INDEX idx_tasks_status_tag ON tasks(status, tag);

-- 4. Claims Table
CREATE TABLE claims (
    id UUID NOT NULL,
    task_id UUID NOT NULL,
    account_id UUID NOT NULL,
    role_tag VARCHAR(16) NOT NULL,
    claimed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    lease_expires_at TIMESTAMP NOT NULL,
    released_at TIMESTAMP,
    result_status VARCHAR(16),
    PRIMARY KEY (id),
    CONSTRAINT fk_claims_task FOREIGN KEY (task_id) REFERENCES tasks(id),
    CONSTRAINT fk_claims_account FOREIGN KEY (account_id) REFERENCES accounts(id),
    CONSTRAINT fk_claims_role FOREIGN KEY (role_tag) REFERENCES roles(tag)
);

-- Note: The unique constraint for active claims (released_at IS NULL)
-- is intentionally omitted here for H2 compatibility.
-- It MUST be enforced in the PostgreSQL production environment and
-- at the application level in Java.

-- Seed 12 Roles
INSERT INTO roles (tag, description, rules_path) VALUES ('BARCAN-TAG-00', 'Code Guardian / Tech Lead', 'BARCAN-TAG-00_CODE-GUARDIAN.md');
INSERT INTO roles (tag, description, rules_path) VALUES ('BARCAN-TAG-01', 'Solution Architect', 'BARCAN-TAG-01_ACTUALIST-OBJECT.md');
INSERT INTO roles (tag, description, rules_path) VALUES ('BARCAN-TAG-02', 'Backend Engineer', 'BARCAN-TAG-02_RIGID-DESIGNATOR.md');
INSERT INTO roles (tag, description, rules_path) VALUES ('BARCAN-TAG-03', 'UI/UX Designer', 'BARCAN-TAG-03_BELIEF-INTENSION.md');
INSERT INTO roles (tag, description, rules_path) VALUES ('BARCAN-TAG-04', 'ML Engineer', 'BARCAN-TAG-04_MODAL-QUANTIFIER.md');
INSERT INTO roles (tag, description, rules_path) VALUES ('BARCAN-TAG-05', 'DevOps / SRE', 'BARCAN-TAG-05_NECESSARY-IDENTITY.md');
INSERT INTO roles (tag, description, rules_path) VALUES ('BARCAN-TAG-06', 'QA Automation', 'BARCAN-TAG-06_DEONTIC-CONSISTENCY.md');
INSERT INTO roles (tag, description, rules_path) VALUES ('BARCAN-TAG-07', 'AppSec / Security', 'BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE.md');
INSERT INTO roles (tag, description, rules_path) VALUES ('BARCAN-TAG-08', 'Data Engineer / DBA', 'BARCAN-TAG-08_SUBSTITUTIVITY-SALVA-VERITATE.md');
INSERT INTO roles (tag, description, rules_path) VALUES ('BARCAN-TAG-09', 'Technical Product Manager', 'BARCAN-TAG-09_MORAL-DILEMMA.md');
INSERT INTO roles (tag, description, rules_path) VALUES ('BARCAN-TAG-10', 'Compliance Officer', 'BARCAN-TAG-10_DEONTIC-PROHIBITION.md');
INSERT INTO roles (tag, description, rules_path) VALUES ('BARCAN-TAG-11', 'Frontend Engineer', 'BARCAN-TAG-11_CLIENT-PERCEPTION.md');
