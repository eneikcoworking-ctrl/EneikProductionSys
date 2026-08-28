-- 2026-08-28. compileAttempts records that the dispatcher TRIED; nothing recorded whether the compiler
-- was actually reached. F42 then drew a conclusion about the brief - "needs a human reading, not another
-- retry" - from evidence that was only ever about the factory: measured on test-fiftieth, six briefs spent
-- their entire three-attempt budget while the single persistent compiler worker was busy, so not one
-- message was ever sent. Charter invariant 12: the party producing a result cannot be the only witness to
-- it. This column is the channel's own record.
ALTER TABLE wishlist ADD COLUMN last_compile_reached_at TIMESTAMP WITH TIME ZONE NULL;

-- Existing rows: unknown, left NULL. For a brief whose budget is already spent that reads as UNREACHED,
-- which is the honest default - nothing in the data establishes that any of them was ever put to the
-- compiler, and treating "unknown" as "asked and refused" would repeat the very error this fixes.
