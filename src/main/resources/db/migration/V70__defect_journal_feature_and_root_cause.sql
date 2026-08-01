-- V70: unified Lean/TOC/Six Sigma system, Layer 1 substrate (2026-08-01).
-- feature_id: u-chart subgroup (эпик), never a calendar-date bucket or cross-project aggregate.
-- root_cause_pattern_id: links to docs/ENGINEERING_INVARIANTS_CHARTER.md pattern 1-12, null pending triage.
ALTER TABLE defect_journal ADD COLUMN feature_id UUID;
ALTER TABLE defect_journal ADD COLUMN root_cause_pattern_id INT;

CREATE INDEX idx_defect_journal_feature ON defect_journal (feature_id);
