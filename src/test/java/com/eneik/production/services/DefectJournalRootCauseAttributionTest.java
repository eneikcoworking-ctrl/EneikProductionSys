package com.eneik.production.services;

import com.eneik.production.kaizen.service.DefectJournalService;
import com.eneik.production.models.persistence.FeatureEntity;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.PrReviewEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.FeatureRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.PrReviewRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.advice.RoleAdviceLoopService;
import com.eneik.production.services.dashboard.ProjectOperationalContextService;
import com.eneik.production.services.github.GitHubApiBudgetService;
import com.eneik.production.services.github.GitHubPullRequestService;
import com.eneik.production.services.jules.JulesDispatchService;
import com.eneik.production.services.monitor.SystemProgressTracker;
import com.eneik.production.services.quality.ProcessControlService;
import com.eneik.production.services.settings.SystemSettingsService;
import com.eneik.production.services.video.VideoAssetService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Screen for Prescription 45 (DefectJournalEntity.rootCausePatternId attribution · INUS_FACTOR_CHECK D007):
 * Verifies that AutoMergeService poka-yoke rejections and ProjectFlowService lifecycle & review deadlock defects
 * attribute their root cause to documented Charter invariant patterns rather than passing null, eliminating
 * zero-attribution in Pareto analysis.
 */
class DefectJournalRootCauseAttributionTest {

    @Test
    @DisplayName("AutoMergeService poka-yoke records Charter Pattern #6 for Quinean core violation")
    void autoMergePokaYokeRecordsPattern6ForQuineanCoreViolation() throws Exception {
        var prReviews = mock(PrReviewRepository.class);
        var sessions = mock(JulesSessionRepository.class);
        var tasks = mock(TaskRepository.class);
        var settings = mock(SystemSettingsService.class);
        var gitHubPullRequestService = mock(GitHubPullRequestService.class);
        var defectJournal = mock(DefectJournalService.class);
        var features = mock(FeatureRepository.class);

        when(settings.effectiveValue("github_token")).thenReturn("ghp_test_token");

        AutoMergeService service = createAutoMergeService(prReviews, sessions, tasks, settings,
                gitHubPullRequestService, defectJournal, features);

        AutoMergeService spyService = spy(service);
        doReturn(List.of("src/main/resources/db/migration/V99__core_schema.sql"))
                .when(spyService).fetchPrFiles(any(), any(), any(), any());

        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        UUID featureId = UUID.randomUUID();

        RoleEntity role = new RoleEntity();
        role.setTag("BARCAN-TAG-11"); // UI periphery role

        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setProject(project);
        task.setFeatureId(featureId);
        task.setRole(role);
        task.setStatus(TaskStatus.review);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(UUID.randomUUID());
        session.setStatus("pr_opened");

        PrReviewEntity review = new PrReviewEntity();
        review.setId(UUID.randomUUID());

        var target = new AutoMergeService.PullRequestTarget("owner", "repo", "42", "https://github.com/owner/repo/pull/42");
        var pr = new GitHubPullRequestService.GitHubPullRequest("https://github.com/owner/repo/pull/42", 42,
                "UI update touching migration", "feature/ui-42", "agent-ui", false, "main", false, Instant.now());

        boolean rejected = spyService.rejectByFactoryPokaYoke(review, task, session, target, pr);

        assertTrue(rejected);
        assertEquals("core_violation", review.getCiStatus());

        // INUS check: rootCausePatternId is Charter Pattern #6 (Category errors at serialization boundaries)
        verify(defectJournal).recordDefect(
                eq(project.getId()),
                eq(featureId),
                eq(6),
                eq("high"),
                eq("epistemic_layer_invariant"),
                eq("AutoMergeService"),
                eq("core_violation"),
                anyString(),
                isNull()
        );
    }

