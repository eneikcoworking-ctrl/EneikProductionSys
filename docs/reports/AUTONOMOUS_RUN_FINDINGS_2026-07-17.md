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
- **Resolution (user decision, went further than the initial suggestion):** user's explicit principle -
  "time is never the argument, only whether Jules is still responding" - so rather than patch the age-limit
  check with a PR lookup, `closeOverdueActiveSessions()` / `isAgeLimitedActiveStatus()` / `maxActiveSessionMinutes`
  were **removed entirely**. The only remaining closure path is `closeOverdueStuckSessions()`
  (`stuck_session_timeout`), which is behavior-based - it only fires once Jules itself reports the "stuck"
  status, never on elapsed time alone. **Status: DEPLOYED 2026-07-18T00:57Z**, commit `e79557c`.

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

---

### 2026-07-18T00:XXZ — Stitch integrated and deployed as a free alternative to nano-banana

- **Outcome:** Following the Gemini billing finding above, verified live (via direct MCP calls: `create_project`,
  `generate_screen_from_text` against `https://stitch.googleapis.com/mcp`) that Google's Stitch generates real
  UI screens (design system, theme, HTML, screenshot) with zero billing errors while nano-banana was fully
  blocked by the depleted Gemini prepay balance - Stitch is billed/rate-limited independently.
- **Implemented:** `StitchClient` (minimal JSON-RPC MCP client), wired into `DesignAssetService.generateAsset()`
  as the preferred path when `stitch_enabled` + a Stitch API key are configured; falls back to the existing
  nano-banana/Gemini path unchanged if Stitch isn't configured or fails. `DesignAssetServiceTest` covers all
  three paths (Stitch success, Stitch not configured, Stitch failure fallback). Commit `b4524fd`.
- **Status: DEPLOYED and verified 2026-07-18T00:57Z.** Fresh container, clean startup, `stitch_api_key` saved
  via `PUT /api/settings` (database-backed, never committed to git) and confirmed via `GET /api/settings`
  (`source":"database"`).
- **Not covered:** Video generation (Veo) has no Stitch equivalent - it remains blocked by the same depleted
  Gemini balance until the user tops up prepay credits or a free video-gen alternative is found. User
  explicitly decided not to pay for it - `veo_enabled` set to `false` in settings so the system stops
  attempting a resource that will never be free.

---

### 2026-07-18T00:00Z — Falsification cycle fail-safe misreported infra failures as policy violations

- **Observed:** User asked to enable the falsification cycle (`falsification_cycle_enabled`, was `false`) and
  raise its frequency from once/day to every 4 hours - explicitly called this "the core of the whole system".
  While enabling it, reviewed `FalsificationCycleService.executeCycleForProject()` and found
  `MLPredictionServiceClient.checkRefusalCriteria()` deliberately returns `compliant=false` with reason
  `VERIFICATION_SERVICE_UNAVAILABLE: ...` whenever the ML/Gemini pipeline itself is unreachable - a correct
  fail-safe for `AutoMergeService`'s merge gate (don't merge what you can't verify). But
  `FalsificationCycleService` treated that identically to a real detected violation, creating a
  `HIGH_PRIORITY_DEBT` wishlist item demanding a fix for a regression that never happened.
- **Why it matters:** With the Gemini prepay balance already confirmed depleted (see the design/video
  finding above) and the cycle about to run 6x more often, this would have flooded every active project with
  fake "fix this regression" work across all 12 roles on the very next scheduled run - a textbook "dumb"
  design smell: infrastructure unavailability was being misreported as a philosophical/policy violation.
- **Suggestion:** Fixed directly (small, narrowly scoped): the `VERIFICATION_SERVICE_UNAVAILABLE` sentinel is
  now recognized and skipped with a warning log instead of being treated as a violation. The methodological
  falsification check (`checkMethodologicalFalsification`) was already safe by contrast - it fails toward an
  empty result list (silently misses checks during an outage, but never fabricates a violation).
  **Status: DEPLOYED 2026-07-18T00:57Z**, commit `2d22dce`. Cron changed from `0 0 2 * * ?` (daily) to
  `0 0 */4 * * ?` (every 4 hours), also deployed in the same commit.

---

### 2026-07-18T00:15Z — Scope-tagged logs (SYSTEM vs PROJECT) + LogScopeBuffer feeding falsification context

- **Context:** User raised a design question (not a bug): the factory's own operational health (dispatch,
  circuit breakers, account maintenance) and one specific built project's activity were mixed together in
  logs and findings with no way to separate them - and any findings fed back to project roles must never leak
  factory-internal ("SYSTEM") noise, only that project's own activity.
