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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketResearchServiceTest {

    private TaskRepository taskRepository;
    private RoleRepository roleRepository;
    private ProjectRepository projectRepository;
    private MarketResearchService service;

    @BeforeEach
    void setUp() {
        taskRepository = mock(TaskRepository.class);
        roleRepository = mock(RoleRepository.class);
        projectRepository = mock(ProjectRepository.class);
        service = new MarketResearchService(taskRepository, roleRepository, projectRepository);

        RoleEntity researchRole = new RoleEntity();
        researchRole.setTag("BARCAN-TAG-09");
        when(roleRepository.findById("BARCAN-TAG-09")).thenReturn(Optional.of(researchRole));

        when(taskRepository.save(any())).thenAnswer(inv -> {
            TaskEntity t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });
    }

    @Test
    void createResearchTaskUsesActiveProjectAsCarrier() {
        ProjectEntity activeProject = new ProjectEntity();
        activeProject.setId(UUID.randomUUID());
        activeProject.setStatus(ProjectStatus.active);
        activeProject.setName("active-shop");

        when(projectRepository.findFirstByStatusOrderByCreatedAtDesc(ProjectStatus.active))
                .thenReturn(Optional.of(activeProject));

        UUID taskId = service.createResearchTask("shop", "DE", 20);

        assertThat(taskId).isNotNull();
        ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
        verify(taskRepository).save(captor.capture());

        TaskEntity saved = captor.getValue();
        assertThat(saved.getProject()).isEqualTo(activeProject);
        assertThat(saved.getTargetContext()).isEqualTo(TargetContext.ORCHESTRATOR_SYSTEM);
        assertThat(saved.getStatus()).isEqualTo(TaskStatus.queued);
        assertThat(saved.getTitle()).contains("shop DE");
        assertThat(saved.getDescription()).contains("Sample size: examine 20 real");
    }

    @Test
    void createResearchTaskBoundsSampleSizeBetween5And40() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        when(projectRepository.findFirstByStatusOrderByCreatedAtDesc(ProjectStatus.active))
                .thenReturn(Optional.of(project));

        service.createResearchTask("booking", "US", 2); // below min 5

        ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
        verify(taskRepository).save(captor.capture());
        assertThat(captor.getValue().getDescription()).contains("Sample size: examine 5 real");
    }

    @Test
    void throwsWhenNoCarrierProjectExists() {
        when(projectRepository.findFirstByStatusOrderByCreatedAtDesc(ProjectStatus.active))
                .thenReturn(Optional.empty());
        when(projectRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(java.util.Collections.emptyList());
        when(projectRepository.findAll())
                .thenReturn(java.util.Collections.emptyList());

        assertThrows(IllegalStateException.class, () -> service.createResearchTask("shop", "DE", 10));
    }
}
