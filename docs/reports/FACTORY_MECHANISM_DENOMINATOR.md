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

## 2026-09-10 Strict Family Record: Task Quality Gates And Verdict Surfaces

Status: strict family record filled in `docs/FACTORY_MECHANISMS.md` by factory-wide Codex tact after the operator demanded a shared vocabulary.

Moved from mentioned-only / mechanism-part work:

- `GateCheck`, `GateStage`, `GateResult`: folded into the task-quality-gate family as applicability/stage/result parts, not standalone mechanisms.
- `QualityGateController`: strict-recorded as an observation surface over quality-gate aggregate truth; implementation still non-ideal because it owns a duplicate all-task DPMO computation instead of the shared Six Sigma/report-corpus owner.
- `VerdictController`: strict-recorded as the read-only HTTP surface for the verdict lattice; it observes readiness judgement and does not actuate dispatch/acceptance.
- `BaseQualityGate`: corrected in the strict record as an outer wrapper around live nested `@Component` checks; future work must not call the whole file dead without preserving that distinction.
- `VerdictGate`: included as the separate project-readiness claim gate so task-spec, implementation-result and project-readiness subjects stay distinct.

Updated rough-presence scan after the record:

| Layer | Source files | Rough records | Mentioned only | Missing name |
| --- | ---: | ---: | ---: | ---: |
| `src/main/java/com/eneik/production/services` | 139 | 129 | 10 | 0 |
| `src/main/java/com/eneik/production/controllers` | 35 | 16 | 19 | 0 |
| `src/main/java/com/eneik/production/kaizen` | 8 | 5 | 3 | 0 |
| `src/main/java/com/eneik/production/toc` | 10 | 10 | 0 | 0 |
| `src/main/java/com/eneik/production/config` | 3 | 3 | 0 | 0 |

Antigravity comments in `docs/FACTORY_MECHANISMS.md`: 38. This still proves strict completion is false for the whole factory; it only proves this family now has a strict record.

Remaining not-recorded rough-presence names after this pass: services/result carriers `ChessService`, `GeminiProjectObserverService`, `OrchestrationCooldownException`, `JulesDispatchResult`, `CollaboratorProvisioningResult`, `GitHubProvisioningResult`, `LinearProvisioningResult`, `ProjectFactoryResult`, `WorkspaceArtifacts`, `WorkspaceProvisioningResult`; controllers `GreetingController`, `HomeController`, `InternalJulesActivitiesProbeController`, `InternalRepairController`, `LinearSyncController`, `SystemAuditController`, `ClientDeliveryController`, `CommandDashboardController`, `DashboardController`, `OperationalTruthController`, `SystemDriftController`, `SystemStatusController`, `GithubAccessController`, `MarketResearchController`, `JulesMonitorController`, `RoleRulesController`, `InternalSettingsController`, `JulesConfigController`, `SettingsController`; kaizen `KaizenController`, `DefectJournalRepository`, `KaizenProposalRepository`.

комментарий для Антигравити: gate-family documentation is now a whole-family record, but implementation is not ideal. Do not patch one gate or one endpoint separately; keep the three subjects distinct: task specification, implementation-result verification, project-readiness claim. Apply `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`, and `ACP-061 Hoare Triple Review`. First remeasure reachability and denominator, then unify `QualityGateController` with the Six Sigma report-corpus owner, then decide the epistemic absent-evidence rule.

## 2026-09-12 Strict Family Record: Project Factory Provisioning Result Chain

Status: strict family record filled in `docs/FACTORY_MECHANISMS.md` by Codex as a factory-wide documentation tact, with no code change.

Moved from mentioned-only / mechanism-part work:

- `CollaboratorProvisioningResult`: folded into the GitHub collaborator-access outcome contract.
- `GitHubProvisioningResult`: strict-recorded as GitHub repository identity/status/warnings/collaborator evidence, not a standalone mechanism.
- `LinearProvisioningResult`: strict-recorded as Linear project id/url/status evidence.
- `WorkspaceArtifacts`: strict-recorded as the bootstrap file bundle emitted by the workspace factory.
- `WorkspaceProvisioningResult`: strict-recorded as local workspace path/artifact/status evidence.
- `ProjectFactoryResult`: strict-recorded as the aggregate returned by `ProjectFactoryService` and persisted by `ProjectFlowService`.

