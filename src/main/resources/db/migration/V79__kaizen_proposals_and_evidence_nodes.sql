-- Kaizen proposals used to live only in an in-memory ConcurrentHashMap (KaizenService.proposals) - wiped
-- on every backend restart, with no persisted trace beyond a log line. Real table now.
-- id stays a prefixed String ("kz-systemic-<uuid>" / "kz-pattern-<uuid>"), matching the existing
-- KaizenProposal.id shape exactly (not a raw UUID) - no external reference format changes.
CREATE TABLE kaizen_proposals (
    id VARCHAR(64) NOT NULL,
    title TEXT NOT NULL,
    category VARCHAR(32) NOT NULL,
    target_component VARCHAR(255),
    action_description TEXT,
    expected_gain_percent DOUBLE,
    project_id UUID,
    project_name VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    baseline_metric DOUBLE,
    post_metric DOUBLE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    applied_at TIMESTAMP,
    PRIMARY KEY (id)
);

-- Shared evidence-node projection feeding the coherence engine (Thagard/ECHO). Each of the 3 independent
-- signal sources (statistical process control, code-audit falsification, Kaizen) additively writes one row
-- here alongside its own existing write, so the coherence engine has one common schema to reconcile across
-- sources instead of each source being an isolated, uncrossed "clue". Exactly one of the 4 source FK columns
-- is set per row (gemini_finding_id has no FK target yet - Gemini findings become WishlistEntity today).
-- project_id is nullable: some evidence (e.g. KaizenService.recordSystemicDefectProposal called with no
-- project - a genuinely global/factory-wide systemic finding) is not scoped to any single project. A fake
-- sentinel UUID here would be exactly the kind of fabricated data this system's own falsification track
-- exists to catch.
CREATE TABLE evidence_nodes (
    id UUID NOT NULL,
    project_id UUID,
    feature_id UUID,
    pr_number INT,
    polarity VARCHAR(24) NOT NULL,
    summary_text TEXT NOT NULL,
    defect_journal_id UUID,
    code_integrity_finding_id UUID,
    kaizen_proposal_id VARCHAR(64),
    gemini_finding_id UUID,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_evidence_nodes_defect_journal FOREIGN KEY (defect_journal_id) REFERENCES defect_journal(id) ON DELETE CASCADE,
    CONSTRAINT fk_evidence_nodes_code_integrity FOREIGN KEY (code_integrity_finding_id) REFERENCES code_integrity_findings(id) ON DELETE CASCADE,
    CONSTRAINT fk_evidence_nodes_kaizen_proposal FOREIGN KEY (kaizen_proposal_id) REFERENCES kaizen_proposals(id) ON DELETE CASCADE,
    CONSTRAINT chk_evidence_nodes_exactly_one_source CHECK (
        (CASE WHEN defect_journal_id IS NOT NULL THEN 1 ELSE 0 END
       + CASE WHEN code_integrity_finding_id IS NOT NULL THEN 1 ELSE 0 END
       + CASE WHEN kaizen_proposal_id IS NOT NULL THEN 1 ELSE 0 END
       + CASE WHEN gemini_finding_id IS NOT NULL THEN 1 ELSE 0 END) = 1
    )
);
