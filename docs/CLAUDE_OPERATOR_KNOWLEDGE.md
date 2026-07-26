# Claude Operator Knowledge (fed into GeminiContextService's RAG index)

This file is a distilled snapshot of what the human-facing orchestrator (Claude, operating this system
alongside eneikdru) has learned about EneikProductionSys across many live sessions - architecture decisions,
confirmed bugs, standing design principles, and terminology. It is indexed by `GeminiContextService`
(source_type `claude_operator_notes`) exactly like OBSERVER_LOG.md and the BARCAN role charters, so the
embedded ops auditor (Gemini) retrieves relevant excerpts by similarity instead of starting from zero on
every call. It is a manually-refreshed snapshot, not a live feed - ask Claude to update it when it drifts.

## Standing architectural principle: testimony vs. evidence

Never gate a decision on another agent's SELF-REPORT about itself (a Jules session's `status` field,
`lastProgressAt`) - only act on something the orchestrator did itself, or an independently-verifiable
artifact (a real commit, branch, or PR on GitHub). `JulesDispatchService.honorDavidsonProgressEvidence`
(named for Davidson's principle of charity) is the canonical checkpoint: it calls
`persistentWorkerHasReadyAnswer`/`hasNewProgressOnGitHub`, which check GitHub directly (open PR, then
branch-fallback with `findBranchBySession`) rather than trusting session status. A periodic sweep,
`reconcileTaskStatusAgainstGitHubTruth`, applies the same principle unconditionally on a schedule (not just
reactively to sessions that already look stuck), gated by `github_truth_reconciliation_enabled`.

## Known, confirmed architecture gaps (not yet code-fixed as of 2026-07-25)

- **Feature-thread closeout "last task merged directly to main" gap**: `AutoMergeService.progressCloseout`
  assumes a feature's accumulated work always needs a closeout PR from an intermediate branch. When a
  feature's LAST task's PR already merged straight into `main`, the closeout branch no longer exists, and
  `progressCloseout` retries `createPullRequest` forever, failing HTTP 422 "head invalid" every cycle. Fixed
  manually once via SQL (`feature_threads.merged_to_main_at`) for feature `ddd91e1e`; will recur for any
  future feature in the same shape until code-fixed.
- **Merge-conflict escalation dead end (fixed 2026-07-25)**: `AutoMergeService`'s 3-attempt escalation path
  only ever set `resolutionStatus="escalated"` with no real resurrection for conflicts touching real product
  code (`resurrectTriviallyEscalatedConflicts` only covers `.eneik/`/`.gitignore`-only conflicts). Fixed via
  `resurrectEscalatedConflictsWithRealCode`, which dispatches exactly one fresh ad-hoc session per escalated
  conflict and marks it `escalated_fresh_dispatch` so it's never re-selected.

## BARCAN role design philosophy

The 13 BARCAN-TAG roles are distinct ANALYTIC-PHILOSOPHY worldviews, not work-domain labels (not "the
backend role" or "the frontend role") - each role's charter must carry a real philosopher's actual
conceptual apparatus (e.g. Barcan Marcus's actualism, Williamson's knowledge-first epistemology, Davidson's
principle of charity) and that charter must actually reach the Jules session doing the work, not just exist
as documentation. Philosophical-falsification critiques are evaluated by clustering (union-find over Jaccard
similarity with a dynamic Otsu threshold - see `WishlistContentSimilarityMatcher.clusterBySimilarity`), never
by hard Kano/confidence filters that would discard genuine minority voices; every voice clusters into a
group, and each cluster's Kano class is a majority vote with an assertiveness tiebreak
(Attractive > Performance > Must-Be > Indifferent).

## Terminology (fixed, do not re-derive differently)

- **вишлист (wishlist)**: a single stated need/idea/critique before decomposition.
- **задача (task)**: one Jules session/branch/PR - the atomic unit of dispatched work.
- **эпик (epic)**: `FeatureEntity`/`featureId` in code - a themed group of tasks sharing one JTBD, may span
  many tasks and may itself require a closeout merge into `main`.
- **фича** in everyday operator speech = задача (one session/branch/PR), NOT эпик - do not conflate.

## Operational lessons (apply without being re-told)

- A piped/backgrounded command's reported exit code is unreliable; grep the actual output for
  `[ERROR]`/`BUILD FAILURE` instead of trusting the notification.
- H2's raw-JDBC `JdbcTemplate` query results return UPPERCASE column-name map keys.
- Never edit `src/` while a `docker compose build` is running in the background - the build context is
  snapshotted early (`COPY src ./src`), not at completion; a concurrent edit can bake a stale/inconsistent
  hybrid into the image with no build error.
- The frontend container has no bind mount - a source edit is invisible at `localhost:3000` until the
  frontend image is rebuilt and redeployed, exactly like the backend.
- Jules session `status`/`lastProgressAt` is never proof of anything by itself - see the testimony-vs-
  evidence principle above; this is the single most load-bearing standing rule in this codebase.