Connected owners named in the same family: `ProjectWorkspaceFactoryService`, `GitHubProjectFactoryClient`, `LinearProjectFactoryClient`, `ProjectFactoryService`, `ProjectFlowService`, `ProjectEntity`, `ProjectDto`, `ProjectHotspotFileRepository`, GitHub API, Linear API and migrations `V5`, `V6`, `V8`.

Updated rough-presence scan after the record:

| Layer | Source files | Rough records | Mentioned only | Missing name |
| --- | ---: | ---: | ---: | ---: |
| `src/main/java/com/eneik/production/services` | 139 | 135 | 4 | 0 |
| `src/main/java/com/eneik/production/controllers` | 35 | 16 | 19 | 0 |
| `src/main/java/com/eneik/production/kaizen` | 8 | 5 | 3 | 0 |
| `src/main/java/com/eneik/production/toc` | 10 | 10 | 0 | 0 |
| `src/main/java/com/eneik/production/config` | 3 | 3 | 0 | 0 |

Exact lowercase `комментарий для Антигравити` grep in `docs/FACTORY_MECHANISMS.md`: 37. This still proves strict completion is false for the whole factory; it only proves this family now has a strict record.

Remaining not-recorded rough-presence names after this pass: services/result carriers `ChessService`, `GeminiProjectObserverService`, `OrchestrationCooldownException`, `JulesDispatchResult`; controllers `GreetingController`, `HomeController`, `InternalJulesActivitiesProbeController`, `InternalRepairController`, `LinearSyncController`, `SystemAuditController`, `ClientDeliveryController`, `CommandDashboardController`, `DashboardController`, `OperationalTruthController`, `SystemDriftController`, `SystemStatusController`, `GithubAccessController`, `MarketResearchController`, `JulesMonitorController`, `RoleRulesController`, `InternalSettingsController`, `JulesConfigController`, `SettingsController`; kaizen `KaizenController`, `DefectJournalRepository`, `KaizenProposalRepository`.

Not complete as implementation: status/result outcomes remain mostly free text; workspace bootstrap text can still render raw `Repository: null`; verified/unverified existing-repository `422` branches need fixture proof; stdout debug token-prefix output should be replaced by a proper log/audit path.

комментарий для Антигравити: project-factory provisioning-result documentation is now a whole-family record, but implementation remains non-ideal. Do not patch one record type or one client branch separately; preserve the whole evidence chain from local workspace to GitHub proof to Linear proof to persisted project fields. Philosophy: `BARCAN-TAG-07_SECOND-ORDER-KNOWLEDGE`, Элвин Голдман, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, family `RELIABILITY_CHAIN`, defect `D010 Data lineage loss`; additionally `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`; common background `ACP-061 Hoare Triple Review`.

## 2026-09-12 Codex Recovery: Claude Session Stopped, Structure Contract Repaired

Status: Claude server session stopped and recoverable by Codex after the operator ordered Codex to take over. Recovery package: `/home/remotecli/claude-stop-20260912T084112Z`.

What Claude completed before stop, all in `docs/FACTORY_MECHANISMS.md` and all documentation-only:

- `dbf00ef`: strict family record for market corpus, statutory compliance gate and field research.
- `a725273`: strict family record for brownfield admission / repository-stack analysis, with old section XXXI marked stale.
- `e2be016`: strict family record for compilation family in section II.
- `8bc98db`: strict family record for dispatch in section III, preserving the earlier Codex Jules-operation cluster.

Defect confirmed and corrected: `docs/FACTORY_MECHANISM_DENOMINATOR.md` had not been updated after Claude's records. It still said the exact Antigravity-comment grep was 37 and still listed already-strict-recorded items such as `MarketResearchController` as remaining. Current exact lowercase `комментарий для Антигравити` grep in `docs/FACTORY_MECHANISMS.md`: 42.

Current strict-family records visible at the top of `docs/FACTORY_MECHANISMS.md`:

