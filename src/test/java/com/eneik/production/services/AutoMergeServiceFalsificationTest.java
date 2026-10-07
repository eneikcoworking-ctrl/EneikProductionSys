package com.eneik.production.services;

import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.PrReviewEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.EvidenceNodeRepository;
import com.eneik.production.repositories.FeatureThreadRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.OperationalRealityFindingRepository;
import com.eneik.production.repositories.PrReviewRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskConflictRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.advice.RoleAdviceLoopService;
import com.eneik.production.services.dashboard.ProjectOperationalContextService;
import com.eneik.production.services.github.GitHubApiBudgetService;
import com.eneik.production.services.github.GitHubPullRequestService;
import com.eneik.production.services.jules.JulesDispatchService;
import com.eneik.production.services.monitor.SystemProgressTracker;
import com.eneik.production.services.operational.OperationalAction;
import com.eneik.production.services.operational.OperationalPolicyService;
import com.eneik.production.services.settings.SystemSettingsService;
import com.eneik.production.services.video.VideoAssetService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Popperian Falsification Test Harness for AutoMergeService (Stage 4).
 *
 * <p>Philosopher Anchors:
 * 1. DZHOZEF_RAZ_01_PROHIBITION_AS_CODE [D006, Raz]:
 *    Deontic exclusionary reasons implemented as code: auto-merge requires authorization
 *    from OperationalPolicyService (MERGE_PR). When prohibited, execution terminates immediately,
 *    marks review as "policy_denied", and persists to repository with zero external merge calls.
 *    Crucially, "policy_denied" remains a poll candidate (isReviewPollCandidate = true) so that
 *    once the external blocker clears, merge recovers automatically without operator intervention.
 *
 * 2. LUDVIG_VITGENSHTEYN_01_ANTI_MIRROR_TELEMETRY [D013, Wittgenstein]:
 *    Anti-mirror telemetry: physical state of the world (GitHub telemetry) takes precedence over
 *    local internal beliefs. When GitHub PR is closed and unmerged, local state is marked
 *    "closed_unmerged" (terminal, isReviewPollCandidate = false), permanently halting futile retry loops.
 *    When GitHub PR is already merged externally, idempotent reconciliation records it as merged
 *    without issuing duplicate PUT /merge calls (which GitHub rejects with 405).
 *
 * 3. ELVIN_GOLDMAN_01_RELIABILITY_CHAIN [D010, Goldman]:
 *    Reliable causal lineage for reviews:
 *    Synthesis of Laws 11 and 13: ¬attributable(x) ⟹ x stays in decision set ∧ spend(x) = 0 ∧ x named unattributable once.
 *    belongsToActiveProject returns false when a review cannot be mapped to any known active project,
 *    withholding GitHub API budget (spend = 0) while preserving the review in the decision set
 *    without destructive truncation. When attributable to an active project, operations are permitted.
 */
class AutoMergeServiceFalsificationTest {

    private PrReviewRepository prReviews;
    private JulesSessionRepository julesSessions;
    private TaskRepository tasks;
    private SystemSettingsService settings;
    private GitHubPullRequestService gitHub;
    private ProjectRepository projects;
    private OperationalPolicyService operationalPolicyService;
    private SystemProgressTracker progressTracker;

    private AutoMergeService service;

    @BeforeEach
    void setUp() {
        prReviews = mock(PrReviewRepository.class);
        julesSessions = mock(JulesSessionRepository.class);
        tasks = mock(TaskRepository.class);
        settings = mock(SystemSettingsService.class);
        gitHub = mock(GitHubPullRequestService.class);
        projects = mock(ProjectRepository.class);
        operationalPolicyService = mock(OperationalPolicyService.class);
        progressTracker = mock(SystemProgressTracker.class);

        service = new AutoMergeService(
                prReviews,
                julesSessions,
                tasks,
                settings,
                new ObjectMapper(),
                mock(RoleAdviceLoopService.class),
                mock(TaskConflictRepository.class),
                mock(JulesDispatchService.class),
                mock(RoleCapabilityLoader.class),
                mock(WishlistRepository.class),
                mock(MLPredictionServiceClient.class),
                gitHub,
                new GitHubApiBudgetService(),
                mock(VideoAssetService.class),
                mock(ProjectOperationalContextService.class),
                progressTracker,
                mock(CodeChangeClassifier.class),
                mock(FeatureThreadRepository.class),
                mock(ClaimService.class),
                projects,
                mock(ClientDeliverableReadinessService.class),
                mock(GeminiContextService.class),
                mock(ProjectFlowService.class),
                mock(EvidenceNodeRepository.class),
                mock(OperationalRealityFindingRepository.class)
        );

        ReflectionTestUtils.setField(service, "operationalPolicyService", operationalPolicyService);
    }

