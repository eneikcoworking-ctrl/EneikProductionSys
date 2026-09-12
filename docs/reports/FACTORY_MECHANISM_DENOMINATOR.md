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

## 2026-09-12 Codex Strict Record: Quality Metrics Truth Partition

Status: documentation-only strict-family replacement for section XXXIX in `docs/FACTORY_MECHANISMS.md`.

Mechanism/family now recorded: `QualityMetricsController`, `OperationalTruthService`,
`OperationalTruthController`, `OperationalTruthDto.EvidenceSummary`, `TaskEntity`, `TaskRepository`,
`TaskConflictRepository`, `PrReviewRepository`, `JulesSessionRepository`, `ProjectRepository`,
`OnboardingAuditFindingRepository` and `SixSigmaAuditService` as the quality-metrics truth-partition family.

Denominator effect: one more old narrative section is now a strict mechanism record with philosophical
pattern, ideal form, boundary, inputs, outputs, truth/state owners, invariants, strong/weak form, what to do,
what not to touch, refutation, closure criterion, evidence, current status and Antigravity comment.

Current exact section denominator after this tact:

| Counted thing | Count |
| --- | ---: |
| Top-level mechanism sections, excluding file title | 63 |
| Sections with visible `комментарий для Антигравити` inside the section | 17 |
| Sections still missing visible `комментарий для Антигравити` | 46 |

This is a section denominator, not a mechanism count. It proves section progress only and does not claim the
entire factory source denominator is complete.

Key correction: the old "388 tasks have failed quality-gate evidence" instruction is stale. Current source
has a three-way task-level partition in `TaskEntity`, exposes `qualityGateUnapplied` in
`OperationalTruthDto`, counts it separately in `OperationalTruthService`, and has tests that keep zero
applicable checks out of failed evidence. `QualityMetricsController` counts failed check rows, not absent
task-level delivery evidence.

Remaining non-ideal point for this family: no code was changed in this tact. The family is not declared
ideal because quality evidence is still computed in several owners (`QualityMetricsController`,
`OperationalTruthService`, `SystemStatusService`, `SixSigmaAuditService`) and `/api/quality/defect-summary`
does not expose unapplied as explicit not-a-defect context.

комментарий для Антигравити: do not repair the old 388 defect as current. First preserve the current
passed/failed/unapplied task partition, then unify quality-evidence projection across the quality metrics,
operational truth, system status and Six Sigma surfaces. Expose unapplied as context, not as a defect. Apply
`ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE`, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`,
`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` and `ACP-061 Hoare Triple Review`.

## 2026-09-12 Codex Strict Record: Stranded Finalizing Sweep

Status: documentation-only strict-family replacement for section XL in `docs/FACTORY_MECHANISMS.md`.

Mechanism/family now recorded: `StrandedFinalizingSweepService`, `JulesDispatchService`,
`WishlistEntity`, `WishlistStatus.finalizing`, `WishlistRepository`, `ProjectRepository`,
`DefectJournalRepository`, migration `V139__wishlist_finalizing_since.sql` and `LogScope` as the
stranded-finalizing recovery family.

Denominator effect: one more old narrative section is now a strict mechanism record with philosophical
pattern, ideal form, boundary, inputs, outputs, truth/state owners, invariants, strong/weak form, what to do,
what not to touch, refutation, closure criterion, evidence, current status and Antigravity comment.

Current exact section denominator after this tact:

| Counted thing | Count |
| --- | ---: |
| Top-level mechanism sections, excluding file title | 63 |
| Sections with visible `комментарий для Антигравити` inside the section | 18 |
| Sections still missing visible `комментарий для Антигравити` | 45 |

This is a section denominator, not a mechanism count. It proves section progress only and does not claim the
entire factory source denominator is complete.

Key correction: the record is now visible as an ideal mechanism, not just old incident prose. Current source
uses `finalizingSince`, active-project scoped acquisition, CAS release, null-lease initialization, optional
data-driven lease sizing from `FINALIZING_DURATION`, and focused tests for release/non-release/renewal/null
initialization/fallback.

Remaining non-ideal point for this family: no implementation weakness was identified from current source/test
evidence. Runtime silence remains valid only when scheduler liveness is separately observable.

комментарий для Антигравити: считаю механизм идеальным.

## 2026-09-12 Codex Strict Record: External AI RAG Runtime Boundary Family

Status: documentation-only strict-family replacement for section IX in `docs/FACTORY_MECHANISMS.md`.

Mechanism/family now recorded: `GoogleAiResourceController`, `GoogleAiResourceService`,
`GeminiContextService`, `ContextChunkRepository`, `ContextChunkEntity`, removed/stale
`GeminiContextCacheManager`, `EmbeddingSimilarityUtil`, `StitchClient`, `DesignAssetService`,
`DesignConsistencyAuditService`, `VideoAssetService`, `RuntimeLauncherClient`,
`GeminiObserverActionService`, `GeminiObserverActionEntity`, `InternalGeminiObserverController`,
`GeminiProjectObserverService` and `V111__permanently_disable_gemini_project_observer.sql` as the external
AI/RAG/runtime boundary family.

Denominator effect: one more old narrative section is now a strict mechanism record with a complete list of
mechanisms inside the section, philosophical pattern, ideal form, boundary, inputs, outputs, truth/state owners,
invariants, strong/weak form, what to do, what not to touch, refutation, closure criterion, evidence, current
status and Antigravity comment.

Current exact section denominator after this tact:

| Counted thing | Count |
| --- | ---: |
| Top-level mechanism sections, excluding file title | 63 |
| Sections with visible `комментарий для Антигравити` inside the section | 22 |
| Sections still missing visible `комментарий для Антигравити` | 41 |

This is a section denominator, not a mechanism count. It proves section progress only and does not claim the
entire factory source denominator is complete.

Key correction: the old section mixed live mechanisms with stale history. It still named `GeminiContextCacheManager`
even though source/test grep no longer finds that class or `cachedContents`, and it said `GeminiObserverActionService`
had no callers even though `InternalGeminiObserverController` now reaches it through the manual/internal
`retire-stuck-worker-now` surface. The strict record now treats the whole section as one external boundary:
Google AI, RAG, Stitch/Nano Banana/Veo assets, runtime launcher, audited observer powers and the permanently inert
project observer.

Remaining non-ideal point for this family: code was not changed in this tact. `GeminiContextService`
`buildProductWorkerContextBlock` still reaches `retrieveFiltered(query, DEFAULT_TOP_K, predicate)`, whose supplier
is `repository::findAllVectorRows`, before the product-worker predicate is applied. `VideoAssetService` also lacks
a focused branch-test file for disabled flag, missing key, unavailable interaction, `no_video` metadata and
`write_error` behavior.

комментарий для Антигравити: mechanism is not ideal. Apply `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`,
`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK`, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY`,
`DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX`, `RICHARD_DZHEFFRI_02_DECISION_EXPECTED_LOSS` and `ACP-061`: treat the
section as one external boundary, first move `buildProductWorkerContextBlock` to repository-scoped acquisition
and prove it never calls `findAllVectorRows` for the scoped product-worker path, then add focused
`VideoAssetService` branch tests; preserve redacted/fail-closed Google calls, removed prompt cache, Stitch
HTML-before-image rule, token-audited GitHub drafts, one runtime-launcher client, policy-gated audited observer
actions and the permanently inert `GeminiProjectObserverService`.

