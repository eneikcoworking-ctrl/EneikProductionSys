package com.eneik.production.services;

import com.eneik.production.kaizen.service.DefectJournalService;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
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
import com.eneik.production.services.projectfactory.ProjectFactoryService;
import com.eneik.production.services.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Screen for Law 8 (Finite Budget on Retirement Loop) & Law 9 (Reverse Case):
 * <ul>
 *   <li>{@code nu(P) = MAX_RETIRE_ATTEMPTS - retireAttempts(P)} is bounded below by 0 and strictly decreasing</li>
 *   <li>{@code Delta retireAttempts = 1 <=> attempt reached external systems (Jules API or GitHub API)}</li>
 *   <li>Law 9 reverse case: an attempt that reaches no external system does not spend budget (Delta = 0)</li>
 *   <li>Retirement is idempotent: already cancelled sessions and already retired projects are never re-contacted</li>
 *   <li>Exhaustion of nu(P) records non-silently into {@code defect_journal} exactly once</li>
 * </ul>
 */
class ProjectRetirementLaw8Test {

    private ProjectRepository projectRepository;
    private TaskRepository taskRepository;
    private JulesSessionRepository julesSessionRepository;
    private JulesDispatchService julesDispatchService;
    private GitHubPullRequestService gitHubPullRequestService;
    private DefectJournalService defectJournalService;

    private ProjectFlowService service;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        taskRepository = mock(TaskRepository.class);
        julesSessionRepository = mock(JulesSessionRepository.class);
        julesDispatchService = mock(JulesDispatchService.class);
        gitHubPullRequestService = mock(GitHubPullRequestService.class);
        defectJournalService = mock(DefectJournalService.class);

