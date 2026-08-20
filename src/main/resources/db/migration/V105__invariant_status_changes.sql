-- 2026-08-20: the factory computes its own refutation every cycle and forgets it.
--
-- OperationalTruthService.invariants() evaluates seven Charter invariants - delivered_requires_evidence,
-- done_is_not_delivery, closed_unmerged_is_not_delivery, runtime_status_affects_trust,
-- duplicate_content_blocks_throughput_trust, agent_claims_are_weak_evidence,
-- defect_requires_invariant_capture - into a DTO that only the dashboard controller reads. Confirmed by
-- reading every reference to InvariantStatus: nothing persists them. With no stored previous value a
-- transition from `pass` to `warn` is undetectable in principle, so the one signal that means "something
-- the factory asserted about itself has stopped being true" cannot be acted on by anything.
--
-- A row here is written ONLY when the status differs from the last one stored for that
-- (project, invariant). That is deliberate and it is the lesson from KAIZEN_PROPOSALS, measured the same
-- day: 347 rows carrying 10 distinct (category, target_component) pairs, because the write path had no
-- identity while the read path deduplicated. Charter invariant 4, idempotency, belongs at the write.
--
-- What this table is for: it makes "how often does the factory contradict itself" a measured number
-- instead of an estimate, and it is the wake signal for factory-level judgment - refutation, not change.
CREATE TABLE invariant_status_changes (
    id UUID PRIMARY KEY,
    project_id UUID,
    invariant_key VARCHAR(120) NOT NULL,
    status VARCHAR(32) NOT NULL,
    previous_status VARCHAR(32),
    statement VARCHAR(500),
    evidence VARCHAR(2000),
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_invariant_changes_project_key ON invariant_status_changes (project_id, invariant_key, observed_at DESC);
