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
    private ProjectFileClaimRepository projectFileClaimRepository;

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
        projectFileClaimRepository.deleteAll();
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

    /**
     * 2026-08-13 (live incident: test-forty-fourth). generationStopped has exactly one writer -
     * stopGeneration(), called only from acceptProject() - so if it's still true while the project's real
     * status is active, that's an orphaned leftover from a past acceptance/status correction, not a
     * legitimate block (there is no supported path back from accepted to active, so an active project can
     * never legitimately have this flag set). Compilation must self-heal it instead of blocking forever
     * with no way to clear it.
     */
    @Test
    public void testCreateTaskFromWishlistSelfHealsOrphanedGenerationStoppedFlagOnActiveProject() {
        compiler.stopGeneration(projectId);
        assertTrue(stateRepository.findById(projectId).orElseThrow().isGenerationStopped());

        compiler.compile(wishlistId, "BARCAN-TAG-09", "When a client submits a request, they want the platform to process it reliably so the result can be verified.",
                         LeanValue.essential, "toc", "metric", "Completed according to BARCAN-TAG-02 refusal criteria", "Given something, When action, Then result");

        TaskEntity task = compiler.createTaskFromWishlist(wishlistId);
        assertNotNull(task);
        assertFalse(stateRepository.findById(projectId).orElseThrow().isGenerationStopped());
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
    public void testNonEnglishWishlistContentReachesTaskDescription() {
        // Task compilation no longer routes through Gemini (see ProjectFlowService.resolveTaskSlices),
        // so the real original wishlist text - in whatever language it's in - is the only signal Jules
        // ever receives. Previously this was deliberately stripped out for non-English content, which
        // meant Jules never saw the actual client ask when the JTBD/acceptance-criteria fields were
        // generic (e.g. during a Gemini outage) - exactly the bug that produced hours of fabricated,
        // self-referential task titles disconnected from the real (Russian) client brief.
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

        assertTrue(task.getDescription().contains("\u0421\u0434\u0435\u043b\u0430\u0442\u044c \u043b\u0438\u0447\u043d\u044b\u0439 \u043a\u0430\u0431\u0438\u043d\u0435\u0442"));
        assertTrue(task.getDescription().contains("Original Brief"));
        assertTrue(task.getDescription().contains("When this slice is delivered"));
        assertTrue(task.getDescription().contains("do not paste the raw Original Brief verbatim into the PR narrative"));
    }

    @Test
    public void testSingleOwnerRoleAndGranularScope() {
        // Register some project hotspot files
        ProjectHotspotFileEntity hotspot = new ProjectHotspotFileEntity();
        hotspot.setProjectId(projectId);
        hotspot.setFilePath("frontend/src/App.svelte");
        projectHotspotFileRepository.save(hotspot);

        // Compile chess wish (which has UI and is Chess)
        WishlistEntity chessWish = new WishlistEntity();
        chessWish.setProjectId(projectId);
        chessWish.setSource(WishlistSource.client);
        chessWish.setSourceRoleTag("BARCAN-TAG-03");
        chessWish.setContent("Add a chess AI with a 3D board");
        chessWish = wishlistRepository.save(chessWish);

        compiler.compile(chessWish.getId(), "BARCAN-TAG-09", "When I play chess, I want a 3D board and a computer opponent so I can play a complete game.",
                LeanValue.essential, "toc", "metric", "Completed according to BARCAN-TAG-03 refusal criteria and docs/DESIGN_SYSTEM.md", "Given something, When action, Then result");

        // Convert to tasks
        TaskEntity task = compiler.createTaskFromWishlist(chessWish.getId());

        // Find all tasks of the project
        java.util.List<TaskEntity> tasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId);

        assertEquals(1, tasks.size());
        assertEquals(task.getId(), tasks.get(0).getId());
        assertEquals("BARCAN-TAG-03", task.getRole().getTag());
        assertNull(task.getDependsOn());
        assertTrue(task.getFileScope().contains("frontend/src/components/ChessBoard.svelte"));
        assertFalse(task.getFileScope().contains("src/main/java/com/eneik/production/services/ChessService.java"));
        assertFalse(task.getFileScope().contains("frontend/src/App.svelte"));
    }

    // Smart-decomposition v2 (2026-07-31): general, code-enforced cross-эпик file-collision guard,
    // replacing the earlier same-day compiler-prompt "ceiling rule" the operator rejected as a заплатка.

    @Test
    public void secondEpicsTaskHasAlreadyClaimedHotspotStrippedFromFileScopeWithDirective() {
        ProjectHotspotFileEntity hotspot = new ProjectHotspotFileEntity();
        hotspot.setProjectId(projectId);
        hotspot.setFilePath("frontend/src/App.svelte");
        projectHotspotFileRepository.save(hotspot);

        // First эпик's TAG-11 task claims frontend/src/App.svelte via the hotspot-broadcast branch
        // (isIntegrationTask=false, hasIntegrationTask=false triggers it for BARCAN-TAG-11).
        WishlistEntity firstWish = new WishlistEntity();
        firstWish.setProjectId(projectId);
        firstWish.setSource(WishlistSource.client);
        firstWish.setContent("Build a dashboard screen");
        firstWish = wishlistRepository.save(firstWish);
        compiler.compile(firstWish.getId(), "BARCAN-TAG-09",
                "When I view the dashboard, I want to see my data so I can make decisions.",
                LeanValue.essential, "toc", "metric",
                "Completed according to BARCAN-TAG-11 refusal criteria and docs/DESIGN_SYSTEM.md",
                "Given something, When action, Then result");
        TaskEntity firstTask = compiler.createTaskFromWishlist(firstWish.getId());
        assertTrue(firstTask.getFileScope().contains("frontend/src/App.svelte"));

        // Second, genuinely different эпик (a fresh wishlist with no featureId gets its own new feature -
        // see FeatureService.resolveOrCreateFeatureId) - its TAG-11 task must not be assigned the same
        // already-claimed path, and its description must carry the collision directive.
        WishlistEntity secondWish = new WishlistEntity();
        secondWish.setProjectId(projectId);
        secondWish.setSource(WishlistSource.client);
        secondWish.setContent("Build a knowledge base search screen");
        secondWish = wishlistRepository.save(secondWish);
        compiler.compile(secondWish.getId(), "BARCAN-TAG-09",
                "When I search the knowledge base, I want relevant results so I can find answers.",
                LeanValue.essential, "toc", "metric",
                "Completed according to BARCAN-TAG-11 refusal criteria and docs/DESIGN_SYSTEM.md",
                "Given something, When action, Then result");
        TaskEntity secondTask = compiler.createTaskFromWishlist(secondWish.getId());

        assertNotEquals(firstTask.getFeatureId(), secondTask.getFeatureId());
        assertFalse(secondTask.getFileScope().contains("frontend/src/App.svelte"));
        assertTrue(secondTask.getDescription().contains("CROSS-EPIC RESOURCE GUARD"));
        assertTrue(secondTask.getDescription().contains("frontend/src/App.svelte"));
    }

    @Test
    public void secondTaskInTheSameEpicKeepsTheSharedPathItAlreadyOwns() {
        ProjectHotspotFileEntity hotspot = new ProjectHotspotFileEntity();
        hotspot.setProjectId(projectId);
        hotspot.setFilePath("frontend/src/App.svelte");
        projectHotspotFileRepository.save(hotspot);

        WishlistEntity firstWish = new WishlistEntity();
        firstWish.setProjectId(projectId);
        firstWish.setSource(WishlistSource.client);
        firstWish.setContent("Build a dashboard screen");
        firstWish = wishlistRepository.save(firstWish);
        compiler.compile(firstWish.getId(), "BARCAN-TAG-09",
                "When I view the dashboard, I want to see my data so I can make decisions.",
                LeanValue.essential, "toc", "metric",
                "Completed according to BARCAN-TAG-11 refusal criteria and docs/DESIGN_SYSTEM.md",
                "Given something, When action, Then result");
        TaskEntity firstTask = compiler.createTaskFromWishlist(firstWish.getId());

        // Same эпик as firstWish - explicitly reuses its featureId rather than letting a fresh one be minted.
        WishlistEntity secondWish = new WishlistEntity();
        secondWish.setProjectId(projectId);
        secondWish.setSource(WishlistSource.client);
        secondWish.setFeatureId(firstTask.getFeatureId());
        secondWish.setContent("Add a settings panel to the same dashboard");
        secondWish = wishlistRepository.save(secondWish);
        compiler.compile(secondWish.getId(), "BARCAN-TAG-09",
                "When I configure the dashboard, I want a settings panel so I can customize it.",
                LeanValue.essential, "toc", "metric",
                "Completed according to BARCAN-TAG-11 refusal criteria and docs/DESIGN_SYSTEM.md",
                "Given something, When action, Then result");
        TaskEntity secondTask = compiler.createTaskFromWishlist(secondWish.getId());

        assertEquals(firstTask.getFeatureId(), secondTask.getFeatureId());
        assertTrue(secondTask.getFileScope().contains("frontend/src/App.svelte"));
        assertFalse(secondTask.getDescription().contains("CROSS-EPIC RESOURCE GUARD"));
    }
}
