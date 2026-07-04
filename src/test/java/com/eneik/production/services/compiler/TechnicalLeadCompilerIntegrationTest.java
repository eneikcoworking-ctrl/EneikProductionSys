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

    private UUID projectId;
    private UUID wishlistId;

    @BeforeEach
    public void setup() {
        ProjectEntity project = new ProjectEntity();
        project.setName("Test Project");
        project.setSlug("test-project");
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
    public void testCreateTaskFromWishlistSuccessStep6DesignSystem() {
        // UI role WITH design system ref (since docs/DESIGN_SYSTEM.md exists)
        compiler.compile(wishlistId, "BARCAN-TAG-09", "Когда ситуация, клиент хочет мотивация, чтобы результат",
                LeanValue.essential, "toc", "metric", "BARCAN-TAG-03: must follow docs/DESIGN_SYSTEM.md", "Given/When/Then");

        TaskEntity task = compiler.createTaskFromWishlist(wishlistId);
        assertNotNull(task);
    }
}
