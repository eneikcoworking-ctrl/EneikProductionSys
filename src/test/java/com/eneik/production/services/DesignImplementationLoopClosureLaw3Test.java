package com.eneik.production.services;

import com.eneik.production.models.persistence.LeanValue;
import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.RoleRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Law 3 (Закон замкнутости контура):
 * Every department finding becomes a wishlist item and enters normal decomposition, inheriting an epic.
 * Tasks created bypassing compilation do not inherit an epic, breaking the value circuit.
 *
 * Verifies that dispatchDesignImplementation creates a pending WishlistEntity with design source
 * rather than instantiating and queueing a raw orphan TaskEntity.
 */
class DesignImplementationLoopClosureLaw3Test {

    private WishlistRepository wishlistRepository;
    private TaskRepository taskRepository;
    private RoleRepository roleRepository;
    private ProjectFlowService projectFlowService;
    private ProjectEntity project;

    @BeforeEach
    void setUp() {
        wishlistRepository = mock(WishlistRepository.class);
        taskRepository = mock(TaskRepository.class);
        roleRepository = mock(RoleRepository.class);

        // Instantiate ProjectFlowService or mock collaborators
        projectFlowService = org.mockito.Mockito.mock(ProjectFlowService.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(projectFlowService, "wishlistRepository", wishlistRepository);
        ReflectionTestUtils.setField(projectFlowService, "taskRepository", taskRepository);
        ReflectionTestUtils.setField(projectFlowService, "roleRepository", roleRepository);

        project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("Law 3 Test Project");

        when(wishlistRepository.save(any(WishlistEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void dispatchDesignImplementationCreatesWishlistAndNeverCreatesRawTask() {
        String approvedPath = "design/approved/round-3";
        String jtbd = "Match header layout and typography";

        projectFlowService.dispatchDesignImplementation(project, approvedPath, jtbd);

        ArgumentCaptor<WishlistEntity> wishlistCaptor = ArgumentCaptor.forClass(WishlistEntity.class);
        verify(wishlistRepository).save(wishlistCaptor.capture());
        WishlistEntity saved = wishlistCaptor.getValue();

        assertThat(saved.getProjectId()).isEqualTo(project.getId());
        assertThat(saved.getSource()).isEqualTo(WishlistSource.design_review_concern_pattern);
        assertThat(saved.getSourceRoleTag()).isEqualTo("BARCAN-TAG-11");
        assertThat(saved.getStatus()).isEqualTo(WishlistStatus.pending);
        assertThat(saved.getLeanValue()).isEqualTo(LeanValue.essential);
        assertThat(saved.getCynefinDomain()).isEqualTo("clear");
        assertThat(saved.getContent()).contains(approvedPath + "/mockup.html");
        assertThat(saved.getJtbd()).isEqualTo(jtbd);
        assertThat(saved.getAcceptanceCriteria()).isNotBlank();
        assertThat(saved.getDod()).isNotBlank();

        // Structural and behavioral invariant: taskRepository must NEVER be called from dispatchDesignImplementation
        verifyNoInteractions(taskRepository);
    }

    @Test
    void structuralCheckNoDirectTaskCreationInDispatchDesignImplementation() throws IOException {
        Path path = Paths.get("src/main/java/com/eneik/production/services/ProjectFlowService.java");
        String content = Files.readString(path);

        // Extract dispatchDesignImplementation method body
        int start = content.indexOf("public void dispatchDesignImplementation");
        assertThat(start).isGreaterThan(0);
        int end = content.indexOf("public void dispatchDesignConcernTriage", start);
        assertThat(end).isGreaterThan(start);
        String methodBody = content.substring(start, end);

        assertFalse(methodBody.contains("new TaskEntity()"),
                "dispatchDesignImplementation must not instantiate new TaskEntity() (violates Law 3)");
        assertFalse(methodBody.contains("taskRepository.save"),
                "dispatchDesignImplementation must not call taskRepository.save (violates Law 3)");
        assertFalse(methodBody.contains("dispatchToGeneralPool"),
                "dispatchDesignImplementation must route through wishlist decomposition, not dispatchToGeneralPool");
        assertThat(methodBody).contains("wishlistRepository.save");
    }
}
