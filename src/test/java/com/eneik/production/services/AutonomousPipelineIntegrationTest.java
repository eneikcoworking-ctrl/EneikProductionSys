package com.eneik.production.services;

import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.*;
import com.eneik.production.services.compiler.TechnicalLeadCompiler;
import com.eneik.production.services.monitor.PrReviewPipelineService;
import com.eneik.production.dto.monitor.PrDataDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import com.eneik.production.services.settings.SystemSettingsService;
import java.util.Collections;
import java.util.List;
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

    @Autowired
    private ClaimRepository claimRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private JulesSessionRepository julesSessionRepository;

    @Autowired
    private WishlistRepository wishlistRepository;

    @Autowired
    private FalsificationCycleService falsificationCycleService;

    @Autowired
    private FalsificationRunRepository falsificationRunRepository;

    @Autowired
    private SystemSettingsService settingsService;

    @BeforeEach
    void setUp() {
        claimRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM needs_human_review");
        jdbcTemplate.update("DELETE FROM task_conflicts");
        jdbcTemplate.update("DELETE FROM jules_sessions");
        prReviewRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM falsification_runs");
        taskRepository.deleteAll();
        wishlistItemRepository.deleteAll();
        wishlistRepository.deleteAll();
        accountRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM github_access_status");
        projectRepository.deleteAll();
    }

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
                .filter(t -> t.getProject() != null && t.getProject().getId().equals(projectId))
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

    @Test
    void testComplexDomainSpikeStatus() {
        ProjectEntity project = new ProjectEntity();
        project.setName("Complex Project");
        project.setSlug("complex-project");
        project.setStatus(ProjectStatus.active);
        project.setRepositoryName("complex-repo");
        project = projectRepository.saveAndFlush(project);

        TaskEntity task = new TaskEntity();
        task.setProject(project);
        task.setRole(roleRepository.findById("BARCAN-TAG-02").orElseThrow());
        task.setDescription("Complex Spike Task");
        task.setStatus(TaskStatus.review);
        task.setCynefinDomain("complex");
        task = taskRepository.saveAndFlush(task);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setTaskId(task.getId());
        session.setStatus("pr_opened");
        session.setExternalSessionId("mock-session-complex");
        session = julesSessionRepository.saveAndFlush(session);

        PrReviewEntity review = new PrReviewEntity();
        review.setPrUrl("https://github.com/complex-repo/pull/1");
        review.setJulesSessionId(session.getId());
        review.setCiStatus("success");
        review.setDiffSummary("CORE ARCHITECTURE VERIFIED. APPROVED.");
        review.setRiskLevel("low");
        review = prReviewRepository.saveAndFlush(review);

        autoMergeService.processAutoMerge();

        TaskEntity updatedTask = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(updatedTask.getStatus()).isEqualTo(TaskStatus.spike_completed);

        PrReviewEntity updatedReview = prReviewRepository.findById(review.getId()).orElseThrow();
        assertThat(updatedReview.getMerged()).isFalse();
    }

    @Test
    void testChaoticDomainImmediateMergeAndDebt() {
        ProjectEntity project = new ProjectEntity();
        project.setName("Chaotic Project");
        project.setSlug("chaotic-project");
        project.setStatus(ProjectStatus.active);
        project.setRepositoryName("chaotic-repo");
        project = projectRepository.saveAndFlush(project);

        TaskEntity task = new TaskEntity();
        task.setProject(project);
        task.setRole(roleRepository.findById("BARCAN-TAG-02").orElseThrow());
        task.setDescription("Chaotic Fix Task");
        task.setStatus(TaskStatus.review);
        task.setCynefinDomain("chaotic");
        task = taskRepository.saveAndFlush(task);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setTaskId(task.getId());
        session.setStatus("pr_opened");
        session.setExternalSessionId("mock-session-chaotic");
        session = julesSessionRepository.saveAndFlush(session);

        PrReviewEntity review = new PrReviewEntity();
        review.setPrUrl("https://github.com/chaotic-repo/pull/2");
        review.setJulesSessionId(session.getId());
        review.setCiStatus("success");
        review.setDiffSummary("CI Success but no approval token");
        review.setRiskLevel("low");
        review = prReviewRepository.saveAndFlush(review);

        autoMergeService.processAutoMerge();

        PrReviewEntity updatedReview = prReviewRepository.findById(review.getId()).orElseThrow();
        assertThat(updatedReview.getMerged()).isTrue();

        TaskEntity updatedTask = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(updatedTask.getStatus()).isEqualTo(TaskStatus.done);

        List<WishlistEntity> wishlistItems = wishlistRepository.findByProjectId(project.getId()).stream()
                .filter(w -> w.getSource() == WishlistSource.chaotic_debt)
                .toList();
        assertThat(wishlistItems).hasSize(1);
        assertThat(wishlistItems.get(0).getTocConstraintRef()).isEqualTo("HIGH_PRIORITY_DEBT");
    }

    @Test
    void testRolePhilosophicalFilterMismatch() {
        ProjectEntity project = new ProjectEntity();
        project.setName("Philosophical Filter Project");
        project.setSlug("philosophical-filter-project");
        project.setStatus(ProjectStatus.active);
        project.setRepositoryName("philosophical-filter-repo");
        project = projectRepository.saveAndFlush(project);

        TaskEntity task = new TaskEntity();
        task.setProject(project);
        task.setRole(roleRepository.findById("BARCAN-TAG-02").orElseThrow());
        task.setDescription("Standard Task with Mismatch");
        task.setStatus(TaskStatus.review);
        task = taskRepository.saveAndFlush(task);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setTaskId(task.getId());
        session.setStatus("pr_opened");
        session.setExternalSessionId("mock-session-mismatch");
        session = julesSessionRepository.saveAndFlush(session);

        PrReviewEntity review = new PrReviewEntity();
        review.setPrUrl("https://github.com/philosophical-filter-repo/pull/3");
        review.setJulesSessionId(session.getId());
        review.setCiStatus("success");
        review.setDiffSummary("CORE ARCHITECTURE VERIFIED. APPROVED. refusal_violation: hardcoded hex found.");
        review.setRiskLevel("low");
        review = prReviewRepository.saveAndFlush(review);

        autoMergeService.processAutoMerge();

        PrReviewEntity updatedReview = prReviewRepository.findById(review.getId()).orElseThrow();
        assertThat(updatedReview.getMerged()).isTrue();

        List<WishlistEntity> wishlistItems = wishlistRepository.findByProjectId(project.getId());
        assertThat(wishlistItems).isNotEmpty();
        assertThat(wishlistItems.get(0).getSource()).isEqualTo(WishlistSource.role_mismatch_followup);
        assertThat(wishlistItems.get(0).getSourceRoleTag()).isEqualTo("BARCAN-TAG-02");
    }

    @Test
    void testFalsificationCycleRateLimitAndRunRecord() throws java.io.IOException {
        // Enable falsification cycle
        settingsService.save("falsification_cycle_enabled", "true");

        // Simulate critical regression (actuator health is DOWN/unhealthy)
        settingsService.save("simulated_actuator_health", "DOWN");

        // Setup Project
        ProjectEntity project = new ProjectEntity();
        project.setName("Falsification Project");
        project.setSlug("falsification-project");
        project.setStatus(ProjectStatus.active);
        project.setRepositoryName("falsification-repo");
        project = projectRepository.saveAndFlush(project);

        // Mock tasks so that getLatestProjectDiff gets this review. We need the task to belong to this project
        TaskEntity mockTask = new TaskEntity();
        mockTask.setProject(project);
        mockTask.setRole(roleRepository.findById("BARCAN-TAG-00").orElseThrow());
        mockTask.setDescription("Mock Task for Diff Linkage");
        mockTask.setStatus(TaskStatus.review);
        mockTask = taskRepository.saveAndFlush(mockTask);

        // Setup a PR review in database with a diff that triggers violations
        JulesSessionEntity session = new JulesSessionEntity();
        session.setTaskId(mockTask.getId());
        session.setStatus("pr_opened");
        session.setExternalSessionId("mock-session-falsify");
        session = julesSessionRepository.saveAndFlush(session);

        // Create a PR review with violating diff
        PrReviewEntity review = new PrReviewEntity();
        review.setPrUrl("https://github.com/falsification-repo/pull/1");
        review.setJulesSessionId(session.getId());
        review.setCiStatus("success");
        review.setDiffSummary("violates_criteria: hardcoded hex color used.");
        review.setRiskLevel("low");
        review = prReviewRepository.saveAndFlush(review);

        // Run falsification cycle
        falsificationCycleService.executeCycleForProject(project);

        // Verify:
        // 1. Roles checked count should be 12
        List<FalsificationRunEntity> runs = falsificationRunRepository.findByProjectId(project.getId());
        assertThat(runs).hasSize(1);
        FalsificationRunEntity run = runs.get(0);
        assertThat(run.getRolesCheckedCount()).isEqualTo(12);

        // 2. Tasks created count should be capped at 2
        assertThat(run.getTasksCreatedCount()).isEqualTo(2);
        assertThat(run.getViolationsFoundCount()).isEqualTo(12);

        final UUID targetProjectId = project.getId();
        List<TaskEntity> tasks = taskRepository.findAll().stream()
                .filter(t -> t.getProject() != null && t.getProject().getId().equals(targetProjectId))
                .filter(t -> "chaotic".equals(t.getCynefinDomain()))
                .toList();
        assertThat(tasks).isNotEmpty();
        assertThat(tasks.size()).isBetween(2, 8);
    }
}
