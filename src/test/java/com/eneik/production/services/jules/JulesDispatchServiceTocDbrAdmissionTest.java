package com.eneik.production.services.jules;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.models.persistence.TargetContext;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.ClaimService;
import com.eneik.production.toc.engine.TocAnomalyDetector;
import com.eneik.production.toc.engine.TocExecutionGraph;
import com.eneik.production.toc.engine.TocOptimizer;
import com.eneik.production.toc.model.TocToken;
import com.eneik.production.toc.service.TocSentinelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Заслон для прикладной топологии границы допуска TOC DBR на входе в JulesDispatchService
 * (AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY [D006], AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP [D004]).
 *
 * 1. Проверка shouldAdmit() на входе dispatch() отсекает запуск до создания сессий.
 * 2. Прямой вызов dispatch(UUID, UUID) не забирает claim при отказе допуска.
 * 3. Высокоприоритетные задачи (>= 80) имеют VIP-проход верёвки DBR.
 * 4. Ad-hoc сессия на ветку подчиняется проверке допуска верёвки DBR.
 */
class JulesDispatchServiceTocDbrAdmissionTest {

    private JulesApiClient julesApiClient;
    private JulesSessionRepository julesSessionRepository;
    private TaskRepository taskRepository;
    private ClaimService claimService;
    private AccountRepository accountRepository;
    private JulesDispatchService julesDispatchService;

    private TocExecutionGraph graph;
    private TocOptimizer optimizer;
    private TocSentinelService tocSentinelService;