| Section | Strict family record | Status |
| --- | --- | --- |
| I | Market corpus, statutory compliance gate and field research | Not ideal; `MarketResearchService` lacks its own guard, corpus-refresh ownership and false-positive rate remain unmeasured. |
| I | Brownfield admission / repository-stack analysis | Not ideal in one remainder; tri-state inspection is strong, but `ProjectFlowService` still discards the audit result. |
| II | Compilation: requirement becomes tasks | Not ideal by coverage; core mechanisms are strong, compiler paths and wishlist deletion ownership need guards. |
| III | Dispatch: task goes to work | Not ideal; target-context derivation is strong, but shared table ownership and `LeaseWatchdogService` guard are open. |
| Later appended record | Project-factory provisioning result chain | Not ideal; phantom repository URL is strong, status/result typing and workspace unknown semantics remain weak. |

Remaining not-recorded rough-presence list must be regenerated, not trusted from prior sections. Known removals from the stale list: `MarketResearchController` is now inside the market family; dispatch/Jules surfaces are now partly covered by section III but still need a fresh denominator pass before removal claims.

Top-of-file structure repair applied in `docs/FACTORY_MECHANISMS.md`: the old "five fields" instruction was false after strict records began. It now requires visible fields for ideal form, boundary, inputs, outputs, state owners, invariants, strong/weak form, refutation, closure, evidence, current status and `комментарий для Антигравити`.

