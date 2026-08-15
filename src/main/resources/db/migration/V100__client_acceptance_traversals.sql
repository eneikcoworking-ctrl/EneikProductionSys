-- Step 13 (closes F30/F21): traversal evidence - the witness that a declared value chain was actually
-- walked on the deployed instance, by the client, against real content.
--
-- Every valuePath in the market corpus states what must be POSSIBLE. Nothing stated that anything was ever
-- actually DONE, so DELIVERED was computed from merge counts: a claim about what was built standing in for
-- a claim about what was shown. This table holds the missing witness.
--
-- Append-only by intent, exactly like client_runtime_observations: a traversal is an event that happened at
-- a moment, and a product that changes afterwards does not un-happen it - it makes it describe a product
-- that no longer exists, which is the referent test's job to notice, not this table's.
CREATE TABLE client_acceptance_traversals (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    -- Which declared chain, identified the way the corpus identifies it rather than by index: an index
    -- would silently re-point at a different chain the moment a profile gains or reorders a path.
    profile_id VARCHAR(64) NOT NULL,
    actor VARCHAR(64) NOT NULL,
    -- The link's own text, verbatim from the corpus path. Same reason: the referent must survive the
    -- corpus being edited, and a position cannot.
    link VARCHAR(512) NOT NULL,
    traversed_at TIMESTAMP NOT NULL,
    -- Who walked it. The rule requires the CLIENT to have walked it; a factory-side walk is evidence of a
    -- different proposition and must be distinguishable rather than quietly counted the same.
    walked_by VARCHAR(32) NOT NULL,
    -- What was observed - a URL, a screenshot path, a response summary. A witness nobody can re-check is
    -- an assertion, not evidence.
    evidence VARCHAR(2000),
    -- The live instance this was walked on, so a traversal can be tied to the deployment it describes.
    instance_url VARCHAR(512)
);

CREATE INDEX idx_acceptance_traversals_project ON client_acceptance_traversals (project_id, traversed_at DESC);