    private PrReviewEntity reviewWithStatus(String status) {
        PrReviewEntity review = new PrReviewEntity();
        review.setId(UUID.randomUUID());
        review.setCiStatus(status);
        review.setMerged(false);
        return review;
    }

    // =========================================================================
    // 1. DZHOZEF_RAZ_01_PROHIBITION_AS_CODE [D006, Raz]
    // =========================================================================

    @Test
    @DisplayName("Raz D006: Operational policy denial halts merge immediately and sets review to policy_denied")
    void falsifyRazProhibition_policyDeniedProhibitsMergeAndMarksReviewPolicyDenied() {
        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);
        project.setRepositoryName("repo");

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setStatus(TaskStatus.review);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setExternalSessionId("sessions/policy-check");

        PrReviewEntity review = reviewWithStatus("success");
        review.setJulesSessionId(sessionId);
        review.setPrUrl("https://github.com/org/repo/pull/101");

        when(julesSessions.findById(sessionId)).thenReturn(Optional.of(session));
        when(tasks.findById(taskId)).thenReturn(Optional.of(task));

        // Policy denies MERGE_PR
        var deniedDecision = new OperationalPolicyService.OperationalDecision(
                projectId,
                OperationalAction.MERGE_PR,
                false,
                "BLOCKED_BY_MAIN_CI",
                "DENIED",
                "Main branch CI is failing",
                List.of("ci_failure"),
                null
        );
        when(operationalPolicyService.authorize(eq(projectId), eq(OperationalAction.MERGE_PR)))
                .thenReturn(deniedDecision);

        service.executeMerge(review);

