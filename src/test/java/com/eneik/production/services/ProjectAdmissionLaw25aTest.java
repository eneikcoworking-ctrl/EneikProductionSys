package com.eneik.production.services;

import com.eneik.production.dto.ProjectDto;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.ClaimRepository;
import com.eneik.production.repositories.FeatureRepository;
import com.eneik.production.repositories.FeatureThreadRepository;
import com.eneik.production.repositories.JulesActivityResponseRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.LinearIssueMetadataRepository;
import com.eneik.production.repositories.ProjectFileClaimRepository;
import com.eneik.production.repositories.ProjectFinalReportRepository;
import com.eneik.production.repositories.ProjectGenerationStateRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.RoleRepository;
import com.eneik.production.repositories.TaskConflictRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.compiler.TechnicalLeadCompiler;
import com.eneik.production.services.dashboard.ClientDeliveryService;
import com.eneik.production.services.dashboard.EmsMetricsService;
import com.eneik.production.services.dashboard.ProjectOperationalContextService;
import com.eneik.production.services.design.DesignAssetService;
import com.eneik.production.services.github.GitHubPullRequestService;
import com.eneik.production.services.jules.JulesDispatchService;
import com.eneik.production.services.onboarding.OnboardingAuditService;
import com.eneik.production.services.operational.OperationalPolicyService;
import com.eneik.production.services.projectfactory.GitHubProjectFactoryClient;
import com.eneik.production.services.projectfactory.ProjectFactoryResult;
import com.eneik.production.services.projectfactory.ProjectFactoryService;
import com.eneik.production.services.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Screen for Law 25a (Clean Project Admission Law):
 * <ul>
 *   <li>{@code dom(admit) = { w : content(w) ≠ ∅ }}</li>
 *   <li>{@code admit ⊥ state(P_prev)}</li>
 *   <li>{@code admit ⊥ availability(Jules, GitHub)}</li>
 *   <li>{@code effects(admit) ⊆ factory local data}</li>
 * </ul>
 */
class ProjectAdmissionLaw25aTest {

    private ProjectRepository projectRepository;
    private WishlistRepository wishlistRepository;
    private TaskRepository taskRepository;
    private ClaimRepository claimRepository;
    private ClaimService claimService;
    private JulesDispatchService julesDispatchService;
    private ProjectFactoryService projectFactoryService;
    private GitHubProjectFactoryClient gitHubProjectFactoryClient;
    private GitHubPullRequestService gitHubPullRequestService;
    private JulesSessionRepository julesSessionRepository;
    private ProjectGenerationStateRepository projectGenerationStateRepository;
    private OnboardingAuditService onboardingAuditService;

    private ProjectFlowService service;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        wishlistRepository = mock(WishlistRepository.class);
        taskRepository = mock(TaskRepository.class);
        claimRepository = mock(ClaimRepository.class);
        claimService = mock(ClaimService.class);
        julesDispatchService = mock(JulesDispatchService.class);
        projectFactoryService = mock(ProjectFactoryService.class);
        gitHubProjectFactoryClient = mock(GitHubProjectFactoryClient.class);
        gitHubPullRequestService = mock(GitHubPullRequestService.class);
        julesSessionRepository = mock(JulesSessionRepository.class);
        projectGenerationStateRepository = mock(ProjectGenerationStateRepository.class);
        onboardingAuditService = mock(OnboardingAuditService.class);

