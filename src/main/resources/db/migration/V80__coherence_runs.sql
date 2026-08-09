-- EvidenceCoherenceService (Thagard/ECHO) run results - coherence_score is the external, non-self-reported
-- anchor future termination logic (Gemini's agentic tool-use loop, Phase 5) will check against, instead of
-- trusting the LLM's own claim that it "learned something new" this round.
CREATE TABLE coherence_runs (
    id UUID NOT NULL,
    project_id UUID,
    ran_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    total_nodes INT NOT NULL,
    accepted_nodes INT NOT NULL,
    coherence_score DOUBLE NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE coherence_run_node_results (
    id UUID NOT NULL,
    coherence_run_id UUID NOT NULL,
    evidence_node_id UUID NOT NULL,
    accepted BOOLEAN NOT NULL,
    final_activation DOUBLE NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_crnr_run FOREIGN KEY (coherence_run_id) REFERENCES coherence_runs(id) ON DELETE CASCADE,
    CONSTRAINT fk_crnr_node FOREIGN KEY (evidence_node_id) REFERENCES evidence_nodes(id) ON DELETE CASCADE
);