    @Test
    @DisplayName("AutoMergeService poka-yoke records Charter Pattern #6 for contaminated PR")
    void autoMergePokaYokeRecordsPattern6ForContaminatedPr() throws Exception {
        var prReviews = mock(PrReviewRepository.class);
        var sessions = mock(JulesSessionRepository.class);
        var tasks = mock(TaskRepository.class);
        var settings = mock(SystemSettingsService.class);
        var gitHubPullRequestService = mock(GitHubPullRequestService.class);
        var defectJournal = mock(DefectJournalService.class);
        var features = mock(FeatureRepository.class);

        when(settings.effectiveValue("github_token")).thenReturn("ghp_test_token");

        AutoMergeService service = createAutoMergeService(prReviews, sessions, tasks, settings,
                gitHubPullRequestService, defectJournal, features);

        AutoMergeService spyService = spy(service);
        doReturn(List.of("_temp_submit_harness.sh"))
                .when(spyService).fetchPrFiles(any(), any(), any(), any());

        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());

        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setProject(project);
        task.setStatus(TaskStatus.review);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(UUID.randomUUID());
        session.setStatus("pr_opened");

        PrReviewEntity review = new PrReviewEntity();
        review.setId(UUID.randomUUID());

        var target = new AutoMergeService.PullRequestTarget("owner", "repo", "43", "https://github.com/owner/repo/pull/43");
        var pr = new GitHubPullRequestService.GitHubPullRequest("https://github.com/owner/repo/pull/43", 43,
                "feat: product feature with harness", "feature/harness-leak", "agent", false, "main", false, Instant.now());

        boolean rejected = spyService.rejectByFactoryPokaYoke(review, task, session, target, pr);

        assertTrue(rejected);
        assertEquals("contaminated", review.getCiStatus());

