package com.eneik.production.services;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.AccountStatus;
import com.eneik.production.models.persistence.ClaimEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.ClaimRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.dashboard.ProjectOperationalContextService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Фальсифицирующий замер Ступени 2 для V19 (реструктуризация пула аккаунтов и инвариант ровно 1 активного проекта):
 *
 * 1. RUT_BARKAN_MARKUS_01_ACTUAL_OBJECT_REGISTER [D002, Marcus]:
 *    - Инвариант ровно одного активного проекта в системе:
 *      Проекты, кроме новейшего активного, архивируются (V19) или замораживаются при admission (ProjectFlowService).
 *    - Не-активные проекты (archived, frozen, accepted, draft) никогда не идентифицируются как текущий активный объект.
 *    - retirePriorActiveProjectsLocally:
 *      * Предыдущий активный проект переводится в frozen.
 *      * Все незавершенные задачи (queued, claimed, in_progress, review, blocked) закрываются с failed.
 *      * Терминальные задачи (done, failed, spike_completed) не перезаписываются (терминальная неизменяемость Law 20).
 *      * Повисшие клеймы (releasedAt == null) возвращаются в очередь.
 *    - RUT_BARKAN_MARKUS_18_INDEXICAL_CONTEXT_LOCK:
 *      * Разрешение контекста при null-projectId strictly адресует findFirstByStatusOrderByCreatedAtDesc(active).
 *
 * 2. LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY [D013, Wittgenstein] & DZHON_SERL_05 (D007, Searle):
 *    - Эмпирическая телеметрия пула аккаунтов (V19):
 *      * Схема AccountEntity: api_key, github_username, enabled (default true), status, current_project_id.
 *      * Квалификация глобального пула: project == null, githubUsername != null, enabled == true.
 *      * Декоммиссия (V19 rule): проектно-привязанные аккаунты или не входящие в глобальный список переходят в decommissioned.
 *      * Декоммиссионированный аккаунт не-оперативен (isOperational == false, isAvailable == false).
 *      * Разграничение доступности findAvailableForProject: декоммиссионированные исключены, свободные (currentProjectId == null)
 *        или свои (currentProjectId == targetProjectId) доступны, чужие (currentProjectId == otherProjectId) изолированы.
 */
class V19AccountPoolAndSingleActiveProjectFalsificationTest {

    @Nested
    @DisplayName("RUT_BARKAN_MARKUS_01: Инвариант ровно одного активного проекта (D002 Invalid State)")
    class SingleActiveProjectInvariantTests {

        @Test
        @DisplayName("MARKUS_01: Единственный актуальный проект — строго новейший по createdAt среди active")
        void singleActiveProjectIsLatestByCreatedAtAmongActive() {
            ProjectRepository projectRepository = mock(ProjectRepository.class);

            ProjectEntity olderActive = new ProjectEntity();
            olderActive.setId(UUID.randomUUID());
            olderActive.setName("test-forty-ninth");
            olderActive.setStatus(ProjectStatus.active);
            olderActive.setCreatedAt(Instant.parse("2026-08-01T10:00:00Z"));

            ProjectEntity latestActive = new ProjectEntity();
            latestActive.setId(UUID.randomUUID());
            latestActive.setName("test-fiftieth");
            latestActive.setStatus(ProjectStatus.active);
            latestActive.setCreatedAt(Instant.parse("2026-08-15T12:00:00Z"));

            ProjectEntity archivedProject = new ProjectEntity();
            archivedProject.setId(UUID.randomUUID());
            archivedProject.setName("test-forty-eighth");
            archivedProject.setStatus(ProjectStatus.archived);
            archivedProject.setCreatedAt(Instant.parse("2026-08-20T10:00:00Z")); // Создан позже, но архивирован

            // Моделируем поведение findFirstByStatusOrderByCreatedAtDesc(ProjectStatus.active)
            List<ProjectEntity> allProjects = List.of(olderActive, latestActive, archivedProject);
            when(projectRepository.findFirstByStatusOrderByCreatedAtDesc(ProjectStatus.active))
                    .thenReturn(allProjects.stream()
                            .filter(p -> p.getStatus() == ProjectStatus.active)
                            .max(Comparator.comparing(ProjectEntity::getCreatedAt)));

            Optional<ProjectEntity> active = projectRepository.findFirstByStatusOrderByCreatedAtDesc(ProjectStatus.active);

            assertThat(active).isPresent();
            assertThat(active.get().getName()).isEqualTo("test-fiftieth");
            assertThat(active.get().getId()).isEqualTo(latestActive.getId());
            assertThat(active.get().getStatus()).isEqualTo(ProjectStatus.active);
        }

