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

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private JulesSessionRepository julesSessionRepository;

    @Autowired
    private WishlistItemRepository wishlistItemRepository;

    private UUID projectId;
    private UUID wishlistId;

    @BeforeEach
    public void setup() {
        julesSessionRepository.deleteAll();
        claimRepository.deleteAll();
        taskRepository.deleteAll();
        wishlistItemRepository.deleteAll();
        wishlistRepository.deleteAll();
        projectHotspotFileRepository.deleteAll();
        stateRepository.deleteAll();
        projectRepository.deleteAll();

        ProjectEntity project = new ProjectEntity();
        project.setName("Test Project");
        project.setSlug("test-project-" + UUID.randomUUID().toString().substring(0, 8));
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
        compiler.compile(wishlistId, "BARCAN-TAG-09", "When a client submits a request, they want the platform to process it reliably so the result can be verified.",
                         LeanValue.essential, "toc", "metric", "Completed according to BARCAN-TAG-02 refusal criteria", "Given something, When action, Then result");

        TaskEntity task = compiler.createTaskFromWishlist(wishlistId);
        assertNotNull(task);
        assertTrue(task.getDescription().startsWith("Role: BARCAN-TAG-02 - Backend API"));
        assertTrue(task.getDescription().contains("Atomic Goal: Implement the smallest backend/API/data change needed for this JTBD slice."));
        assertTrue(task.getDescription().contains("Kano: Must-Be"));
        assertTrue(task.getDescription().contains("Cynefin: clear"));
        assertFalse(task.getDescription().matches(".*[\\p{IsCyrillic}].*"));
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
        compiler.compile(wishlistId, "BARCAN-TAG-09", "When a client submits a request, they want the platform to process it reliably so the result can be verified.",
                LeanValue.essential, "toc", "metric", "Just some text", "Given/When/Then");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> compiler.createTaskFromWishlist(wishlistId));
        assertTrue(ex.getMessage().contains("Step 5 failed: DoD does not reference role refusal criteria (BARCAN-TAG-XX)"));
    }

    @Test
    public void testCreateTaskFromWishlistRejectionStep6() {
        // UI role without pending/design system ref
        compiler.compile(wishlistId, "BARCAN-TAG-09", "When a client submits a request, they want the platform to process it reliably so the result can be verified.",
                LeanValue.essential, "toc", "metric", "BARCAN-TAG-03: must follow rules", "Given/When/Then");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> compiler.createTaskFromWishlist(wishlistId));
        assertTrue(ex.getMessage().contains("Step 6 failed"));
    }

    @Test
    public void testCreateTaskFromWishlistSuccessStep6WithDesignSystem() {
        // UI role WITH docs/DESIGN_SYSTEM.md ref since it exists
        compiler.compile(wishlistId, "BARCAN-TAG-09", "When a client submits a request, they want the platform to process it reliably so the result can be verified.",
                LeanValue.essential, "toc", "metric", "BARCAN-TAG-03: must follow docs/DESIGN_SYSTEM.md", "Given something, When action, Then result");

        TaskEntity task = compiler.createTaskFromWishlist(wishlistId);
        assertNotNull(task);
    }

    @Test
    public void testNonEnglishWishlistDoesNotLeakIntoTaskDescription() {
        WishlistEntity localWish = new WishlistEntity();
        localWish.setProjectId(projectId);
        localWish.setSource(WishlistSource.client);
        localWish.setContent("\u0421\u0434\u0435\u043b\u0430\u0442\u044c \u043b\u0438\u0447\u043d\u044b\u0439 \u043a\u0430\u0431\u0438\u043d\u0435\u0442");
        localWish = wishlistRepository.save(localWish);

        compiler.compile(localWish.getId(), "BARCAN-TAG-09",
                "\u041a\u043b\u0438\u0435\u043d\u0442 \u0445\u043e\u0447\u0435\u0442 \u043b\u0438\u0447\u043d\u044b\u0439 \u043a\u0430\u0431\u0438\u043d\u0435\u0442",
                LeanValue.essential,
                "toc",
                "metric",
                "Completed according to BARCAN-TAG-02 refusal criteria",
                "Given something, When action, Then result");

        TaskEntity task = compiler.createTaskFromWishlist(localWish.getId());

        assertFalse(task.getDescription().matches(".*[\\p{IsCyrillic}].*"));
        assertTrue(task.getDescription().contains("When this slice is delivered"));
        assertTrue(task.getDescription().contains("Do not paste, translate, or re-interpret the original client wish"));
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
        chessWish.setContent("Add a chess AI with a 3D board");
        chessWish = wishlistRepository.save(chessWish);

        compiler.compile(chessWish.getId(), "BARCAN-TAG-09", "When I play chess, I want a 3D board and a computer opponent so I can play a complete game.",
                LeanValue.essential, "toc", "metric", "Completed according to BARCAN-TAG-03 refusal criteria and docs/DESIGN_SYSTEM.md", "Given something, When action, Then result");

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
