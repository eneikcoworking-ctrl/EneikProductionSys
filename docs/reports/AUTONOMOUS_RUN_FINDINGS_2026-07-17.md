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

---

### 2026-07-17T18:0X Z — active_session_age_limit abandons already-opened PRs instead of reviewing them

- **Observed:** User cross-checked GitHub directly (`gh pr list --repo eneikcoworking-ctrl/test-twenty-fourth
  --state all`): 5 PRs merged, 6 still open. The 6 open PRs line up exactly with the project's 5 current
  `blocked` tasks (plus one older PR #3 from before today's reset) - all of them Jules sessions that were
  force-closed by the 180-minute `active_session_age_limit` breaker while their session status was
  `revising`, each followed by a fresh replacement task ("Restart the work as a fresh short session").
- **Why it matters:** `JulesDispatchService.pollStatus()` only calls `detectOpenPullRequestFromGitHub()` when
  `oldStatus` is NOT `"revising"` (line ~394, deliberate: avoids misreading a stale "SUCCEEDED" poll while a
  revision is in flight). But `closeOverdueActiveSessions()`'s age-limit check treats `revising` as an
  active/incomplete state needing closure (`isAgeLimitedActiveStatus`, JulesDispatchService.java:1487-1492)
  with NO check for whether a PR already exists on GitHub. Net effect: if Jules is slow to transition out of
  `revising` and crosses 180 minutes, Eneik never looks at GitHub, assumes zero progress, closes the session,
  and dispatches a brand new task to redo the same work from scratch - while the real PR (which may contain
  working, reviewable code) is left open forever, un-reviewed, un-merged, cluttering the repo. This is not a
  hard bug (no exception) but a genuine "dumb" design smell: real completed/near-complete work is discarded
  based on Eneik's own internal status label, not on GitHub's actual state.
- **Suggestion (recorded, NOT changed yet - needs a human judgment call, offered to the user, awaiting
  answer):** before closing a session for `active_session_age_limit`, check
  `detectOpenPullRequestFromGitHub()` regardless of `revising` status; if a PR exists, route the task to
  review/merge evaluation instead of a fresh restart. Risk to weigh: the `revising` skip was added
  deliberately for a reason (avoiding a specific false-positive), so the fix needs to distinguish "PR exists
  and is real" from whatever edge case the original skip was protecting against - not a pattern-match-and-fix,
  a design decision.

---

### 2026-07-17T19:40Z — Design/video generation never runs: Gemini prepay balance depleted (billing, not a code bug)

- **Observed:** User asked why no design mockups or videos exist for test-twenty-fourth despite the feature
  being enabled (`DESIGN_SERVICE_ENABLED=true`, `NANO_BANANA_ENABLED=true`, `VEO_ENABLED=true`, a valid
  `gemini_api_key` configured in the settings DB). `data/design-assets/` has assets only for a *different*
  project (test-twenty-third); `data/video-assets/` doesn't exist at all. ML service logs
  (`docker compose logs ml`) show the real cause: `HTTP Error calling Gemini API model gemini-3.1-pro-preview:
  429 - {"code":429,"message":"Your prepayment credits are depleted...","status":"RESOURCE_EXHAUSTED"}` -
  and the same 429 for every fallback model (gemini-3.5-flash, gemini-2.5-flash) too.
- **Why it matters:** This is a billing/account issue, not an application bug - the Gemini API key's prepaid
  balance is at zero, so every Gemini-backed call fails regardless of which model is tried. It affects three
  things through the same key: ML-service task-slice classification (falls back to local heuristics, so this
  one is silently self-healing), `DesignAssetService.generateAsset()` (mockups), and `VideoAssetService`
  (Veo videos) - the latter two have no fallback and simply produce nothing.
- **Secondary finding (real code smell):** `GoogleAiResourceService` and `DesignAssetService` contain zero
  `log.*` calls anywhere. A failed design/video generation returns a quiet `available=false` result object
  that calling code (`ProjectFlowService.compileSliceMetadata`, `ProjectOperatorService`) checks and silently
  skips on - no warning, no error, nothing in `docker compose logs backend`. The only reason this was
  diagnosable at all was that the *Python* ML service happens to log its own Gemini failures. Tracked as a
  follow-up, not fixed yet (pending user confirmation): add logging to `GoogleAiResourceService.callInteraction`
  and `DesignAssetService`/`VideoAssetService`'s unavailable-result branches so a depleted-billing (or any
  other) failure is visible in the backend's own logs instead of requiring cross-service log archaeology.
- **Suggestion:** Not a code fix - user needs to add prepay credits at https://ai.studio/projects. Once
  billing is restored, design/video generation should work without any code change (the logic is already
  correctly wired). The logging gap noted above is a separate, optional follow-up.