- **Implemented:** `LogScope` (SLF4J MDC wrapper: `system()`/`project(id)`/`clear()`), applied at the entry
  points where per-project work actually happens - the main loop and `pollActiveJulesSessions` in
  `ContinuousOrchestrationService`, and the per-review merge loop in `AutoMergeService` (each resolves the
  owning project via task lookup). Every existing `log.*` call anywhere in the resulting call stack picks up
  the tag automatically via MDC - no changes needed to individual log statements.
  `logging.pattern.level` renders it as `[PROJECT:{id}]` or `[SYSTEM]` in console output.
  `LogScopeBuffer` + `ScopedBufferAppender` (custom Logback appender, `logback-spring.xml`): an in-memory,
  per-project ring buffer that only ever stores `PROJECT:{id}`-scoped events, never `SYSTEM`-scoped ones -
  system noise cannot leak into project-facing context by construction, not by convention.
  `FalsificationCycleService.getLatestProjectDiff()` now appends the last 60 buffered lines for that project
  alongside the git diff and test logs, so role compliance checks see recent operational activity, not just
  the code diff in isolation.
- **Status: DEPLOYED 2026-07-18T00:57Z**, commit `2d22dce`. Verified live: `docker compose logs backend`
  shows `[SYSTEM]` for startup/account-maintenance lines and `[PROJECT:52ae4a5a-...]` for
  `ContinuousOrchestrationService: Processing project test-twenty-fourth` and subsequent per-project lines.
- **Not yet done:** this session's own periodic monitoring findings (this file) are NOT wired into the
  falsification cycle - only recent *log* activity is. Feeding curated findings in as well was discussed but
  not implemented before the session paused.

---

### 2026-07-18T01:00Z — Flow-fix plan (Areas 2-5) implemented, tested, deployed, verified live

Full plan (root-caused via direct DB/GitHub queries: reactive-only wishlist generation starved by a low
merge rate, itself starved by circuit-breaker bugs already fixed above) implemented in order:

- **Area 2 (rescue abandoned PRs):** `JulesDispatchService.reconcileAbandonedPullRequests()` sweeps
  `loop_closed` sessions with no `pr_reviews` row, checks GitHub directly for a PR the session already
  opened before being force-closed. Verified live against the real repo: found and reconciled real
  abandoned PRs across `test-twentieth` and `test-twenty-first` within the first orchestration cycle after
  deploy (`#22`, `#24`, `#23`, `test-twenty-first#2`, `#1`).
- **Area 3 (project monitoring):** `GET /api/projects/{id}/pull-requests` (wraps the already-existing
  `GitHubPullRequestService.pullRequestSnapshot`) and `GET /api/projects/{id}/recent-activity` (wraps
  `LogScopeBuffer.recent`). Verified against `test-twenty-fourth`: matches `gh pr list` exactly (6 open -
  `#3,#5,#7,#8,#10,#11`, 5 merged, 0 closed-unmerged).
- **Area 4 (break wishlist starvation):** `IdleProjectAdviceService` (new, 15-min cron) detects a genuinely
  idle active project (no pending wishlist, nothing queued, nothing in flight) and asks Gemini for one
  wishlist item targeted at the project's least-used role - capped at one role/one item/project/cycle.
  `RoleAdviceLoopService.afterTaskComplete` upgraded from templating the task description back to actually
  asking Gemini for a follow-up recommendation (safe fallback to the old template if the ML service is
  unavailable). New `WishlistSource.idle_generation` enum value.
- **Area 5 (system monitoring):** `SystemProgressTracker` (in-memory heartbeat, recorded on real dispatch
  success and real merge success only - not every 60s tick) + `ContinuousOrchestrationService.checkForSystemStall()`
  checks it against idle capacity every cycle, persists `system_stall_status` (ok/stalled/idle_no_work).
  Exposed via a new `systemHealth` section in `SystemStatusService.getStatus()`.
- All 127 backend tests pass (a real, pre-existing test bug was caught and fixed along the way: the FK
  added in the housekeeping pass correctly rejected `AutonomousPipelineIntegrationTest` passing a
  never-persisted random session id to `onPrOpened` - fixed by creating a real session, not by weakening
  the FK).
- **Status: DEPLOYED and verified 2026-07-18T00:57Z-01:10Z**, commits `f237467` (Areas 2-5).

---

### 2026-07-18T01:10Z — `needs_human_review` was a silent write-only dead end (caught by user, not by me)

- **Observed:** while implementing Area 2, `reconcileAbandonedPullRequests()` was written to route a
  rescued-but-unreviewable PR to a `NeedsHumanReviewEntity` row as a "conservative first slice" - explicitly
  reasoned as safer than auto-merging a session Jules might no longer recognize as active. The user
  immediately flagged this on sight: "человек не должен участвовать" (a human must never need to
  participate) - this system's stated design is that a human only creates the project, writes the wishlist,
  and accepts the finished project; nothing else may wait on a person.