## 2026-09-12 Codex Strict Record: Design Shop And Live Drift Family

Status: documentation-only strict-family replacement for section X in `docs/FACTORY_MECHANISMS.md`.

Mechanism/family now recorded: `DesignShopOrchestrationService`, `DesignShopCycleEntity`,
`DesignShopCycleRepository`, migrations `V93__design_shop_cycles.sql`, `V94__design_shop_cycles_baseline.sql`,
`V98__design_shop_start_cycle_claim.sql`, `DesignConsistencyAuditService`, `LayoutGeometryAuditService`,
`DesignSystemFalsificationService`, `DesignDriftMonitorService`, and linked mechanisms `DesignAssetService`,
`StitchClient`, `ClientDeliverableReadinessService`, `ClientRuntimeObservabilityService`, `ProjectFlowService`,
`GitHubPullRequestService`, `WishlistRepository` and `ProjectRepository` as the design-shop, design-system and
live-drift family.

Denominator effect: one more old narrative section is now a strict mechanism record with a complete list of
mechanisms inside the section, philosophical pattern, ideal form, boundary, inputs, outputs, truth/state owners,
invariants, strong/weak form, what to do, what not to touch, refutation, closure criterion, evidence, current
status and Antigravity comment.

Current exact section denominator after this tact:

| Counted thing | Count |
| --- | ---: |
| Top-level mechanism sections, excluding file title | 63 |
| Sections with visible `комментарий для Антигравити` inside the section | 23 |
| Sections still missing visible `комментарий для Антигравити` | 40 |

This is a section denominator, not a mechanism count. It proves section progress only and does not claim the
entire factory source denominator is complete.

Key correction: old section X still carried stale statements from earlier measurements. Current source/test
evidence shows the readiness hold now logs named facts, `LayoutGeometryAuditService` covers viewport/collision/
proximity checks, and `DesignDriftMonitorService` has a real caller from `ClientRuntimeObservabilityService`
inside the live runtime observation window.

Remaining non-ideal point for this family: no current implementation weakness was identified from source/test
evidence in this tact. No code was changed.

комментарий для Антигравити: считаю механизм идеальным.

## 2026-09-12 Codex Strict Record: Review Gate Merge Family

Status: documentation-only strict-family replacement for section IV in `docs/FACTORY_MECHANISMS.md`.

Mechanism/family now recorded: `AutoMergeService`, `GitHubPullRequestService`, `GitHubApiBudgetService`,
`GithubAccessService`, `CodeChangeClassifier`, `GateOrchestrator`, `GateCheck`, `BaseQualityGate`,
`BackendContractGate`, `DesignExcellenceGate`, `VerificationEvidenceGate`, `EpistemicLayerInvariantGate`,
`BranchGarbageCollectorService`, `PrReviewPipelineService`, `RiskLevelCalculator`, `PrReviewRepository`,
`TaskGateLogRepository`, `TaskRepository`, `JulesSessionRepository`, `TaskConflictRepository`,
`FeatureThreadRepository`, `GithubWebhookController` and `JulesDispatchService` as the review/gate/merge family.

