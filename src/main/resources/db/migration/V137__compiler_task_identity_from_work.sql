-- V137 (Law 8 / PART_WHOLE_OWNERSHIP D004, PERSISTENCE_SNAPSHOT D010): a compiler task's identity is a
-- function of the work it does, not minted fresh on every turn.
--
-- Measured 2026-09-05 on project test-fiftieth: 92 tasks created in one day, 69 of them duplicates of four
-- subjects - thirty-one tasks compiling one wishlist, thirty compiling another. Each dispatched task is a
-- Jules session, so the external daily budget was spent on four units of work by sixty-nine requests.
--
-- Why identity rather than another guard. The existing guard (an active compiler task already exists) was
-- never wrong: it honestly saw no live carrier, because a sweep had marked the previous one done. A fresh
-- identity per turn makes the repetition invisible - thirty-one tasks, each with its own attempt counter of
-- one, so the attempt bound of Law 8 measured nothing. With identity derived from the work, the same
-- request finds the same row and increments ITS counter, so the bound finally measures what it names.
--
-- Deliberately NOT a unique constraint: rows created before this migration carry no key and legitimate
-- history holds many terminal tasks for the same work. Uniqueness is enforced by there being exactly one
-- creation site, which a structural screen pins the way the Law 20 S4 screen pins merge sites.
ALTER TABLE tasks ADD COLUMN content_key VARCHAR(255);
CREATE INDEX idx_tasks_content_key ON tasks(content_key);