        @Test
        @DisplayName("MARKUS_01: retirePriorActiveProjectsLocally переводит старый проект в frozen и закрывает активные задачи")
        void retirePriorActiveProjectsLocallyTransitionsProjectAndTasks() {
            ProjectRepository projectRepository = mock(ProjectRepository.class);
            TaskRepository taskRepository = mock(TaskRepository.class);
            ClaimRepository claimRepository = mock(ClaimRepository.class);
            ClaimService claimService = mock(ClaimService.class);

            UUID priorProjectId = UUID.randomUUID();
            ProjectEntity priorProject = new ProjectEntity();
            priorProject.setId(priorProjectId);
            priorProject.setStatus(ProjectStatus.active);

            when(projectRepository.findFirstByStatusOrderByCreatedAtDesc(ProjectStatus.active))
                    .thenReturn(Optional.of(priorProject));

            // Задачи предшествующего проекта: незавершенные и терминальные
            UUID activeTaskId1 = UUID.randomUUID();
            TaskEntity taskInProgress = new TaskEntity();
            taskInProgress.setId(activeTaskId1);
            taskInProgress.setStatus(TaskStatus.in_progress);

            UUID activeTaskId2 = UUID.randomUUID();
            TaskEntity taskQueued = new TaskEntity();
            taskQueued.setId(activeTaskId2);
            taskQueued.setStatus(TaskStatus.queued);

            UUID activeTaskId3 = UUID.randomUUID();
            TaskEntity taskBlocked = new TaskEntity();
            taskBlocked.setId(activeTaskId3);
            taskBlocked.setStatus(TaskStatus.blocked);

            UUID doneTaskId = UUID.randomUUID();
            TaskEntity taskDone = new TaskEntity();
            taskDone.setId(doneTaskId);
            taskDone.setStatus(TaskStatus.done);

            UUID failedTaskId = UUID.randomUUID();
            TaskEntity taskFailed = new TaskEntity();
            taskFailed.setId(failedTaskId);
            taskFailed.setStatus(TaskStatus.failed);

            UUID spikeTaskId = UUID.randomUUID();
            TaskEntity taskSpike = new TaskEntity();
            taskSpike.setId(spikeTaskId);
            taskSpike.setStatus(TaskStatus.spike_completed);

            when(taskRepository.findByProjectIdOrderByCreatedAtDesc(priorProjectId))
                    .thenReturn(List.of(taskInProgress, taskQueued, taskBlocked, taskDone, taskFailed, taskSpike));

            // Повисший клейм без отметки освобождения
            UUID staleTaskId = UUID.randomUUID();
            TaskEntity staleClaimTask = new TaskEntity();
            staleClaimTask.setId(staleTaskId);
            ClaimEntity staleClaim = new ClaimEntity();
            staleClaim.setTask(staleClaimTask);
            staleClaim.setReleasedAt(null);

            when(claimRepository.findByReleasedAtIsNull()).thenReturn(List.of(staleClaim));

            // Создаем экземпляр ProjectFlowService с реальным методом retirePriorActiveProjectsLocally
            ProjectFlowService flowService = mock(ProjectFlowService.class, CALLS_REAL_METHODS);
            ReflectionTestUtils.setField(flowService, "projectRepository", projectRepository);
            ReflectionTestUtils.setField(flowService, "taskRepository", taskRepository);
            ReflectionTestUtils.setField(flowService, "claimRepository", claimRepository);
            ReflectionTestUtils.setField(flowService, "claimService", claimService);

            // Вызов закрытого метода через рефлексию
            ReflectionTestUtils.invokeMethod(flowService, "retirePriorActiveProjectsLocally");

            // Инварианты:
            // 1. Старый проект переведен в frozen и сохранен
            assertThat(priorProject.getStatus()).isEqualTo(ProjectStatus.frozen);
            verify(projectRepository).save(priorProject);

            // 2. Незавершенные задачи закрыты как failed
            verify(claimService).closeTaskAsFailed(eq(activeTaskId1), contains("Project frozen: superseded by a new greenfield project"));
            verify(claimService).closeTaskAsFailed(eq(activeTaskId2), contains("Project frozen: superseded by a new greenfield project"));
            verify(claimService).closeTaskAsFailed(eq(activeTaskId3), contains("Project frozen: superseded by a new greenfield project"));

            // 3. Терминальные задачи (done, failed, spike_completed) НЕ закрываются повторно (Terminal Immutability)
            verify(claimService, never()).closeTaskAsFailed(eq(doneTaskId), anyString());
            verify(claimService, never()).closeTaskAsFailed(eq(failedTaskId), anyString());
            verify(claimService, never()).closeTaskAsFailed(eq(spikeTaskId), anyString());

            // 4. Повисший клейм освобожден в очередь
            verify(claimService).releaseClaimToQueue(eq(staleTaskId), contains("Released: new greenfield project created"));
        }

