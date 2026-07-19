-- Which Jules account actually authenticated the branch's PR. Continuation (startingBranch) must be
-- pinned to this account: a different account picking up the next task for the same role has no
-- verified relationship to a branch it didn't create, and cross-account branch continuation on the
-- Jules API has never been tested. Nullable/ON DELETE SET NULL - losing the account link degrades to
-- "no continuation this round" (falls back to main), never to an error.
ALTER TABLE role_threads ADD COLUMN account_id UUID REFERENCES accounts(id) ON DELETE SET NULL;