        verify(defectJournal).recordDefect(
                eq(project.getId()),
                isNull(),
                eq(6),
                eq("high"),
                eq("ontological_stratification"),
                eq("AutoMergeService"),
                eq("contaminated"),
                anyString(),
                isNull()
        );
    }

    @Test
    @DisplayName("AutoMergeService poka-yoke records Charter Pattern #6 for blocker PR title")
    void autoMergePokaYokeRecordsPattern6ForBlockerPrTitle() throws Exception {
        var prReviews = mock(PrReviewRepository.class);
        var sessions = mock(JulesSessionRepository.class);
        var tasks = mock(TaskRepository.class);
        var settings = mock(SystemSettingsService.class);
        var gitHubPullRequestService = mock(GitHubPullRequestService.class);
        var defectJournal = mock(DefectJournalService.class);
        var features = mock(FeatureRepository.class);

        when(settings.effectiveValue("github_token")).thenReturn("ghp_test_token");

        AutoMergeService service = createAutoMergeService(prReviews, sessions, tasks, settings,
                gitHubPullRequestService, defectJournal, features);

        AutoMergeService spyService = spy(service);
        doReturn(List.of("src/App.tsx"))
                .when(spyService).fetchPrFiles(any(), any(), any(), any());

        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());

        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setProject(project);
        task.setStatus(TaskStatus.review);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(UUID.randomUUID());
        session.setStatus("pr_opened");

        PrReviewEntity review = new PrReviewEntity();
        review.setId(UUID.randomUUID());

        var target = new AutoMergeService.PullRequestTarget("owner", "repo", "44", "https://github.com/owner/repo/pull/44");
        var pr = new GitHubPullRequestService.GitHubPullRequest("https://github.com/owner/repo/pull/44", 44,
                "Blocker: cannot proceed due to missing backend API", "feature/refusal", "agent", false, "main", false, Instant.now());

        boolean rejected = spyService.rejectByFactoryPokaYoke(review, task, session, target, pr);

        assertTrue(rejected);
        assertEquals("blocker_pr", review.getCiStatus());

        verify(defectJournal).recordDefect(
                eq(project.getId()),
                isNull(),
                eq(6),
                eq("high"),
                eq("ontological_stratification"),
                eq("AutoMergeService"),
                eq("blocker_pr"),
                anyString(),
                isNull()
        );
    }

    @Test
    @DisplayName("ProjectFlowService.cancelExternalWorkForProject records Charter Pattern #9 (Correlated entity consistency)")
    void projectFlowRetirementExhaustionRecordsPattern9() {
        var defectJournal = mock(DefectJournalService.class);
        var taskRepository = mock(TaskRepository.class);
        var projectRepository = mock(ProjectRepository.class);
        var julesSessionRepository = mock(JulesSessionRepository.class);
        var julesDispatchService = mock(JulesDispatchService.class);

        ProjectFlowService service = createProjectFlowService(
                taskRepository, projectRepository, julesSessionRepository, julesDispatchService, defectJournal);

        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setRetireAttempts(3); // already at MAX_RETIRE_ATTEMPTS = 3

        service.cancelExternalWorkForProject(project, "Retire exhaustion check");

        assertTrue(project.isRetireExhausted());
        // INUS check: rootCausePatternId is Charter Pattern #9 (Correlated entity consistency)
        verify(defectJournal).recordDefect(
                eq(project.getId()),
                isNull(),
                eq(9),
                eq("CRITICAL"),
                eq("LIFECYCLE"),
                eq("ProjectFlowService"),
                eq("RETIRE_BUDGET_EXHAUSTED"),
                anyString(),
                eq(3.0)
        );
    }

    @Test
    @DisplayName("ProjectFlowService.recordReviewFallbackDeadlockDefect records Charter Pattern #7 and preserves featureId")
    void reviewFallbackDeadlockRecordsPattern7AndFeatureId() {
        var defectJournal = mock(DefectJournalService.class);
        ProjectFlowService service = createProjectFlowService(
                mock(TaskRepository.class), mock(ProjectRepository.class),
                mock(JulesSessionRepository.class), mock(JulesDispatchService.class), defectJournal);

        UUID projectId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setFeatureId(featureId);

        service.recordReviewFallbackDeadlockDefect(projectId, task, "https://github.com/org/repo/pull/42", "hash123", 3);

        // INUS check: rootCausePatternId is Charter Pattern #7 (Monotonic watermarks against infinite loops)
        verify(defectJournal).recordDefect(
                eq(projectId),
                eq(featureId),
                eq(7),
                eq("HIGH"),
                eq("PERCEPTION_ACTION_LOOP"),
                eq("JulesDispatchService"),
                eq("REVIEW_FALLBACK_DEADLOCK"),
                anyString(),
                eq(3.0)
        );
    }

    private ProjectFlowService createProjectFlowService(
            TaskRepository taskRepository,
            ProjectRepository projectRepository,
            JulesSessionRepository julesSessionRepository,
            JulesDispatchService julesDispatchService,
            DefectJournalService defectJournalService) {
        ProjectFlowService service = new ProjectFlowService(
                projectRepository,
                mock(com.eneik.production.repositories.WishlistRepository.class),
                mock(com.eneik.production.repositories.AccountRepository.class),
                taskRepository,
                mock(com.eneik.production.repositories.ClaimRepository.class),
                mock(com.eneik.production.repositories.RoleRepository.class),
                mock(ClaimService.class),
                julesDispatchService,
                mock(com.eneik.production.services.projectfactory.ProjectFactoryService.class),
                mock(com.eneik.production.services.projectfactory.GitHubProjectFactoryClient.class),
                mock(SystemSettingsService.class),
                null,
                null,
                mock(com.eneik.production.services.compiler.TechnicalLeadCompiler.class),
                mock(com.eneik.production.services.dashboard.ClientDeliveryService.class),
                mock(com.eneik.production.repositories.ProjectFinalReportRepository.class),
                julesSessionRepository,
                mock(com.eneik.production.repositories.JulesActivityResponseRepository.class),
                mock(com.eneik.production.repositories.ProjectGenerationStateRepository.class),
                new ObjectMapper(),
                "eneik-test-org",
                mock(com.eneik.production.services.onboarding.OnboardingAuditService.class),
                mock(com.eneik.production.services.dashboard.EmsMetricsService.class),
                mock(ProjectOperationalContextService.class),
                mock(com.eneik.production.services.design.DesignAssetService.class),
                mock(GitHubPullRequestService.class),
                mock(ClientDeliverableReadinessService.class),
                mock(com.eneik.production.services.FeatureService.class),
                mock(PersistentWorkerSessionService.class),
                mock(SelfFalsificationEpicMatcher.class),
                mock(com.eneik.production.services.operational.OperationalPolicyService.class),
                mock(com.eneik.production.repositories.ProjectFileClaimRepository.class),
                mock(RequirementGroundingService.class),
                mock(GeminiContextService.class),
                mock(com.eneik.production.repositories.TaskConflictRepository.class),
                mock(com.eneik.production.repositories.LinearIssueMetadataRepository.class),
                mock(FeatureRepository.class),
                mock(com.eneik.production.repositories.FeatureThreadRepository.class),
                mock(PlannedWorkRecoveryService.class),
                null);
        service.setDefectJournalService(defectJournalService);
        return service;
    }

    @Test
    @DisplayName("ProcessControlService catalogues all 16 Charter invariant patterns from Layer 0 taxonomy")
    void processControlServiceCataloguesAll16CharterPatterns() {
        assertEquals(16, ProcessControlService.CHARTER_PATTERN_NAMES.size());
        for (int i = 1; i <= 16; i++) {
            assertTrue(ProcessControlService.CHARTER_PATTERN_NAMES.containsKey(i),
                    "CHARTER_PATTERN_NAMES must contain key " + i);
            String name = ProcessControlService.CHARTER_PATTERN_NAMES.get(i);
            assertNotNull(name);
            assertFalse(name.isBlank(), "Pattern " + i + " name must not be blank");
        }
        assertEquals("CAS vs read-then-write", ProcessControlService.CHARTER_PATTERN_NAMES.get(1));
        assertEquals("Category errors at serialization boundaries", ProcessControlService.CHARTER_PATTERN_NAMES.get(6));
        assertEquals("Monotonic watermarks", ProcessControlService.CHARTER_PATTERN_NAMES.get(7));
        assertEquals("Exact denominator scoping", ProcessControlService.CHARTER_PATTERN_NAMES.get(8));
        assertEquals("Correlated entity consistency", ProcessControlService.CHARTER_PATTERN_NAMES.get(9));
        assertEquals("Rigid designation on duplicate entities", ProcessControlService.CHARTER_PATTERN_NAMES.get(13));
        assertEquals("Unified belief revision for external reality", ProcessControlService.CHARTER_PATTERN_NAMES.get(14));
        assertEquals("External capacity is revisable belief not constant", ProcessControlService.CHARTER_PATTERN_NAMES.get(15));
        assertEquals("Local extraction vs subscribed judgment", ProcessControlService.CHARTER_PATTERN_NAMES.get(16));
    }

    private AutoMergeService createAutoMergeService(
            PrReviewRepository prReviews, JulesSessionRepository sessions,
            TaskRepository tasks, SystemSettingsService settings,
            GitHubPullRequestService gitHubPullRequestService,
            DefectJournalService defectJournal, FeatureRepository features) throws Exception {

        AutoMergeService service = new AutoMergeService(
                prReviews, sessions, tasks, settings,
                new ObjectMapper(),
                mock(RoleAdviceLoopService.class),
                mock(com.eneik.production.repositories.TaskConflictRepository.class),
                mock(JulesDispatchService.class),
                mock(RoleCapabilityLoader.class),
                mock(com.eneik.production.repositories.WishlistRepository.class),
                mock(MLPredictionServiceClient.class),
                gitHubPullRequestService,
                new GitHubApiBudgetService(),
                mock(VideoAssetService.class),
                mock(ProjectOperationalContextService.class),
                mock(SystemProgressTracker.class),
                new CodeChangeClassifier(),
                mock(com.eneik.production.repositories.FeatureThreadRepository.class),
                mock(ClaimService.class),
                mock(ProjectRepository.class),
                mock(ClientDeliverableReadinessService.class),
                mock(GeminiContextService.class),
                mock(ProjectFlowService.class),
                mock(com.eneik.production.repositories.EvidenceNodeRepository.class),
                mock(com.eneik.production.repositories.OperationalRealityFindingRepository.class));

        var defectField = AutoMergeService.class.getDeclaredField("defectJournalService");
        defectField.setAccessible(true);
        defectField.set(service, defectJournal);
        service.setFeatureRepository(features);
        return service;
    }
}
