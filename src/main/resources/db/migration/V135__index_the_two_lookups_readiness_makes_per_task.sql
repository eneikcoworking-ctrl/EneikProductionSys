-- V135 (2026-09-03, plan 4.52): index the two lookups a readiness computation makes for every task.
--
-- Measured on the live circuit: /flow-spine answers in 20-96 seconds, and the orchestrator asks it on every
-- tick. The cost was counted rather than guessed - two earlier guesses were both wrong:
--
--     mergedReviews round trips this computation: 59
--
-- Each round trip is two queries: the task's sessions, then those sessions' merged reviews. Neither column
-- carries an index. jules_sessions.task_id has only a foreign key (V10) and pr_reviews.jules_session_id
-- only a foreign key (V37), so each of the ~120 queries scans its table, and both tables have grown with
-- five hundred tasks' worth of history.
--
-- This is the same remedy V118 applied to jules_sessions(account_id), for the same reason and in the same
-- words: "the table has no index on account_id at all - every selection...". It changes what a query COSTS,
-- never what it ANSWERS, which is why it is the right shape of fix here: an earlier attempt to cut the
-- number of queries by prefetching changed the answer when the batch was incomplete and was reverted.
CREATE INDEX IF NOT EXISTS idx_jules_sessions_task ON jules_sessions (task_id);
CREATE INDEX IF NOT EXISTS idx_pr_reviews_session ON pr_reviews (jules_session_id);
CREATE INDEX IF NOT EXISTS idx_pr_reviews_session_merged ON pr_reviews (jules_session_id, merged);