        when(projectRepository.save(any(ProjectEntity.class))).thenAnswer(inv -> {
            ProjectEntity p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(UUID.randomUUID());
            }
            return p;
        });

        when(wishlistRepository.save(any(WishlistEntity.class))).thenAnswer(inv -> {
            WishlistEntity w = inv.getArgument(0);
            if (w.getId() == null) {
                w.setId(UUID.randomUUID());
            }
            return w;
        });

        service = new ProjectFlowService(
                projectRepository,
                wishlistRepository,
                mock(AccountRepository.class),
                taskRepository,
                claimRepository,
                mock(RoleRepository.class),
                claimService,
                julesDispatchService,
                projectFactoryService,
                gitHubProjectFactoryClient,
                mock(SystemSettingsService.class),
                null,
                null,
                mock(TechnicalLeadCompiler.class),
                mock(ClientDeliveryService.class),
                mock(ProjectFinalReportRepository.class),
                julesSessionRepository,
                mock(JulesActivityResponseRepository.class),
                projectGenerationStateRepository,
                new ObjectMapper(),
                "eneik-org",
                onboardingAuditService,
                mock(EmsMetricsService.class),
                mock(ProjectOperationalContextService.class),
                mock(DesignAssetService.class),
                gitHubPullRequestService,
                mock(ClientDeliverableReadinessService.class),
                mock(FeatureService.class),
                mock(PersistentWorkerSessionService.class),
                mock(SelfFalsificationEpicMatcher.class),
                mock(OperationalPolicyService.class),
                mock(ProjectFileClaimRepository.class),
                mock(RequirementGroundingService.class),
                mock(GeminiContextService.class),
                mock(TaskConflictRepository.class),
                mock(LinearIssueMetadataRepository.class),
                mock(FeatureRepository.class),
                mock(FeatureThreadRepository.class),
                mock(PlannedWorkRecoveryService.class),
                null
        );
        org.springframework.test.util.ReflectionTestUtils.setField(service, "self", service);
    }

    @Test
    @DisplayName("Law 25a: admit succeeds for non-empty brief and persists Project and initial Wishlist locally")
    void admitSucceedsForNonEmptyBriefAndPersistsLocally() {
        String name = "New Greenfield Project";
        String brief = "Build an accounting dashboard with invoice export";

        ProjectEntity admitted = service.admitProject(name, "greenfield", brief);

        assertNotNull(admitted);
        assertNotNull(admitted.getId());
        assertEquals("New Greenfield Project", admitted.getName());
        assertEquals(ProjectStatus.active, admitted.getStatus());

        ArgumentCaptor<WishlistEntity> wishlistCaptor = ArgumentCaptor.forClass(WishlistEntity.class);
        verify(wishlistRepository).save(wishlistCaptor.capture());
        WishlistEntity savedWishlist = wishlistCaptor.getValue();
        assertEquals(admitted.getId(), savedWishlist.getProjectId());
        assertEquals(brief, savedWishlist.getContent());
        assertEquals(WishlistSource.client, savedWishlist.getSource());
        assertEquals(WishlistStatus.pending, savedWishlist.getStatus());

        // ZERO external calls performed during admit
        verifyNoInteractions(gitHubPullRequestService);
        verifyNoInteractions(julesDispatchService);
        verifyNoInteractions(projectFactoryService);
        verifyNoInteractions(gitHubProjectFactoryClient);
    }

    @Test
    @DisplayName("Law 25a: dom(admit) = { w : content(w) != empty } - empty or null brief is rejected")
    void emptyOrNullBriefIsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                service.admitProject("Project Name", "greenfield", null));

        assertThrows(IllegalArgumentException.class, () ->
                service.admitProject("Project Name", "greenfield", ""));

        assertThrows(IllegalArgumentException.class, () ->
                service.admitProject("Project Name", "greenfield", "   \t\n  "));

        assertThrows(IllegalArgumentException.class, () ->
                service.admitProject(null, "greenfield", "Valid brief"));

        assertThrows(IllegalArgumentException.class, () ->
                service.admitProject("", "greenfield", "Valid brief"));

        assertThrows(IllegalArgumentException.class, () ->
                service.admitProject("   ", "greenfield", "Valid brief"));
    }

    @Test
    @DisplayName("Law 25a: admit ⊥ availability(Jules, GitHub) - external provisioning failure does not abort admission")
    void externalProvisioningFailureDoesNotAbortAdmission() {
        String name = "Resilient Project";
        String brief = "A brief that survives external network outages";

        when(projectFactoryService.provision(any(ProjectEntity.class)))
                .thenThrow(new RuntimeException("GitHub API 503 Service Unavailable / Jules network timeout"));

        ProjectDto dto = service.createProject(name, "greenfield", brief);

        assertNotNull(dto);
        assertEquals("Resilient Project", dto.name());
        assertEquals("provision_failed", dto.factoryStatus());

        // Verify project and wishlist were saved locally despite external outage
        // 1st save is atomic local admission; 2nd save records provision_failed status
        verify(projectRepository, times(2)).save(any(ProjectEntity.class));
        verify(wishlistRepository).save(any(WishlistEntity.class));
    }

    @Test
    @DisplayName("Law 25a transitive whitelist guard: admitProject and its transitive helper call graph access ONLY allowed local data collaborators")
    void structuralDemarcationInvariant() throws IOException {
        Path sourcePath = Path.of("src/main/java/com/eneik/production/services/ProjectFlowService.java");
        assertTrue(Files.exists(sourcePath), "ProjectFlowService.java must exist");
        String src = Files.readString(sourcePath);

        // 1. Transactional annotation must be on admitProject
        int admitIdx = src.indexOf("public ProjectEntity admitProject(");
        assertTrue(admitIdx > 0, "admitProject method must exist");
        String precedingAdmit = src.substring(Math.max(0, admitIdx - 100), admitIdx);
        assertTrue(precedingAdmit.contains("@Transactional"),
                "admitProject must be @Transactional so local DB writes are atomic");

        // 2. Transactional annotation must NOT be on createProject
        int createIdx = src.indexOf("public ProjectDto createProject(");
        assertTrue(createIdx > 0, "createProject method must exist");
        String precedingCreate = src.substring(Math.max(0, createIdx - 100), createIdx);
        assertFalse(precedingCreate.contains("@Transactional"),
                "createProject must NOT be @Transactional - external network calls must not span the local DB transaction");

        // 3. Extract all method bodies across the transitive call graph of admitProject
        String admitBody = extractMethodBody(src, "public ProjectEntity admitProject(");
        String retireLocallyBody = extractMethodBody(src, "private void retirePriorActiveProjectsLocally(");
        String ensureGenStateBody = extractMethodBody(src, "private void ensureProjectGenerationState(");
        String uniqueSlugBody = extractMethodBody(src, "private String uniqueSlug(");

        String fullTransitiveCode = String.join("\n", admitBody, retireLocallyBody, ensureGenStateBody, uniqueSlugBody);

        // 4. Strict Whitelist of permitted instance fields / collaborators
        Set<String> permittedCollaborators = Set.of(
                "projectRepository",
                "wishlistRepository",
                "taskRepository",
                "claimRepository",
                "claimService",
                "projectGenerationStateRepository",
                "githubOrganization",
                "self",
                "log"
        );

        // 5. Inspect every declared field of ProjectFlowService.
        // If any field outside the whitelist appears in the transitive call graph, FAIL.
        Field[] fields = ProjectFlowService.class.getDeclaredFields();
        List<String> violations = new ArrayList<>();
        for (Field f : fields) {
            String fieldName = f.getName();
            if (!permittedCollaborators.contains(fieldName)) {
                Pattern fieldUsage = Pattern.compile("\\b" + Pattern.quote(fieldName) + "\\b");
                if (fieldUsage.matcher(fullTransitiveCode).find()) {
                    violations.add(fieldName + " (" + f.getType().getSimpleName() + ")");
                }
            }
        }

        assertEquals(List.of(), violations,
                "Law 25a Transitive Whitelist Violation: The admission transaction must strictly touch factory local data. "
                        + "The following non-whitelisted collaborators are accessed across the admitProject transitive call graph: "
                        + violations);

        // 6. Direct external network/process call ban
        List<String> rawExternalKeywords = List.of(
                "HttpClient", "RestTemplate", "WebClient", "ProcessBuilder", "Socket", "HttpURLConnection"
        );
        for (String kw : rawExternalKeywords) {
            assertFalse(fullTransitiveCode.contains(kw),
                    "Law 25a violation: direct external I/O primitive '" + kw + "' found in admission call graph");
        }
    }

    @Test
    @DisplayName("Law 25a generalized admission guard: admitReviewFallbackBatch isolates admission transaction from Jules network dispatch")
    void structuralDemarcationInvariantForReviewFallbackAdmission() throws IOException {
        Path sourcePath = Path.of("src/main/java/com/eneik/production/services/jules/JulesDispatchService.java");
        assertTrue(Files.exists(sourcePath), "JulesDispatchService.java must exist");
        String src = Files.readString(sourcePath);

        // 1. Transactional annotation must be on admitReviewFallbackBatch
        int admitIdx = src.indexOf("public ReviewFallbackAdmission admitReviewFallbackBatch(");
        assertTrue(admitIdx > 0, "admitReviewFallbackBatch method must exist");
        String precedingAdmit = src.substring(Math.max(0, admitIdx - 150), admitIdx);
        assertTrue(precedingAdmit.contains("@Transactional"),
                "admitReviewFallbackBatch must be @Transactional so lock and task creation are atomic");

        // 2. Transactional annotation must NOT be on admitAndDispatchReviewFallbackBatch
        int dispatchIdx = src.indexOf("public void admitAndDispatchReviewFallbackBatch(");
        assertTrue(dispatchIdx > 0, "admitAndDispatchReviewFallbackBatch method must exist");
        String precedingDispatch = src.substring(Math.max(0, dispatchIdx - 150), dispatchIdx);
        assertFalse(precedingDispatch.contains("@Transactional"),
                "admitAndDispatchReviewFallbackBatch must NOT be @Transactional - network dispatch to Jules must be outside the admission transaction");

        // 3. Extract method body of admitReviewFallbackBatch and verify no julesApiClient / httpClient references
        String admitBody = extractMethodBody(src, "public ReviewFallbackAdmission admitReviewFallbackBatch(");
        assertFalse(admitBody.contains("julesApiClient"), "admitReviewFallbackBatch must not invoke julesApiClient");
        assertFalse(admitBody.contains("httpClient"), "admitReviewFallbackBatch must not invoke httpClient");
        assertFalse(admitBody.contains("sendGitHub"), "admitReviewFallbackBatch must not invoke sendGitHub");
    }

    private static String extractMethodBody(String src, String methodSignature) {
        int sigIdx = src.indexOf(methodSignature);
        assertTrue(sigIdx >= 0, "Method signature not found: " + methodSignature);
        int openBrace = src.indexOf('{', sigIdx);
        assertTrue(openBrace >= 0, "Opening brace not found for: " + methodSignature);
        int depth = 1;
        int i = openBrace + 1;
        while (i < src.length() && depth > 0) {
            char c = src.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
            } else if (c == '"' || c == '\'') {
                char quote = c;
                i++;
                while (i < src.length()) {
                    char q = src.charAt(i);
                    if (q == '\\' && i + 1 < src.length()) {
                        i += 2;
                        continue;
                    }
                    if (q == quote) {
                        break;
                    }
                    i++;
                }
            } else if (c == '/' && i + 1 < src.length() && src.charAt(i + 1) == '/') {
                while (i < src.length() && src.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && i + 1 < src.length() && src.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < src.length() && !(src.charAt(i) == '*' && src.charAt(i + 1) == '/')) {
                    i++;
                }
                i = Math.min(i + 1, src.length() - 1);
            }
            i++;
        }
        return src.substring(openBrace, i);
    }
}

