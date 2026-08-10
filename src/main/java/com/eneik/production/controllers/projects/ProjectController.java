package com.eneik.production.controllers.projects;

import com.eneik.production.dto.*;
import com.eneik.production.services.ClaimService;
import com.eneik.production.services.OrchestrationCooldownException;
import com.eneik.production.services.ProjectFlowService;
import com.eneik.production.services.operational.OperationalPolicyDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectFlowService projectFlowService;
    private final ClaimService claimService;
    private final com.eneik.production.repositories.OnboardingAuditFindingRepository onboardingAuditFindingRepository;
    private final com.eneik.production.services.onboarding.OnboardingAuditService onboardingAuditService;
    private final com.eneik.production.services.ClientDeliverableReadinessService readinessService;
    private final com.eneik.production.services.FalsificationCycleService falsificationCycleService;
    private final com.eneik.production.services.tree.ProjectTreeService projectTreeService;
    private final com.eneik.production.services.runtime.ClientRuntimeObservabilityService clientRuntimeObservabilityService;
    private final com.eneik.production.services.coherence.EvidenceCoherenceService evidenceCoherenceService;
    private final com.eneik.production.repositories.GeminiObserverJournalRepository geminiObserverJournalRepository;

    public ProjectController(ProjectFlowService projectFlowService,
                             ClaimService claimService,
                             com.eneik.production.repositories.OnboardingAuditFindingRepository onboardingAuditFindingRepository,
                             com.eneik.production.services.onboarding.OnboardingAuditService onboardingAuditService,
                             com.eneik.production.services.ClientDeliverableReadinessService readinessService,
                             com.eneik.production.services.FalsificationCycleService falsificationCycleService,
                             com.eneik.production.services.tree.ProjectTreeService projectTreeService,
                             com.eneik.production.services.runtime.ClientRuntimeObservabilityService clientRuntimeObservabilityService,
                             com.eneik.production.services.coherence.EvidenceCoherenceService evidenceCoherenceService,
                             com.eneik.production.repositories.GeminiObserverJournalRepository geminiObserverJournalRepository) {
        this.projectFlowService = projectFlowService;
        this.claimService = claimService;
        this.onboardingAuditFindingRepository = onboardingAuditFindingRepository;
        this.onboardingAuditService = onboardingAuditService;
        this.readinessService = readinessService;
        this.falsificationCycleService = falsificationCycleService;
        this.projectTreeService = projectTreeService;
        this.clientRuntimeObservabilityService = clientRuntimeObservabilityService;
        this.evidenceCoherenceService = evidenceCoherenceService;
        this.geminiObserverJournalRepository = geminiObserverJournalRepository;
    }

    @GetMapping
    public List<ProjectDto> list() {
        return projectFlowService.listProjects();
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody ProjectCreateRequestDto request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(projectFlowService.createProject(request.name(), request.onboardingMode(), request.initialWishlist()));
        } catch (IllegalArgumentException e) {
            if ("name_conflict".equals(e.getMessage())) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "name_conflict", "message", "Repository already exists on GitHub. Do you want to onboard it?"));
            }
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "code", 400));
        }
    }

    @GetMapping("/{projectId}/onboarding-report")
    public ResponseEntity<?> getOnboardingReport(@PathVariable UUID projectId) {
        try {
            ProjectDto project = projectFlowService.listProjects().stream()
                    .filter(p -> p.id().equals(projectId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));

            java.nio.file.Path reportFile = java.nio.file.Paths.get("docs/reports/onboarding-audit-" + project.slug() + ".md");
            if (java.nio.file.Files.exists(reportFile)) {
                String content = java.nio.file.Files.readString(reportFile);
                return ResponseEntity.ok(Map.of("report", content));
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{projectId}/onboarding-findings")
    public ResponseEntity<?> getOnboardingFindings(@PathVariable UUID projectId) {
        try {
            return ResponseEntity.ok(onboardingAuditFindingRepository.findByProjectIdOrderByCreatedAtAsc(projectId));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{projectId}/onboarding-report/re-run")
    public ResponseEntity<?> reRunOnboardingReport(
            @PathVariable UUID projectId,
            @RequestParam(required = false, defaultValue = "false") boolean force) {
        try {
            com.eneik.production.models.persistence.ProjectEntity project = projectFlowService.requireProject(projectId);
            List<com.eneik.production.models.persistence.OnboardingAuditFindingEntity> existing = onboardingAuditFindingRepository.findByProjectIdOrderByCreatedAtAsc(projectId);
            if (!existing.isEmpty() && project.getStatus() != com.eneik.production.models.persistence.ProjectStatus.analyzing && !force) {
                java.time.Instant date = existing.get(0).getCreatedAt();
                return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                        "error", "audit_already_conducted",
                        "message", "Аудит уже проводился " + date.toString() + ", повторить?"
                ));
            }
            com.eneik.production.services.onboarding.StackProfile profile = onboardingAuditService.runOnboardingAudit(project, true);
            return ResponseEntity.ok(profile);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Manual trigger for the philosophical falsification track (2026-07-25) - lets the operator run it
    // out-of-cycle without waiting for the weekly cron, same reasoning as onboarding-report/re-run above.
    // Runs synchronously through the same gates the scheduled cycle uses (readiness, pending-wishlist cap,
    // feature flag) - it does not bypass them, it just doesn't wait for Sunday to check them.
    @PostMapping("/{projectId}/philosophical-falsification/run")
    public ResponseEntity<?> runPhilosophicalFalsification(@PathVariable UUID projectId) {
        try {
            com.eneik.production.models.persistence.ProjectEntity project = projectFlowService.requireProject(projectId);
            falsificationCycleService.executePhilosophicalCycleForProject(project, true);
            return ResponseEntity.accepted().body(Map.of(
                    "message", "Philosophical falsification cycle triggered; check task history/logs for the dispatch outcome (it may honestly skip if not ready, already active, or the feature flag is off)"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Manual trigger for the regular (code-defect) self_falsification cycle (2026-08-06) - same reasoning
    // as the philosophical one above, and specifically needed to verify the selfFalsificationReadyRatio
    // deadlock fix live without waiting for the daily 2am cron. Unlike the philosophical endpoint, this
    // has no force/bypass parameter - executeCycleForProject's own readiness gate always applies, so a
    // successful dispatch here is real proof the gate is actually passing, not a bypassed one.
    @PostMapping("/{projectId}/falsification/run")
    public ResponseEntity<?> runFalsification(@PathVariable UUID projectId) {
        try {
            com.eneik.production.models.persistence.ProjectEntity project = projectFlowService.requireProject(projectId);
            falsificationCycleService.executeCycleForProject(project);
            return ResponseEntity.accepted().body(Map.of(
                    "message", "Self-falsification cycle triggered; check task history/logs for the outcome (it may honestly skip if not ready, an audit is already active, or GitHub is unavailable)"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }


    @PostMapping("/{projectId}/wishlist")
    public ResponseEntity<?> addWishlist(@PathVariable java.util.UUID projectId, @RequestBody com.eneik.production.dto.WishlistRequestDto request) {
        try {
            return ResponseEntity.ok(projectFlowService.addWishlistItem(projectId, request));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage(), "code", 400));
        }
    }

    @GetMapping("/{projectId}/dashboard")
    public ProjectDashboardDto dashboard(@PathVariable UUID projectId) {
        return projectFlowService.dashboard(projectId);
    }

    // Read-only verification endpoint (2026-07-24, operator directive: "перечислить все 12 эпиков и докажи
    // что ты не лжёшь") - lists the real FeatureEntity rows behind productReadiness.totalFeatures/
    // completeFeatures, so those aggregate numbers can be checked against ground truth. No writes, no SQL
    // execution surface (unlike the deliberately-disabled debug SQL endpoint) - just a typed projection of
    // one existing table, filtered exactly the same way the dashboard number itself is computed.
    @GetMapping("/{projectId}/epics")
    public List<com.eneik.production.services.ClientDeliverableReadinessService.EpicDiagnostic> epics(@PathVariable UUID projectId) {
        return readinessService.listEpicDiagnostics(projectId);
    }

    // Backend for the "living tree" primary frontend view (2026-08-02) - read-only, additive: new
    // projection of data that already exists/is already computed server-side, nothing new written or
    // recomputed. Branches are the same PRODUCT_ITERATION_SOURCES-filtered feature set as /epics above
    // (built on the same listEpicDiagnostics call), so the two must always agree for a given featureId.
    @GetMapping("/{projectId}/tree")
    public com.eneik.production.dto.tree.ProjectTreeDto tree(@PathVariable UUID projectId) {
        return projectTreeService.getTree(projectId);
    }

    // Read-only projection (2026-08-10, Роща canopy glow + Кузница/Product room) - reuses
    // ClientRuntimeObservabilityService's own posterior math, nothing recomputed differently here.
    @GetMapping("/{projectId}/runtime-health")
    public com.eneik.production.services.runtime.ClientRuntimeObservabilityService.RuntimeHealthSummary runtimeHealth(@PathVariable UUID projectId) {
        return clientRuntimeObservabilityService.summarize(projectId);
    }

    // Read-only projection (2026-08-10, Кузница/Delivery room) - the evidence-coherence graph
    // (Thagard ECHO + Gärdenfors AGM) was previously only reachable via the localhost-only
    // /internal/gemini-observer/* surface; this is the same data, safe for the browser.
    @GetMapping("/{projectId}/coherence-graph")
    public com.eneik.production.services.coherence.EvidenceCoherenceService.GraphSnapshot coherenceGraph(@PathVariable UUID projectId) {
        return evidenceCoherenceService.graphSnapshot(projectId);
    }

    // Read-only projection (2026-08-10, Кузница/Delivery room) - Gemini's own real observation cycles
    // (never skip markers - same real-only query her own continuity logic uses), previously only
    // reachable via /internal/gemini-observer/journal (localhost-only).
    @GetMapping("/{projectId}/observer-journal")
    public List<com.eneik.production.models.persistence.GeminiObserverJournalEntity> observerJournal(@PathVariable UUID projectId) {
        return geminiObserverJournalRepository.findTop5ByProjectIdAndGeminiCalledTrueOrderByCreatedAtDesc(projectId);
    }



    @PostMapping("/{projectId}/orchestrate")
    public ResponseEntity<?> orchestrate(@PathVariable UUID projectId) {
        try {
            return ResponseEntity.ok(projectFlowService.orchestrate(projectId));
        } catch (OrchestrationCooldownException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                    "error", e.getMessage(),
                    "code", 429,
                    "retryAfterSeconds", e.getRetryAfterSeconds()
            ));
        } catch (OperationalPolicyDeniedException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "error", e.getMessage(),
                    "code", 409,
                    "action", e.action().name(),
                    "state", e.state(),
                    "authorizationStatus", e.authorizationStatus()
            ));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "code", 400));
        }
    }

    @PostMapping("/{projectId}/claim")
    public ResponseEntity<?> claim(@PathVariable UUID projectId, @RequestBody ProjectClaimRequestDto request) {
        try {
            ClaimDto claim = claimService.claimForProject(projectId, request.accountId());
            if (claim == null) {
                return ResponseEntity.noContent().build();
            }
            return ResponseEntity.ok(claim);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "code", 400));
        }
    }

    @PostMapping("/{projectId}/accept")
    public ResponseEntity<?> accept(@PathVariable UUID projectId) {
        try {
            return ResponseEntity.ok(projectFlowService.acceptProject(projectId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "code", 400));
        }
    }

    @PostMapping("/{projectId}/activate")
    public ResponseEntity<?> activate(@PathVariable UUID projectId) {
        try {
            return ResponseEntity.ok(projectFlowService.activateProject(projectId));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "code", 400));
        }
    }

    @PostMapping("/{projectId}/pause")
    public ResponseEntity<?> pause(@PathVariable UUID projectId) {
        try {
            return ResponseEntity.ok(projectFlowService.pauseProject(projectId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "code", 400));
        }
    }

    // 2026-08-07 (operator directive): deletes a frozen project's first decomposition (tasks, wishlist
    // slices, features) and re-submits the given brief as a fresh client wishlist on the SAME project -
    // same repo/GitHub collaborators, no need to create a brand-new project just to redo decomposition.
    @PostMapping("/{projectId}/reset-for-redecomposition")
    public ResponseEntity<?> resetForRedecomposition(@PathVariable UUID projectId, @RequestBody Map<String, String> body) {
        try {
            return ResponseEntity.ok(projectFlowService.resetProjectForRedecomposition(projectId, body.get("wishlist")));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "code", 400));
        }
    }

    @GetMapping("/{projectId}/pull-requests")
    public ResponseEntity<?> pullRequests(@PathVariable UUID projectId) {
        try {
            return ResponseEntity.ok(projectFlowService.featurePullRequests(projectId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "code", 400));
        }
    }

    @GetMapping("/{projectId}/recent-activity")
    public ResponseEntity<?> recentActivity(@PathVariable UUID projectId,
                                             @RequestParam(required = false, defaultValue = "100") int limit) {
        return ResponseEntity.ok(Map.of(
                "projectId", projectId,
                "lines", com.eneik.production.services.logging.LogScopeBuffer.recent(projectId, limit)
        ));
    }

    @PostMapping("/{projectId}/collaborators/refresh")
    public ResponseEntity<?> refreshCollaborators(@PathVariable UUID projectId) {
        try {
            return ResponseEntity.ok(projectFlowService.refreshCollaborators(projectId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "code", 400));
        }
    }
}
