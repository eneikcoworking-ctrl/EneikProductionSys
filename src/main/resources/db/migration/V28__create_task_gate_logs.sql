CREATE TABLE task_gate_logs (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL,
    passed BOOLEAN NOT NULL,
    report JSON NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
);