Denominator effect: one more old narrative section is now a strict mechanism record with a complete list of
mechanisms inside the section, philosophical pattern, ideal form, boundary, inputs, outputs, truth/state owners,
invariants, strong/weak form, what to code, what not to touch, refutation, closure criterion, evidence, current
status and Antigravity comment.

Current exact section denominator after this tact:

| Counted thing | Count |
| --- | ---: |
| Top-level mechanism sections, excluding file title | 63 |
| Sections with visible `комментарий для Антигравити` inside the section | 19 |
| Sections still missing visible `комментарий для Антигравити` | 44 |

This is a section denominator, not a mechanism count. It proves section progress only and does not claim the
entire factory source denominator is complete.

Key correction: section IV no longer mixes old dispute/prose with mechanism instructions. It now says exactly
which mechanisms are inside the review/gate/merge section and separates the strong invariants
(`AutoMergeService` merge-card return, `CodeChangeClassifier` deny-list, GitHub budget accounting, real-evidence
gates and branch-GC cleanup policy) from the remaining non-ideal work.

Remaining non-ideal point for this family: code was not changed in this tact. The family is not declared ideal
because `EpistemicLayerInvariantGate` still relies on `TaskEntity.fileScope` rather than the real PR diff,
`GithubAccessService` lacks a documented allowed/denied rights matrix in the record, and PR review/open-count
projections still need explicit set/source names before they can be compared.

