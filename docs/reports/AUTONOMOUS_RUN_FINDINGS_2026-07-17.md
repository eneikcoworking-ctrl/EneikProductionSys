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
  `processAutoMerge()` a second time and assert the state stays a no-op. Tests running now; will deploy if
  green.
