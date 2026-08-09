-- 2026-08-08 (ML-update patch, Phase 0): shared "lift" for the promotion ladder OperationalTruthService
-- already documents (observe_only -> warn_only -> soft_gate -> hard_gate -> auto_remediate) but never
-- actually moves anything along - every new prediction/decision lever this patch introduces (Kaizen CTQ
-- targeting, embedding duplicate detection, dispatch success estimation, etc) starts at observe_only
-- (compute + log, zero behavioral effect) and is promoted automatically only once real accumulated
-- evidence justifies it (see LeverPromotionService). One shared pair of tables for every lever, not one
-- table per lever (parsimony) - lever_key is the rigid identifier tying a lever's state to its own
-- observation history across restarts and deploys.
CREATE TABLE lever_promotion_state (
    lever_key VARCHAR(64) NOT NULL,
    current_stage VARCHAR(24) NOT NULL DEFAULT 'observe_only',
    sample_count BIGINT NOT NULL DEFAULT 0,
    agreement_count BIGINT NOT NULL DEFAULT 0,
    promoted_at TIMESTAMP,
    demoted_at TIMESTAMP,
    last_evaluated_at TIMESTAMP,
    PRIMARY KEY (lever_key)
);

-- One row per real decision the lever made, incumbent (current live logic) vs candidate (the new
-- mechanism being evaluated) side by side. `agreement` is a 4-valued diagnostic (TRUE/FALSE/BOTH/NEITHER,
-- Belnap) - never itself the final gating verdict, only input to evaluatePromotions' single canonical
-- revision of lever_promotion_state.current_stage.
CREATE TABLE lever_observations (
    id UUID NOT NULL,
    lever_key VARCHAR(64) NOT NULL,
    subject_id VARCHAR(128),
    incumbent_decision TEXT,
    candidate_decision TEXT,
    agreement VARCHAR(16) NOT NULL,
    ground_truth_outcome VARCHAR(64),
    observed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX idx_lever_observations_lever_key_observed_at ON lever_observations (lever_key, observed_at);