комментарий для Антигравити: documentation mechanism is not ideal yet, but the critical visibility defect is now named. Do not write implementation code from old narrative paragraphs. Only use a mechanism record after the visible structured fields and Antigravity comment are present. Philosophy: `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` for record status vs implementation status, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` for evidence commands, and `ACP-061 Hoare Triple Review` before code.

## 2026-09-12 Codex Cleanup: Main File Is Not A Debate Log

Status: cleanup pass started after operator correction that `docs/FACTORY_MECHANISMS.md` must contain mechanisms and coding guidance, not agent disputes.

Applied to the top strict-recorded sections: visible author/tact labels (`Codex`, `Claude`), "previous record was wrong" style commentary, and self-referential correction language were replaced with neutral current facts: measured counts, current strength, open non-ideal points and what to code next. Historical handoff/provenance remains in this report layer, not in the mechanism file.

Rule for future passes: do not add authorship, inter-agent disagreement, blame language, or narrative self-correction to `docs/FACTORY_MECHANISMS.md`. If a stale statement matters, write the current measured fact in the mechanism record and put the audit history here or in another `docs/reports/*` file. The visible mechanism record must include at minimum: philosophical pattern, ideal form, current state, gap to ideal, what to code for the ideal, what not to touch, refutation/check, closure criterion and evidence.

комментарий для Антигравити: documentation mechanism is not ideal until the whole file is cleaned, but the rule is now explicit. Main mechanism records must be usable by an implementation agent without reading agent drama. Apply `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`: mechanism state, documentation audit history and implementation advice are different levels and must not be mixed.

## 2026-09-12 Codex Cleanup: Fast Author-Layer Scrub

Status: documentation-only cleanup after the operator demanded faster work and a main file without agent dispute/provenance noise.

Applied to `docs/FACTORY_MECHANISMS.md`: visible session-author headers were normalized into mechanism-record language. `Свидетельства такта` became `Свидетельства записи`; `Codex strict family record` became `strict family record`; `Кандидат на будущую реализацию Codex` became `Что кодить следующим после явного разрешения на код`; visible `Antigravity (L2)` execution headers became `Реализационный факт`; the stale `Клод` correction label was removed from the main mechanism text. Several first-person/self-error phrases around dashboard and denominator history were also converted into neutral current-state wording.

This is not a claim that the whole file is ideal. It removes one harmful layer: implementation agents no longer have to read those cleaned records as a dispute between sessions. The remaining work is still to convert older narrative sections into the mandatory strict fields for each connected mechanism family: philosophical pattern, ideal form, current state, gap, what to code, what not to touch, refutation/check, closure criterion, evidence, and `комментарий для Антигравити`.

комментарий для Антигравити: documentation mechanism is still not ideal, but this cleanup moves it closer to the required executable-spec form. Do not restore agent/session labels inside `docs/FACTORY_MECHANISMS.md`; keep provenance in `docs/reports/*`. Apply `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`: mechanism truth, implementation advice, and work-session history are different levels. Apply `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`: every mechanism claim needs source evidence and a closure/refutation path.
## 2026-09-12 Codex Cleanup: First-Person Narrative Scrub Pass 1

Status: documentation-only cleanup of docs/FACTORY_MECHANISMS.md after the session-label scrub. Exact grep class for first-person/self-correction/noise (\bя\b|\bмой\b|\bмоя\b|\bмоих\b|собственн|ложь) changed from 250 to 176 occurrences in this local pass before commit. This is a cleanup metric, not a mechanism-completion claim.

Applied cleanup: replaced direct first-person author claims with neutral record language where the meaning was mechanical and safe to preserve: я не проверял/я не искал became explicit absence of check, мой/моя ошибка became record-measurement error, and several visible self-correction phrases became current-state or boundary wording. The main document should keep mechanisms and coding guidance; provenance and audit story belong here in docs/reports/*.

Remaining work: old narrative sections still contain non-ideal prose and must continue being converted by connected mechanism family into the strict visible fields. Do not treat this cleanup as ideal completion of all factory mechanisms.

комментарий для Антигравити: documentation mechanism is still not ideal. Continue removing first-person/provenance language from docs/FACTORY_MECHANISMS.md only when it can be converted without changing mechanism meaning. Apply ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK: record-author history, mechanism truth, and implementation advice are separate levels. Apply ELVIN_GOLDMAN_01_RELIABILITY_CHAIN: every cleaned claim must keep its evidence/refutation path.

## 2026-09-12 Codex Strict Record: Operational Truth / Trust Read Model

Status: documentation-only strict-family replacement for section XXVII in `docs/FACTORY_MECHANISMS.md`.

Mechanism/family now recorded: `OperationalTruthService`, `OperationalTruthController`, `OperationalTruthDto`, `TrustSnapshotService`, `LeverPromotionService`, `ClientDeliverableReadinessService`, `SystemStatusService` as the operational-truth read model and trust-dynamics family. The previous section was stale: it still told a future implementer to fix trust starting from unconditional `1.0`, while current source already has `computeBaseTrust(positiveEvidenceCount)`, `trustLevel(score, hasPositiveEvidence)`, zero-evidence `undetermined`, packet thresholds, recency-window exclusion and tests for packet growth, demotion, tri-state gate partition and institutional audit exclusion.

Denominator effect: `OperationalTruthController` is no longer just a mentioned controller surface in the stale remaining list; it is folded into the operational-truth family. This does not close all controller surfaces and does not prove the whole factory complete. It removes one stale implementation instruction and replaces it with visible strict fields: philosophical pattern, ideal form, boundary, inputs, outputs, truth owners, invariants, strong/weak form, what to code or not code, refutation, closure criterion, evidence, status and Antigravity comment.

Updated visible counts after this tact: exact lowercase `комментарий для Антигравити` grep in `docs/FACTORY_MECHANISMS.md` is 45. The controller rough denominator should now treat `OperationalTruthController` as recorded through this family; earlier remaining lists in this report are historical snapshots and must not be used as current truth without applying this removal. The remaining controller mentioned-only count is therefore reduced by one from the project-factory snapshot until the next full denominator regeneration.

Remaining non-ideal point for this family: no code was changed or tests run in this tact. The record says the old trust-base defect is strong in source/tests, but the family is not declared ideal until focused tests and live/fixture endpoint evidence prove consumers keep `undetermined` distinct from `blocked` and `trusted`.

комментарий для Антигравити: do not re-code the stale `score = 1.0` defect in `OperationalTruthService`; first verify the current `computeBaseTrust` / `trustLevel(score, hasPositiveEvidence)` behavior. Apply `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` for source/test evidence, `ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` for packet growth and fast demotion, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` for `undetermined` vs `blocked` vs `trusted`, and `ACP-061 Hoare Triple Review` before any later code.

## 2026-09-12 Codex Strict Record: Command Dashboard Readiness

Status: documentation-only strict-family replacement for section XXXIII in `docs/FACTORY_MECHANISMS.md`.

Mechanism/family now recorded: `CommandDashboardService`, `CommandDashboardController`, `AcceptanceReadinessDto`, `CommandDashboardDto`, `ClientAcceptanceTraversalEntity`, `ClientAcceptanceTraversalRepository` and `VerdictGate` as the operator command-dashboard readiness family. The previous section was stale: it still told a future implementer to add client acceptance traversal as the fifth readiness condition, while current source already has `clientAcceptanceWitnessed`, repository/table fallback over `client_acceptance_traversals`, the five-condition construction, and focused tests for zero traversal, positive traversal, traversal-store failure and unfinished tasks.

Denominator effect: `CommandDashboardController` is no longer just a mentioned controller surface in the stale remaining list; it is folded into the command-dashboard readiness family. This does not close all dashboard/operator surfaces and does not prove the factory complete. It removes one stale implementation instruction and makes the current implementation contract visible.

Updated visible counts after this tact: exact lowercase `комментарий для Антигравити` grep in `docs/FACTORY_MECHANISMS.md` is 46. Earlier remaining lists in this report are historical snapshots; the current controller rough denominator should remove both `OperationalTruthController` and `CommandDashboardController` from mentioned-only until the next full denominator regeneration.

Remaining non-ideal point for this family: no code was changed or tests run in this tact. The record says the old missing-client-acceptance defect is strong in source/tests, but the family is not declared ideal until focused tests and live/fixture endpoint evidence prove frontend/operator surfaces keep `unknown`, `not ready`, `ready` and `clientAcceptanceWitnessed` distinct.

комментарий для Антигравити: do not re-code the stale fifth-condition task in `CommandDashboardService`; first verify `CommandDashboardServiceTest` and a live/fixture endpoint. Apply `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` for source/test evidence, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` for construction vs client-shown readiness, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` for `unknown`, `NUEL_BELNAP_04_CONSTRUCTIVE_PROOF_OBJECT` for ready-as-proof-object, and `ACP-061 Hoare Triple Review` before any later code.

## 2026-09-12 Codex Strict Record: Internal Gemini Observer Surface

Status: documentation-only strict-family replacement for section XXXIV in `docs/FACTORY_MECHANISMS.md`.

Mechanism/family now recorded: `InternalGeminiObserverController`, `ApiAuthorizationInterceptor`, `WebConfig`, observer journal/action/evidence/coherence/reality repositories, session/review/account/task/project/persistent-worker/wishlist repositories, `ContinuousOrchestrationService` and `GeminiObserverActionService` as the internal observer diagnostic and repair surface. The previous section was stale: it still described `/internal/**` protection as only a comment, while current source has `ApiAuthorizationInterceptor` registered for `/internal/**` and tests for external denial, loopback read allow, credentialed external read allow, localhost mutating denial without credentials and mutating allow with valid credentials.

Denominator effect: `InternalGeminiObserverController` is no longer just a mentioned controller surface in the stale remaining list; it is folded into the internal observer surface family. This does not close all internal/controller surfaces and does not prove the factory complete. It removes one stale implementation instruction and makes the current security/diagnostic contract visible.

Updated visible counts after this tact: exact lowercase `комментарий для Антигравити` grep in `docs/FACTORY_MECHANISMS.md` is 47. Earlier remaining lists in this report are historical snapshots; the current controller rough denominator should remove `OperationalTruthController`, `CommandDashboardController` and `InternalGeminiObserverController` from mentioned-only until the next full denominator regeneration.

Remaining non-ideal point for this family: no code was changed or tests run in this tact. The record says the old comment-only internal-guard defect is strong in source/tests, but the family is not declared ideal until focused tests and live/fixture probes prove the boundary, and until the remaining broad diagnostics (`dispatchEligibilityDetail`, `accountCapacity`) are either bounded or explicitly accepted as secured diagnostics.

комментарий для Антигравити: do not re-code the stale `/internal/**` guard as missing; first verify `ApiAuthorizationInterceptorTest`, `InternalGeminiObserverControllerTest` and a live/fixture probe. Apply `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` for source/test evidence, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` for safe-read vs mutating internal operations, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` for executable denial, and `ACP-061 Hoare Triple Review` before any later code.

## 2026-09-12 Codex Strict Record: Account Selection / Dispatch Capacity

Status: documentation-only strict-family replacement for section XXXV in `docs/FACTORY_MECHANISMS.md`.

Mechanism/family now recorded: `AccountRepository`, `AccountEntity`, `ProjectFlowService`,
`JulesDispatchService`, `AccountHealthService` and observer diagnostics as the Jules account-selection and
dispatch-capacity family. The record now states the exact contract: general-pool selection is one native SQL
decision with eligibility filters, refusal-run ordering, open-session ordering, least-recent heartbeat
rotation and `FOR UPDATE SKIP LOCKED`.

Denominator effect: the old narrative account-selection section is now a strict mechanism record with
philosophical pattern, ideal form, boundary, inputs, outputs, truth owners, invariants, strong/weak form,
what to do, what not to touch, refutation, closure criterion, evidence, status and Antigravity comment. This
does not prove the whole factory complete and does not authorize code changes.

Updated visible counts after this tact: exact lowercase `комментарий для Антигравити` grep in
`docs/FACTORY_MECHANISMS.md` is 48. Earlier remaining lists in this report are historical snapshots; the
current account-selection family should be treated as recorded until the next full denominator regeneration.

Remaining non-ideal point for this family: no code was changed or tests run in this tact. The source contract
is strong and no implementation defect is identified in the selector itself; closure still needs focused test
execution and a concurrent-dispatch fixture/live probe.

комментарий для Антигравити: account-selection core is considered ideal by the current source/test contract;
do not patch `AccountRepository.lockNextJulesAccountWithCapacity` as cleanup. Preserve penalty as ordering,
not exclusion; preserve accepted-session reset, row locking, learned-capacity precedence and eligibility
filters. Apply `DZHOZEF_RAZ_21_PENALTY_AS_ORDERING`, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`,
`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` and `ACP-061 Hoare Triple Review` before any later code.

## 2026-09-12 Codex Strict Record: Task Repository Terminal-State Guard

Status: documentation-only strict-family replacement for section XXXVI in `docs/FACTORY_MECHANISMS.md`.

Mechanism/family now recorded: `TaskRepository`, `TaskEntity`, `TaskStatus`, `ClaimService`,
`PlannedWorkRecoveryService`, `BranchGarbageCollectorService`, `JulesDispatchService` and
`ProjectFlowService` as the task status transition guard family. The record now states the exact contract:
entity-level terminal overwrite denial plus repository-level exact-CAS / unless-terminal writes for bulk JPQL
paths that bypass entity lifecycle hooks.

Denominator effect: one more old narrative section is now a strict mechanism record with philosophical
pattern, ideal form, boundary, inputs, outputs, truth owners, invariants, strong/weak form, what to do, what
not to touch, refutation, closure criterion, evidence, status and Antigravity comment.

Current exact section denominator after this tact:

| Counted thing | Count |
| --- | ---: |
| Top-level mechanism sections, excluding file title | 63 |
| Sections with visible `комментарий для Антигравити` inside the section | 14 |
| Sections still missing visible `комментарий для Антигравити` | 49 |

This is a section denominator, not a mechanism count. It proves the document is not yet complete by the
operator's visible-comment rule; it does not pretend to know the exact number of remaining mechanisms before
the full source-denominator regeneration.

Remaining non-ideal point for this family: no code was changed or tests run in this tact. The source/test
contract is ideal for the task-status guard; fresh closure evidence requires running `TaskEntityLaw20Test`,
`TaskRepositoryIntegrationTest`, `TaskClaimServiceTest` and `ClaimServiceRaceGuardTest`.

комментарий для Антигравити: считаю механизм идеальным. Do not rewrite `TaskRepository` status guards as
ordinary save logic; preserve the entity guard, exact-state CAS, unless-terminal guard, same-statement
`updatedAt` write and `0` affected-row refusal semantics. Apply `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE`,
`ALVA_NOE_17_CAUSAL_PROCESS_TRACE` and `ACP-061 Hoare Triple Review` before any later code.

## 2026-09-12 Codex Strict Record: TOC Sentinel And Video Asset Surface

Status: documentation-only strict-family replacement for section XXXVII in `docs/FACTORY_MECHANISMS.md`.

Mechanism/family now recorded:

- `TocSentinelService`, `TocExecutionGraph`, `TocAnomalyDetector`, `TocOptimizer`,
  `TocSentinelController`, `TocNode`, `TocEdge`, `TocToken` as the TOC sentinel runtime family.
- `VideoAssetService`, `GoogleAiResourceService`, `GoogleAiResourceController`, `AutoMergeService` as the
  peripheral video-asset generation family that was previously embedded in the same section.

The TOC record replaces stale implementation advice. Current source already has cached pure
`getDbrStatus()`, explicit `refreshDbrStatus()`, dynamic `SchedulingConfigurer` cadence, removed leaky
component getters, unmodifiable graph facade collections and single-writer in-flight lifecycle tests.

The video record is not declared ideal. Source has clear fail-closed branches and metadata/media evidence,
but focused `VideoAssetService` branch tests were not found in this tact.

Current exact section denominator after this tact:

| Counted thing | Count |
| --- | ---: |
| Top-level mechanism sections, excluding file title | 63 |
| Sections with visible `комментарий для Антигравити` inside the section | 15 |
| Sections still missing visible `комментарий для Антигравити` | 48 |

This is a section denominator, not a mechanism count. It proves section progress only.

Remaining non-ideal point for TOC: no tests were run and no live scheduler/log measurement was taken in this
tact, but the current source/test contract is strong for the old TOC defects.

Remaining non-ideal point for Video: focused branch tests are needed for disabled, missing-key, unavailable,
no-video, ok and write-error outcomes with a temporary asset root.

комментарий для Антигравити: TOC core old defects are not current; first verify `TocSentinelServiceTest`,
`TocOptimizerTest` and `TocSentinelControllerTest`, preserving pure read, explicit refresh, facade ownership
and dynamic cadence. Video asset generation is not ideal by tests; add focused branch tests before changing
runtime behavior. Apply `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY`,
`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP`, `ALONZO_CHERCH_21_DERIVED_CUTOFF`,
`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` and `ACP-061 Hoare Triple
Review`.

## 2026-09-12 Codex Strict Record: AI Resource Mutation Boundary

Status: documentation-only strict-family replacement for section XXXVIII in `docs/FACTORY_MECHANISMS.md`.

Mechanism/family now recorded: `GoogleAiResourceController`, `ApiAuthorizationInterceptor`, `WebConfig`,
`GoogleAiResourceService`, `DesignAssetService`, `VideoAssetService`, `ProjectOperationalContextService`,
`ProjectRepository` and `StitchClient` as the AI-resource mutation boundary family.

Denominator effect: one more stale narrative section is now a strict mechanism record with philosophical
pattern, ideal form, boundary, inputs, outputs, truth/state owners, invariants, strong/weak form, what to do,
what not to touch, refutation, closure criterion, evidence, current status and Antigravity comment.

Current exact section denominator after this tact:

| Counted thing | Count |
| --- | ---: |
| Top-level mechanism sections, excluding file title | 63 |
| Sections with visible `комментарий для Антигравити` inside the section | 16 |
| Sections still missing visible `комментарий для Антигравити` | 47 |

This is a section denominator, not a mechanism count. It proves section progress only and does not claim the
entire factory source denominator is complete.

Key correction: the old section described the five AI-resource POST endpoints as open because no filter
existed. Current source is different: `ApiAuthorizationInterceptor` exists, `WebConfig` registers it for
`/api/**` and `/internal/**`, and `ApiAuthorizationInterceptorTest` covers unauthenticated/invalid/valid and
unconfigured-key behavior for the AI-resource mutation paths.

Remaining non-ideal point for this family: no code was changed in this tact. The record does not declare the
mechanism ideal because the evidence found is interceptor-unit plus registration evidence, not an end-to-end
MVC/deploy probe for `GoogleAiResourceController`, and successful AI-resource mutations do not yet have an
actor/project/action audit trail in the strict record.

комментарий для Антигравити: do not code the stale "create a filter from scratch" task. First prove the
current `ApiAuthorizationInterceptor` boundary end-to-end for `GoogleAiResourceController` with focused
MVC/integration tests, verify fail-closed deploy/runbook behavior for `ENEIK_SECURITY_API_KEY`, and add audit
trace for successful AI-resource mutations. Apply `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX`,
`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY`, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`,
`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` and `ACP-061 Hoare Triple Review`.
