-- Union-find canonical-reference pointer for duplicate FeatureEntity rows (engineering invariant #13,
-- docs/ENGINEERING_INVARIANTS_CHARTER.md: rigid designation + substitutivity salva veritate). NULL means
-- this row is its own canonical representative. A non-null value is set exactly once by
-- ClientDeliverableReadinessService.unionDuplicateFeature and never re-pointed afterward - the pointer is
-- rigid, unlike the old per-call deduplicateFeaturesByTitle tie-break it replaces.
ALTER TABLE features ADD COLUMN canonical_feature_id UUID;