комментарий для Антигравити: mechanism is not ideal. Apply `KARL_POPPER_01_FALSIFICATION_HARNESS`,
`DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX`, `ELIZABET_ENSKOM_02_PLANNING_CONSISTENCY`,
`DZHON_OSTIN_02_CATEGORY_ERROR_SCAN`, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` and `ACP-061`: first move
`EpistemicLayerInvariantGate` to real PR-diff evidence, then add allowed/denied authority tests for
`GithubAccessService` and explicit PR-set names for summaries; preserve `AutoMergeService`, `CodeChangeClassifier`,
`GitHubApiBudgetService`, real-evidence gates and branch-GC cleanup as load-bearing invariants.

## 2026-09-12 Codex Strict Record: Delivery Witness Family

Status: documentation-only strict-family replacement for section V in `docs/FACTORY_MECHANISMS.md`.

Mechanism/family now recorded: `ClientDeliverableReadinessService`, `DeliveryRealityProducerService`,
`ProductLaunchabilityService`, `ContinuousOrchestrationService`, `ClientRuntimeObservabilityService`,
`ProjectFlowService`, `WishlistRepository`, `FeatureRepository`, `TaskRepository`, `JulesSessionRepository`,
`PrReviewRepository`, `OperationalRealityFindingRepository`, `EvidenceNodeRepository`, `DefectJournalRepository`,
`ProjectRepository`, `GitHubPullRequestService`, `PlannedWorkRecoveryService`, `TaskEntity`, `WishlistEntity`,
`FeatureEntity`, `PrReviewEntity` and `EvidenceNodeEntity` as the delivery-witness family.

Denominator effect: one more old narrative section is now a strict mechanism record with a complete list of
mechanisms inside the section, philosophical pattern, ideal form, boundary, inputs, outputs, truth/state owners,
invariants, strong/weak form, what to code, what not to touch, refutation, closure criterion, evidence, current
status and Antigravity comment.

Current exact section denominator after this tact:

| Counted thing | Count |
| --- | ---: |
| Top-level mechanism sections, excluding file title | 63 |
| Sections with visible `комментарий для Антигравити` inside the section | 20 |
| Sections still missing visible `комментарий для Антигравити` | 43 |

This is a section denominator, not a mechanism count. It proves section progress only and does not claim the
entire factory source denominator is complete.

Key correction: section V now separates delivered value from task status, carrier work, runtime observation and
repository launchability. It explicitly records that the delivery predicate, reality producer and launchability
checks are currently load-bearing and should not be rewritten as local status cleanups.

Remaining non-ideal point for this family: no implementation weakness was identified from current source/test
evidence. Future work should add a failing counterexample first if runtime metrics later show false positives or
missed delivery failures.

комментарий для Антигравити: считаю механизм идеальным.

## 2026-09-12 Codex Strict Record: Judgment Lattice And Lever Promotion Family

Status: documentation-only strict-family replacement for section VI in `docs/FACTORY_MECHANISMS.md`.

Mechanism/family now recorded: `VerdictLayer`, `Verdict`, `Judgement`, `VerdictReconciliation`,
`VerdictGate`, `VerdictController`, `AutonomousVerdictObservationService`, `AcceptanceVerdictLayer`,
`RuntimeVerdictLayer`, `DoctrineVerdictLayer`, `InfrastructureVerdictLayer`, `SixSigmaVerdictLayer`,
`JudgmentAgentClient`, `FactoryJudgmentService`, `DeliveredWorkJudgmentService`, `CriteriaEvidenceSelector`,
`LeverPromotionService`, `LeverStage`, `LeverAgreement`, `LeverObservation`, `LeverPromotionStateEntity`,
`LeverObservationRepository` and `LeverPromotionStateRepository` as the judgment lattice, refutation judgment
and lever-promotion family.

Denominator effect: one more old narrative section is now a strict mechanism record with a complete list of
mechanisms inside the section, philosophical pattern, ideal form, boundary, inputs, outputs, truth/state owners,
invariants, strong/weak form, what to do, what not to touch, refutation, closure criterion, evidence, current
status and Antigravity comment.

Current exact section denominator after this tact:

| Counted thing | Count |
| --- | ---: |
| Top-level mechanism sections, excluding file title | 63 |
| Sections with visible `комментарий для Антигравити` inside the section | 21 |
| Sections still missing visible `комментарий для Антигравити` | 42 |

This is a section denominator, not a mechanism count. It proves section progress only and does not claim the
entire factory source denominator is complete.

Key correction: the old section said the verdict layers had no real callers and were heard only by the
dashboard. Current source disproves that: `VerdictGate` is called from `CommandDashboardService`,
`OperationalPolicyService` and `ProjectFlowService`, and `AutonomousVerdictObservationService` records new or
changed refusals into the defect journal.

Remaining non-ideal point for this family: no code weakness was identified from current source/test evidence.
The only remaining work before broader runtime use is operational verification of the staged flag/project-scope
deployment and live refusal observation.

комментарий для Антигравити: считаю механизм идеальным.

## 2026-09-12 Codex Strict Record: Runtime Observation And Product Capability Family

Status: documentation-only strict-family replacement for section XI in `docs/FACTORY_MECHANISMS.md`.

Mechanism/family now recorded: `ClientRuntimeObservabilityService`, `ClientRuntimeObservationEntity`,
`ClientRuntimeObservationRepository`, migrations `V92__client_runtime_observations.sql`,
`V95__runtime_preview_window.sql`, `V104__observation_instrument_failure.sql`,
`V109__observation_artifact_identity.sql`, `BetaPosterior`, `RuntimeHealthShiftDetector`,
`ProductCapabilityService`, `CapabilityObservationEntity`, `CapabilityObservationRepository`,
migrations `V107__capability_observations.sql`, `V140__capability_observations_instrument_failure.sql`,
and linked mechanisms `RuntimeLauncherClient`, `DesignDriftMonitorService`, `LaunchabilityConstraintService`,
`KaizenService`, `GitHubPullRequestService`, `ProjectController`, `ContinuousOrchestrationService`,
`FalsificationCycleService`, `DeliveryRealityProducerService` and `RuntimeVerdictLayer` as the live runtime
observation and product capability family.

Denominator effect: one more old narrative section is now a strict mechanism record with a complete list of
mechanisms inside the section, philosophical pattern, ideal form, boundary, inputs, outputs, truth/state owners,
invariants, strong/weak form, what to do, what not to touch, refutation, closure criterion, evidence, current
status and Antigravity comment.

Current exact section denominator after this tact:

| Counted thing | Count |
| --- | ---: |
| Top-level mechanism sections, excluding file title | 63 |
| Sections with visible `комментарий для Антигравити` inside the section | 24 |
| Sections still missing visible `комментарий для Антигравити` | 39 |

This is a section denominator, not a mechanism count. It proves section progress only and does not claim the
entire factory source denominator is complete.

Key correction: section XI now explicitly separates launch attempt, product health, instrument outage, artifact
identity and capability evidence. It records that runtime observation has no separate cron, product posterior skips
instrument failures and duplicate unchanged artifacts, capability denominator comes from OpenAPI contracts, and
401/403/null capability fetches are instrument barriers rather than product defects.

Remaining non-ideal point for this family: no current implementation weakness was identified from source/test
evidence in this tact. No code was changed.

комментарий для Антигравити: считаю механизм идеальным

## 2026-09-12 Codex Strict Record: TOC DBR Admission Rope Family

Status: documentation-only strict-family replacement for section XLIII in `docs/FACTORY_MECHANISMS.md`.

Mechanism/family now recorded: `TocOptimizer`, `TocSentinelService`, `TocExecutionGraph`, `TocNode`,
`TocToken`, `DbrStatus`, `TocSentinelController`, `AutoMergeService.processAutoMerge`, `KaizenService`,
`SixSigmaAuditService` and `SystemAuditController` as the TOC DBR admission-rope and flow-control telemetry
family.

Denominator effect: one more old narrative section is now a strict mechanism record with a complete list of
mechanisms inside the section, philosophical pattern, ideal form, boundary, inputs, outputs, truth/state owners,
invariants, strong/weak form, what to do, what not to touch, refutation, closure criterion, evidence, current
status and Antigravity comment.

Current exact section denominator after this tact:

| Counted thing | Count |
| --- | ---: |
| Top-level mechanism sections, excluding file title | 63 |
| Sections with visible `комментарий для Антигравити` inside the section | 25 |
| Sections still missing visible `комментарий для Антигравити` | 38 |

This is a section denominator, not a mechanism count. It proves section progress only and does not claim the
entire factory source denominator is complete.

Key correction: the old section treated the rope as unable to work at all. Current source/test evidence is more
specific: the engine-level rope can throttle and bypass when a buffer is actually breached, and it no longer
claims "optimal" for an empty or single-stage graph. The remaining non-ideal point is that the built-in factory
path still instruments only `AUTOMERGE_PROCESSING`, so the real production release-control contract is not yet
proved as multi-stage or derived-capacity flow control.

Remaining non-ideal point for this family: code was not changed in this tact. Ideal implementation requires real
factory-flow stage/queue instrumentation or derived buffer capacity, plus a focused proof that the built-in
production path can produce both `DBR_THROTTLE` and `DBR_BYPASS` without making `getDbrStatus` mutate state.

комментарий для Антигравити: механизм не идеален. Применить `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS`,
`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK`, `ALONZO_CHERCH_21_DERIVED_CUTOFF`,
`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY`, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP`,
`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` and `ACP-061`: не правь `TocOptimizer` как локальную константу
буфера; сначала докажи whole release-control contract для реального factory flow. Сохрани cached
`getDbrStatus`, explicit `refreshDbrStatus`, low-priority throttle, high-priority bypass and single-stage
"Flow unmeasured". Править надо разметку реальных стадий/очередей или вывести capacity из наблюдаемой
пропускной способности, потом добавить пробу, где built-in production path дает и `DBR_THROTTLE`, и
`DBR_BYPASS`.

