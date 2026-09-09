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


## 2026-09-10 Classification Pass V2

This pass classifies the mentioned-only and missing-name candidates from v1. It still does not claim factory completion; it makes the denominator more honest.

### Missing-Name Candidates

| Name | Classification | Reason |
| --- | --- | --- |
| `OrchestrationCooldownException` | mechanism part | Carries retry-after semantics for orchestration rate limiting; belongs inside the orchestration/control-surface record, not as a standalone mechanism. |
| `GateResult` | mechanism part | Result carrier for `GateCheck`/`GateOrchestrator`; must be described inside the gate family because pass/fail/reasons define closure, but it does not act alone. |
| `JulesDispatchResult` | mechanism part | Dispatch outcome carrier for `JulesDispatchService`; important for accounting and idempotency, but not a separate mechanism. |
| `CollaboratorProvisioningResult` | mechanism part | GitHub collaborator invitation outcome inside the project-factory provisioning family. |
| `LinearProvisioningResult` | mechanism part | Linear project outcome inside the project-factory provisioning family. |
| `ProjectFactoryResult` | mechanism part | Aggregate provisioning outcome returned by `ProjectFactoryService`; belongs in the project-factory family. |
| `WorkspaceArtifacts` | mechanism part | Bootstrap file bundle emitted by `ProjectWorkspaceFactoryService`; part of project factory, not independent behavior. |
| `WorkspaceProvisioningResult` | mechanism part | Workspace provisioning outcome inside the project-factory family. |

No missing-name item from v1 is promoted to an independent whole-mechanism record by this pass. The project-factory and gate/orchestration records must mention these parts explicitly when strict records are filled.

### Mentioned-Only Services

| Name | Classification | Next documentation action |
| --- | --- | --- |
| `ChessService` | excluded-with-reason | Empty source file; record exclusion as no behavior unless callers or code appear later. |
| `GeminiProjectObserverService` | dead/inert but operationally relevant | Needs a decommissioned-mechanism record tied to `V111__permanently_disable_gemini_project_observer.sql`, because preserving an inert Spring bean is behavior. |
| `GateCheck` | mechanism part | Fold into the gate family with `GateOrchestrator`, `GateResult`, `GateStage`, and individual gates. |
| `GateStage` | mechanism part | Fold into the gate family; it defines task-spec vs implementation-result level. |
| `GitHubProvisioningResult` | mechanism part | Fold into the project-factory provisioning family with GitHub/Linear/workspace/collaborator result carriers. |

### Mentioned-Only Controllers

All 23 mentioned-only controllers remain mechanism-surface work, not exclusions. They should be filled as connected surface families instead of isolated endpoint blurbs:

| Surface family | Controllers | Why it is mechanism-relevant |
| --- | --- | --- |
| Public/basic ingress | `GreetingController`, `HomeController` | Health and greeting write/read surfaces; `GreetingController` also has a PII masking guard. |
| Operator dashboards and projections | `DashboardController`, `CommandDashboardController`, `ClientDeliveryController`, `OperationalTruthController`, `VerdictController`, `SystemAuditController`, `QualityGateController`, `LinearSyncController`, `JulesMonitorController`, `RoleRulesController` | Read-only does not mean irrelevant: these endpoints define what the operator/factory can trust, compare or act on. |
| Operational command surfaces | `SystemStatusController`, `InternalRepairController`, `GithubAccessController`, `MarketResearchController`, `KaizenController`, `TocSentinelController`, `FlowSpineController`, `OperationalFlowCoreController` | These expose reindex/SQL/repair/recheck/research/scan/observe/event/resource mutations or live operational reads. |
| Settings/configuration surfaces | `SettingsController`, `InternalSettingsController`, `JulesConfigController` | These mutate or resolve runtime configuration and dispatch capacity shape. |
| Runtime self-reporting | `SystemDriftController`, `InternalJulesActivitiesProbeController` | These make filesystem/session/drift/orphaned-PR claims visible and therefore need source/freshness/refutation. |

### Kaizen And TOC Mentioned-Only Items

`KaizenController` is a controller surface record needed under the Kaizen family. `DefectJournalRepository` and `KaizenProposalRepository` are data owners, not standalone mechanisms; strict Kaizen records must name their lifecycle predicates and ownership boundaries.