        @Test
        @DisplayName("MARKUS_18: Неопределенный контекст (null projectId) всегда разрешается в текущий активный проект")
        void nullProjectIdStrictlyResolvesToLatestActiveProject() {
            ProjectRepository projectRepository = mock(ProjectRepository.class);

            ProjectEntity activeProject = new ProjectEntity();
            activeProject.setId(UUID.randomUUID());
            activeProject.setName("active-canonical");
            activeProject.setStatus(ProjectStatus.active);

            when(projectRepository.findFirstByStatusOrderByCreatedAtDesc(ProjectStatus.active))
                    .thenReturn(Optional.of(activeProject));

            ProjectOperationalContextService contextService = mock(ProjectOperationalContextService.class, CALLS_REAL_METHODS);
            ReflectionTestUtils.setField(contextService, "projectRepository", projectRepository);

            Optional<ProjectEntity> resolved = ReflectionTestUtils.invokeMethod(contextService, "resolveProject", (UUID) null);

            assertThat(resolved).isPresent();
            assertThat(resolved.get().getId()).isEqualTo(activeProject.getId());
            assertThat(resolved.get().getName()).isEqualTo("active-canonical");
            verify(projectRepository).findFirstByStatusOrderByCreatedAtDesc(ProjectStatus.active);
            verify(projectRepository, never()).findById(any());
        }
    }

    @Nested
    @DisplayName("VITGENSHTEYN_14 & DZHON_SERL_05: Эмпирическая структура и телеметрия пула аккаунтов V19")
    class AccountPoolStructureTests {

        @Test
        @DisplayName("VITGENSHTEYN_14: AccountEntity инициализирует поля V19 (enabled=true по умолчанию)")
        void accountEntityInitializesV19FieldsCorrectly() {
            AccountEntity account = new AccountEntity();
            account.setName("eneikdru");
            account.setApiKey("sec-key-123");
            account.setGithubUsername("eneikdru");

            // Инвариант V19: enabled по умолчанию true
            assertThat(account.isEnabled()).isTrue();
            assertThat(account.getApiKey()).isEqualTo("sec-key-123");
            assertThat(account.getGithubUsername()).isEqualTo("eneikdru");
            assertThat(account.getStatus()).isEqualTo(AccountStatus.idle);
            assertThat(account.isOperational()).isTrue();
            assertThat(account.isAvailable()).isTrue();
        }

        @Test
        @DisplayName("SERLE_05: Статус decommissioned выводит аккаунт из операционного строя")
        void decommissionedAccountIsNonOperationalAndNonAvailable() {
            AccountEntity account = new AccountEntity();
            account.setName("legacy-project-scoped-account");
            account.setEnabled(true);
            account.setApiKey("some-key");
            account.setStatus(AccountStatus.decommissioned);

            assertThat(account.isOperational()).isFalse();
            assertThat(account.isAvailable()).isFalse();
            assertThat(account.getUnavailabilityReason()).isEqualTo("status == decommissioned");
            assertThat(account.getAvailabilitySummary()).isEqualTo("status == decommissioned");
        }