## 2026-09-12 Codex Strict Record: Durable Project Log Retention Family

Status: documentation-only strict-family replacement for section XLIV in `docs/FACTORY_MECHANISMS.md`.

Mechanism/family now recorded: `ProjectEventLogRetentionService`, `ProjectEventLogRepository`,
`ProjectRepository`, `ProjectEntity`, `ProjectStatus`, `ProjectEventLogEntity`, `ProjectEventLogService`,
`DurableProjectLogAppender`, `ProjectLogFlushQueue` and `SystemStatusController` as the durable project log
retention and forensic read boundary family.

Denominator effect: one more old narrative section is now a strict mechanism record with a complete list of
mechanisms inside the section, philosophical pattern, ideal form, boundary, inputs, outputs, truth/state owners,
invariants, strong/weak form, what to do, what not to touch, refutation, closure criterion, evidence, current
status and Antigravity comment.

Current exact section denominator after this tact:

| Counted thing | Count |
| --- | ---: |
| Top-level mechanism sections, excluding file title | 63 |
| Sections with visible `комментарий для Антигравити` inside the section | 26 |
| Sections still missing visible `комментарий для Антигравити` | 37 |

This is a section denominator, not a mechanism count. It proves section progress only and does not claim the
entire factory source denominator is complete.

Key correction: the old section still contained a stale live defect about daily retention. Current source and
tests show that the daily cron was replaced with frequent fixed-delay retention and that trimming now acquires
one boundary row instead of loading the full excess into JVM memory.

Remaining non-ideal point for this family: no current implementation weakness was identified from source/test
evidence in this tact. No code was changed.

комментарий для Антигравити: считаю механизм идеальным

## 2026-09-12 Codex Strict Record: AI Resource Authorization Boundary Family

Status: documentation-only strict-family replacement for section XXXVIII in `docs/FACTORY_MECHANISMS.md`.

Mechanism/family now recorded: `GoogleAiResourceController`, `ApiAuthorizationInterceptor`, `WebConfig`,
`GoogleAiResourceService`, `DesignAssetService`, `VideoAssetService`, `ProjectOperationalContextService`,
`ProjectRepository`, `StitchClient`, `ApiAuthorizationInterceptorTest` and
`GoogleAiResourceControllerTest` as the AI resource authorization boundary and manual model-command surface.

Denominator effect: one more old/stale section is now a strict mechanism record with a complete list of
mechanisms inside the section, philosophical pattern, ideal form, boundary, inputs, outputs, truth/state owners,
invariants, strong/weak form, what to do, what not to touch, refutation, closure criterion, evidence, current
status and Antigravity comment.

Current exact section denominator after this tact:

| Counted thing | Count |
| --- | ---: |
| Top-level mechanism sections, excluding file title | 63 |
| Sections with visible `комментарий для Антигравити` inside the section | 27 |
| Sections still missing visible `комментарий для Антигравити` | 36 |

This is a section denominator, not a mechanism count. It proves section progress only and does not claim the
entire factory source denominator is complete.

Key correction: the old section title still said five model-resource mutations were on an open path. Current
source/test evidence shows the mutating `/api/**` path is guarded by `ApiAuthorizationInterceptor`, registered
through `WebConfig`, and fail-closed when the server key is blank. The remaining non-ideal point is proof and
audit: no focused MVC/integration test for the assembled `GoogleAiResourceController`, no deploy/runbook key
probe in this record, and no structured audit trace for successful AI-resource mutations.

Remaining non-ideal point for this family: code was not changed in this tact. Ideal work is MVC/integration
boundary proof plus structured successful-mutation audit, not recreating the interceptor from scratch.

