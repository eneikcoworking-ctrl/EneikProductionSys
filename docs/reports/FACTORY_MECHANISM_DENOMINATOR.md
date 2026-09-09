# Factory-Wide Mechanism Denominator

Status: v1 audit map, created by Codex on 2026-09-09 after the operator corrected the scope. This file is not a completion claim. It exists to stop confusing one technical symptom with the whole factory.

## Human Definition

A factory mechanism is a behavior-changing unit of the system: service, controller command surface, scheduled job, sidecar, migration with behavioral meaning, state machine/gate, dispatch/accounting path, or persistent evidence/retention process.

A repository call pattern, a table read, a DTO, or a helper class is not automatically a mechanism. It becomes mechanism-relevant only when it changes what the factory can decide, create, block, dispatch, delete, retain, expose, or trust.

## Record-Presence Test

Rough record presence is counted by source name appearing as `**`Name`**` in `docs/FACTORY_MECHANISMS.md` with `*Связи:*` close below it. This proves only that a mechanism has a named record shell. It does not prove ideal form, Antigravity advice, or implementation readiness.

Correct completion now requires a stricter record: ideal form, boundary, inputs/outputs, callers/callees/interactions, state/data owners, invariants, strong form, weak form, refutation observation, closure criterion, evidence commands/source lines, current status, and `комментарий для Антигравити` with applicable philosophy/pattern or exactly `считаю механизм идеальным`.

## 2026-09-09 Snapshot

| Layer | Source files | Rough records | Mentioned only | Missing name |
| --- | ---: | ---: | ---: | ---: |
| `src/main/java/com/eneik/production/services` | 139 | 126 | 5 | 8 |
| `src/main/java/com/eneik/production/controllers` | 35 | 12 | 23 | 0 |
| `src/main/java/com/eneik/production/kaizen` | 8 | 5 | 3 | 0 |
| `src/main/java/com/eneik/production/toc` | 10 | 8 | 2 | 0 |
| `src/main/java/com/eneik/production/config` | 3 | 3 | 0 | 0 |

Scheduled top-level Java files: 28/28 have rough records. This does not close them; it only means the scheduled layer is not missing by name.

Migrations: 137 SQL files exist. They are not yet classified by behavioral family in this v1 denominator. They must be handled as migration mechanisms/families, not ignored.

Sidecars/scripts: `frontend_proxy.py` is visible at repo root in this snapshot; earlier docs mention the sidecar layer as closed for `launcher.py`, `server.js`, and `MLPredictionService`, but v1 must re-check actual filesystem locations before claiming all sidecars closed.

## Mentioned-Only Top-Level Mechanism Candidates

Services: `ChessService`, `GeminiProjectObserverService`, `GateCheck`, `GateStage`, `GitHubProvisioningResult`.

Controllers: `GreetingController`, `HomeController`, `InternalJulesActivitiesProbeController`, `InternalRepairController`, `LinearSyncController`, `QualityGateController`, `SystemAuditController`, `ClientDeliveryController`, `CommandDashboardController`, `DashboardController`, `FlowSpineController`, `OperationalFlowCoreController`, `OperationalTruthController`, `SystemDriftController`, `SystemStatusController`, `GithubAccessController`, `MarketResearchController`, `JulesMonitorController`, `RoleRulesController`, `InternalSettingsController`, `JulesConfigController`, `SettingsController`, `VerdictController`.

Kaizen: `KaizenController`, `DefectJournalRepository`, `KaizenProposalRepository`.

TOC: `TocSentinelController`, `TocExecutionGraph`.

## Missing-Name Top-Level Candidates

Services/result carriers: `OrchestrationCooldownException`, `GateResult`, `JulesDispatchResult`, `CollaboratorProvisioningResult`, `LinearProvisioningResult`, `ProjectFactoryResult`, `WorkspaceArtifacts`, `WorkspaceProvisioningResult`.

These names are candidates, not automatic mechanisms. Next pass must classify each as mechanism, part of a mechanism, or excluded-with-reason.

## Next Pass

The next denominator tact must not add code. It should classify mentioned-only and missing-name candidates into: `whole mechanism record needed`, `belongs inside existing mechanism record`, `data/result type excluded with reason`, or `dead/inert but still operationally relevant`. It must also begin checking which rough records lack the stricter Antigravity ideal-form comment.

комментарий для Антигравити: механизм-документация пока не идеальна. Не принимай rough record count as completion. First make the denominator honest: mechanism vs part vs data/result type vs command surface vs migration family, then fill only whole mechanisms or connected families with ideal form and refutation. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` for mechanism identity vs source-file/class identity; common background `ACP-061 Hoare Triple Review`.