    @BeforeEach
    void setUp() {
        julesApiClient = mock(JulesApiClient.class);
        julesSessionRepository = mock(JulesSessionRepository.class);
        taskRepository = mock(TaskRepository.class);
        claimService = mock(ClaimService.class);
        accountRepository = mock(AccountRepository.class);

        graph = new TocExecutionGraph();
        optimizer = new TocOptimizer(graph);
        tocSentinelService = new TocSentinelService(graph, new TocAnomalyDetector(graph), optimizer);

        var progressTracker = mock(com.eneik.production.services.monitor.SystemProgressTracker.class);
        var settingsService = mock(com.eneik.production.services.settings.SystemSettingsService.class);
        when(settingsService.effectiveBoolean("jules_enabled")).thenReturn(true);

        SessionLifecycleService sessionLifecycleService = new SessionLifecycleService(
                julesSessionRepository, accountRepository, taskRepository, julesApiClient, null);
        ReflectionTestUtils.setField(sessionLifecycleService, "self", sessionLifecycleService);

        julesDispatchService = new JulesDispatchService(
                julesApiClient, julesSessionRepository,
                mock(com.eneik.production.repositories.JulesActivityResponseRepository.class),
                mock(com.eneik.production.repositories.WishlistRepository.class),
                accountRepository, taskRepository,
                mock(com.eneik.production.repositories.TaskConflictRepository.class),
                claimService,
                mock(com.eneik.production.services.RoleCapabilityLoader.class),
                mock(com.eneik.production.services.monitor.PrReviewPipelineService.class),
                mock(com.eneik.production.services.MLPredictionServiceClient.class),
                mock(com.eneik.production.repositories.RoleRepository.class),
                mock(com.eneik.production.services.github.GitHubPullRequestService.class),
                mock(com.eneik.production.repositories.PrReviewRepository.class),
                progressTracker,
                mock(com.eneik.production.services.ProjectFlowService.class),
                mock(com.eneik.production.services.FalsificationCycleService.class),
                mock(com.eneik.production.repositories.FeatureThreadRepository.class),
                mock(com.eneik.production.services.ClientDeliverableReadinessService.class),
                mock(com.eneik.production.services.PersistentWorkerSessionService.class),
                mock(com.eneik.production.repositories.ProjectRepository.class),
                mock(com.eneik.production.services.WishlistContentSimilarityMatcher.class),
                settingsService,
                mock(com.eneik.production.services.GeminiContextService.class),
                mock(com.eneik.production.repositories.ReviewConcernRepository.class),
                mock(com.eneik.production.services.accounts.AccountHealthService.class),
                sessionLifecycleService,
                mock(com.eneik.production.repositories.DesignShopCycleRepository.class),
                mock(com.eneik.production.services.stitch.StitchClient.class),
                "prefix/",
                null
        );
        ReflectionTestUtils.setField(julesDispatchService, "self", julesDispatchService);
        julesDispatchService.setTocSentinelService(tocSentinelService);
        when(julesSessionRepository.save(any(JulesSessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private ProjectEntity createProject() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("test-product");
        project.setRepositoryName("test-repo");
        project.setStatus(ProjectStatus.active);
        return project;
    }

    private TaskEntity createTask(ProjectEntity project, int priority) {
        RoleEntity role = new RoleEntity();
        role.setTag("BARCAN-TAG-02");
        role.setDescription("Backend implementation");

        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setProject(project);
        task.setStatus(TaskStatus.queued);
        task.setTargetContext(TargetContext.PRODUCT_CODEBASE);
        task.setPriority(priority);
        task.setRole(role);
        task.setTitle("Implement feature");
        task.setDescription("Feature implementation details");
        return task;
    }

    @Test
    @DisplayName("Заслон: dispatch(TaskEntity) отклоняется при переполнении буфера TOC Sentinel (shouldAdmit = false)")
    void dispatchIsThrottledWhenConstraintBufferOverflows() {
        // Переполняем буфер ограничения
        tocSentinelService.setMaxBufferCapacity(2);
        TocToken t1 = tocSentinelService.startExecution("WORK_1", 10);
        tocSentinelService.enterStep(t1, "JULES_BOTTLENECK");
        TocToken t2 = tocSentinelService.startExecution("WORK_2", 10);
        tocSentinelService.enterStep(t2, "JULES_BOTTLENECK");
        tocSentinelService.periodicWatchdog();

        assertThat(tocSentinelService.getDbrStatus().ropeThrottlingActive()).isTrue();

        ProjectEntity project = createProject();
        TaskEntity normalTask = createTask(project, 50);

        JulesDispatchResult result = julesDispatchService.dispatch(normalTask);

        assertThat(result.dispatched()).isFalse();
        assertThat(result.reason()).contains("Throttled by TOC Sentinel DBR Rope");
        verify(julesApiClient, never()).createSessionDetailed(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Заслон: VIP-задача (priority >= 80) допускается верёвкой DBR даже при переполненном буфере")
    void highPriorityTaskBypassesRopeThrottling() {
        tocSentinelService.setMaxBufferCapacity(2);
        TocToken t1 = tocSentinelService.startExecution("WORK_1", 10);
        tocSentinelService.enterStep(t1, "JULES_BOTTLENECK");
        TocToken t2 = tocSentinelService.startExecution("WORK_2", 10);
        tocSentinelService.enterStep(t2, "JULES_BOTTLENECK");
        tocSentinelService.periodicWatchdog();

        assertThat(tocSentinelService.getDbrStatus().ropeThrottlingActive()).isTrue();

        ProjectEntity project = createProject();
        TaskEntity vipTask = createTask(project, 90);
        UUID accountId = UUID.randomUUID();

        AccountEntity account = new AccountEntity();
        account.setId(accountId);
        account.setApiKey("test-api-key");
        account.setEnabled(true);
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(julesApiClient.createSessionDetailed(any(), any(), any(), any(), any(), any()))
                .thenReturn(new JulesApiClient.CreateSessionResult("sessions/vip-123", 200, ""));

        JulesDispatchResult result = julesDispatchService.dispatch(vipTask, accountId);

        assertThat(result.dispatched()).isTrue();
        assertThat(result.sessionName()).isEqualTo("sessions/vip-123");
        verify(julesApiClient).createSessionDetailed(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("BOUNDARY_TOPOLOGY: dispatch(UUID, UUID) не забирает claim, если допуск TOC Sentinel отклонён")
    void directDispatchDoesNotClaimTaskWhenAdmissionDenied() {
        tocSentinelService.setMaxBufferCapacity(2);
        TocToken t1 = tocSentinelService.startExecution("WORK_1", 10);
        tocSentinelService.enterStep(t1, "JULES_BOTTLENECK");
        TocToken t2 = tocSentinelService.startExecution("WORK_2", 10);
        tocSentinelService.enterStep(t2, "JULES_BOTTLENECK");
        tocSentinelService.periodicWatchdog();

        ProjectEntity project = createProject();
        TaskEntity task = createTask(project, 50);
        UUID accountId = UUID.randomUUID();

        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));

        var session = julesDispatchService.dispatch(task.getId(), accountId);

        assertThat(session).isNull();
        // Топологическая граница: claim не должен быть захвачен, задача остаётся в очереди
        verify(claimService, never()).claimSpecificTask(any(), any());
        verify(julesApiClient, never()).createSessionDetailed(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Ad-hoc диспетчеризация на ветку подчиняется проверке shouldAdmit")
    void dispatchAdHocSessionToBranchThrottledWhenBufferOverflows() {
        tocSentinelService.setMaxBufferCapacity(2);
        TocToken t1 = tocSentinelService.startExecution("WORK_1", 10);
        tocSentinelService.enterStep(t1, "JULES_BOTTLENECK");
        TocToken t2 = tocSentinelService.startExecution("WORK_2", 10);
        tocSentinelService.enterStep(t2, "JULES_BOTTLENECK");
        tocSentinelService.periodicWatchdog();

        ProjectEntity project = createProject();
        julesDispatchService.dispatchAdHocSessionToBranch(project, "feature-branch", "Fix conflict", "Conflict fix");

        verify(julesApiClient, never()).createSessionDetailed(any(), any(), any(), any(), any(), any());
    }
}