комментарий для Антигравити: механизм не идеален. Применить `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX`,
`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY`, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`,
`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` and `ACP-061`: не кодь старую задачу "создать фильтр с нуля".
Сначала закрепи whole authorization boundary for `GoogleAiResourceController`: MVC/integration denial tests for
all five mutating endpoints, fail-closed blank-key test, refutation by removing `WebConfig` registration,
deploy/runbook key probe, and structured audit trace for successful AI-resource mutations. Сохрани safe GET vs
mutation distinction, path normalization in `listVideoAssets`, and current fail-closed owner-service semantics.

## 2026-09-12 Codex Strict Record: Quality Evidence Truth Table Family

Status: documentation-only strict-family replacement for section XXXIX in `docs/FACTORY_MECHANISMS.md`.

Mechanism/family now recorded: `QualityMetricsController`, `QualityGateController`,
`OperationalTruthController`, `OperationalTruthService`, `OperationalTruthDto.EvidenceSummary`, `TaskEntity`,
`TaskRepository`, `TaskConflictRepository`, `PrReviewRepository`, `JulesSessionRepository`,
`ProjectRepository`, `OnboardingAuditFindingRepository`, `SixSigmaAuditService` and `SystemStatusService` as
the quality evidence truth table, DPMO and operational trust projection family.

Denominator effect: one more old/stale section is now a strict mechanism record with a complete list of
mechanisms inside the section, philosophical pattern, ideal form, boundary, inputs, outputs, truth/state owners,
invariants, strong/weak form, what to do, what not to touch, refutation, closure criterion, evidence, current
status and per-mechanism Antigravity comments.

Current exact section denominator after this tact:

| Counted thing | Count |
| --- | ---: |
| Top-level mechanism sections, excluding file title | 63 |
| Sections with visible `комментарий для Антигравити` inside the section | 28 |
| Sections still missing visible `комментарий для Антигравити` | 35 |

This is a section denominator, not a mechanism count. It proves section progress only and does not claim the
entire factory source denominator is complete.

Key correction: the old section's "388 failed quality gates" defect is no longer current. Current code/tests
already separate verified, failed and unapplied task-level evidence; empty checks yield zero quality defects;
missing check `passed` is undetermined in Six Sigma. The remaining non-ideal point is projection ownership:
`QualityMetricsController`, `OperationalTruthService`, `SystemStatusService` and `SixSigmaAuditService` still
publish related quality truths through separate projections.

Remaining non-ideal point for this family: code was not changed in this tact. Ideal work is one shared quality
evidence projection plus explicit `qualityGateUnapplied` context in `/api/quality/defect-summary`, not reviving
the old 388-as-failed bug.

комментарий для Антигравити: смотри per-mechanism comments in section XXXIX; family summary is not a substitute.

## 2026-09-12 Codex Strict Record: Stranded Finalizing Sweep Family

Status: documentation-only strict-family replacement for section XL in `docs/FACTORY_MECHANISMS.md`.

Mechanism/family now recorded: `StrandedFinalizingSweepService.sweep`,
`StrandedFinalizingSweepService.sweepProject`, `StrandedFinalizingSweepService.calculateEffectiveLeaseDuration`,
`JulesDispatchService.admitWishlistCompilationCompletion`, `JulesDispatchService.renewFinalizingLeases`,
`JulesDispatchService.releaseUnfinishedClaims`, `JulesDispatchService.recordFinalizingDuration`,
`WishlistEntity.finalizingSince`, `WishlistStatus.finalizing`, `WishlistRepository.compareAndSetStatus`,
`WishlistRepository.compareAndSetStatusWithTimestamp`, `WishlistRepository.renewFinalizingLeases`,
`ProjectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)`, `DefectJournalRepository`, migration
`V139__wishlist_finalizing_since.sql` and `LogScope` as the stranded-finalizing recovery family.

Denominator effect: one more old/stale section is now a strict mechanism record with a complete list of
mechanisms inside the section, philosophical pattern, ideal form, boundary, inputs, outputs, truth/state owners,
invariants, strong/weak form, what to do, what not to touch, refutation, closure criterion, evidence, current
status and per-mechanism Antigravity comments.

Current exact section denominator after this tact:

| Counted thing | Count |
| --- | ---: |
| Top-level mechanism sections, excluding file title | 63 |
| Sections with visible `комментарий для Антигравити` inside the section | 29 |
| Sections still missing visible `комментарий для Антигравити` | 34 |

This is a section denominator, not a mechanism count. It proves section progress only and does not claim the
entire factory source denominator is complete.

Key correction: the old section had one family-level Antigravity comment. Section XL now states the ideal
stranded-finalizing recovery contract and gives per-mechanism comments for every behavior-changing participant.
Current evidence says the source/test mechanism is ideal: no code change is required.

Remaining non-ideal point for this family: no source/test non-ideality is identified in this tact. The only
operational caution is that runtime silence must be interpreted with scheduler-alive evidence.

комментарий для Антигравити: смотри per-mechanism comments in section XL; family summary is not a substitute.

## 2026-09-12 Codex Strict Record: Screen Quality Evidence Gate Family

Status: documentation-only strict-family replacement for section XLI in `docs/FACTORY_MECHANISMS.md`.

Mechanism/family now recorded: `DesignExcellenceGate`, `LayoutGeometryAuditService`,
`JulesDispatchService.designVerificationInstruction`, `ProjectFlowService.designReviewPrompt`,
`GateOrchestrator`, `GateCheck.isBuildPhaseExempt`, `DesignConsistencyAuditService`,
`GitHubPullRequestService` and `JulesSessionRepository` as the screen-quality evidence gate family.

Denominator effect: one more old/stale section is now a strict mechanism record with a complete list of
mechanisms inside the section, philosophical pattern, ideal form, boundary, inputs, outputs, truth/state owners,
invariants, strong/weak form, what to do, what not to touch, refutation, closure criterion, evidence, current
status and per-mechanism Antigravity comments.

Current exact section denominator after this tact:

| Counted thing | Count |
| --- | ---: |
| Top-level mechanism sections, excluding file title | 63 |
| Sections with visible `комментарий для Антигравити` inside the section | 30 |
| Sections still missing visible `комментарий для Антигравити` | 33 |

This is a section denominator, not a mechanism count. It proves section progress only and does not claim the
entire factory source denominator is complete.

Key correction: the old section mixed narrative history, prescription and test summary. Section XLI now states
the exact ideal screen-quality contract: real PR evidence, two screenshots, verifiable geometry, no collision,
allowed viewport scaling, Gestalt proximity, and machine/human review demarcation. It also gives
per-mechanism Antigravity comments.

Remaining non-ideal point for this family: no source/test non-ideality is identified in this tact. Runtime gate
frequency was not remeasured here; that is an operational observability question, not a code defect in this
mechanism.

комментарий для Антигравити: смотри per-mechanism comments in section XLI; family summary is not a substitute.

## 2026-09-12 Codex Strict Record: Static Corpus Cache Ownership Family

Status: documentation-only strict-family replacement for section XLII in `docs/FACTORY_MECHANISMS.md`.

Mechanism/family now recorded: stale/removed `GeminiContextCacheManager`,
`SystemStatusController.reindexGeminiContext`, `GeminiContextService.reindexStandingKnowledge`,
`MLPredictionServiceClient.chat(prompt, systemInstruction, cacheKey)`, sidecar
`PredictionService.ensure_gemini_cache`, sidecar `PredictionService.ask_gemini_cached` and the sidecar chat
fallback path as the static-corpus provider cache ownership family.

Denominator effect: one more old/stale section is now a strict mechanism record with a complete list of
mechanisms inside the section, philosophical pattern, ideal form, boundary, inputs, outputs, truth/state owners,
invariants, strong/weak form, what to do, what not to touch, refutation, closure criterion, evidence, current
status and per-mechanism Antigravity comments.

Current exact section denominator after this tact:

| Counted thing | Count |
| --- | ---: |
| Top-level mechanism sections, excluding file title | 63 |
| Sections with visible `комментарий для Антигравити` inside the section | 31 |
| Sections still missing visible `комментарий для Антигравити` | 32 |

This is a section denominator, not a mechanism count. It proves section progress only and does not claim the
entire factory source denominator is complete.

Key correction: the old section still described the pre-fix backend `GeminiContextCacheManager` defect as an
open coding task. Current source/test evidence says that duplicate backend manager has already been removed.
Section XLII now records the current contract: backend Java must not create provider `cachedContents`; manual
reindex refreshes RAG only; sidecar `PredictionService.py` is the single legal owner of provider cached-content
creation and must fail open to uncached calls.

Remaining non-ideal point for this family: no deletion work remains. The only current usage gap is that
`MLPredictionServiceClient.chat(..., cacheKey)` exists as the Java carrier, but no main Java production caller
was proven in this tact to pass a nonblank `cacheKey`.

комментарий для Антигравити: смотри per-mechanism comments in section XLII; family summary is not a substitute.

## 2026-09-12 Codex Strict Record: State Writer Ownership Family

Status: documentation-only strict-family replacement for section XIII in `docs/FACTORY_MECHANISMS.md`.

Mechanism/family now recorded: the factory state-owner map, `WishlistRepository`/`WishlistEntity` wishlist
status transitions, `TaskRepository`/`TaskEntity` task status transitions, and
`SessionLifecycleService`/`JulesSessionRepository` Jules-session lifecycle ownership.

Denominator effect: one more old/stale section is now a strict mechanism record with a complete list of
mechanisms inside the section, philosophical pattern, ideal form, boundary, inputs, outputs, truth/state owners,
invariants, strong/weak form, what to do, what not to touch, refutation, closure criterion, evidence, current
status and per-mechanism Antigravity comments.

Current exact section denominator after this tact:

| Counted thing | Count |
| --- | ---: |
| Top-level mechanism sections, excluding file title | 63 |
| Sections with visible `комментарий для Антигравити` inside the section | 32 |
| Sections still missing visible `комментарий для Антигравити` | 31 |

This is a section denominator, not a mechanism count. It proves section progress only and does not claim the
entire factory source denominator is complete.

Key correction: the old section was a narrative warning about many writers. Section XIII now states the exact
engineering contract: many writers are allowed only when the transition owner, allowed writers and atomic
write path are named. It records the ideal for wishlist status transitions, task status transitions and Jules
session lifecycle fields, and it keeps the strong existing CAS/lifecycle mechanisms intact.

Remaining non-ideal point for this family: the source has strong local forms (`WishlistRepository` CAS,
`TaskRepository` terminal guards and CAS, `SessionLifecycleService` as remote-session owner), but it still lacks
a complete owner-map for every mutable repository field and transition. No code was changed in this tact.

комментарий для Антигравити: смотри per-mechanism comments in section XIII; family summary is not a substitute.

## 2026-09-12 Codex Strict Record: Sidecar Runtime Mechanisms

Status: documentation-only strict-family replacement for section XIX in `docs/FACTORY_MECHANISMS.md`.

Mechanism/family now recorded: `runtime-launcher/launcher.py` launch/health/fetch/teardown surface,
`judgment-proxy/server.js` Gemini/manual/heuristic judgment proxy, and `src/models/ml/PredictionService.py`
prediction/chat/embedding/cache sidecar.

Denominator effect: one more old/stale section is now a strict mechanism record with a complete list of
mechanisms inside the section, philosophical pattern, ideal form, boundary, inputs, outputs, truth/state owners,
invariants, strong/weak form, what to do, what not to touch, refutation, closure criterion, evidence, current
status and visible per-mechanism Antigravity comments.

Current exact section denominator after this tact:

| Counted thing | Count |
| --- | ---: |
| Top-level mechanism sections, excluding file title | 63 |
| Sections with visible `комментарий для Антигравити` inside the section | 33 |
| Sections still missing visible `комментарий для Антигравити` | 30 |

This is a section denominator, not a mechanism count. It proves section progress only and does not claim the
entire factory source denominator is complete.

Key correction: the old section mixed narrative verdicts with sidecar descriptions. Section XIX now states
the exact ideal contracts: docker-socket sidecar needs a real authorization boundary; judgment fallback needs
visible provenance; bottleneck prediction needs either heuristic naming or a belief-update ledger. Existing
strong pieces are explicitly preserved: launcher topology/memory protections, judgment shadow audit and
validation, PredictionService cache fail-open and local embedding.

Remaining non-ideal point for this family: all three sidecars remain non-ideal in different ways, but no code
was changed in this tact. The record now tells Antigravity exactly what to preserve and what to fix.

комментарий для Антигравити: смотри per-mechanism comments in section XIX; family summary is not a substitute.

## 2026-09-12 Codex Strict Record: Flow-Holding Repositories

Status: documentation-only strict-family replacement for section XX in `docs/FACTORY_MECHANISMS.md`.

Mechanism/family now recorded: `DesignShopCycleRepository` cycle-start lease, `ClaimRepository` task-claim
cardinality and expiry queries, `ProjectEventLogRepository` durable project log retention,
`ContextChunkRepository` reindex/source identity, and `GreetingRepository` demo greeting cycle-time metric.

Denominator effect: one more old/stale section is now a strict mechanism record with a complete list of
mechanisms inside the section, philosophical pattern, ideal form, boundary, inputs, outputs, truth/state owners,
invariants, strong/weak form, what to do, what not to touch, refutation, closure criterion, evidence, current
status and visible per-mechanism Antigravity comments.

Current exact section denominator after this tact:

| Counted thing | Count |
| --- | ---: |
| Top-level mechanism sections, excluding file title | 63 |
| Sections with visible `комментарий для Антигравити` inside the section | 34 |
| Sections still missing visible `комментарий для Антигравити` | 29 |

This is a section denominator, not a mechanism count. It proves section progress only and does not claim the
entire factory source denominator is complete.

Key correction: the old section was a compact narrative over five repositories. Section XX now separates each
repository mechanism: design-cycle lease ownership, claim cardinality safety, durable log retention,
context-chunk identity, and demo-only greeting metrics. Per-mechanism Antigravity comments name which
repositories are ideal and which must not be promoted or changed without the right philosophy.

Remaining non-ideal point for this family: `DesignShopCycleRepository` still lacks symmetric owner/lease
checking for release and bounded stale-claim recovery; `GreetingRepository` is not proven harmful, but its
metric must remain demo-level and not factory-level. No code was changed in this tact.

комментарий для Антигравити: смотри per-mechanism comments in section XX; family summary is not a substitute.

## 2026-09-12 Codex Strict Record: Philosopher Corpus Cross-Check

Status: documentation-only strict-family replacement for section XIV in `docs/FACTORY_MECHANISMS.md`.

Mechanism/family now recorded: `docs/philosopher-patterns` as the external defect-pattern corpus, the
section XIV defect-family application map, and the grounding guard that prevents hallucinated mechanism
records in `docs/FACTORY_MECHANISMS.md`.

Denominator effect: one more old/stale section is now a strict mechanism record with a complete list of
mechanisms inside the section, philosophical pattern, ideal form, boundary, inputs, outputs, truth/state owners,
invariants, strong/weak form, what to do, what not to touch, refutation, closure criterion, evidence, current
status and visible per-mechanism Antigravity comments.

Current exact section denominator after this tact:

| Counted thing | Count |
| --- | ---: |
| Top-level mechanism sections, excluding file title | 63 |
| Sections with visible `комментарий для Антигравити` inside the section | 35 |
| Sections still missing visible `комментарий для Антигравити` | 28 |

This is a section denominator, not a mechanism count. It proves section progress only and does not claim the
entire factory source denominator is complete.

Key correction: the old section mixed corpus summary, examples, current obligations and agent self-audit.
Section XIV now separates the corpus, the application map and the grounding guard. It keeps exact evidence
counts for the corpus (`86` philosopher files, `1720` pattern rows, `15` defect codes) and states which parts
are ideal versus merely protocol-level and not yet automated.

Remaining non-ideal point for this family: the corpus itself is treated as ideal, but the document-wide
grounding guard is still manual/protocol-level. A future ideal form is a doc checker for pattern ids,
evidence lines, closure criteria and per-mechanism Antigravity comments. No code was changed in this tact.

комментарий для Антигравити: смотри per-mechanism comments in section XIV; family summary is not a substitute.
