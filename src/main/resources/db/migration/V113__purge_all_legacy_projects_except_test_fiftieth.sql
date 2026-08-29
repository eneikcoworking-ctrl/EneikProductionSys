-- 2026-08-29: three DELETE statements removed from this file. Two named project_event_log and
-- project_observer_watermark, which V58 drops fifty-five migrations before this one; the third named
-- role_threads, which V45 renamed to feature_threads sixty-eight migrations before it - and this file
-- deletes from BOTH names, so the surviving one was always the new name and the old one was always dead.
-- On any
-- schema built from this chain they do not exist and this migration cannot complete: measured that day,
-- every @SpringBootTest in the repository failed identically with `Table "PROJECT_OBSERVER_WATERMARK" not
-- found` (TaskClaimServiceTest 8 errors, OrchestrationStatusTest 1), and a fresh deployment could not
-- start at all. Editing an applied migration is safe here and only here: validate-on-migrate is off and
-- EneikProductionApplication calls flyway.repair() before migrate, so the checksum is reconciled; on the
-- live database V113 is already recorded successful and never runs again.
﻿-- Flyway migration V113: Purge all legacy projects and retain ONLY test-fiftieth
-- Preserves test-fiftieth with all its tasks, features, wishlists, and sessions intact.

-- 1. Delete child records referencing tasks from non-fiftieth projects
DELETE FROM claims WHERE task_id IN (SELECT id FROM tasks WHERE project_id NOT IN (SELECT id FROM projects WHERE slug = 'test-fiftieth'));
DELETE FROM jules_activity_responses WHERE jules_session_id IN (SELECT id FROM jules_sessions WHERE task_id IN (SELECT id FROM tasks WHERE project_id NOT IN (SELECT id FROM projects WHERE slug = 'test-fiftieth')));
DELETE FROM pr_reviews WHERE jules_session_id IN (SELECT id FROM jules_sessions WHERE task_id IN (SELECT id FROM tasks WHERE project_id NOT IN (SELECT id FROM projects WHERE slug = 'test-fiftieth')));
DELETE FROM jules_sessions WHERE task_id IN (SELECT id FROM tasks WHERE project_id NOT IN (SELECT id FROM projects WHERE slug = 'test-fiftieth'));
DELETE FROM linear_issue_metadata WHERE task_id IN (SELECT id FROM tasks WHERE project_id NOT IN (SELECT id FROM projects WHERE slug = 'test-fiftieth'));
DELETE FROM needs_human_review WHERE task_id IN (SELECT id FROM tasks WHERE project_id NOT IN (SELECT id FROM projects WHERE slug = 'test-fiftieth'));
DELETE FROM operational_reality_findings WHERE task_id IN (SELECT id FROM tasks WHERE project_id NOT IN (SELECT id FROM projects WHERE slug = 'test-fiftieth'));
DELETE FROM task_conflicts WHERE task_id IN (SELECT id FROM tasks WHERE project_id NOT IN (SELECT id FROM projects WHERE slug = 'test-fiftieth'));
DELETE FROM task_gate_logs WHERE task_id IN (SELECT id FROM tasks WHERE project_id NOT IN (SELECT id FROM projects WHERE slug = 'test-fiftieth'));

-- 2. Delete coherence child records
DELETE FROM coherence_run_node_results WHERE coherence_run_id IN (SELECT id FROM coherence_runs WHERE project_id NOT IN (SELECT id FROM projects WHERE slug = 'test-fiftieth'));

-- 3. Clear accounts link to other projects (accounts themselves are preserved)
UPDATE accounts SET project_id = NULL WHERE project_id NOT IN (SELECT id FROM projects WHERE slug = 'test-fiftieth');

