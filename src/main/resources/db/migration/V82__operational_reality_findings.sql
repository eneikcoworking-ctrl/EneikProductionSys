-- Real, traceable record of a detected mismatch between a Jules session's self-reported status and the
-- actual GitHub PR reality (e.g. status stuck at "running" while the real PR is already open/mergeable, or
-- no PrReviewEntity ever got created for a real PR) - the exact bug class behind the 2026-08-06 incident
-- where a factory stall went unnoticed because neither the merge pipeline nor Gemini's observer had any
-- structural way to see "what Jules claims" diverge from "what GitHub actually shows".
CREATE TABLE operational_reality_findings (
    id UUID NOT NULL,
    task_id UUID NOT NULL,
    jules_session_id UUID NOT NULL,
    expected_status VARCHAR(32) NOT NULL,
    actual_github_state VARCHAR(64) NOT NULL,
    pr_url TEXT,
    detected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (id)
);

-- Fifth evidence source for EvidenceCoherenceService (Thagard/ECHO) - additive, same pattern as the other
-- 4: a new nullable FK column plus rewriting the exactly-one-source CHECK to sum 5 terms instead of 4.
ALTER TABLE evidence_nodes ADD COLUMN operational_reality_finding_id UUID;
ALTER TABLE evidence_nodes ADD CONSTRAINT fk_evidence_nodes_operational_reality
    FOREIGN KEY (operational_reality_finding_id) REFERENCES operational_reality_findings(id) ON DELETE CASCADE;

ALTER TABLE evidence_nodes DROP CONSTRAINT chk_evidence_nodes_exactly_one_source;
ALTER TABLE evidence_nodes ADD CONSTRAINT chk_evidence_nodes_exactly_one_source CHECK (
    (CASE WHEN defect_journal_id IS NOT NULL THEN 1 ELSE 0 END
   + CASE WHEN code_integrity_finding_id IS NOT NULL THEN 1 ELSE 0 END
   + CASE WHEN kaizen_proposal_id IS NOT NULL THEN 1 ELSE 0 END
   + CASE WHEN gemini_finding_id IS NOT NULL THEN 1 ELSE 0 END
   + CASE WHEN operational_reality_finding_id IS NOT NULL THEN 1 ELSE 0 END) = 1
);
