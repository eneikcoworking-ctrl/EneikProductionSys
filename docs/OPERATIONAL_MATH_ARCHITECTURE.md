# Operational Math Architecture

## Purpose

This document defines the first safe layer of strict operational mathematics above the agentic Eneik
Production System. The goal is not to add another controller. The goal is to expose one coherent
operational truth about value delivery, evidence, trust, defects, and learning while preserving the
current write-side ownership of the system.

The first implementation mode is read-only shadow mode.

## Non-Negotiable Boundary

The operational math layer must not mutate production flow state in its first phase.

It must not:

- change `TaskStatus`;
- change `WishlistStatus`;
- change `PrReviewEntity`;
- dispatch Jules;
- call GitHub write APIs;
- trigger AutoMerge;
- create a scheduled write-side process;
- use an LLM claim as final evidence of delivery.

It may:

- read existing repositories and services;
- normalize facts into a stable DTO;
- explain value delivery in frontend language;
- recommend next action as non-binding guidance;
- expose invariant violations as warnings.

## Source Of Truth Matrix

| Fact | Write Owner | Read Owner For Operational Math | Notes |
| --- | --- | --- | --- |
| Task lifecycle | `ProjectFlowService`, `ClaimService`, `JulesDispatchService` | `OperationalTruthService` | Derived meanings must not write back into `TaskStatus`. |
| Wishlist lifecycle | `ProjectFlowService`, `TechnicalLeadCompiler`, `OpsAuditorService` | `OperationalTruthService` | Dismissal/remediation remains outside the read model. |
| PR review and merge truth | `AutoMergeService`, `GitHubPullRequestService` | `OperationalTruthService` | GitHub truth remains stronger than local review claims. |
| Delivery readiness | `ClientDeliverableReadinessService` | `OperationalTruthService` | This remains the canonical feature/deliverable readiness calculator. |
| Quality gate evidence | `GateOrchestrator` | `OperationalTruthService` | Operational math aggregates gate results; it does not replace gates. |
| Drift/runtime truth | `SystemDriftController`, `check_system_drift.ps1` | `OperationalTruthService` / frontend | Runtime drift is trust evidence, not delivery evidence. |
| Bottlenecks | `BottleneckDetectionService`, `TocSentinelService` | `OperationalTruthService` | Bottlenecks are translated into delivery impact. |
| Defect memory | `DefectJournalService`, `KaizenService` | `OperationalTruthService` | Defects become learning only after verification and invariant capture. |
| RAG context | `GeminiContextService` | Agents and auditors | RAG is advisory context, not a source of delivery truth. |

## Invariant Catalogue

The layer starts with explicit invariants. They are observed in shadow mode first.

| Invariant | Logical Form | Initial Action |
| --- | --- | --- |
| Delivered value requires evidence | `delivered(x) -> exists evidence(x)` | Warn only. |
| Merged PR is stronger than task status | `task_done(x) and no_merged_pr(x) -> not_delivered(x)` for code-producing work | Explain as blocked value. |
| Closed unmerged PR is terminal non-delivery | `closed_unmerged(pr) -> not_delivery_evidence(pr)` | Explain as scrap/COPQ evidence. |
| Runtime drift blocks trust, not necessarily work | `drift_bad -> trust_degraded` | Show trust warning. |
| Duplicate generated content blocks throughput trust | `duplicate_content >= threshold -> stop_the_line_quality_defect` | Explain as quality stop. |
| Agent claims are never final evidence | `agent_claim(x) and no_artifact(x) -> unverified(x)` | Keep as weak evidence. |
| Improvement is only real after invariant capture | `defect_fixed(x) and no_invariant(x) -> not_learned(x)` | Show as unresolved learning. |

## Evidence Algebra

Evidence is ordered by strength. Stronger evidence may satisfy delivery or trust conditions; weaker
evidence may only support interpretation.

| Strength | Evidence | Meaning |
| --- | --- | --- |
| 5 | Merged PR into main / feature thread closed into main | Strong delivery evidence. |
| 4 | Passing CI and quality gate report | Strong implementation confidence, not delivery alone. |
| 3 | Screenshot, API check, runtime drift OK | Contextual verification evidence. |
| 2 | Jules session status, PR opened, task in review | Activity evidence only. |
| 1 | Agent prose, generated title, planned item text | Intent or claim, not delivery. |
| 0 | Duplicate/fallback/generated generic content | Negative evidence. |

Operational truth must not treat activity evidence as delivered value.

## Promotion Policy

Every new rule moves through controlled modes:

1. `observe_only`: compute and display only.
2. `warn_only`: display warnings and recommended action.
3. `soft_gate`: block only optional/new work, with explicit bypass.
4. `hard_gate`: block unsafe flow automatically.
5. `auto_remediate`: execute a narrow, precondition-checked repair.

Default mode for this layer is `observe_only`.

Promotion criteria:

- the rule matches real incidents across multiple cycles;
- false positives are understood and bounded;
- a regression test exists;
- source-of-truth ownership is documented;
- rollback is one setting or one deploy revert.

## Frontend Translation Contract

The frontend should not expose backend implementation trivia as the primary user experience. It should
translate system facts into value-facing language:

| Backend Fact | Frontend Meaning |
| --- | --- |
| `completeFeatures / totalFeatures` | What has been delivered as product capability. |
| `mergedPlannedTasks / totalPlannedTasks` | How much planned implementation has reached merge evidence. |
| queued/claimed/in_progress/review counts | What is moving now. |
| blocked tasks, duplicate content, stale sessions, failing PRs | What blocks value. |
| drift OK, CI/gates OK, GitHub reconciliation OK | How much the operator can trust the system. |
| defect journal and Kaizen proposals | What the system has learned or still needs to turn into invariant. |

The first UI should answer five questions:

- What value has been delivered?
- What is currently moving?
- What blocks delivery?
- Can I trust the system state?
- What has the system learned?

## Analytic Philosophy Basis

This design is intentionally grounded in shared analytic-philosophy patterns already used in the
philosopher RAG corpus:

- truth conditions: every operational claim has explicit conditions of truth;
- logical form: messy runtime events are normalized into `work -> evidence -> value -> learning`;
- type distinctions: activity, merge, delivery, trust, and user value are not interchangeable;
- verification/falsification: progress is counted through checkable evidence;
- deontic logic: each service has permitted and forbidden actions;
- limits of substitutivity: `task done` cannot be substituted for `value delivered`;
- public criteria: the frontend state must be understandable without backend knowledge;
- common knowledge: agents and operator should see the same operational truth;
- modal discipline: invariants are separated from heuristics.

## Phase 1 Deliverable

Phase 1 is complete when:

- `OperationalTruthService` exposes a read-only DTO;
- the DTO includes delivery, active flow, blocked value, evidence, defects, learning, source-of-truth,
  invariants, promotion policy, and frontend translations;
- the current dashboard renders a concise value summary above technical metrics;
- tests prove the service does not need write-side collaborators;
- no existing orchestration behavior changes.
