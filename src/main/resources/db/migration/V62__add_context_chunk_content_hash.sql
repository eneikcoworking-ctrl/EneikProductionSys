-- 2026-07-26 operator directive ("общая цифра быстро кончается" - reduce Gemini spend): reindexStandingKnowledge
-- used to delete-then-reembed EVERY source on EVERY cron tick, even sources whose content never changes
-- (BARCAN charters, engineering invariants, and now the new philosopher-patterns corpus). Storing each
-- source's content hash lets a reindex skip re-embedding a source that hasn't actually changed since last
-- time - real cost, zero behavior change for anything that reads the chunks.
ALTER TABLE context_chunks ADD COLUMN content_hash VARCHAR(64);
