-- 2026-08-08 (ML-update patch, Phase 4 / lever F2_ACCOUNT_ROLE_SUCCESS_PROBABILITY): Beta-Bernoulli
-- posterior over "does a dispatch to this (account, role) pair succeed", separate from invariant #15's
-- estimatedDailyCapacity (which tracks CAPACITY - how many dispatches fit - not success PROBABILITY of a
-- given dispatch). alpha=beta=1 is the uninformative Bayes-Laplace prior (uniform over [0,1]) - a single
-- early outcome must not dominate the estimate, same discipline as invariant #15's slow-start floor.
CREATE TABLE account_role_success_stats (
    id UUID NOT NULL,
    account_id UUID NOT NULL,
    role_tag VARCHAR(64) NOT NULL,
    alpha DOUBLE NOT NULL DEFAULT 1,
    beta DOUBLE NOT NULL DEFAULT 1,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_account_role_success_stats UNIQUE (account_id, role_tag)
);
