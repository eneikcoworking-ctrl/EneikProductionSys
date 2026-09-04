package com.eneik.production.services;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.jules.JulesDispatchResult;
import com.eneik.production.services.jules.JulesDispatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Law 1 (|impl(I)| = 1): Single Point of Application for Jules Dispatch.
 *
 * All three dispatch pathways (general pool, reviewer, compiler) must execute through
 * the unified dispatchToGeneralPool method, with distinctions expressed by arguments rather than duplicate code.
 *
 * Structural Invariant:
 * lockNextJulesAccountWithCapacity and lockAccountByNameWithCapacity are called from strictly one place each.
 */
class ProjectFlowServiceLaw1JulesDispatchTest {

    private AccountRepository accountRepository;
    private TaskRepository taskRepository;
    private JulesDispatchService julesDispatchService;
    private ProjectFlowService service;
    private ProjectEntity project;
    private AccountEntity account;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        taskRepository = mock(TaskRepository.class);
        julesDispatchService = mock(JulesDispatchService.class);

        service = mock(ProjectFlowService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service, "accountRepository", accountRepository);
        ReflectionTestUtils.setField(service, "taskRepository", taskRepository);
        ReflectionTestUtils.setField(service, "julesDispatchService", julesDispatchService);
        ReflectionTestUtils.setField(service, "self", service);
        ReflectionTestUtils.setField(service, "maxConcurrentJulesSessionsPerAccount", 3);
        ReflectionTestUtils.setField(service, "maxDailySessionsPerAccount", 15);

        // bypass self.claimAccountForTask by directly executing the action supplier
        doAnswer(inv -> {
            Supplier<Optional<AccountEntity>> action = inv.getArgument(1);
            return action.get();
        }).when(service).claimAccountForTask(any(), any());

        project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("Law 1 Test Project");

        account = new AccountEntity();
        account.setId(UUID.randomUUID());
        account.setName("eneikdru");
    }

    @Test
    void structuralCheckSinglePointOfApplicationForAccountLockingQueries() throws IOException {
        Path path = Paths.get("src/main/java/com/eneik/production/services/ProjectFlowService.java");
        String content = Files.readString(path);

        // Count non-comment calls to lockNextJulesAccountWithCapacity
        long lockNextCalls = content.lines()
                .filter(line -> line.contains("accountRepository.lockNextJulesAccountWithCapacity"))
                .filter(line -> !line.trim().startsWith("//") && !line.trim().startsWith("*"))
                .count();
        assertEquals(1, lockNextCalls,
                "accountRepository.lockNextJulesAccountWithCapacity must be called from strictly ONE place in ProjectFlowService");

        // Count non-comment calls to lockAccountByNameWithCapacity
        long lockByNameCalls = content.lines()
                .filter(line -> line.contains("accountRepository.lockAccountByNameWithCapacity"))
                .filter(line -> !line.trim().startsWith("//") && !line.trim().startsWith("*"))
                .count();
        assertEquals(1, lockByNameCalls,
                "accountRepository.lockAccountByNameWithCapacity must be called from strictly ONE place in ProjectFlowService");

        // Verify dispatchReviewTasks does not call accountRepository directly
        int startReview = content.indexOf("public void dispatchReviewTasks");
        assertThat(startReview).isGreaterThan(0);
        int endReview = content.indexOf("public ProjectDto acceptProject", startReview);
        assertThat(endReview).isGreaterThan(startReview);
        String reviewMethod = content.substring(startReview, endReview);

        assertFalse(reviewMethod.contains("accountRepository.lockNextJulesAccountWithCapacity"),
                "dispatchReviewTasks must delegate to unified dispatchToGeneralPool, not call lockNextJulesAccountWithCapacity directly");
    }

    @Test
    void reviewerDispatchUsesReviewerModeInUnifiedDispatch() {
        TaskEntity reviewTask = new TaskEntity();
        reviewTask.setId(UUID.randomUUID());
        reviewTask.setProject(project);
        RoleEntity role = new RoleEntity();
        role.setTag("BARCAN-TAG-01");
        reviewTask.setRole(role);
        reviewTask.setStatus(TaskStatus.review);

        when(taskRepository.findById(reviewTask.getId())).thenReturn(Optional.of(reviewTask));
        when(accountRepository.lockNextJulesAccountWithCapacity(any(), any(), anyInt(), any(), anyInt(), any()))
                .thenReturn(Optional.of(account));
        when(julesDispatchService.dispatch(any(TaskEntity.class), eq(account.getId()), eq("REVIEWER")))
                .thenReturn(new JulesDispatchResult(true, "session-123", "ok"));

        // Call the package-private or private unified method via reflection with mode="REVIEWER"
        boolean dispatched = ReflectionTestUtils.invokeMethod(service, "dispatchToGeneralPool",
                reviewTask, java.util.Set.of(), "REVIEWER", null);

        assertThat(dispatched).isTrue();
        verify(accountRepository).lockNextJulesAccountWithCapacity(
                eq(project.getId()), eq("BARCAN-TAG-01"), eq(3), isNull(), eq(15), isNull());
        verify(julesDispatchService).dispatch(reviewTask, account.getId(), "REVIEWER");
    }

    @Test
    void compilerDispatchUsesNamedAccountInUnifiedDispatch() {
        TaskEntity compilerTask = new TaskEntity();
        compilerTask.setId(UUID.randomUUID());
        compilerTask.setProject(project);
        RoleEntity role = new RoleEntity();
        role.setTag("BARCAN-TAG-09");
        compilerTask.setRole(role);
        compilerTask.setStatus(TaskStatus.queued);

        when(taskRepository.findById(compilerTask.getId())).thenReturn(Optional.of(compilerTask));
        when(accountRepository.lockAccountByNameWithCapacity(eq("eneikdru"), anyInt()))
                .thenReturn(Optional.of(account));
        when(julesDispatchService.dispatch(any(TaskEntity.class), eq(account.getId())))
                .thenReturn(new JulesDispatchResult(true, "session-comp-1", "ok"));

        // Call dispatchCompilerTask via reflection
        boolean dispatched = ReflectionTestUtils.invokeMethod(service, "dispatchCompilerTask", compilerTask);

        assertThat(dispatched).isTrue();
        verify(accountRepository).lockAccountByNameWithCapacity("eneikdru", 3);
        verify(julesDispatchService).dispatch(compilerTask, account.getId());
        verify(accountRepository, never()).lockNextJulesAccountWithCapacity(any(), any(), anyInt(), any(), anyInt(), any());
    }
}