- **Investigation confirmed it was worse than "needs a human":** grepping the whole codebase found
  `NeedsHumanReviewRepository`/`NeedsHumanReviewEntity` had **zero readers anywhere** - not the frontend, not
  any scheduled job, nothing. A second, older, pre-existing writer already existed too
  (`AutoMergeService.handleMergeConflict()`, escalating after 3 failed auto-resolve attempts) with the exact
  same problem. Both were genuine dead ends: work simply stopped forever, silently, with no human and no
  system ever revisiting it - a worse failure mode than the "abandoned PR" bug Area 2 was built to fix in
  the first place.
- **Fixed (same session, before deploy):** both writers now resolve autonomously instead of parking.
  `reconcileAbandonedPullRequests()` runs the rescued PR through the existing automated reviewer
  (`mlPredictionServiceClient.reviewPr`) - approved PRs get a `pr_reviews` row that `AutoMergeService`'s own
  next cycle merges through its already-tested pipeline (zero new merge logic); rejected PRs spawn a fresh
  atomic recovery task. A `VERIFICATION_SERVICE_UNAVAILABLE` fail-safe response is retried next cycle, not
  treated as a rejection (same precedent as the falsification fail-safe fix above).
  `AutoMergeService.handleMergeConflict()`'s 3-strikes escalation now abandons the conflicting branch and
  spawns a fresh recovery task the same way, instead of writing to the dead table.
  `NeedsHumanReviewRepository` wiring removed from all three services that referenced it (the entity/table
  itself left in place, now fully dormant - a candidate for outright removal in a future pass, not urgent).
- **Status: DEPLOYED and verified 2026-07-18T01:25Z** (all 127 tests pass, clean restart, no errors in the
  first orchestration cycles after deploy), commit `22ff365`.
- **Lesson:** "conservative first slice" reasoning silently smuggled in a human-in-the-loop assumption that
  contradicts this project's core design. The philosophy check ("could a human be required here, even
  implicitly, even as a fallback?") needs to be applied to every new code path, not just the obviously
  autonomous ones.

---

### 2026-07-18T01:25Z — Design generated via Stitch + frontend redesign applied

- **Design:** second Stitch generation (first was rejected as "мрачный и вторичный" - dark and generic).
  New direction: light-industrial "mission control" for the factory floor, Cynefin domain colors used as
  real functional status coding (Clear/Complicated/Complex/Chaotic), sharp panel edges, IBM Plex Sans +
  JetBrains Mono, dial-gauge and event-ticker motifs instead of generic cards/progress-bars. Three screens
  generated (System Dashboard, Project View, Resources & Tokens) and reviewed via a published artifact
  before porting - all real Stitch HTML/CSS output, not hand-authored (per explicit instruction: "ты сам
  ничего не должен генерировать. только ресурс stitch!").
- **Frontend:** applied the new design tokens globally (`app.css` - colors, IBM Plex Sans/JetBrains Mono,
  sharp radius, hard offset shadows) so every existing view inherits the look without touching its data
  logic - a full blind rewrite of ~4,700 lines of working Svelte with no visual-rendering capability
  available this session was judged too risky given "флоу, ядро и истина" were stated as the top priority.
  Instead: three new reusable components (`DialGauge`, `CynefinBadge`, `ActivityTicker`) plus real,
  additive panels wired to the Area 2/3 backend work - a GitHub-PR-truth panel and recent-activity ticker on
  the Project View, a System Health panel on the System view, Cynefin badges on task cards. Product name
  corrected to "Eneik Management System" throughout (was still showing "Project Command Center" in one spot).
- **Deliberately not done:** AI-resource utilization dials were not faked - no real percentage/quota metric
  exists server-side yet, and inventing one would contradict the falsification-first principle this session
  keeps coming back to. Flagged as real follow-up work (token/cost metering), not faked for visual completeness.
- **Known remaining debt (not fixed this session):** ~17 hardcoded old-palette hex colors remain in status
  badges across `CommandDashboardV2`/`MetricsView`/`AdminDashboard` (light-blue "accepted"/"waiting" badge
  backgrounds) - cosmetically inconsistent with the new palette but not broken. Left alone rather than
  edited blind, since this session has no way to visually verify the result.
- **Status: DEPLOYED and verified 2026-07-18T01:26Z** - `svelte-check` clean (0 errors), both containers
  rebuilt and restarted cleanly, frontend title confirmed via curl, `systemHealth` endpoint confirmed live.
  Commit `3c31a3c`.
