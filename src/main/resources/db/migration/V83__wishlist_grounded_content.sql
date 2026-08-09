-- Precision-grounding cache (2026-08-07, operator directive): the client's own raw wording stays untouched
-- in `content` - this column holds the same text with real, already-established mathematical/philosophical
-- patterns (idempotency, atomicity, etc. - the same corpus already indexed for role-charter RAG retrieval)
-- attached as extra context wherever a real match is found, computed once and cached lazily on first
-- compilation (RequirementGroundingService), never destructively replacing the original brief.
ALTER TABLE wishlist ADD COLUMN grounded_content TEXT;