        when(projectRepository.save(any(ProjectEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        service = new ProjectFlowService(
                projectRepository,
                mock(WishlistRepository.class),
                mock(AccountRepository.class),
                taskRepository,
                mock(ClaimRepository.class),
                mock(RoleRepository.class),
                mock(ClaimService.class),
                julesDispatchService,
                mock(ProjectFactoryService.class),
                mock(GitHubProjectFactoryClient.class),
                mock(SystemSettingsService.class),
                null,
                null,
                mock(TechnicalLeadCompiler.class),
                mock(ClientDeliveryService.class),
                mock(ProjectFinalReportRepository.class),
                julesSessionRepository,
                mock(JulesActivityResponseRepository.class),
                mock(ProjectGenerationStateRepository.class),
                new ObjectMapper(),
                "eneik-org",
                mock(OnboardingAuditService.class),
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
        service.setDefectJournalService(defectJournalService);
    }

    private ProjectEntity createSampleProject(String name) {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName(name);
        project.setSlug(name.toLowerCase().replace(" ", "-"));
        project.setStatus(ProjectStatus.frozen);
        project.setRetireAttempts(0);
        project.setRetireExhausted(false);
        project.setRetiredAt(null);
        return project;
    }

    @Test
    @DisplayName("Law 8: nu(P) strictly decreases by 1 on every external attempt")
    void strictlyDecreasingVariantFunctionOnExternalAttempt() {
        ProjectEntity project = createSampleProject("P1");
        project.setRepositoryName("p1-repo");
        project.setGithubRepositoryId("gh-repo-1");

        // Simulate active Jules session
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setProject(project);
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of(task));

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(UUID.randomUUID());
        session.setExternalSessionId("ext-sess-1");
        session.setStatus("running");
        when(julesSessionRepository.findByTaskId(task.getId())).thenReturn(List.of(session));

        // External cancellation fails (e.g. timeout)
        doThrow(new RuntimeException("Jules timeout"))
                .when(julesDispatchService).cancelSession(eq(session.getId()), anyString());

        int initialAttempts = project.getRetireAttempts();
        int initialNu = ProjectFlowService.MAX_RETIRE_ATTEMPTS - initialAttempts;
        assertEquals(3, initialNu);

        // Attempt 1: reached external system
        service.cancelExternalWorkForProject(project, "Superseded");

        int nuAfterAttempt1 = ProjectFlowService.MAX_RETIRE_ATTEMPTS - project.getRetireAttempts();
        assertEquals(1, project.getRetireAttempts(), "Delta attempts must be exactly 1 when external systems were reached");
        assertEquals(2, nuAfterAttempt1, "nu(P) must strictly decrease from 3 to 2");
        assertTrue(nuAfterAttempt1 < initialNu);
        assertFalse(project.isRetireExhausted(), "Must not be exhausted before reaching attempt limit");
        assertNull(project.getRetiredAt(), "retiredAt must not be stamped while external cancellations fail");

        // Attempt 2: reached external system again
        service.cancelExternalWorkForProject(project, "Superseded");

        int nuAfterAttempt2 = ProjectFlowService.MAX_RETIRE_ATTEMPTS - project.getRetireAttempts();
        assertEquals(2, project.getRetireAttempts());
        assertEquals(1, nuAfterAttempt2, "nu(P) must strictly decrease from 2 to 1");
        assertTrue(nuAfterAttempt2 < nuAfterAttempt1);
        assertFalse(project.isRetireExhausted());
        assertNull(project.getRetiredAt());

        // Zero defect entries logged while nu > 0
        verifyNoInteractions(defectJournalService);
    }

    @Test
    @DisplayName("Law 9 reverse case: no external work to cancel does not spend budget (Delta retireAttempts = 0)")
    void noExternalAttemptDoesNotSpendBudget_Law9ReverseCase() {
        ProjectEntity project = createSampleProject("Local Only Admitted Project");
        // No GitHub repo provisioned
        project.setRepositoryName(null);
        project.setGithubRepositoryId(null);
        project.setGithubRepositoryStatus(null);
        project.setFactoryStatus("admitted");

        // Zero tasks or sessions
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of());

        assertEquals(0, project.getRetireAttempts());

        service.cancelExternalWorkForProject(project, "Superseded");

        // Zero attempts spent
        assertEquals(0, project.getRetireAttempts(),
                "Law 9 reverse case: attempt that reaches no external system must NOT spend budget (Delta attempts = 0)");
        assertNotNull(project.getRetiredAt(), "Project must be marked retired immediately");
        assertFalse(project.isRetireExhausted());

        // Zero external calls made
        verifyNoInteractions(julesDispatchService);
        verifyNoInteractions(gitHubPullRequestService);
        verifyNoInteractions(defectJournalService);
    }

    @Test
    @DisplayName("Law 8 idempotency: already cancelled session is never re-contacted")
    void idempotentAlreadyCancelledSessionNotCancelledTwice() {
        ProjectEntity project = createSampleProject("P2");
        project.setRepositoryName("p2-repo");
        project.setGithubRepositoryId("gh-repo-2");

        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setProject(project);
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of(task));

        // Two sessions: one active, one already cancelled
        JulesSessionEntity activeSession = new JulesSessionEntity();
        activeSession.setId(UUID.randomUUID());
        activeSession.setExternalSessionId("ext-active");
        activeSession.setStatus("running");

        JulesSessionEntity alreadyCancelledSession = new JulesSessionEntity();
        alreadyCancelledSession.setId(UUID.randomUUID());
        alreadyCancelledSession.setExternalSessionId("ext-cancelled");
        alreadyCancelledSession.setStatus("cancelled");

        when(julesSessionRepository.findByTaskId(task.getId())).thenReturn(List.of(activeSession, alreadyCancelledSession));

        service.cancelExternalWorkForProject(project, "Superseded");

        // activeSession was cancelled
        verify(julesDispatchService, times(1)).cancelSession(eq(activeSession.getId()), anyString());
        // alreadyCancelledSession was NEVER called
        verify(julesDispatchService, never()).cancelSession(eq(alreadyCancelledSession.getId()), anyString());
    }