        assertEquals("policy_denied", review.getCiStatus(),
                "Falsification: policy denial must set ciStatus to policy_denied");
        assertFalse(review.getMerged(),
                "Falsification: prohibited PR must not be marked merged");
        verify(prReviews).save(review);
        verifyNoInteractions(gitHub);
    }

    @Test
    @DisplayName("Raz D006: policy_denied review remains a poll candidate so it can recover when blocker clears")
    void falsifyRazProhibition_policyDeniedRemainsPollCandidateSoItCanRecover() {
        PrReviewEntity policyDeniedReview = reviewWithStatus("policy_denied");
        assertTrue(AutoMergeService.isReviewPollCandidate(policyDeniedReview),
                "Falsification: policy_denied must remain poll candidate to allow recovery");

        // Contrast with genuinely terminal statuses
        assertFalse(AutoMergeService.isReviewPollCandidate(reviewWithStatus("closed_unmerged")),
                "Falsification: closed_unmerged must be terminal");
        assertFalse(AutoMergeService.isReviewPollCandidate(reviewWithStatus("escalated")),
                "Falsification: escalated must be terminal");
        assertFalse(AutoMergeService.isReviewPollCandidate(reviewWithStatus("invalid_pr")),
                "Falsification: invalid_pr must be terminal");
    }

    // =========================================================================
    // 2. LUDVIG_VITGENSHTEYN_01_ANTI_MIRROR_TELEMETRY [D013, Wittgenstein]
    // =========================================================================

    @Test
    @DisplayName("Wittgenstein D013: Real closed_unmerged state on GitHub marks review terminal and stops retry loop")
    void falsifyWittgensteinAntiMirror_closedUnmergedGitHubPrStopsRetryForever() {
        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);
        project.setRepositoryName("repo");

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setExternalSessionId("sessions/closed-pr");

        PrReviewEntity review = reviewWithStatus("success");
        review.setJulesSessionId(sessionId);
        review.setPrUrl("https://github.com/org/repo/pull/102");

        when(julesSessions.findById(sessionId)).thenReturn(Optional.of(session));
        when(tasks.findById(taskId)).thenReturn(Optional.of(task));
        when(settings.effectiveBoolean("github_enabled")).thenReturn(true);

        // Policy allows merge
        var allowedDecision = new OperationalPolicyService.OperationalDecision(
                projectId, OperationalAction.MERGE_PR, true, "OK", "ALLOWED", "Ready", List.of(), null);
        when(operationalPolicyService.authorize(eq(projectId), eq(OperationalAction.MERGE_PR)))
                .thenReturn(allowedDecision);

        // Physical telemetry: GitHub reports PR is closed and NOT merged
        var closedPr = new GitHubPullRequestService.GitHubPullRequest(
                "https://github.com/org/repo/pull/102",
                102,
                "Operator closed stale duplicate PR",
                "feature/102",
                "jules",
                false,
                "main",
                true,
                Instant.now()
        );
        when(gitHub.fetchPullRequestByNumber("org", "repo", 102)).thenReturn(Optional.of(closedPr));

        service.executeMerge(review);

        assertEquals("closed_unmerged", review.getCiStatus(),
                "Falsification: closed unmerged PR must be marked closed_unmerged");
        assertFalse(review.getMerged());
        assertFalse(AutoMergeService.isReviewPollCandidate(review),
                "Falsification: closed_unmerged review must not be polled again");
        verify(prReviews).save(review);
    }

    @Test
    @DisplayName("Wittgenstein D013: Externally merged PR reconciles idempotently without duplicate PUT merge call")
    void falsifyWittgensteinAntiMirror_alreadyMergedExternalPrReconcilesIdempotently() {
        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);
        project.setRepositoryName("repo");

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(project);
        task.setStatus(TaskStatus.review);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);
        session.setExternalSessionId("sessions/already-merged");

        PrReviewEntity review = reviewWithStatus("success");
        review.setJulesSessionId(sessionId);
        review.setPrUrl("https://github.com/org/repo/pull/103");

        when(julesSessions.findById(sessionId)).thenReturn(Optional.of(session));
        when(tasks.findById(taskId)).thenReturn(Optional.of(task));
        when(settings.effectiveBoolean("github_enabled")).thenReturn(true);

        var allowedDecision = new OperationalPolicyService.OperationalDecision(
                projectId, OperationalAction.MERGE_PR, true, "OK", "ALLOWED", "Ready", List.of(), null);
        when(operationalPolicyService.authorize(eq(projectId), eq(OperationalAction.MERGE_PR)))
                .thenReturn(allowedDecision);

        // Physical telemetry: GitHub reports PR was already merged externally
        var alreadyMergedPr = new GitHubPullRequestService.GitHubPullRequest(
                "https://github.com/org/repo/pull/103",
                103,
                "Live Chat CRM feature",
                "feature/103",
                "jules",
                true,
                "main",
                true,
                Instant.now()
        );
        when(gitHub.fetchPullRequestByNumber("org", "repo", 103)).thenReturn(Optional.of(alreadyMergedPr));

        service.executeMerge(review);

        assertTrue(review.getMerged(),
                "Falsification: already merged PR must be recorded as merged");
        verify(prReviews).save(review);
        verify(progressTracker).recordProgress();
    }

    // =========================================================================
    // 3. ELVIN_GOLDMAN_01_RELIABILITY_CHAIN [D010, Goldman]
    // =========================================================================

    @Test
    @DisplayName("Goldman D010: Unattributable review withholds GitHub spend (spend=0) while preserving in decision set")
    void falsifyGoldmanReliabilityChain_unattributableReviewWithholdsSpendWhilePreservingInDecisionSet() {
        PrReviewEntity orphanReview = reviewWithStatus("pending");
        orphanReview.setJulesSessionId(null);
        orphanReview.setPrUrl("https://github.com/unknown-org/orphan-repo/pull/999");

        when(projects.findFirstByRepositoryNameIgnoreCase("orphan-repo")).thenReturn(Optional.empty());
        when(projects.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of());
        when(projects.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

        boolean belongs = service.belongsToActiveProject(orphanReview);

        assertFalse(belongs,
                "Falsification: unattributable review must not be declared belonging to active project");
        // Synthesis of Laws 11 and 13: spend(x) = 0, stays in decision set
        verifyNoInteractions(gitHub);
        assertEquals("pending", orphanReview.getCiStatus(),
                "Falsification: unattributable review must not be falsely terminated or mutated");
        assertFalse(orphanReview.getMerged());
    }

    @Test
    @DisplayName("Goldman D010: Attributable review belonging to active project permits operation, inactive is blocked")
    void falsifyGoldmanReliabilityChain_attributableReviewToActiveProjectPermitsSpend() {
        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        ProjectEntity activeProject = new ProjectEntity();
        activeProject.setId(projectId);
        activeProject.setStatus(ProjectStatus.active);
        activeProject.setRepositoryName("active-repo");

        TaskEntity task = new TaskEntity();
        task.setId(taskId);
        task.setProject(activeProject);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(sessionId);
        session.setTaskId(taskId);

        PrReviewEntity activeReview = reviewWithStatus("success");
        activeReview.setJulesSessionId(sessionId);
        activeReview.setPrUrl("https://github.com/org/active-repo/pull/50");

        when(julesSessions.findById(sessionId)).thenReturn(Optional.of(session));
        when(tasks.findById(taskId)).thenReturn(Optional.of(task));

        // Case A: Project is active -> belongsToActiveProject is true
        assertTrue(service.belongsToActiveProject(activeReview),
                "Falsification: review mapped to active project must return true");

        // Case B: Project is completed/accepted -> belongsToActiveProject is false (protects GitHub budget)
        activeProject.setStatus(ProjectStatus.accepted);
        assertFalse(service.belongsToActiveProject(activeReview),
                "Falsification: review mapped to non-active project must return false to protect API budget");
    }
}
