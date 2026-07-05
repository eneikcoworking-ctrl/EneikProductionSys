package com.eneik.production.services;

import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.*;
import com.eneik.production.services.compiler.TechnicalLeadCompiler;
import com.eneik.production.services.monitor.PrReviewPipelineService;
import com.eneik.production.dto.monitor.PrDataDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AutonomousPipelineIntegrationTest {

    @Autowired
    private WishlistItemRepository wishlistItemRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private PrReviewRepository prReviewRepository;

    @Autowired
    private TechnicalLeadCompiler technicalLeadCompiler;

    @Autowired
    private ContinuousOrchestrationService continuousOrchestrationService;

    @Autowired
    private PrReviewPipelineService prReviewPipelineService;

    @Autowired
    private AutoMergeService autoMergeService;

    @Test
    void testFullAutonomousPipelineLoop() {
        // 1. Setup Project
        ProjectEntity project = new ProjectEntity();
        project.setName("Autonomous Verification");
        project.setSlug("auto-verify");
        project.setStatus(ProjectStatus.active);
        project.setRepositoryName("auto-verify-repo");
        project = projectRepository.saveAndFlush(project);

        // 2. Add Verification Task to Wishlist
        WishlistItemEntity item = new WishlistItemEntity();
        item.setProject(project);
        item.setText("Verification-Task-Alpha-2026");
        item.setStatus(WishlistItemStatus.open);
        item.setType(WishlistItemType.client_wish);
        wishlistItemRepository.saveAndFlush(item);

        // 3. Trigger Continuous Orchestration (Pick up from wishlist)
        continuousOrchestrationService.continuousOrchestrate();

        // 4. Verify Task Created and Wishlist Converted
        WishlistItemEntity updatedItem = wishlistItemRepository.findById(item.getId()).orElseThrow();
        assertThat(updatedItem.getStatus()).isEqualTo(WishlistItemStatus.converted);

        UUID projectId = project.getId();
        TaskEntity task = taskRepository.findAll().stream()
                .filter(t -> t.getProject().getId().equals(projectId))
                .findFirst().orElseThrow();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.queued);

        // 5. Simulate PR Opening (AI Reviewer Dispatch)
        PrDataDto prData = new PrDataDto();
        prData.setCiStatus("success");
        prData.setDiffSummary("Some changes. CORE ARCHITECTURE VERIFIED. APPROVED.");
        prData.setLinesChanged(10);
        prData.setFilesChanged(1);
        prData.setHasTestChanges(false);
        prData.setChangedFiles(Collections.emptyList());

        prReviewPipelineService.onPrOpened("https://github.com/auto-verify-repo/pull/1", UUID.randomUUID(), prData);

        // 6. Trigger Auto-Merge
        autoMergeService.processAutoMerge();

        // 7. Verify PR Merged
        PrReviewEntity review = prReviewRepository.findAll().stream()
                .filter(r -> r.getPrUrl().contains("auto-verify-repo"))
                .findFirst().orElseThrow();
        assertThat(review.getMerged()).isTrue();
    }
}
