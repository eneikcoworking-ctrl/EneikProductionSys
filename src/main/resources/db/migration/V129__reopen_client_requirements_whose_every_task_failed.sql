-- V129 (2026-08-30, plan §4.40 and §5.11): client requirements whose every attempt failed re-enter the flow.
--
-- Measured on the live circuit, 2026-08-29/30, over the epics rooted in the client's own briefs:
--
--     Moodle SSO Authentication and Role Mapping   4 done, 1 in flight
--     Measurement: Moodle SSO Adoption             1 done
--     Repair API Authentication Invariants         2 done
--     System Backup and Verified Restore           1 done
--     Self-Service Account Recovery                2 done, 1 FAILED   (PR#439 merged carrying no code)
--     Data Subject Rights and Privacy Consent      0 done, 2 FAILED   (PR#437 closed unmerged; then the
--                                                                      resume/block loop of §5.11)
--
-- So the queue was not empty because the client's scope was finished. It was empty because three of its
-- work items ended terminally and nothing re-planned them: one whole epic delivered nothing at all.
--
-- The mechanism that should have carried the requirement forward is reopening the brief so the compiler
-- mints a fresh task - "what survives is the REQUIREMENT, not the task identity". It did not run for these:
-- for the empty merge because the reopen refused a `dismissed` brief (fixed forward in the same session),
-- and for the other two because the resume/block loop consumed their only automatic retry instead.
--
-- Charter invariant 8: an element that can still reach a verdict must not leave the decision set. These can:
-- their briefs exist, the compiler is working, and the client's own PROJECT_BRIEF.md still names the scope.
--
-- Bounded by construction - a migration runs once. The predicate is narrow on purpose: only briefs whose
-- tasks ALL ended `failed`, that produced at least one task, and that belong to an epic rooted in a brief
-- the client wrote.
UPDATE wishlist w
SET status = 'pending', compile_attempts = 0
WHERE w.status IN ('dismissed', 'converted_to_task')
  AND EXISTS (SELECT 1 FROM tasks t WHERE t.source_wishlist_id = w.id)
  AND NOT EXISTS (SELECT 1 FROM tasks t WHERE t.source_wishlist_id = w.id AND t.status <> 'failed')
  AND EXISTS (
      SELECT 1 FROM tasks t
      JOIN features f ON f.id = t.feature_id
      JOIN wishlist root ON root.id = f.root_wishlist_id
      WHERE t.source_wishlist_id = w.id AND root.source = 'client');
