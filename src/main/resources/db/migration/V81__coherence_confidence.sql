-- Bovens & Hartmann-style Bayesian corroboration score (Phase 4) - nullable: only computed for nodes that
-- are part of an accepted, agreeing cluster (a lone rejected node, or one with no cluster at all, has
-- nothing to corroborate a confidence score against).
ALTER TABLE coherence_run_node_results ADD COLUMN confidence DOUBLE;