    @Test
    @DisplayName("Law 8 non-silent exhaustion: upon reaching attempt budget, records exactly ONE defect in journal")
    void exhaustionRecordsExactlyOneDefectInJournal_NonSilent() {
        ProjectEntity project = createSampleProject("Stubborn Project");
        project.setRepositoryName("stubborn-repo");
        project.setGithubRepositoryId("gh-stubborn");

        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setProject(project);
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of(task));

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(UUID.randomUUID());
        session.setExternalSessionId("ext-sess");
        session.setStatus("queued");
        when(julesSessionRepository.findByTaskId(task.getId())).thenReturn(List.of(session));

        doThrow(new RuntimeException("Continuous 500 error"))
                .when(julesDispatchService).cancelSession(eq(session.getId()), anyString());

        // Run attempts 1, 2, 3
        service.cancelExternalWorkForProject(project, "Retire attempt 1");
        assertEquals(1, project.getRetireAttempts());
        assertFalse(project.isRetireExhausted());
        verifyNoInteractions(defectJournalService);

        service.cancelExternalWorkForProject(project, "Retire attempt 2");
        assertEquals(2, project.getRetireAttempts());
        assertFalse(project.isRetireExhausted());
        verifyNoInteractions(defectJournalService);

        // Attempt 3: Budget reaches MAX_RETIRE_ATTEMPTS (3)
        service.cancelExternalWorkForProject(project, "Retire attempt 3");
        assertEquals(3, project.getRetireAttempts());
        assertTrue(project.isRetireExhausted(), "Project must be marked retireExhausted = true");
        assertNull(project.getRetiredAt(), "retiredAt must remain null due to ongoing failures");

        // Exactly ONE defect recorded
        verify(defectJournalService, times(1)).recordDefect(
                eq(project.getId()),
                eq("CRITICAL"),
                eq("LIFECYCLE"),
                eq("ProjectFlowService"),
                eq("RETIRE_BUDGET_EXHAUSTED"),
                anyString(),
                eq(3.0)
        );

        // Run attempt 4 (subsequent scheduled cycle): must be a no-op due to retireExhausted
        service.cancelExternalWorkForProject(project, "Retire attempt 4");

        // Attempts remain 3, still exactly 1 defect recorded, no new external calls
        assertEquals(3, project.getRetireAttempts());
        verify(julesDispatchService, times(3)).cancelSession(eq(session.getId()), anyString());
        verify(defectJournalService, times(1)).recordDefect(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Law 8 success: successful cancellation stamps retiredAt and makes subsequent invocations no-ops")
    void successfulRetirementMarksRetiredAtAndIsIdempotent() {
        ProjectEntity project = createSampleProject("Clean Project");
        project.setRepositoryName("clean-repo");
        project.setGithubRepositoryId("gh-clean");

        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setProject(project);
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of(task));

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(UUID.randomUUID());
        session.setExternalSessionId("ext-clean");
        session.setStatus("running");
        when(julesSessionRepository.findByTaskId(task.getId())).thenReturn(List.of(session));

        service.cancelExternalWorkForProject(project, "Superseded");

        assertEquals(1, project.getRetireAttempts());
        assertNotNull(project.getRetiredAt());
        assertFalse(project.isRetireExhausted());
        verify(julesDispatchService, times(1)).cancelSession(eq(session.getId()), anyString());
        verify(gitHubPullRequestService, times(1)).closeOpenPullRequests(eq(project), anyString());

        // Subsequent call is a total no-op because retiredAt != null
        service.cancelExternalWorkForProject(project, "Superseded again");

        assertEquals(1, project.getRetireAttempts(), "Attempts must remain 1");
        verify(julesDispatchService, times(1)).cancelSession(any(), any());
        verify(gitHubPullRequestService, times(1)).closeOpenPullRequests(any(), any());
    }
}
