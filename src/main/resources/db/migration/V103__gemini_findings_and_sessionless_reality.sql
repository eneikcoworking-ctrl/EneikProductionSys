-- Two schema truthfulness fixes. Both remove a category error in which an accidental property of how a
-- fact was discovered was encoded as an essential property of what the fact is.
--
-- (1) gemini_findings - the referent that gemini_finding_id was written for and never given.
--
-- V79 created evidence_nodes.gemini_finding_id and EvidenceNodeEntity.sourceType() returns GEMINI_FINDING
-- for it, but V79's own comment records that it had no FK target: "Gemini findings become WishlistEntity
-- today". Nothing has ever set the column. The observer's assertions therefore enter the evidence graph
-- only as whatever was DONE about them - a wishlist, or a KAIZEN_PROPOSAL - so the graph types them by the
-- channel that stored them rather than by where they came from.
--
-- Measured 2026-08-17: 10 of the 26 KAIZEN_PROPOSAL nodes in her own 24h read window were her own prior
-- findings, and sourceReliability() keys on sourceType, so her prose inherited the reliability earned by
-- measurement-derived proposals (FactorySelfHealthService's 12.9x database bloat is typed identically).
-- distinctHistoricallyCorroboratingSourceTypes then counts her restatement as a second independent source
-- corroborating her own position. That is the mechanism behind the quantifier inflation recorded as F51 -
-- "nearly all done tasks" measured against 1 of 33. A claim that manufactures its own corroboration
-- strengthens regardless of the world.
--
-- The evidence algebra already draws the distinction the schema could not: agent prose is strength 1,
-- "intent or claim, not delivery", and "agent claims are never final evidence". Giving testimony its own
-- persisted identity is what lets the graph honour that. It also discharges Austin's proof obligation as
-- the corpus states it (DZHON_OSTIN_02_CATEGORY_ERROR_SCAN): "reject code that treats an observation as
-- authority without an adapter - point to the type, schema or adapter that preserves the category
-- boundary."
--
-- project_id is nullable on purpose: a platform-scope finding is about EneikProductionSys and belongs to
-- no client project, the same actualist rule already applied to factory-scope Kaizen proposals.

CREATE TABLE gemini_findings (
    id UUID NOT NULL,
    project_id UUID,
    scope VARCHAR(32) NOT NULL,
    severity VARCHAR(16),
    summary TEXT NOT NULL,
    evidence_text TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_gemini_findings PRIMARY KEY (id)
);

CREATE INDEX idx_gemini_findings_project ON gemini_findings (project_id);
CREATE INDEX idx_gemini_findings_created ON gemini_findings (created_at);

ALTER TABLE evidence_nodes ADD CONSTRAINT fk_evidence_nodes_gemini_finding
    FOREIGN KEY (gemini_finding_id) REFERENCES gemini_findings(id) ON DELETE CASCADE;

-- (2) An operational reality finding no longer presupposes a Jules session.
--
-- The table records "the record disagrees with reality". jules_session_id was NOT NULL because the only
-- detector that existed compared a session's self-reported status against the real GitHub PR state. That
-- made a property of the DETECTOR into a property of the FACT.
--
-- Measured on the live blocking instance, 2026-08-17: task f163e834 "Runtime Contract 8becdc01" is status
-- `done` with julesSessionName null, julesDispatchStatus null, no PR and no featureId - it claims
-- completion and has no evidence of any work at all. ClientDeliverableReadinessService detects exactly
-- this and has flagged it `done_not_reached_main` since 2026-08-16, as the single entry in blockedItems.
--
-- Nothing can act on it. blockedItems is consumed only by ProductReadinessDto - a dashboard field, no
-- reasoner. OpsAuditorService gathers evidence only about `failed` tasks and orphaned wishlists, and this
-- task is `done`. The operational-reality detector cannot emit because there is no session to compare.
-- Zero nodes in the evidence graph name it. Meanwhile the project stands at 5/6 features and 25/26 merged
-- tasks: this one item is what separates it from readiness 1.0, the threshold the design shop waits on and
-- above the 0.9 the philosophical track waits on.
--
-- A session is one kind of record, not the essence of the claim. Widening the column to nullable widens
-- the predicate to its true extension; it does not weaken an invariant, it removes an over-specification.

ALTER TABLE operational_reality_findings ALTER COLUMN jules_session_id SET NULL;
