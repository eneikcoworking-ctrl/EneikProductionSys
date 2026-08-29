-- 2026-08-29, action plan 8.3 item 4, on the ground of 8.8.
--
-- A brief is withheld from compilation for good once its budget is spent AND the compiler was reached -
-- read as "we asked and got nothing back". For these six that reading is wrong twice over. The compiler did
-- answer; what refused the answer was this factory's own validator. And the reason it refused was not
-- recorded at all: compilerPlanRejection began reporting one only after these verdicts were passed. Section
-- 8.8 states the consequence directly - a refusal whose cause nobody can read cannot ground an absorbing
-- verdict.
--
-- Identification is exact and was measured before this migration was written: raw briefs (no compiledByRole)
-- still pending, budget spent, compiler reached - and not one of them ever produced a single task. Six rows,
-- and nothing else in the table matches.
--
-- This is not leniency. The next round passes the verdict again if it is deserved, but with a reason
-- recorded beside it, which is a verdict that can be read and argued with. Liveness 8.11-L6 is restored at
-- the same time: the factory regains a way to ASK rather than only to wait.
UPDATE wishlist
   SET compile_attempts = 0
 WHERE status = 'pending'
   AND compiled_by_role IS NULL
   AND compile_attempts >= 3
   AND last_compile_reached_at IS NOT NULL
   AND id NOT IN (SELECT DISTINCT source_wishlist_id FROM tasks WHERE source_wishlist_id IS NOT NULL);