`TocSentinelController` is a controller surface record needed. `TocExecutionGraph` is not a DTO: it is stateful execution-graph behavior and should be filled together with `TocSentinelService`, `TocOptimizer` and the sentinel controller as one TOC runtime graph family.

### Strict-Record Quality Check Started

A rough scan finds 166 rough record lines in `docs/FACTORY_MECHANISMS.md`, while only 33 occurrences of `комментарий для Антигравити` exist in the same file. This is not a direct remaining-mechanism count because many records are grouped, but it proves strict completion is not true yet. The next tact must start upgrading/filling by connected family, beginning with either controller surfaces or project-factory/gate mechanism parts.

комментарий для Антигравити: механизм-документация пока не идеальна. The denominator now separates missing-name result carriers from real surfaces, but strict records are still missing. Next tact should fill a connected whole family, not another narrow symptom: recommended first family is controller surfaces that mutate runtime state (`SystemStatusController`, `InternalRepairController`, `SettingsController`, `JulesConfigController`, `MarketResearchController`, `KaizenController`, `TocSentinelController`). Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` for source class vs mechanism surface identity; common background `ACP-061 Hoare Triple Review`.


## 2026-09-10 Strict Family Record: Settings And Configuration Surfaces

Status: strict family record filled in `docs/FACTORY_MECHANISMS.md` by factory-wide tact 3.

Moved from mentioned-only surface work:

- `SettingsController`: strict-recorded as the public/operator settings list/update surface.
- `InternalSettingsController`: strict-recorded as raw internal setting resolver, with trust-boundary risk called out.
- `JulesConfigController`: strict-recorded as legacy Jules config writer/list/delete surface, blocked by canonical-world question.

Connected owner/parts named in the same family: `SystemSettingsService`, `system_settings`, `JulesConfigRepository`, `JulesConfigEntity`, `SettingDto`, `SettingUpdateRequest`, `JulesConfigDto`, migrations `V16`, `V17`, `V19`.

Not complete as implementation: missing actor/source audit, independent guard for debug/raw paths, explicit trusted boundary for raw secret resolution, and final decision on whether `/api/jules-configs` is retired, bridged to `accounts`, or kept inert.

комментарий для Антигравити: settings/configuration documentation is now a whole-family record, but implementation remains non-ideal. Do not patch the endpoints separately; first answer the legacy Jules config fate and then enforce one configuration world plus audit/trusted-boundary rules. Философия: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`; local governing patterns `DZHON_SERL_05_INSTITUTIONAL_FACT_REGISTER`, `PRINCIPLED_INTEGRITY`, `WORLD_VERSION_MAP`; common background `ACP-061 Hoare Triple Review`.


## 2026-09-10 Strict Family Record: Flow Observability And TOC Telemetry Surfaces

Status: strict family record filled in `docs/FACTORY_MECHANISMS.md` by factory-wide tact 4.

Moved from mentioned-only work:

- `FlowSpineController`: strict-recorded as project flow read/observe/events surface.
- `OperationalFlowCoreController`: strict-recorded as enforceable flow-core read/observe/events surface.
- `TocSentinelController`: strict-recorded as TOC status/graph/event/resource HTTP telemetry surface.
- `TocExecutionGraph`: strict-recorded as stateful in-memory TOC execution graph behavior.

Connected owners named in the same family: `FlowSpineService`, `OperationalFlowCoreService`, `TocSentinelService`, `TocOptimizer`, `TocAnomalyDetector`, `FlowSpineEventRepository` and runtime TOC node/token/edge maps.

Not complete as implementation: mutation endpoints still need explicit auth/call audit evidence; TOC unknown-token responses and graph restart non-durability need stronger operator-visible semantics; observe idempotency and mode separation need fixture proof before code changes.

комментарий для Антигравити: flow observability/TOC telemetry documentation is now a whole-family record, but implementation remains non-ideal. Do not patch endpoints separately; preserve read vs observe vs control telemetry, durable vs in-memory truth, event bounds, idempotency and throttle/not-found semantics. Philosophy: Goldman reliability chain plus level-of-abstraction lock and Hoare triple review.
