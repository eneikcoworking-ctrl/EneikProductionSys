package com.eneik.production.services.compiler;

import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class TechnicalLeadCompilerIntegrationTest {

    @Autowired
    private TechnicalLeadCompiler compiler;

    @Autowired
    private WishlistRepository wishlistRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectGenerationStateRepository stateRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private RoleRepository roleRepository;

    private UUID projectId;
    private UUID wishlistId;

    @BeforeEach
    public void setup() {
        ProjectEntity project = new ProjectEntity();
        project.setName("Test Project");
        project.setSlug("test-project-" + UUID.randomUUID().toString());
        project.setStatus(ProjectStatus.active);
        project.setRepositoryName("test-repo");
        project.setRepoUrl("http://github.com/test");
        project = projectRepository.save(project);
        projectId = project.getId();

        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setProjectId(projectId);
        wishlist.setSource(WishlistSource.client);
        wishlist.setContent("Test wish");
        wishlist = wishlistRepository.save(wishlist);
        wishlistId = wishlist.getId();
    }

    @Test
    public void testStopGenerationIdempotency() {
        compiler.stopGeneration(projectId);
        ProjectGenerationStateEntity state1 = stateRepository.findById(projectId).orElseThrow();
        assertTrue(state1.isGenerationStopped());

        compiler.stopGeneration(projectId);
        ProjectGenerationStateEntity state2 = stateRepository.findById(projectId).orElseThrow();
        assertTrue(state2.isGenerationStopped());
        // Should not fail
    }

    @Test
    public void testCreateTaskFromWishlistRejection() {
        // Not compiled yet
        assertThrows(IllegalStateException.class, () -> compiler.createTaskFromWishlist(wishlistId));

        // Compiled by wrong role
        compiler.compile(wishlistId, "WRONG-ROLE", "jtbd", LeanValue.essential, "toc", "metric", "dod", "Given/When/Then");
        assertThrows(IllegalStateException.class, () -> compiler.createTaskFromWishlist(wishlistId));

        // Missing fields
        compiler.compile(wishlistId, "BARCAN-TAG-09", "", LeanValue.essential, "toc", "metric", "dod", "Given/When/Then");
        assertThrows(IllegalStateException.class, () -> compiler.createTaskFromWishlist(wishlistId));
    }

    @Test
    public void testCreateTaskFromWishlistSuccess() {
        compiler.compile(wishlistId, "BARCAN-TAG-09", "Когда ситуация, клиент хочет мотивация, чтобы результат",
                         LeanValue.essential, "toc", "metric", "Выполнено согласно BARCAN-TAG-02 критерий 1", "Given something, When action, Then result");

        TaskEntity task = compiler.createTaskFromWishlist(wishlistId);
        assertNotNull(task);
        assertEquals("Test wish", task.getDescription());
        assertEquals(TaskStatus.queued, task.getStatus());
        assertTrue(task.isQualityGatePassed());
        assertNotNull(task.getQualityGateReport());
        assertEquals(5, task.getQualityGateReport().get("checks").size());

        WishlistEntity wishlist = wishlistRepository.findById(wishlistId).orElseThrow();
        assertEquals(WishlistStatus.converted_to_task, wishlist.getStatus());
    }

    @Test
    public void testCreateTaskFromWishlistRejectionStep5() {
        // DoD without BARCAN-TAG reference
        compiler.compile(wishlistId, "BARCAN-TAG-09", "Когда ситуация, клиент хочет мотивация, чтобы результат",
                LeanValue.essential, "toc", "metric", "Just some text", "Given/When/Then");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> compiler.createTaskFromWishlist(wishlistId));
        assertTrue(ex.getMessage().contains("Шаг 5 не пройден: DoD не ссылается на Refusal Criteria роли (BARCAN-TAG-XX)"));
    }

    @Test
    public void testCreateTaskFromWishlistRejectionStep6() {
        // UI role without pending/design system ref
        compiler.compile(wishlistId, "BARCAN-TAG-09", "Когда ситуация, клиент хочет мотивация, чтобы результат",
                LeanValue.essential, "toc", "metric", "BARCAN-TAG-03: must follow rules", "Given/When/Then");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> compiler.createTaskFromWishlist(wishlistId));
        assertTrue(ex.getMessage().contains("Шаг 6 не пройден"));
    }

    @Test
    public void testCreateTaskFromWishlistSuccessStep6WithDesignSystem() {
        // UI role WITH docs/DESIGN_SYSTEM.md ref since it exists
        compiler.compile(wishlistId, "BARCAN-TAG-09", "Когда ситуация, клиент хочет мотивация, чтобы результат",
                LeanValue.essential, "toc", "metric", "BARCAN-TAG-03: must follow docs/DESIGN_SYSTEM.md", "Given something, When action, Then result");

        TaskEntity task = compiler.createTaskFromWishlist(wishlistId);
        assertNotNull(task);
    }

    @Test
    public void testLockNextQueuedTaskDependencies() {
        // Create parent task
        TaskEntity parent = new TaskEntity();
        parent.setProject(projectRepository.findById(projectId).orElseThrow());
        parent.setDescription("Parent Task");
        parent.setRole(roleRepository.findById("BARCAN-TAG-02").orElseThrow());
        parent.setStatus(TaskStatus.queued);
        parent = taskRepository.save(parent);

        // Create dependent task
        TaskEntity dependent = new TaskEntity();
        dependent.setProject(projectRepository.findById(projectId).orElseThrow());
        dependent.setDescription("Dependent Task");
        dependent.setRole(roleRepository.findById("BARCAN-TAG-11").orElseThrow());
        dependent.setStatus(TaskStatus.queued);
        dependent.setDependsOn(parent.getId());
        dependent = taskRepository.save(dependent);

        // Attempt to lock tasks for project
        java.util.Optional<TaskEntity> locked = taskRepository.lockNextQueuedTaskForProject(projectId);
        assertTrue(locked.isPresent());
        assertEquals(parent.getId(), locked.get().getId(), "Should lock parent task first because dependent has unresolved dependencies");

        // Mark parent as done
        parent.setStatus(TaskStatus.done);
        taskRepository.save(parent);

        // Attempt to lock again - should now lock dependent
        java.util.Optional<TaskEntity> lockedNext = taskRepository.lockNextQueuedTaskForProject(projectId);
        assertTrue(lockedNext.isPresent());
        assertEquals(dependent.getId(), lockedNext.get().getId(), "Should lock dependent task now that parent is done");
    }

    @Test
    public void testIntegrationTaskScopeAndDependencies() {
        // Use a UI wishlist to trigger multiple tasks creation (Design, Backend, Frontend, Integration, QA)
        WishlistEntity wish = new WishlistEntity();
        wish.setProjectId(projectId);
        wish.setSource(WishlistSource.client);
        wish.setContent("Test wish with UI interface");
        wish = wishlistRepository.save(wish);

        compiler.compile(wish.getId(), "BARCAN-TAG-09", "jtbd", LeanValue.essential, "toc", "metric", "BARCAN-TAG-03 docs/DESIGN_SYSTEM.md", "ac");

        TaskEntity resultTask = compiler.createTaskFromWishlist(wish.getId());
        assertNotNull(resultTask);

        java.util.List<TaskEntity> tasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        assertEquals(5, tasks.size(), "Should generate Design, Backend, Frontend, Integration, and QA tasks");

        TaskEntity designTask = tasks.stream().filter(t -> t.getRole().getTag().equals("BARCAN-TAG-03")).findFirst().orElseThrow();
        TaskEntity backendTask = tasks.stream().filter(t -> t.getRole().getTag().equals("BARCAN-TAG-02")).findFirst().orElseThrow();
        TaskEntity frontendTask = tasks.stream().filter(t -> t.getRole().getTag().equals("BARCAN-TAG-11")).findFirst().orElseThrow();
        TaskEntity integrationTask = tasks.stream().filter(t -> t.getRole().getTag().equals("BARCAN-TAG-00")).findFirst().orElseThrow();
        TaskEntity qaTask = tasks.stream().filter(t -> t.getRole().getTag().equals("BARCAN-TAG-06")).findFirst().orElseThrow();

        // Dependencies Check
        assertNull(designTask.getDependsOn());
        assertNull(backendTask.getDependsOn());
        assertEquals(designTask.getId(), frontendTask.getDependsOn());
        assertEquals(frontendTask.getId(), integrationTask.getDependsOn());
        assertEquals(integrationTask.getId(), qaTask.getDependsOn());

        // File Scope Check
        // The file scope is a JSON array string containing paths.
        // Even for specific files, the path contains '/', e.g., 'src/main/java/com/eneik/production/services/NewService.java'
        assertTrue(backendTask.getFileScope().contains("NewService.java"));
        assertFalse(backendTask.getFileScope().equals("[\"src/main/java/com/eneik/production/services/\"]"));

        assertTrue(frontendTask.getFileScope().contains("NewComponent.svelte"));
        assertFalse(frontendTask.getFileScope().equals("[\"frontend/src/components/\"]")); // Not just the generic directory
    }
}