-- 4. Delete project-scoped records
DELETE FROM capability_observations WHERE project_id NOT IN (SELECT id FROM projects WHERE slug = 'test-fiftieth');
DELETE FROM client_acceptance_traversals WHERE project_id NOT IN (SELECT id FROM projects WHERE slug = 'test-fiftieth');
DELETE FROM client_runtime_observations WHERE project_id NOT IN (SELECT id FROM projects WHERE slug = 'test-fiftieth');
DELETE FROM code_integrity_findings WHERE project_id NOT IN (SELECT id FROM projects WHERE slug = 'test-fiftieth');
DELETE FROM coherence_runs WHERE project_id NOT IN (SELECT id FROM projects WHERE slug = 'test-fiftieth');
DELETE FROM defect_journal WHERE project_id NOT IN (SELECT id FROM projects WHERE slug = 'test-fiftieth');
DELETE FROM design_shop_cycles WHERE project_id NOT IN (SELECT id FROM projects WHERE slug = 'test-fiftieth');
DELETE FROM evidence_nodes WHERE project_id NOT IN (SELECT id FROM projects WHERE slug = 'test-fiftieth');
DELETE FROM falsification_runs WHERE project_id NOT IN (SELECT id FROM projects WHERE slug = 'test-fiftieth');
DELETE FROM features WHERE project_id NOT IN (SELECT id FROM projects WHERE slug = 'test-fiftieth');
DELETE FROM feature_threads WHERE project_id NOT IN (SELECT id FROM projects WHERE slug = 'test-fiftieth');
DELETE FROM flow_spine_events WHERE project_id NOT IN (SELECT id FROM projects WHERE slug = 'test-fiftieth');
DELETE FROM gemini_findings WHERE project_id NOT IN (SELECT id FROM projects WHERE slug = 'test-fiftieth');
DELETE FROM gemini_observer_actions WHERE project_id NOT IN (SELECT id FROM projects WHERE slug = 'test-fiftieth');
DELETE FROM gemini_observer_journal WHERE project_id NOT IN (SELECT id FROM projects WHERE slug = 'test-fiftieth');
DELETE FROM github_access_status WHERE project_id NOT IN (SELECT id FROM projects WHERE slug = 'test-fiftieth');
DELETE FROM invariant_status_changes WHERE project_id NOT IN (SELECT id FROM projects WHERE slug = 'test-fiftieth');
DELETE FROM kaizen_proposals WHERE project_id NOT IN (SELECT id FROM projects WHERE slug = 'test-fiftieth');
DELETE FROM onboarding_audit_findings WHERE project_id NOT IN (SELECT id FROM projects WHERE slug = 'test-fiftieth');
DELETE FROM persistent_worker_sessions WHERE project_id NOT IN (SELECT id FROM projects WHERE slug = 'test-fiftieth');
DELETE FROM process_control_snapshots WHERE project_id NOT IN (SELECT id FROM projects WHERE slug = 'test-fiftieth');
DELETE FROM project_file_claims WHERE project_id NOT IN (SELECT id FROM projects WHERE slug = 'test-fiftieth');
DELETE FROM project_final_reports WHERE project_id NOT IN (SELECT id FROM projects WHERE slug = 'test-fiftieth');
DELETE FROM project_generation_state WHERE project_id NOT IN (SELECT id FROM projects WHERE slug = 'test-fiftieth');
DELETE FROM project_hotspot_files WHERE project_id NOT IN (SELECT id FROM projects WHERE slug = 'test-fiftieth');
DELETE FROM review_concerns WHERE project_id NOT IN (SELECT id FROM projects WHERE slug = 'test-fiftieth');
DELETE FROM tasks WHERE project_id NOT IN (SELECT id FROM projects WHERE slug = 'test-fiftieth');
DELETE FROM trust_signal_snapshots WHERE project_id NOT IN (SELECT id FROM projects WHERE slug = 'test-fiftieth');
DELETE FROM wishlist WHERE project_id NOT IN (SELECT id FROM projects WHERE slug = 'test-fiftieth');
DELETE FROM wishlist_items WHERE project_id NOT IN (SELECT id FROM projects WHERE slug = 'test-fiftieth');

-- 5. Delete all other projects
DELETE FROM projects WHERE slug != 'test-fiftieth';
