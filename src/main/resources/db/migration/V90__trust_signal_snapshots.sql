-- 2026-08-08 (ML-update patch, Phase 3 / lever D2_TRUST_SCORE_WEIGHTS): Stage-1 data collection for
-- eventually fitting trust.score's weights against real outcomes instead of the current hand-picked
-- penalties (OperationalTruthService.trust()). Deliberately does NOT yet include a candidate-weight
-- computation or a fitted_model_coefficients table in this patch - honestly, there is not yet enough
-- labeled history (eventual_outcome) to fit anything real, and inventing placeholder weights here would
-- repeat the exact mistake this whole lever exists to fix. This table is the real prerequisite: once
-- enough rows accumulate real eventual_outcome values, a genuine offline fit becomes possible.
CREATE TABLE trust_signal_snapshots (
    id UUID NOT NULL,
    project_id UUID NOT NULL,
    snapshot_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    merged_reviews INT NOT NULL,
    quality_gate_passed INT NOT NULL,
    quality_gate_failed INT NOT NULL,
    failing_reviews INT NOT NULL,
    duplicate_content BOOLEAN NOT NULL,
    recent_defects_count INT NOT NULL,
    computed_score DOUBLE NOT NULL,
    eventual_outcome VARCHAR(32),
    outcome_recorded_at TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX idx_trust_signal_snapshots_project ON trust_signal_snapshots (project_id, snapshot_at);
CREATE INDEX idx_trust_signal_snapshots_unresolved ON trust_signal_snapshots (eventual_outcome, snapshot_at);
