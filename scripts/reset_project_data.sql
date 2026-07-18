-- Wipes all task/wishlist/session/review data for ONE project, in FK-respecting order.
-- Use this instead of ad-hoc DELETE statements - an earlier manual reset skipped pr_reviews
-- (which had no FK at the time) and left 4 orphaned rows silently invisible to JOIN-based queries.
-- pr_reviews now has ON DELETE CASCADE via jules_sessions (see V37), so deleting jules_sessions
-- here also cleans pr_reviews automatically - it's listed anyway for clarity/documentation.
--
-- Usage (H2 Shell, backend must be stopped first to avoid a file-lock conflict):
--   java -cp h2-2.2.224.jar org.h2.tools.Shell -url "jdbc:h2:file:./data/eneik_db;DB_CLOSE_DELAY=-1" \
--        -user sa -password "" -sql "$(cat scripts/reset_project_data.sql | sed 's/:project_id/<uuid>/g')"
--
-- Replace :project_id below with the actual project UUID before running.

DELETE FROM task_gate_logs WHERE task_id IN (SELECT id FROM tasks WHERE project_id = ':project_id');
DELETE FROM pr_reviews WHERE jules_session_id IN (
    SELECT js.id FROM jules_sessions js JOIN tasks t ON js.task_id = t.id WHERE t.project_id = ':project_id'
);
DELETE FROM jules_activity_responses WHERE jules_session_id IN (
    SELECT js.id FROM jules_sessions js JOIN tasks t ON js.task_id = t.id WHERE t.project_id = ':project_id'
);
DELETE FROM task_conflicts WHERE task_id IN (SELECT id FROM tasks WHERE project_id = ':project_id');
DELETE FROM needs_human_review WHERE task_id IN (SELECT id FROM tasks WHERE project_id = ':project_id');
UPDATE tasks SET depends_on = NULL WHERE project_id = ':project_id';
DELETE FROM jules_sessions WHERE task_id IN (SELECT id FROM tasks WHERE project_id = ':project_id');
DELETE FROM claims WHERE task_id IN (SELECT id FROM tasks WHERE project_id = ':project_id');
DELETE FROM tasks WHERE project_id = ':project_id';

-- Wishlist is reset to pending (not deleted) by default, so orchestrate() recompiles it fresh.
-- Uncomment to hard-delete wishlist too instead:
-- DELETE FROM wishlist WHERE project_id = ':project_id';
UPDATE wishlist
SET status = 'pending', jtbd = NULL, lean_value = NULL, toc_constraint_ref = NULL,
    six_sigma_metric = NULL, dod = NULL, acceptance_criteria = NULL, compiled_by_role = NULL
WHERE project_id = ':project_id' AND status = 'converted_to_task';
