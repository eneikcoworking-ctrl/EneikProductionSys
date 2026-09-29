package com.eneik.production.services.market;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.models.persistence.TargetContext;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.RoleRepository;
import com.eneik.production.repositories.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Falsification tests for MarketResearchService anchored in Barcan philosopher patterns:
 * 1. ELVIN_GOLDMAN_01_RELIABILITY_CHAIN (D010 Data lineage loss, Goldman):
 *    Empirical reliability of research facts: bounded sample size (5..40), strict refusal of hallucinated URLs,
 *    and tri-state reporting (present/absent/unknown) with date and method metadata.
 * 2. AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY (D006 Boundary topology, Varzi):
 *    Topological boundary isolation: research task targets ORCHESTRATOR_SYSTEM, strictly forbidding
 *    modifications to client codebase or writing product code.
 * 3. DZHOZEF_RAZ_01_PROHIBITION_AS_CODE (D006 Authorization ambiguity, Raz):
 *    Prohibition as executable refusal: throws IllegalStateException when BARCAN-TAG-09 role or carrier
 *    project is missing; enforces standard queue accounting (TaskStatus.queued).
 */
class MarketResearchServiceFalsificationTest {

    private TaskRepository taskRepository;
    private RoleRepository roleRepository;
    private ProjectRepository projectRepository;
    private MarketResearchService service;
    private RoleEntity researchRole;
    private ProjectEntity carrierProject;

    @BeforeEach
    void setUp() {
        taskRepository = mock(TaskRepository.class);
        roleRepository = mock(RoleRepository.class);
        projectRepository = mock(ProjectRepository.class);
        service = new MarketResearchService(taskRepository, roleRepository, projectRepository);

        researchRole = new RoleEntity();
        researchRole.setTag("BARCAN-TAG-09");
        when(roleRepository.findById("BARCAN-TAG-09")).thenReturn(Optional.of(researchRole));

        carrierProject = new ProjectEntity();
        carrierProject.setId(UUID.randomUUID());
        carrierProject.setStatus(ProjectStatus.active);
        carrierProject.setName("carrier-project");
        when(projectRepository.findFirstByStatusOrderByCreatedAtDesc(ProjectStatus.active))
                .thenReturn(Optional.of(carrierProject));

        when(taskRepository.save(any())).thenAnswer(inv -> {
            TaskEntity t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });
    }

    @Test
    @DisplayName("Goldman D010: Sample size is bounded [5, 40] to ensure epistemically reliable empirical evidence")
    void falsifyGoldmanReliabilityChain_sampleSizeBounded() {
        // Below lower bound (1 -> 5)
        service.createResearchTask("ecommerce", "DE", 1);
        ArgumentCaptor<TaskEntity> captorLow = ArgumentCaptor.forClass(TaskEntity.class);
        verify(taskRepository).save(captorLow.capture());
        assertThat(captorLow.getValue().getDescription()).contains("Sample size: examine 5 real");

        // Above upper bound (100 -> 40)
        service.createResearchTask("booking", "US", 100);
        ArgumentCaptor<TaskEntity> captorHigh = ArgumentCaptor.forClass(TaskEntity.class);
        verify(taskRepository, org.mockito.Mockito.atLeastOnce()).save(captorHigh.capture());
        assertThat(captorHigh.getValue().getDescription()).contains("Sample size: examine 40 real");
    }

    @Test
    @DisplayName("Goldman D010: Research prompt and acceptance criteria require verifiable methodology and real URLs")
    void falsifyGoldmanReliabilityChain_methodologyAndVerifiableFactsRequired() {
        service.createResearchTask("streaming", "DE", 15);
        ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
        verify(taskRepository).save(captor.capture());

        TaskEntity task = captor.getValue();
        // Acceptance criteria must enforce empirical observation and method description
        assertThat(task.getAcceptanceCriteria())
                .contains("date and the method used")
                .contains("marks any figure it could not source as absent rather than estimating it");

        // Prompt must forbid guessing and fake URLs
        assertThat(task.getDescription())
                .contains("Never invent a URL")
                .contains("Produce a measurement, not an opinion")
                .contains("observedAt")
                .contains("productsExamined");
    }

    @Test
    @DisplayName("Varzi D006: TargetContext is strictly ORCHESTRATOR_SYSTEM, isolating client product repos")
    void falsifyVarziBoundaryTopology_clientProductReposIsolated() {
        service.createResearchTask("fintech", "US", 10);
        ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
        verify(taskRepository).save(captor.capture());

        TaskEntity task = captor.getValue();
        // Target context must be ORCHESTRATOR_SYSTEM, not the client's repository
        assertThat(task.getTargetContext()).isEqualTo(TargetContext.ORCHESTRATOR_SYSTEM);

        // Prompt explicitly confines work to factory market-corpus and bans product code modification
        assertThat(task.getDescription())
                .contains("Do not modify any other file. Do not write product code.")
                .contains("market-corpus/observations/");
    }

    @Test
    @DisplayName("Raz D006: Refusal as code when BARCAN-TAG-09 role is absent")
    void falsifyRazProhibitionAsCode_missingRoleThrowsRefusal() {
        when(roleRepository.findById("BARCAN-TAG-09")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createResearchTask("saas", "DE", 10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Role BARCAN-TAG-09 not found; cannot create research task");
    }

    @Test
    @DisplayName("Raz D006: Refusal as code when no carrier project exists to hold relational task")
    void falsifyRazProhibitionAsCode_missingCarrierProjectThrowsRefusal() {
        when(projectRepository.findFirstByStatusOrderByCreatedAtDesc(ProjectStatus.active))
                .thenReturn(Optional.empty());
        when(projectRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(Collections.emptyList());
        when(projectRepository.findAll())
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> service.createResearchTask("saas", "DE", 10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No project exists to carry the research task");
    }

    @Test
    @DisplayName("Raz D006: Task status is initialized to queued, respecting shared Jules accounting")
    void falsifyRazProhibitionAsCode_taskStatusInitializedToQueued() {
        service.createResearchTask("logistics", "DE", 12);
        ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
        verify(taskRepository).save(captor.capture());

        assertThat(captor.getValue().getStatus()).isEqualTo(TaskStatus.queued);
    }
}
