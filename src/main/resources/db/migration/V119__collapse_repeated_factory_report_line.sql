-- 2026-08-29, action plan 4.16. withholdFromCompileDispatch appended one line to projects.factory_report
-- every tick for every brief whose decomposition budget was spent - an absorbing fact, re-recorded forever.
-- Measured before this migration: the report held 174687 characters and the sentence "Decomposition budget
-- exhausted for wishlist" appeared 868 times in it; removing every line containing that sentence left ZERO
-- characters. The report a human is asked to read consisted of one fact about six briefs and nothing else.
--
-- Cleared only where that is true, tested by the same expression the measurement used, so a report holding
-- anything else is untouched. Nothing is lost: the corrected code records one line per exhausted brief on
-- the next tick, because the report will no longer contain it.
UPDATE projects
   SET factory_report = NULL
 WHERE factory_report IS NOT NULL
   AND REGEXP_REPLACE(CAST(factory_report AS VARCHAR),
                      '[^' || CHAR(10) || ']*Decomposition budget exhausted[^' || CHAR(10) || ']*' || CHAR(10) || '?',
                      '') = '';
