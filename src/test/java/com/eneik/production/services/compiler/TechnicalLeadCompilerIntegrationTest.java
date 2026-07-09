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
    private ProjectHotspotFileRepository projectHotspotFileRepository;

    private UUID projectId;
    private UUID wishlistId;

    @BeforeEach
    public void setup() {
        ProjectEntity project = new ProjectEntity();
        project.setName("Test Project");
        project.setSlug("test-project");
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
    public void testTaskDependenciesAndGranularScopesAndHotspots() {
        // Register some project hotspot files
        ProjectHotspotFileEntity hotspot = new ProjectHotspotFileEntity();
        hotspot.setProjectId(projectId);
        hotspot.setFilePath("frontend/src/App.svelte");
        projectHotspotFileRepository.save(hotspot);

        // Compile chess wish (which has UI and is Chess)
        WishlistEntity chessWish = new WishlistEntity();
        chessWish.setProjectId(projectId);
        chessWish.setSource(WishlistSource.client);
        chessWish.setContent("добавить шахматный ИИ с 3D доской");
        chessWish = wishlistRepository.save(chessWish);

        compiler.compile(chessWish.getId(), "BARCAN-TAG-09", "Хочу играть в шахматы",
                LeanValue.essential, "toc", "metric", "Выполнено согласно BARCAN-TAG-03 критерий 1 и docs/DESIGN_SYSTEM.md", "Given something, When action, Then result");

        // Convert to tasks
        compiler.createTaskFromWishlist(chessWish.getId());

        // Find all tasks of the project
        java.util.List<TaskEntity> tasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId);

        // The chess wish is decomposed into 5 tasks: Design, Backend, Frontend, Integration, QA
        // Let's filter tasks by roles
        TaskEntity designTask = null;
        TaskEntity backendTask = null;
        TaskEntity frontendTask = null;
        TaskEntity integrationTask = null;
        TaskEntity qaTask = null;

        for (TaskEntity t : tasks) {
            if ("BARCAN-TAG-03".equals(t.getRole().getTag())) {
                designTask = t;
            } else if ("BARCAN-TAG-02".equals(t.getRole().getTag())) {
                backendTask = t;
            } else if ("BARCAN-TAG-11".equals(t.getRole().getTag())) {
                frontendTask = t;
            } else if ("BARCAN-TAG-00".equals(t.getRole().getTag())) {
                integrationTask = t;
            } else if ("BARCAN-TAG-06".equals(t.getRole().getTag())) {
                qaTask = t;
            }
        }

        assertNotNull(designTask);
        assertNotNull(backendTask);
        assertNotNull(frontendTask);
        assertNotNull(integrationTask);
        assertNotNull(qaTask);

        System.out.println("DESIGN TASK FILESCOPE: " + designTask.getFileScope());
        System.out.println("BACKEND TASK FILESCOPE: " + backendTask.getFileScope());
        System.out.println("FRONTEND TASK FILESCOPE: " + frontendTask.getFileScope());

        // 1. Granular file scopes check (should contain specific files, not generic directories)
        assertTrue(designTask.getFileScope().contains("frontend/src/components/ChessBoard.svelte"));
        assertTrue(backendTask.getFileScope().contains("src/main/java/com/eneik/production/services/ChessService.java"));
        assertTrue(frontendTask.getFileScope().contains("frontend/src/components/ChessBoard.svelte"));
        // Hotspot files are NOT in frontend task since integration task exists
        assertFalse(frontendTask.getFileScope().contains("frontend/src/App.svelte"));
        // Integration task (TAG-00) gets hotspot files
        assertTrue(integrationTask.getFileScope().contains("frontend/src/App.svelte"));

        // 2. Explicit dependency graph check
        assertEquals(designTask.getId(), frontendTask.getDependsOn().getId());
        assertEquals(frontendTask.getId(), integrationTask.getDependsOn().getId());
        assertEquals(integrationTask.getId(), qaTask.getDependsOn().getId());

        // 3. lockNextQueuedTask testing
        // Trying to claim frontendTask (TAG-11) when designTask (TAG-03) is queued: should fail/skip
        java.util.Optional<TaskEntity> lockedFrontend = taskRepository.lockNextQueuedTask(java.util.List.of("BARCAN-TAG-11"));
        assertFalse(lockedFrontend.isPresent());

        // Now resolve dependency by marking designTask as done
        designTask.setStatus(TaskStatus.done);
        taskRepository.save(designTask);

        // Try claiming again: should succeed
        lockedFrontend = taskRepository.lockNextQueuedTask(java.util.List.of("BARCAN-TAG-11"));
        assertTrue(lockedFrontend.isPresent());
        assertEquals(frontendTask.getId(), lockedFrontend.get().getId());
    }
}
