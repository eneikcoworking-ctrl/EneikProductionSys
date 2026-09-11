package com.eneik.production.controllers;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.PersistentWorkerPurpose;
import com.eneik.production.models.persistence.PersistentWorkerSessionEntity;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.CoherenceRunRepository;
import com.eneik.production.repositories.EvidenceNodeRepository;
import com.eneik.production.repositories.GeminiObserverActionRepository;
import com.eneik.production.repositories.GeminiObserverJournalRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.OperationalRealityFindingRepository;
import com.eneik.production.repositories.PersistentWorkerSessionRepository;
import com.eneik.production.repositories.PrReviewRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.ContinuousOrchestrationService;
import com.eneik.production.services.GeminiObserverActionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for InternalGeminiObserverController repairing Section XXIII and Item 8 defect
 * (NUEL_BELNAP_03_TRUTH_STATUS_TABLE, DZHOZEF_RAZ_01_PROHIBITION_AS_CODE / D012, D006).
 */
class InternalGeminiObserverControllerTest {

    private ProjectRepository projectRepository;
    private PersistentWorkerSessionRepository persistentWorkerSessionRepository;
    private AccountRepository accountRepository;
    private InternalGeminiObserverController controller;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        persistentWorkerSessionRepository = mock(PersistentWorkerSessionRepository.class);
        accountRepository = mock(AccountRepository.class);

        controller = new InternalGeminiObserverController(
                mock(GeminiObserverJournalRepository.class),
                mock(GeminiObserverActionRepository.class),
                mock(EvidenceNodeRepository.class),
                mock(CoherenceRunRepository.class),
                mock(OperationalRealityFindingRepository.class),
                mock(JulesSessionRepository.class),
                mock(PrReviewRepository.class),
                accountRepository,
                mock(TaskRepository.class),
                mock(ContinuousOrchestrationService.class),
                mock(GeminiObserverActionService.class),
                projectRepository,
                persistentWorkerSessionRepository,
                mock(JdbcTemplate.class),
                mock(WishlistRepository.class)
        );
    }

    @Test
    @DisplayName("persistentWorkers with explicit projectId returns workers for that project")
    void persistentWorkersWithExplicitProjectId() {
        UUID projectId = UUID.randomUUID();
        PersistentWorkerSessionEntity worker = new PersistentWorkerSessionEntity();
        worker.setId(UUID.randomUUID());
        worker.setProjectId(projectId);
        worker.setPurpose(PersistentWorkerPurpose.WISHLIST_COMPILER);
        worker.setCreatedAt(Instant.now());

        when(persistentWorkerSessionRepository.findByProjectId(projectId)).thenReturn(List.of(worker));

        List<Map<String, Object>> result = controller.persistentWorkers(projectId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("id")).isEqualTo(worker.getId());
        assertThat(result.get(0).get("projectId")).isEqualTo(projectId);
        assertThat(result.get(0).get("purpose")).isEqualTo(PersistentWorkerPurpose.WISHLIST_COMPILER);
        verify(persistentWorkerSessionRepository).findByProjectId(projectId);
    }

    @Test
    @DisplayName("persistentWorkers without projectId falls back to single active project")
    void persistentWorkersWithoutProjectIdResolvesActiveProject() {
        UUID activeProjectId = UUID.randomUUID();
        ProjectEntity activeProject = new ProjectEntity();
        activeProject.setId(activeProjectId);
        activeProject.setStatus(ProjectStatus.active);

        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active))
                .thenReturn(List.of(activeProject));

        PersistentWorkerSessionEntity worker = new PersistentWorkerSessionEntity();
        worker.setId(UUID.randomUUID());
        worker.setProjectId(activeProjectId);
        when(persistentWorkerSessionRepository.findByProjectId(activeProjectId)).thenReturn(List.of(worker));

        List<Map<String, Object>> result = controller.persistentWorkers(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("id")).isEqualTo(worker.getId());
        verify(persistentWorkerSessionRepository).findByProjectId(activeProjectId);
    }

    @Test
    @DisplayName("persistentWorkers without projectId falls back to findAll when no active project exists")
    void persistentWorkersWithoutProjectIdFallsBackToAllWhenNoActiveProject() {
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active))
                .thenReturn(List.of());

        PersistentWorkerSessionEntity worker = new PersistentWorkerSessionEntity();
        worker.setId(UUID.randomUUID());
        when(persistentWorkerSessionRepository.findAll()).thenReturn(List.of(worker));

        List<Map<String, Object>> result = controller.persistentWorkers(null);

        assertThat(result).hasSize(1);
        verify(persistentWorkerSessionRepository).findAll();
    }

    @Test
    @DisplayName("dispatchCapacityProbe with explicit parameters queries capacity accurately")
    void dispatchCapacityProbeWithExplicitParams() {
        UUID projectId = UUID.randomUUID();
        String tag = "BARCAN-TAG-05";

        AccountEntity account = new AccountEntity();
        account.setName("EneikGroup");
        when(accountRepository.lockNextJulesAccountWithCapacity(eq(projectId), eq(tag), eq(3), any(), eq(15), any()))
                .thenReturn(Optional.of(account));

        Map<String, Object> result = controller.dispatchCapacityProbe(projectId, tag);

        assertThat(result.get("found")).isEqualTo(true);
        assertThat(result.get("tag")).isEqualTo(tag);
        assertThat(result.get("projectId")).isEqualTo(projectId);
        assertThat(result.get("accountName")).isEqualTo("EneikGroup");
    }

    @Test
    @DisplayName("dispatchCapacityProbe without params resolves active project and defaults tag to BARCAN-TAG-11")
    void dispatchCapacityProbeWithoutParamsResolvesActiveProjectAndDefaultTag() {
        UUID activeProjectId = UUID.randomUUID();
        ProjectEntity activeProject = new ProjectEntity();
        activeProject.setId(activeProjectId);
        activeProject.setStatus(ProjectStatus.active);

        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active))
                .thenReturn(List.of(activeProject));

        AccountEntity account = new AccountEntity();
        account.setName("JulesWorker");
        when(accountRepository.lockNextJulesAccountWithCapacity(eq(activeProjectId), eq("BARCAN-TAG-11"), eq(3), any(), eq(15), any()))
                .thenReturn(Optional.of(account));

        Map<String, Object> result = controller.dispatchCapacityProbe(null, "BARCAN-TAG-11");

        assertThat(result.get("found")).isEqualTo(true);
        assertThat(result.get("tag")).isEqualTo("BARCAN-TAG-11");
        assertThat(result.get("projectId")).isEqualTo(activeProjectId);
        assertThat(result.get("accountName")).isEqualTo("JulesWorker");
    }

    @Test
    @DisplayName("dispatchCapacityProbe without active project returns UNDETERMINED_PROJECT without 500 error")
    void dispatchCapacityProbeWithoutActiveProjectReturnsUndeterminedStatus() {
        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active))
                .thenReturn(List.of());

        Map<String, Object> result = controller.dispatchCapacityProbe(null, "BARCAN-TAG-11");

        assertThat(result.get("found")).isEqualTo(false);
        assertThat(result.get("status")).isEqualTo("UNDETERMINED_PROJECT");
        assertThat(result.get("tag")).isEqualTo("BARCAN-TAG-11");
        assertThat(result).containsKey("message");
    }
}