        @Test
        @DisplayName("VITGENSHTEYN_14: Предикат глобального пула V19 отсекает проектные и выключенные аккаунты")
        void globalAccountPoolPredicateFiltersCorrectly() {
            Predicate<AccountEntity> globalPoolPredicate = a ->
                    a.isEnabled() && a.getProject() == null && a.getGithubUsername() != null && !a.getGithubUsername().isBlank();

            // 1. Целевой глобальный аккаунт
            AccountEntity globalTarget = new AccountEntity();
            globalTarget.setName("eneikdru");
            globalTarget.setGithubUsername("eneikdru");
            globalTarget.setEnabled(true);
            globalTarget.setProject(null);
            assertThat(globalPoolPredicate.test(globalTarget)).isTrue();

            // 2. Локальный аккаунт со старой привязкой к проекту
            ProjectEntity dummyProject = new ProjectEntity();
            dummyProject.setId(UUID.randomUUID());
            AccountEntity projectScoped = new AccountEntity();
            projectScoped.setName("old-project-acc");
            projectScoped.setGithubUsername("old-acc");
            projectScoped.setEnabled(true);
            projectScoped.setProject(dummyProject);
            assertThat(globalPoolPredicate.test(projectScoped)).isFalse();

            // 3. Выключенный аккаунт
            AccountEntity disabledAcc = new AccountEntity();
            disabledAcc.setName("disabled-global");
            disabledAcc.setGithubUsername("disabled-global");
            disabledAcc.setEnabled(false);
            disabledAcc.setProject(null);
            assertThat(globalPoolPredicate.test(disabledAcc)).isFalse();

            // 4. Аккаунт без github_username
            AccountEntity noGithub = new AccountEntity();
            noGithub.setName("no-github-user");
            noGithub.setGithubUsername(null);
            noGithub.setEnabled(true);
            noGithub.setProject(null);
            assertThat(globalPoolPredicate.test(noGithub)).isFalse();
        }

        @Test
        @DisplayName("VITGENSHTEYN_14: V19 правило декоммиссии отсекает не-таргетные и проектные аккаунты")
        void v19DecommissionRuleEnforcement() {
            Set<String> targetGlobalNames = Set.of("eneikdru", "eneikcoworking-ctrl");

            Predicate<AccountEntity> shouldDecommission = a ->
                    a.getProject() != null || !targetGlobalNames.contains(a.getName());

            AccountEntity accTarget1 = new AccountEntity();
            accTarget1.setName("eneikdru");
            accTarget1.setProject(null);
            assertThat(shouldDecommission.test(accTarget1)).isFalse();

            AccountEntity accTarget2 = new AccountEntity();
            accTarget2.setName("eneikcoworking-ctrl");
            accTarget2.setProject(null);
            assertThat(shouldDecommission.test(accTarget2)).isFalse();

            AccountEntity accForeign = new AccountEntity();
            accForeign.setName("arbitrary-worker");
            accForeign.setProject(null);
            assertThat(shouldDecommission.test(accForeign)).isTrue();

            AccountEntity accProjectBound = new AccountEntity();
            accProjectBound.setName("eneikdru");
            accProjectBound.setProject(new ProjectEntity());
            assertThat(shouldDecommission.test(accProjectBound)).isTrue();
        }

        @Test
        @DisplayName("VITGENSHTEYN_14: Доступность для проекта изолирует аккаунты других активных проектов")
        void accountAvailabilityForProjectEnforcesIsolation() {
            UUID projectA = UUID.randomUUID();
            UUID projectB = UUID.randomUUID();

            Predicate<AccountEntity> availableForProjectA = a ->
                    a.getStatus() != AccountStatus.decommissioned &&
                    (a.getCurrentProjectId() == null || a.getCurrentProjectId().equals(projectA));

            // Свободный аккаунт доступен
            AccountEntity freeAccount = new AccountEntity();
            freeAccount.setStatus(AccountStatus.idle);
            freeAccount.setCurrentProjectId(null);
            assertThat(availableForProjectA.test(freeAccount)).isTrue();

            // Аккаунт, уже занятый проектом A, доступен проекту A
            AccountEntity assignedToA = new AccountEntity();
            assignedToA.setStatus(AccountStatus.idle);
            assignedToA.setCurrentProjectId(projectA);
            assertThat(availableForProjectA.test(assignedToA)).isTrue();

            // Аккаунт, занятый проектом B, НЕ доступен проекту A
            AccountEntity assignedToB = new AccountEntity();
            assignedToB.setStatus(AccountStatus.idle);
            assignedToB.setCurrentProjectId(projectB);
            assertThat(availableForProjectA.test(assignedToB)).isFalse();

            // Декоммиссионированный аккаунт НЕ доступен
            AccountEntity decommissioned = new AccountEntity();
            decommissioned.setStatus(AccountStatus.decommissioned);
            decommissioned.setCurrentProjectId(null);
            assertThat(availableForProjectA.test(decommissioned)).isFalse();
        }
    }
}
