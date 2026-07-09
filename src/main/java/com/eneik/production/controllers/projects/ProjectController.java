package com.eneik.production.controllers.projects;

import com.eneik.production.dto.*;
import com.eneik.production.services.ClaimService;
import com.eneik.production.services.ProjectFlowService;
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

    public ProjectController(ProjectFlowService projectFlowService,
                             ClaimService claimService,
                             com.eneik.production.repositories.OnboardingAuditFindingRepository onboardingAuditFindingRepository,
                             com.eneik.production.services.onboarding.OnboardingAuditService onboardingAuditService) {
        this.projectFlowService = projectFlowService;
        this.claimService = claimService;
        this.onboardingAuditFindingRepository = onboardingAuditFindingRepository;
        this.onboardingAuditService = onboardingAuditService;
    }

    @GetMapping
    public List<ProjectDto> list() {
        return projectFlowService.listProjects();
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody ProjectCreateRequestDto request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(projectFlowService.createProject(request.name(), request.onboardingMode()));
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

    @GetMapping("/{projectId}/dashboard")
    public ProjectDashboardDto dashboard(@PathVariable UUID projectId) {
        return projectFlowService.dashboard(projectId);
    }

    @PostMapping("/{projectId}/wishlist")
    public ResponseEntity<?> addWishlist(@PathVariable UUID projectId, @RequestBody WishlistItemRequestDto request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(projectFlowService.addWishlistItem(projectId, request));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "code", 400));
        }
    }

    @PostMapping("/{projectId}/orchestrate")
    public ResponseEntity<?> orchestrate(@PathVariable UUID projectId) {
        try {
            return ResponseEntity.ok(projectFlowService.orchestrate(projectId));
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

    @PostMapping("/{projectId}/collaborators/refresh")
    public ResponseEntity<?> refreshCollaborators(@PathVariable UUID projectId) {
        try {
            return ResponseEntity.ok(projectFlowService.refreshCollaborators(projectId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage(), "code", 400));
        }
    }
}
