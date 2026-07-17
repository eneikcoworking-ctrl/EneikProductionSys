# Autonomous run findings — test-twenty-fourth

Started: 2026-07-17T14:16:59Z, after a full task reset (see conversation for context: all
tasks deleted, wishlist items reset to `pending`, system restarted from zero).

Monitoring setup: a zero-cost background process tails `docker compose logs backend` into
`logs/orchestration_raw.log` (gitignored, full detail, no LLM involved). A narrow Monitor
watches that file for `ERROR|Exception|Failed to|blocked|circuit breaker|dialog_limit_exceeded|
activity_log_overflow|role_mismatch_followup` only — routine successful dispatch/compile lines
are deliberately excluded to keep this cheap. Entries below are added only when something is
actually worth a human's attention: a bug, a design weak spot, or a recurring pattern.

---

## Format for entries

`### <UTC timestamp> — <short title>`
- **Observed:** what happened (log lines / evidence)
- **Why it matters:** the actual impact
- **Suggestion:** concrete fix or "no action needed, noted for pattern-tracking"

---

### 2026-07-17T14:53Z — AutoMergeService re-evaluated already-resolved "complex" spikes every cycle forever

- **Observed:** In a 15-minute window, the same PRs (e.g. `test-twentieth/pull/9`, `test-twenty-second/pull/9`,
  and ~10 others) each logged "AutoMergeService: Merging PR ... due to valid approval token and green CI"
  immediately followed by "Cynefin Domain complex: Task X spike completed. Not merging branch." — identically,
  12 times each (once per ~60s scheduled cycle), with zero state change between occurrences.
- **Why it matters:** `AutoMergeService.processAutoMerge()` selects all `PrReviewEntity` rows where
  `ciStatus = success` and `merged != true`. When a task's Cynefin domain is "complex", `executeMerge()`
  correctly marks the task `spike_completed` (a terminal status) but sets `review.merged = false` (true would
  be a lie - it wasn't merged). Since `false` still satisfies `!Boolean.TRUE.equals(merged)`, the review keeps
  matching the query forever. Nothing was actually broken (no exception, no wrong outcome), just wasted
  work every single cycle, forever, for every project that ever produces a "complex" spike PR - a "dumb"
  design smell rather than a hard bug, exactly the pattern the user asked to watch for.
- **Suggestion:** Fixed directly (small, safe, narrowly scoped): added `isAlreadyResolvedSpike()` filter to
  `processAutoMerge()`'s candidate query that excludes reviews whose task has already reached
  `spike_completed`. Extended `AutonomousPipelineIntegrationTest#testComplexDomainSpikeStatus` to call
  `processAutoMerge()` a second time and assert the state stays a no-op.
- **Status: DEPLOYED and confirmed 2026-07-17T16:05Z.** Commit `5ecc4dc`, live in container created
  2026-07-17T15:57:55Z. Verified via `docker compose logs backend --since <redeploy time>`: zero
  `AutoMergeService` log lines at all for the previously-repeating PRs (was ~12-15 identical
  merge-attempt/reject cycles per 15 min before the fix) - the loop is gone.

**Side note on tooling reliability during this fix:** deploying this took 5 attempts because this
session's background-task completion notifications repeatedly reported `killed` for processes that were
actually still running fine underneath (confirmed via `docker ps`/`docker logs` on the orphaned container
and via the redirected host-side log file, both of which showed genuine progress and eventual
`BUILD SUCCESS` regardless of what the notification claimed). Ground truth was always the file/container
state, never the notification. Not an application bug, but worth knowing if future autonomous-run
sessions see repeated spurious "killed" events.
