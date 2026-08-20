-- 2026-08-20: the product layer of this factory's Six Sigma had no product-level opportunity.
--
-- SixSigmaAuditService.calculateFeatureSixSigmaAudit - the "Product" layer number - counts quality-gate
-- checks, PR conflicts and code-integrity findings. All three are facts about how the work was MADE. None
-- is a defect a user could experience, so the number describes the process that produced the product
-- rather than the product. That is the three-levels confusion (factory / delivery / product) living inside
-- the quality measure itself.
--
-- A capability observation is the missing opportunity type:
--   opportunity = one observation of one DECLARED capability on the running instance
--   defect      = that capability did not work
-- which is precisely what DPMO is meant to count, and it feeds the existing sigma machinery rather than
-- standing beside it as a second statistic.
--
-- The declared set is the product's own OpenAPI contract (docs/contracts/<feature>.openapi.yaml, produced
-- by BARCAN-TAG-12 at stage 27). The denominator therefore comes from what the product ASSERTS about
-- itself, not from the factory's decomposition - Charter invariant 8, and invariant 12, since the witness
-- (the launcher) is external to the agent that wrote the code.
--
-- Popper: a capability is never proven. V_p counts capabilities whose 95% credible LOWER bound clears a
-- declared threshold, so confidence has to be earned by evidence and one failing observation can remove a
-- capability from the count again.
CREATE TABLE capability_observations (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    -- Rigid designator for one capability: "<method> <path>" from the declared contract. A renamed key
    -- silently starts a fresh, evidence-less capability (Frege, BARCAN-TAG-08).
    capability_key VARCHAR(300) NOT NULL,
    source_contract VARCHAR(400),
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    satisfied BOOLEAN NOT NULL,
    status_code INT,
    detail VARCHAR(2000)
);

CREATE INDEX idx_capability_obs_project_key ON capability_observations (project_id, capability_key, observed_at DESC);
