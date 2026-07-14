package com.eneik.production.services;

import com.eneik.production.dto.*;
import com.eneik.production.dto.dashboard.AgentDashboardDto;
import com.eneik.production.dto.dashboard.PipelineDashboardDto;
import com.eneik.production.dto.dashboard.QueueDashboardDto;
import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.*;
import com.eneik.production.dto.dashboard.ClientDeliveryDto;
import com.eneik.production.services.compiler.TechnicalLeadCompiler;
import com.eneik.production.services.dashboard.ClientDeliveryService;
import com.eneik.production.services.jules.JulesDispatchResult;
import com.eneik.production.services.jules.JulesDispatchService;
import com.eneik.production.services.projectfactory.CollaboratorProvisioningResult;
import com.eneik.production.services.projectfactory.GitHubProjectFactoryClient;
import com.eneik.production.services.projectfactory.ProjectFactoryResult;
import com.eneik.production.services.projectfactory.ProjectFactoryService;
import com.eneik.production.services.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class ProjectFlowService {
    private static final Logger log = LoggerFactory.getLogger(ProjectFlowService.class);
    private static final List<String> JULES_NAMES = List.of(
            "Jules-01", "Jules-02", "Jules-03", "Jules-04", "Jules-05", "Jules-06", "Jules-07"
    );
    private static final String UNIVERSAL_CAPABILITIES = "*";
    private static final String ORCHESTRATOR_ROLE = "BARCAN-TAG-09";
    private static final long ORCHESTRATION_COOLDOWN_SECONDS = 300L;

    private final ProjectRepository projectRepository;
    private final WishlistRepository wishlistRepository;
    private final AccountRepository accountRepository;
    private final TaskRepository taskRepository;
    private final ClaimRepository claimRepository;
    private final RoleRepository roleRepository;
    private final ClaimService claimService;
    private final JulesDispatchService julesDispatchService;
    private final ProjectFactoryService projectFactoryService;
    private final GitHubProjectFactoryClient gitHubProjectFactoryClient;
    private final SystemSettingsService settingsService;
    private final TechnicalLeadCompiler technicalLeadCompiler;
    private final ClientDeliveryService clientDeliveryService;
    private final ProjectFinalReportRepository projectFinalReportRepository;
    private final JulesSessionRepository julesSessionRepository;
    private final ProjectGenerationStateRepository projectGenerationStateRepository;
    private final ObjectMapper objectMapper;
    private final String githubOrganization;
    private final com.eneik.production.services.onboarding.OnboardingAuditService onboardingAuditService;
    private final MLPredictionServiceClient mlPredictionServiceClient;

    @Value("${jules.max-concurrent-sessions-per-account:3}")
    private int maxConcurrentJulesSessionsPerAccount;



    public ProjectFlowService(ProjectRepository projectRepository,
                              WishlistRepository wishlistRepository,
                              AccountRepository accountRepository,
                              TaskRepository taskRepository,
                              ClaimRepository claimRepository,
                              RoleRepository roleRepository,
                              ClaimService claimService,
                              JulesDispatchService julesDispatchService,
                              ProjectFactoryService projectFactoryService,
                              GitHubProjectFactoryClient gitHubProjectFactoryClient,
                              SystemSettingsService settingsService,
                              TechnicalLeadCompiler technicalLeadCompiler,
                              ClientDeliveryService clientDeliveryService,
                              ProjectFinalReportRepository projectFinalReportRepository,
                              JulesSessionRepository julesSessionRepository,
                              ProjectGenerationStateRepository projectGenerationStateRepository,
                              ObjectMapper objectMapper,
                              @Value("${github.org}") String githubOrganization,
                              com.eneik.production.services.onboarding.OnboardingAuditService onboardingAuditService,
                              MLPredictionServiceClient mlPredictionServiceClient) {
        this.projectRepository = projectRepository;
        this.wishlistRepository = wishlistRepository;
        this.accountRepository = accountRepository;
        this.taskRepository = taskRepository;
        this.claimRepository = claimRepository;
        this.roleRepository = roleRepository;
        this.claimService = claimService;
        this.julesDispatchService = julesDispatchService;
        this.projectFactoryService = projectFactoryService;
        this.gitHubProjectFactoryClient = gitHubProjectFactoryClient;
        this.settingsService = settingsService;
        this.technicalLeadCompiler = technicalLeadCompiler;
        this.clientDeliveryService = clientDeliveryService;
        this.projectFinalReportRepository = projectFinalReportRepository;
        this.julesSessionRepository = julesSessionRepository;
        this.projectGenerationStateRepository = projectGenerationStateRepository;
        this.objectMapper = objectMapper;
        this.githubOrganization = githubOrganization;
        this.onboardingAuditService = onboardingAuditService;
        this.mlPredictionServiceClient = mlPredictionServiceClient;
    }

    @Transactional
    public ProjectDto createProject(String name) {
        return createProject(name, "greenfield");
    }

    @Transactional
    public ProjectDto createProject(String name, String onboardingMode) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Project name is required");
        }

        String mode = onboardingMode != null ? onboardingMode.trim() : "greenfield";

        // 1. Freeze current active project only if greenfield
        if ("greenfield".equalsIgnoreCase(mode)) {
            projectRepository.findFirstByStatusOrderByCreatedAtDesc(ProjectStatus.active)
                    .ifPresent(p -> {
                        p.setStatus(ProjectStatus.frozen);
                        projectRepository.save(p);
                    });
        }

        // 2. Create new project
        ProjectEntity project = new ProjectEntity();
        project.setName(name.trim());
        project.setSlug(uniqueSlug(name));
        project.setOnboardingMode(mode);
        if ("brownfield".equalsIgnoreCase(mode)) {
            project.setStatus(ProjectStatus.analyzing);
        } else {
            project.setStatus(ProjectStatus.active);
        }
        project.setRepositoryName(project.getSlug());
        project.setRepositoryUrl("https://github.com/" + githubOrganization + "/" + project.getSlug());
        project.setRepoUrl(project.getRepositoryUrl());
        project.setLinearProjectKey(project.getSlug().toUpperCase(Locale.ROOT).replace("-", "_"));
        
        ProjectEntity saved = projectRepository.save(project);
        ensureProjectGenerationState(saved.getId());
        ProjectFactoryResult factoryResult = projectFactoryService.provision(saved);
        saved.setRepositoryUrl(factoryResult.repositoryUrl());
        saved.setRepoUrl(factoryResult.repositoryUrl());
        saved.setGithubRepositoryStatus(factoryResult.githubRepositoryStatus());
        saved.setGithubRepositoryId(factoryResult.githubRepositoryId());
        saved.setLinearProjectStatus(factoryResult.linearProjectStatus());
        saved.setLinearProjectId(factoryResult.linearProjectId());
        saved.setWorkspacePath(factoryResult.workspacePath());
        saved.setFactoryStatus(factoryResult.factoryStatus());
        saved.setFactoryReport(factoryResult.factoryReport());
        if ("waiting".equals(factoryResult.factoryStatus())) {
            saved.setStatus(ProjectStatus.waiting);
        }
        saved = projectRepository.save(saved);

        // Run onboarding audit if brownfield
        if ("brownfield".equalsIgnoreCase(mode)) {
            try {
                onboardingAuditService.runOnboardingAudit(saved);
            } catch (Exception e) {
                log.error("Failed to run onboarding audit for project {}", saved.getId(), e);
            }
        }

        return toProjectDto(saved);
    }

    private void ensureProjectGenerationState(UUID projectId) {
        if (projectGenerationStateRepository.existsById(projectId)) {
            return;
        }
        ProjectGenerationStateEntity state = new ProjectGenerationStateEntity();
        state.setProjectId(projectId);
        projectGenerationStateRepository.save(state);
    }

    @Transactional
    public ProjectDto activateProject(UUID projectId) {
        ProjectEntity project = requireProject(projectId);
        if (project.getStatus() == ProjectStatus.accepted || project.getStatus() == ProjectStatus.archived) {
            throw new IllegalStateException("Cannot activate " + project.getStatus() + " project");
        }

        if (project.getStatus() == ProjectStatus.active) {
            return toProjectDto(project);
        }

        // Freeze other active projects
        projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)
                .forEach(p -> {
                    if (!p.getId().equals(projectId)) {
                        p.setStatus(ProjectStatus.frozen);
                        projectRepository.save(p);
                    }
                });

        project.setStatus(ProjectStatus.active);
        return toProjectDto(projectRepository.save(project));
    }



    @Transactional
    public com.eneik.production.dto.WishlistResponseDto addWishlistItem(UUID projectId, com.eneik.production.dto.WishlistRequestDto request) {
        ProjectEntity project = requireActiveProject(projectId);
        if (project.getStatus() == com.eneik.production.models.persistence.ProjectStatus.analyzing) {
            throw new IllegalStateException("Cannot add wishlist to a project in analyzing state");
        }
        if (request == null || request.content() == null || request.content().isBlank()) {
            throw new IllegalArgumentException("Wishlist text is required");
        }
        com.eneik.production.models.persistence.WishlistEntity item = new com.eneik.production.models.persistence.WishlistEntity();
        item.setProjectId(project.getId());
        item.setContent(request.content().trim());
        com.eneik.production.models.persistence.WishlistSource source =
                request.source() != null ? request.source() : com.eneik.production.models.persistence.WishlistSource.client;
        if (source == com.eneik.production.models.persistence.WishlistSource.role
                && (request.sourceRoleTag() == null || request.sourceRoleTag().isBlank())) {
            throw new IllegalArgumentException("sourceRoleTag is required when source is 'role'");
        }
        item.setSource(source);
        item.setSourceRoleTag(source == com.eneik.production.models.persistence.WishlistSource.client ? null : request.sourceRoleTag());
        item.setStatus(com.eneik.production.models.persistence.WishlistStatus.pending);
        item = wishlistRepository.save(item);

        return new com.eneik.production.dto.WishlistResponseDto(item.getId(), item.getProjectId(), item.getSource(), item.getSourceRoleTag(), item.getContent(), item.getStatus(), item.getCreatedAt());
    }

    @Transactional
    public OrchestrationResultDto orchestrate(UUID projectId) {
        ProjectEntity project = requireActiveProject(projectId);
        recordOrchestrationStartOrThrow(projectId);

        // 1. Record existing task IDs of this project
        java.util.List<TaskEntity> existingTasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        java.util.Set<UUID> existingIds = new java.util.HashSet<>();
        for (TaskEntity t : existingTasks) {
            existingIds.add(t.getId());
        }

        // 2. Fetch pending wishlists
        java.util.List<com.eneik.production.models.persistence.WishlistEntity> pendingItems =
                wishlistRepository.findByProjectIdAndStatus(project.getId(), com.eneik.production.models.persistence.WishlistStatus.pending);

        int processedCount = 0;
        for (com.eneik.production.models.persistence.WishlistEntity wishlist : pendingItems) {
            try {
                // Reload from repository to ensure we have the latest compiled data
                wishlist = wishlistRepository.findById(wishlist.getId()).orElse(wishlist);

                if (compileWishlistIntoAtomicSlices(project, wishlist)) {
                    processedCount++;
                    log.info("ProjectFlowService: Synchronously compiled wishlist {} into atomic task slices", wishlist.getId());
                    continue;
                } else if (wishlist.getCompiledByRole() == null) {
                    java.util.Map<String, Object> aiMeta = new java.util.HashMap<>();
                    if (mlPredictionServiceClient != null) {
                        try {
                            aiMeta = mlPredictionServiceClient.generateTaskMetadata(wishlist.getContent());
                        } catch (Exception e) {
                            log.error("Failed to generate AI metadata for wishlist {}: {}", wishlist.getId(), e.getMessage());
                            // Skip this wishlist item so it can be retried later
                            continue;
                        }
                    }
                    String jtbd = aiMeta != null && aiMeta.containsKey("jtbd") ? aiMeta.get("jtbd").toString() : fallbackTaskSlice(wishlist.getContent()).jtbd();
                    String ac = aiMeta != null && aiMeta.containsKey("acceptanceCriteria") ? aiMeta.get("acceptanceCriteria").toString() : fallbackTaskSlice(wishlist.getContent()).acceptanceCriteria();

                    technicalLeadCompiler.compile(
                        wishlist.getId(),
                        "BARCAN-TAG-09",
                        jtbd,
                        com.eneik.production.models.persistence.LeanValue.essential,
                        "TOC-CONSTRAINT-DECOMPOSITION",
                        "Defect Rate <= 5%",
                        "Compiled from English JTBD slice by Eneik Management System. Role refusal criteria: BARCAN-TAG-09.",
                        ac
                    );

                    // Re-fetch after compile to get the updated entity
                    wishlist = wishlistRepository.findById(wishlist.getId()).orElse(wishlist);
                }
                if (wishlist.getLeanValue() != com.eneik.production.models.persistence.LeanValue.waste) {
                    technicalLeadCompiler.createTaskFromWishlist(wishlist.getId());
                    processedCount++;
                    log.info("ProjectFlowService: Synchronously compiled wishlist {} into task", wishlist.getId());
                }
            } catch (Exception e) {
                log.error("Failed to compile pending wishlist {} for project {}", wishlist.getId(), project.getId(), e);
            }
        }

        // 3. Find all newly created tasks
        java.util.List<TaskEntity> currentTasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        java.util.List<TaskShortDto> createdTasks = new java.util.ArrayList<>();
        for (TaskEntity t : currentTasks) {
            if (!existingIds.contains(t.getId())) {
                createdTasks.add(new TaskShortDto(t.getId(), t.getRole().getTag(), t.getDescription()));
            }
        }

        return new OrchestrationResultDto(
            project.getId(),
            processedCount,
            createdTasks,
            "Orchestrated " + processedCount + " wishlist items. " + createdTasks.size() + " tasks created."
        );
    }

    private void recordOrchestrationStartOrThrow(UUID projectId) {
        Instant now = Instant.now();
        ProjectGenerationStateEntity state = projectGenerationStateRepository.findById(projectId)
                .orElseGet(() -> {
                    ProjectGenerationStateEntity newState = new ProjectGenerationStateEntity();
                    newState.setProjectId(projectId);
                    return newState;
                });

        Instant last = state.getLastOrchestratedAt();
        if (last != null) {
            long elapsedSeconds = Duration.between(last, now).getSeconds();
            if (elapsedSeconds < ORCHESTRATION_COOLDOWN_SECONDS) {
                throw new OrchestrationCooldownException(ORCHESTRATION_COOLDOWN_SECONDS - elapsedSeconds);
            }
        }

        state.setLastOrchestratedAt(now);
        projectGenerationStateRepository.saveAndFlush(state);
    }

    private boolean compileWishlistIntoAtomicSlices(ProjectEntity project, WishlistEntity wishlist) {
        if (wishlist.getCompiledByRole() != null) {
            if (wishlist.getLeanValue() != LeanValue.waste) {
                technicalLeadCompiler.createTaskFromWishlist(wishlist.getId());
                return true;
            }
            return false;
        }

        java.util.List<MLPredictionServiceClient.TaskSliceMetadata> slices = resolveTaskSlices(wishlist);
        if (slices.isEmpty()) {
            return false;
        }

        if (wishlist.getSource() == WishlistSource.role_mismatch_followup) {
            MLPredictionServiceClient.TaskSliceMetadata slice = slices.get(0);
            String ownerRole = targetRoleForSlice(wishlist, slice);
            wishlist.setSourceRoleTag(ownerRole);
            wishlistRepository.save(wishlist);
            compileSliceMetadata(wishlist.getId(), slice, ownerRole);
            technicalLeadCompiler.createTaskFromWishlist(wishlist.getId());
            return true;
        }

        int index = 1;
        for (MLPredictionServiceClient.TaskSliceMetadata slice : slices) {
            if (slice.leanValue() == LeanValue.waste) {
                continue;
            }
            WishlistEntity sliceWishlist = new WishlistEntity();
            sliceWishlist.setProjectId(project.getId());
            sliceWishlist.setSource(wishlist.getSource());
            String ownerRole = targetRoleForSlice(wishlist, slice);
            sliceWishlist.setSourceRoleTag(ownerRole);
            sliceWishlist.setContent(internalSliceContent(wishlist, slice, index));
            sliceWishlist.setStatus(WishlistStatus.pending);
            sliceWishlist = wishlistRepository.save(sliceWishlist);
            compileSliceMetadata(sliceWishlist.getId(), slice, ownerRole);
            technicalLeadCompiler.createTaskFromWishlist(sliceWishlist.getId());
            index++;
        }

        wishlist.setStatus(WishlistStatus.converted_to_task);
        wishlistRepository.save(wishlist);
        return true;
    }

    private java.util.List<MLPredictionServiceClient.TaskSliceMetadata> resolveTaskSlices(WishlistEntity wishlist) {
        if (mlPredictionServiceClient == null) {
            return java.util.List.of(fallbackTaskSlice(wishlist.getContent()));
        }

        java.util.List<MLPredictionServiceClient.TaskSliceMetadata> slices = mlPredictionServiceClient.generateTaskSlices(wishlist.getContent());
        if (slices != null && !slices.isEmpty()) {
            return slices.stream().limit(6).toList();
        }

        java.util.Map<String, Object> aiMeta = mlPredictionServiceClient.generateTaskMetadata(wishlist.getContent());
        return java.util.List.of(legacyMetadataSlice(wishlist.getContent(), aiMeta));
    }

    private void compileSliceMetadata(UUID wishlistId, MLPredictionServiceClient.TaskSliceMetadata slice, String ownerRole) {
        technicalLeadCompiler.compile(
                wishlistId,
                ORCHESTRATOR_ROLE,
                defaultText(slice.jtbd(), fallbackTaskSlice("").jtbd()),
                slice.leanValue() != null ? slice.leanValue() : LeanValue.essential,
                defaultText(slice.tocConstraintRef(), "TOC-CONSTRAINT-DECOMPOSITION"),
                defaultText(slice.sixSigmaMetric(), "Escaped defects <= 5%"),
                compiledDod(ownerRole, slice),
                defaultText(slice.acceptanceCriteria(), fallbackTaskSlice("").acceptanceCriteria())
        );
    }

    private String compiledDod(String ownerRole, MLPredictionServiceClient.TaskSliceMetadata slice) {
        String roleSpecificReadiness = switch (ownerRole) {
            case "BARCAN-TAG-03" -> "UI/design readiness: follow docs/DESIGN_SYSTEM.md for layout, visual states, and interaction evidence.";
            case "BARCAN-TAG-11" -> "Frontend readiness: implement browser UI according to docs/DESIGN_SYSTEM.md and verify the user-visible interaction.";
            default -> "Role readiness: complete the smallest owner-role result without expanding scope.";
        };
        return "Compiled from English JTBD work item by Eneik Management System. Owner role: "
                + ownerRole + ". Role refusal criteria: " + ownerRole + ". Compiler role: BARCAN-TAG-09. Kano: "
                + defaultText(slice.kanoClass(), "Must-Be") + ". Cynefin: " + defaultText(slice.cynefinDomain(), "clear") + ". "
                + roleSpecificReadiness;
    }

    private String targetRoleForSlice(WishlistEntity parent, MLPredictionServiceClient.TaskSliceMetadata slice) {
        if (parent.getSource() == WishlistSource.role_mismatch_followup
                || parent.getSource() == WishlistSource.self_falsification
                || parent.getSource() == WishlistSource.chaotic_debt) {
            return normalizeRoleTag(parent.getSourceRoleTag(), slice);
        }
        return normalizeRoleTag(slice.roleTag(), slice);
    }

    private String normalizeRoleTag(String value, MLPredictionServiceClient.TaskSliceMetadata slice) {
        if (value != null && value.matches("BARCAN-TAG-(0[0-9]|1[0-1])")) {
            return value;
        }
        return inferRoleTag(slice);
    }

    private String inferRoleTag(MLPredictionServiceClient.TaskSliceMetadata slice) {
        String source = ((slice.title() != null ? slice.title() : "") + " "
                + (slice.jtbd() != null ? slice.jtbd() : "") + " "
                + (slice.acceptanceCriteria() != null ? slice.acceptanceCriteria() : ""));
        return inferRoleTag(source, slice.hasUi());
    }

    private String inferRoleTag(String sourceText, boolean hasUi) {
        String source = sourceText == null ? "" : sourceText.toLowerCase(Locale.ROOT);
        if (source.contains("merge") || source.contains("integration") || source.contains("repository hygiene")
                || source.contains("generated artifact") || source.contains("pr diff")) {
            return "BARCAN-TAG-00";
        }
        if (source.contains("architecture") || source.contains("mvc") || source.contains("microservice")
                || source.contains("service boundary") || source.contains("adr")) {
            return "BARCAN-TAG-01";
        }
        if (source.contains("security") || source.contains("auth") || source.contains("credential")
                || source.contains("permission") || source.contains("access-control") || source.contains("login")) {
            return "BARCAN-TAG-07";
        }
        if (source.contains("database") || source.contains("schema") || source.contains("migration")
                || source.contains("storage") || source.contains("csv") || source.contains("pdf")
                || source.contains("parse") || source.contains("upload")) {
            return "BARCAN-TAG-08";
        }
        if (source.contains("ai") || source.contains("llm") || source.contains("model")
                || source.contains("prompt") || source.contains("rag") || source.contains("embedding")) {
            return "BARCAN-TAG-04";
        }
        if (source.contains("legal") || source.contains("tax law") || source.contains("compliance")
                || source.contains("regulatory") || source.contains("disclaimer")) {
            return "BARCAN-TAG-10";
        }
        if (source.contains("test") || source.contains("qa") || source.contains("verify")
                || source.contains("verification") || source.contains("e2e")) {
            return "BARCAN-TAG-06";
        }
        if (source.contains("docker") || source.contains("deploy") || source.contains("ci")
                || source.contains("build") || source.contains("pipeline")) {
            return "BARCAN-TAG-05";
        }
        if (source.contains("design") || source.contains("mockup") || source.contains("wireframe")
                || source.contains("ux")) {
            return "BARCAN-TAG-03";
        }
        if (hasUi || source.contains("frontend") || source.contains("svelte") || source.contains("browser")
                || source.contains("screen") || source.contains("page") || source.contains("button")
                || source.contains("form") || source.contains("ui")) {
            return "BARCAN-TAG-11";
        }
        return "BARCAN-TAG-02";
    }

    private String internalSliceContent(WishlistEntity parent, MLPredictionServiceClient.TaskSliceMetadata slice, int index) {
        String uiMarker = (slice.hasUi()
                || looksLikeUi(slice.title() + " " + slice.jtbd() + " " + slice.acceptanceCriteria())) ? "UI " : "";
        return "Internal " + uiMarker + "work item " + index + " (" + targetRoleForSlice(parent, slice) + ") from wishlist " + parent.getId()
                + ": " + safeSliceTitle(slice.title());
    }

    private String safeSliceTitle(String title) {
        if (title == null || title.isBlank() || containsNonEnglishSignal(title)) {
            return "client-requested capability";
        }
        String compact = title.replaceAll("\\s+", " ").trim();
        if (compact.length() <= 90) {
            return compact;
        }
        return compact.substring(0, 87) + "...";
    }

    private MLPredictionServiceClient.TaskSliceMetadata legacyMetadataSlice(String wishlistContent, java.util.Map<String, Object> aiMeta) {
        String jtbd = aiMeta != null && aiMeta.containsKey("jtbd")
                ? String.valueOf(aiMeta.get("jtbd"))
                : fallbackTaskSlice(wishlistContent).jtbd();
        String ac = aiMeta != null && aiMeta.containsKey("acceptanceCriteria")
                ? String.valueOf(aiMeta.get("acceptanceCriteria"))
                : fallbackTaskSlice(wishlistContent).acceptanceCriteria();
        return new MLPredictionServiceClient.TaskSliceMetadata(
                featureLabel(wishlistContent),
                jtbd,
                ac,
                inferRoleTag(featureLabel(wishlistContent) + " " + jtbd + " " + ac, looksLikeUi(wishlistContent)),
                LeanValue.essential,
                "Must-Be",
                looksLikeUi(wishlistContent) ? "complicated" : "clear",
                "TOC-CONSTRAINT-DECOMPOSITION",
                "Escaped defects <= 5%",
                looksLikeUi(wishlistContent)
        );
    }

    private MLPredictionServiceClient.TaskSliceMetadata fallbackTaskSlice(String wishlistContent) {
        String label = featureLabel(wishlistContent);
        return new MLPredictionServiceClient.TaskSliceMetadata(
                label,
                "When I use the " + label + " slice, I want one small verifiable capability completed, so project progress can be validated without a long Jules session.",
                "Given this slice is implemented, When the primary happy path is exercised, Then it completes without client-side or server-side errors.\n"
                        + "Given invalid or missing input is submitted, When validation runs, Then the system rejects the request without persisting invalid data.\n"
                        + "Given the PR is ready, When verification runs, Then the relevant command passes and no generated artifacts are committed.",
                inferRoleTag(label + " " + wishlistContent, looksLikeUi(wishlistContent)),
                LeanValue.essential,
                "Must-Be",
                "clear",
                "TOC-CONSTRAINT-DECOMPOSITION",
                "Escaped defects <= 5%",
                looksLikeUi(wishlistContent)
        );
    }

    private String featureLabel(String wishlistContent) {
        if (wishlistContent == null || wishlistContent.isBlank() || containsNonEnglishSignal(wishlistContent)) {
            return "client-requested capability";
        }
        java.util.Set<String> stopWords = java.util.Set.of(
                "the", "and", "for", "with", "that", "this", "from", "into", "need", "want",
                "make", "create", "build", "add", "implement", "please", "system", "feature"
        );
        java.util.List<String> words = new java.util.ArrayList<>();
        for (String word : wishlistContent.toLowerCase(java.util.Locale.ROOT).split("[^a-z0-9]+")) {
            if (word.length() >= 3 && !stopWords.contains(word)) {
                words.add(word);
            }
            if (words.size() == 4) {
                break;
            }
        }
        return words.isEmpty() ? "client-requested capability" : String.join(" ", words);
    }

    private boolean looksLikeUi(String value) {
        String lower = value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("ui") || lower.contains("ux") || lower.contains("frontend")
                || lower.contains("screen") || lower.contains("page") || lower.contains("form")
                || lower.contains("button") || lower.contains("browser") || lower.contains("svelte")
                || lower.contains("design") || lower.contains("admin") || lower.contains("panel")
                || lower.contains("portal") || lower.contains("dashboard") || lower.contains("public");
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private boolean containsNonEnglishSignal(String value) {
        if (value == null) {
            return false;
        }
        return value.matches(".*[\\p{IsCyrillic}].*")
                || value.contains("\u00d0")
                || value.contains("\u00d1");
    }

    @Transactional
    public void dispatchQueuedTasks(UUID projectId) {
        ProjectEntity project = requireActiveProject(projectId);
        List<TaskEntity> queuedTasks = taskRepository.findByProjectIdAndStatusOrderByPriorityDescCreatedAtAsc(project.getId(), TaskStatus.queued);

        for (TaskEntity task : queuedTasks) {
            Optional<JulesSessionEntity> existingSession = findActiveJulesSession(task.getId());
            if (existingSession.isPresent() && existingSession.get().getAccountId() != null) {
                JulesSessionEntity session = existingSession.get();
                try {
                    claimService.claimSpecificTask(task.getId(), session.getAccountId());
                    TaskEntity savedTask = taskRepository.findById(task.getId()).orElse(task);
                    savedTask.setJulesSessionName(session.getExternalSessionId());
                    savedTask.setJulesDispatchStatus("already dispatched, skipping duplicate");
                    taskRepository.save(savedTask);
                    log.info("Reconnected queued task {} of project {} to existing Jules session {}",
                            savedTask.getId(), project.getName(), session.getExternalSessionId());
                } catch (Exception e) {
                    log.error("Failed to reconnect queued task {} to existing Jules session {}: {}",
                            task.getId(), session.getId(), e.getMessage(), e);
                }
                continue;
            }

            String roleTag = task.getRole().getTag();
            Optional<AccountEntity> accountOpt = accountRepository.lockNextJulesAccountWithCapacity(
                    project.getId(),
                    roleTag,
                    maxConcurrentJulesSessionsPerAccount
            );
            if (accountOpt.isPresent()) {
                AccountEntity account = accountOpt.get();
                try {
                    claimService.claimSpecificTask(task.getId(), account.getId());

                    // Refresh task state after claim
                    TaskEntity savedTask = taskRepository.findById(task.getId()).orElse(task);

                    JulesDispatchResult dispatch = julesDispatchService.dispatch(savedTask, account.getId());
                    savedTask.setJulesSessionName(dispatch.sessionName());
                    savedTask.setJulesDispatchStatus(dispatch.reason());
                    taskRepository.save(savedTask);
                    log.info("Dispatched queued task {} of project {} to account {}", savedTask.getId(), project.getName(), account.getName());
                } catch (Exception e) {
                    log.error("Failed to claim/dispatch queued task {} to account {}: {}", task.getId(), account.getName(), e.getMessage(), e);
                }
            } else {
                task.setJulesDispatchStatus("No free Jules shared session slot available for role context " + roleTag);
                taskRepository.save(task);
            }
        }
    }

    private Optional<JulesSessionEntity> findActiveJulesSession(UUID taskId) {
        return julesSessionRepository.findByTaskId(taskId).stream()
                .filter(session -> session.getExternalSessionId() != null)
                .filter(session -> !"skipped".equals(session.getExternalSessionId()))
                .filter(session -> {
                    String status = session.getStatus();
                    return "queued".equals(status)
                            || "running".equals(status)
                            || "revising".equals(status)
                            || "stuck".equals(status);
                })
                .findFirst();
    }

    @Transactional
    public void dispatchReviewTasks(UUID projectId) {
        ProjectEntity project = requireActiveProject(projectId);
        List<TaskEntity> reviewTasks = taskRepository.findByProjectIdAndStatusOrderByPriorityDescCreatedAtAsc(project.getId(), TaskStatus.review);

        for (TaskEntity task : reviewTasks) {
            List<JulesSessionEntity> sessions = julesSessionRepository.findByTaskId(task.getId());
            boolean hasActiveReviewSession = sessions.stream()
                    .anyMatch(s -> {
                        String status = s.getStatus();
                        return "running".equals(status) || "queued".equals(status);
                    });

            if (!hasActiveReviewSession) {
                // Find any idle capable account to act as reviewer
                String roleTag = task.getRole().getTag();
                Optional<AccountEntity> accountOpt = accountRepository.lockNextJulesAccountWithCapacity(
                        project.getId(),
                        roleTag,
                        maxConcurrentJulesSessionsPerAccount
                );
                if (accountOpt.isPresent()) {
                    AccountEntity account = accountOpt.get();
                    try {
                        julesDispatchService.dispatch(task, account.getId(), "REVIEWER");
                        log.info("Auto-dispatched reviewer for task {} of project {} to account {}", task.getId(), project.getName(), account.getName());
                    } catch (Exception e) {
                        log.error("Failed to auto-dispatch reviewer for task {}: {}", task.getId(), e.getMessage());
                    }
                }
            }
        }
    }

    @Transactional
    public ProjectDto acceptProject(UUID projectId) {
        ProjectEntity project = requireProject(projectId);

        // 1. Stop generation
        technicalLeadCompiler.stopGeneration(projectId);

        // 2. Linear sync (check for close issues method)
        // Based on exploration, LinearProjectFactoryClient doesn't have it.
        // We'll log the skip as requested.
        log.info("linear sync not available, skipped");

        // 3. Save snapshot
        ClientDeliveryDto snapshot = clientDeliveryService.getDelivery(projectId);
        saveFinalReport(projectId, snapshot);

        project.setStatus(ProjectStatus.accepted);
        project.setAcceptedAt(Instant.now());
        return toProjectDto(projectRepository.save(project));
    }

    private void saveFinalReport(UUID projectId, ClientDeliveryDto snapshot) {
        try {
            ProjectFinalReportEntity report = new ProjectFinalReportEntity();
            report.setProjectId(projectId);
            report.setTotalTasksCompleted(snapshot.delivered().size());
            report.setTotalWishlistItems(snapshot.requested().size());
            report.setReportContent(objectMapper.valueToTree(snapshot));
            projectFinalReportRepository.save(report);
        } catch (Exception e) {
            log.error("Failed to save final report snapshot for project {}", projectId, e);
        }
    }

    @Transactional(readOnly = true)
    public ProjectDashboardDto dashboard(UUID projectId) {
        ProjectEntity project = requireProject(projectId);
        List<AgentDashboardDto> agents = accountRepository.findByProjectIdOrderByNameAsc(projectId).stream()
                .map(account -> {
                    ClaimEntity activeClaim = claimRepository
                            .findByAccountIdAndTaskProjectIdAndReleasedAtIsNull(account.getId(), projectId)
                            .orElse(null);
                    return new AgentDashboardDto(
                            account.getId(),
                            account.getName(),
                            account.getStatus(),
                            activeClaim != null ? activeClaim.getRole().getTag() : null,
                            activeClaim != null ? activeClaim.getTask().getDescription() : null,
                            activeClaim != null ? activeClaim.getClaimedAt() : null,
                            activeClaim != null ? activeClaim.getLeaseExpiresAt() : null,
                            account.getLastHeartbeat()
                    );
                })
                .toList();

        QueueDashboardDto queue = new QueueDashboardDto(
                taskRepository.queuedGroupedByProjectAndTag(projectId),
                taskRepository.countByProjectIdAndStatus(projectId, TaskStatus.queued)
        );
        PipelineDashboardDto pipeline = new PipelineDashboardDto(
                taskRepository.countByProjectIdAndStatus(projectId, TaskStatus.queued),
                taskRepository.countByProjectIdAndStatus(projectId, TaskStatus.claimed),
                taskRepository.countByProjectIdAndStatus(projectId, TaskStatus.in_progress),
                taskRepository.countByProjectIdAndStatus(projectId, TaskStatus.review),
                taskRepository.countByProjectIdAndStatus(projectId, TaskStatus.done),
                taskRepository.countByProjectIdAndStatus(projectId, TaskStatus.failed)
        );
        List<com.eneik.production.dto.WishlistResponseDto> wishlist = wishlistRepository.findByProjectId(projectId)
                .stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(w -> new com.eneik.production.dto.WishlistResponseDto(w.getId(), w.getProjectId(), w.getSource(), w.getSourceRoleTag(), w.getContent(), w.getStatus(), w.getCreatedAt()))
                .toList();
        List<TaskDto> tasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(task -> new TaskDto(
                        task.getId(),
                        task.getRole().getTag(),
                        task.getDescription(),
                        task.getStatus(),
                        task.getPayload(),
                        task.getJulesSessionName(),
                        task.getJulesDispatchStatus()
                ))
                .toList();

        return new ProjectDashboardDto(
                toProjectDto(project),
                agents.size(),
                wishlistRepository.findByProjectIdAndStatus(projectId, com.eneik.production.models.persistence.WishlistStatus.pending).size(),
                queue,
                pipeline,
                agents,
                wishlist,
                tasks
        );
    }

    @Transactional(readOnly = true)
    public List<ProjectDto> listProjects() {
        return projectRepository.findAll().stream().map(this::toProjectDto).toList();
    }

    @Transactional
    public ProjectDto refreshCollaborators(UUID projectId) {
        ProjectEntity project = requireProject(projectId);
        String reportJson = project.getFactoryReport();
        if (reportJson == null || reportJson.isBlank()) {
            return toProjectDto(project);
        }

        try {
            ObjectNode report = (ObjectNode) objectMapper.readTree(reportJson);
            var collaboratorsNode = report.get("collaborators");
            if (collaboratorsNode != null && collaboratorsNode.isArray()) {
                String token = settingsService.effectiveValue("github_token");

                List<CollaboratorProvisioningResult> newResults = new ArrayList<>();
                for (var node : collaboratorsNode) {
                    String username = node.get("username").asText();
                    CollaboratorProvisioningResult result = gitHubProjectFactoryClient.inviteCollaborator(
                            project.getRepositoryName(), username, token);
                    newResults.add(result);
                }

                report.remove("collaborators");
                var newCollaboratorsNode = report.putArray("collaborators");
                for (CollaboratorProvisioningResult res : newResults) {
                    ObjectNode item = newCollaboratorsNode.addObject();
                    item.put("username", res.username());
                    item.put("status", res.status());
                    item.put("githubStatus", res.githubStatus());
                    item.put("detail", res.detail());
                }
                project.setFactoryReport(report.toString());
                projectRepository.save(project);
            }
        } catch (Exception e) {
            log.error("Failed to refresh collaborators for project {}", projectId, e);
        }

        return toProjectDto(project);
    }

    public ProjectEntity requireActiveProject(UUID projectId) {
        ProjectEntity project = requireProject(projectId);
        if (project.getStatus() == ProjectStatus.accepted) {
            throw new IllegalStateException("Project is accepted and cannot receive new work");
        }
        if (project.getStatus() == ProjectStatus.analyzing) {
            throw new IllegalStateException("Project is analyzing and cannot receive new work");
        }
        return project;
    }

    public ProjectEntity requireProject(UUID projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectId));
    }


    private String getRoleSpecificAssignment(String wishlistText, String roleTag) {
        boolean isChess = wishlistText.toLowerCase(Locale.ROOT).contains("шахмат") || 
                          wishlistText.toLowerCase(Locale.ROOT).contains("chess");
        
        if (isChess) {
            switch (roleTag) {
                case "BARCAN-TAG-03":
                    return "Спроектировать 3D-сцену шахматной доски, включая материалы фигур, параметры камеры и освещения в едином визуальном стиле.";
                case "BARCAN-TAG-02":
                    return "Реализовать логику шахматных правил и алгоритм ИИ с 3 уровнями сложности (через глубину поиска или оценочную функцию).";
                case "BARCAN-TAG-11":
                    return "Подключить 3D-визуализацию к логике игры: обработка кликов по фигурам, подсветка доступных ходов, отправка хода в движок.";
                case "BARCAN-TAG-06":
                    return "Разработать автоматизированный E2E тест на сквозной игровой процесс против компьютера.";
            }
        }
        
        switch (roleTag) {
            case "BARCAN-TAG-03":
                return "Спроектировать пользовательский интерфейс, макеты экранов и дизайн-элементы для функции: \"" + wishlistText + "\" согласно docs/DESIGN_SYSTEM.md.";
            case "BARCAN-TAG-02":
                return "Разработать серверную бизнес-логику, API эндпоинты, миграции базы данных и юнит-тесты для функции: \"" + wishlistText + "\".";
            case "BARCAN-TAG-11":
                return "Реализовать фронтенд-компоненты на Svelte, интерактивное взаимодействие и интеграцию с API для функции: \"" + wishlistText + "\" согласно docs/DESIGN_SYSTEM.md.";
            case "BARCAN-TAG-06":
                return "Написать автоматизированные E2E и интеграционные тесты для верификации функции: \"" + wishlistText + "\".";
            case "BARCAN-TAG-05":
                return "Настроить CI/CD пайплайн, Dockerfile, конфигурации сборки и окружения для деплоя функции: \"" + wishlistText + "\".";
            default:
                return "Реализовать технические требования для роли " + roleTag + " по пожеланию клиента: \"" + wishlistText + "\".";
        }
    }



    private boolean containsAny(String text, String... needles) {
        return Arrays.stream(needles).anyMatch(text::contains);
    }

    private String uniqueSlug(String name) {
        String base = Normalizer.normalize(name.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (base.isBlank()) {
            base = "project";
        }
        String slug = base;
        int suffix = 2;
        while (projectRepository.existsBySlug(slug)) {
            slug = base + "-" + suffix++;
        }
        return slug;
    }

    private ProjectDto toProjectDto(ProjectEntity project) {
        String statusLabel = project.getStatus().name().toUpperCase();
        String uiColorToken = switch (project.getStatus()) {
            case active -> "text-success";
            case frozen -> "text-warning";
            case accepted -> "text-primary";
            case archived -> "text-secondary";
            default -> "text-neutral-500";
        };

        List<CollaboratorDto> collaborators = new ArrayList<>();
        if (project.getFactoryReport() != null) {
            try {
                ObjectNode report = (ObjectNode) objectMapper.readTree(project.getFactoryReport());
                if (report.has("collaborators")) {
                    for (JsonNode node : report.get("collaborators")) {
                        String status = node.get("status").asText();
                        String label = switch (status) {
                            case "invitation_sent" -> "Invitation sent";
                            case "already_has_access" -> "Collaborator";
                            case "validation_failed_or_pending" -> "Pending or validation warning";
                            case "not_found" -> "GitHub user not found";
                            default -> status;
                        };
                        String tone = switch (status) {
                            case "invitation_sent", "already_has_access" -> "idle";
                            case "validation_failed_or_pending" -> "offline";
                            default -> "busy";
                        };
                        collaborators.add(new CollaboratorDto(
                                node.get("username").asText(),
                                status,
                                node.get("githubStatus").asText(),
                                node.get("detail").asText(),
                                label,
                                tone
                        ));
                    }
                }
            } catch (Exception e) {
                log.error("Failed to parse factory report for project {}", project.getId(), e);
            }
        }

        return new ProjectDto(
                project.getId(),
                project.getName(),
                project.getSlug(),
                project.getRepositoryName(),
                project.getRepositoryUrl(),
                project.getRepoUrl(),
                project.getLinearProjectKey(),
                project.getGithubRepositoryStatus(),
                project.getGithubRepositoryId(),
                project.getLinearProjectStatus(),
                project.getLinearProjectId(),
                project.getWorkspacePath(),
                project.getFactoryStatus(),
                project.getFactoryReport(),
                project.getStatus(),
                project.getOnboardingMode(),
                project.getCreatedAt(),
                project.getAcceptedAt(),
                accountRepository.findByEnabledTrueAndProjectIsNullAndGithubUsernameIsNotNullOrderByNameAsc().size(),
                statusLabel,
                uiColorToken,
                collaborators
        );
    }

}
