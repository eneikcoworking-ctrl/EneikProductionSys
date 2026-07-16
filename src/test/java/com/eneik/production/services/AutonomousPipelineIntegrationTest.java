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
    private ProjectFlowService projectFlowService;

    @org.springframework.boot.test.mock.mockito.MockBean
    private MLPredictionServiceClient mlPredictionServiceClient;

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
        project.setFactoryStatus("ready_local");
        project.setRepositoryName("auto-verify-repo");
        project = projectRepository.saveAndFlush(project);

        // 2. Add Verification Task to Wishlist
        com.eneik.production.models.persistence.WishlistEntity item = new com.eneik.production.models.persistence.WishlistEntity();
        item.setProjectId(project.getId());
        item.setContent("Verification-Task-Alpha-2026");
        item.setStatus(com.eneik.production.models.persistence.WishlistStatus.pending);
        item.setSource(com.eneik.production.models.persistence.WishlistSource.client);
        item.setSourceRoleTag("BARCAN-TAG-00");
        wishlistRepository.saveAndFlush(item);

                java.util.Map<String, Object> aiResponse = new java.util.HashMap<>();
        aiResponse.put("jtbd", "Automated UI Verification");
        aiResponse.put("acceptanceCriteria", "Visuals match reference");
        org.mockito.Mockito.when(mlPredictionServiceClient.generateTaskMetadata(org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(aiResponse);

        // 3. Trigger manual orchestration (Pick up from wishlist)
        projectFlowService.orchestrate(project.getId());

        // 4. Verify Task Created and Wishlist Converted
        com.eneik.production.models.persistence.WishlistEntity updatedItem = wishlistRepository.findById(item.getId()).orElseThrow();
        assertThat(updatedItem.getStatus()).isEqualTo(com.eneik.production.models.persistence.WishlistStatus.converted_to_task);

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
        project.setFactoryStatus("ready_local");
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
        project.setFactoryStatus("ready_local");
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
        project.setFactoryStatus("ready_local");
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
    void testBlockedWorkIsRecoveredIntoFreshAtomicTaskWithoutOperatorSelectingTaskId() {
        ProjectEntity project = new ProjectEntity();
        project.setName("Blocked Recovery Project");
        project.setSlug("blocked-recovery-project");
        project.setStatus(ProjectStatus.active);
        project.setFactoryStatus("ready_local");
        project.setRepositoryName("blocked-recovery-repo");
        project = projectRepository.saveAndFlush(project);

        AccountEntity account = new AccountEntity();
        account.setName("Recovery Jules");
        account.setCapabilities("*");
        account.setStatus(AccountStatus.idle);
        account.setEnabled(true);
        account = accountRepository.saveAndFlush(account);

        TaskEntity blockedTask = new TaskEntity();
        blockedTask.setProject(project);
        blockedTask.setRole(roleRepository.findById("BARCAN-TAG-11").orElseThrow());
        blockedTask.setDescription("Oversized frontend task that exceeded the Jules session dialogue budget.");
        blockedTask.setStatus(TaskStatus.blocked);
        blockedTask.setRetryCount(3);
        blockedTask.setJulesDispatchStatus("Jules circuit breaker: dialog_limit_exceeded");
        blockedTask = taskRepository.saveAndFlush(blockedTask);
        UUID projectId = project.getId();
        UUID blockedTaskId = blockedTask.getId();

        continuousOrchestrationService.continuousOrchestrate();

        TaskEntity oldTask = taskRepository.findById(blockedTaskId).orElseThrow();
        assertThat(oldTask.getStatus()).isEqualTo(TaskStatus.blocked);

        List<WishlistEntity> recoveryWishlist = wishlistRepository.findByProjectId(projectId).stream()
                .filter(w -> w.getSource() == WishlistSource.role_mismatch_followup)
                .toList();
        assertThat(recoveryWishlist).hasSize(1);
        assertThat(recoveryWishlist.get(0).getStatus()).isEqualTo(WishlistStatus.converted_to_task);
        assertThat(recoveryWishlist.get(0).getContent()).contains("Auto recovery source task: " + blockedTaskId);

        List<TaskEntity> replacementTasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .filter(task -> !task.getId().equals(blockedTaskId))
                .toList();
        assertThat(replacementTasks).hasSize(1);
        assertThat(replacementTasks.get(0).getStatus()).isIn(TaskStatus.queued, TaskStatus.claimed);
        assertThat(replacementTasks.get(0).getDescription()).contains("Role: BARCAN-TAG-11");
    }

    @Test
    void testFalsificationCycleNoRateLimitAndRunRecord() throws java.io.IOException {
        // Enable falsification cycle
        settingsService.save("falsification_cycle_enabled", "true");

        // Simulate critical regression (actuator health is DOWN/unhealthy)
        settingsService.save("simulated_actuator_health", "DOWN");

        // Setup Project
        ProjectEntity project = new ProjectEntity();
        project.setName("Falsification Project");
        project.setSlug("falsification-project");
        project.setStatus(ProjectStatus.active);
        project.setFactoryStatus("ready_local");
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

        // 2. Falsification creates wishlist follow-ups only; task creation is reserved for Orchestrate.
        assertThat(run.getTasksCreatedCount()).isEqualTo(0);
        assertThat(run.getViolationsFoundCount()).isEqualTo(12);

        final UUID targetProjectId = project.getId();
        List<TaskEntity> tasks = taskRepository.findAll().stream()
                .filter(t -> t.getProject() != null && t.getProject().getId().equals(targetProjectId))
                .filter(t -> "chaotic".equals(t.getCynefinDomain()))
                .toList();
        assertThat(tasks).isEmpty();
        List<WishlistEntity> followUps = wishlistRepository.findByProjectId(project.getId()).stream()
                .filter(w -> w.getSource() == WishlistSource.self_falsification)
                .toList();
        assertThat(followUps).hasSize(12);
    }

    @Test
    void testWishlistGroupingAndKanoPriority() {
        // Setup Project
        ProjectEntity project = new ProjectEntity();
        project.setName("Grouping Project");
        project.setSlug("grouping-project");
        project.setStatus(ProjectStatus.active);
        project.setFactoryStatus("ready_local");
        project.setRepositoryName("grouping-repo");
        project = projectRepository.saveAndFlush(project);

        // Stub AI metadata generation
        java.util.Map<String, Object> mockMeta = new java.util.HashMap<>();
        mockMeta.put("jtbd", "Resolve design code constraints");
        mockMeta.put("acceptanceCriteria", "All checks pass successfully");
        org.mockito.Mockito.when(mlPredictionServiceClient.generateTaskMetadata(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(mockMeta);

        // Create similar pending wishlist items (Hex colors violations)
        WishlistEntity item1 = new WishlistEntity();
        item1.setProjectId(project.getId());
        item1.setSource(WishlistSource.self_falsification);
        item1.setSourceRoleTag("BARCAN-TAG-01");
        item1.setContent("Compliance violation detected for role BARCAN-TAG-01. Violates: hex colors used in frontend CSS.");
        item1.setStatus(WishlistStatus.pending);
        item1.setLeanValue(LeanValue.essential);
        item1.setDod("Acceptance check BARCAN-TAG-01");
        item1.setAcceptanceCriteria("No hex colors");
        item1.setTocConstraintRef("some-ref");
        item1.setJtbd("some jtbd");
        item1.setSixSigmaMetric("some metric");
        item1.setCompiledByRole("BARCAN-TAG-09");
        item1 = wishlistRepository.saveAndFlush(item1);

        WishlistEntity item2 = new WishlistEntity();
        item2.setProjectId(project.getId());
        item2.setSource(WishlistSource.self_falsification);
        item2.setSourceRoleTag("BARCAN-TAG-11");
        item2.setContent("Compliance violation detected for role BARCAN-TAG-11. Violates: hex color found in styles.");
        item2.setStatus(WishlistStatus.pending);
        item2.setLeanValue(LeanValue.essential);
        item2.setDod("Acceptance check BARCAN-TAG-11 reference docs/DESIGN_SYSTEM.md");
        item2.setAcceptanceCriteria("No hex colors");
        item2.setTocConstraintRef("some-ref");
        item2.setJtbd("some jtbd");
        item2.setSixSigmaMetric("some metric");
        item2.setCompiledByRole("BARCAN-TAG-09");
        item2 = wishlistRepository.saveAndFlush(item2);

        // Create an independent wishlist item (completely different text)
        WishlistEntity item3 = new WishlistEntity();
        item3.setProjectId(project.getId());
        item3.setSource(WishlistSource.self_falsification);
        item3.setSourceRoleTag("BARCAN-TAG-02");
        item3.setContent("Methodological contradiction: real compare instead of mock diff.");
        item3.setStatus(WishlistStatus.pending);
        item3.setLeanValue(LeanValue.essential);
        item3.setDod("Acceptance check BARCAN-TAG-02");
        item3.setAcceptanceCriteria("Real comparison");
        item3.setTocConstraintRef("some-ref");
        item3.setJtbd("some jtbd");
        item3.setSixSigmaMetric("some metric");
        item3.setCompiledByRole("BARCAN-TAG-09");
        item3 = wishlistRepository.saveAndFlush(item3);

        // Run orchestration
        projectFlowService.orchestrate(project.getId());

        // Verify wishlist statuses
        WishlistEntity loaded1 = wishlistRepository.findById(item1.getId()).orElseThrow();
        WishlistEntity loaded2 = wishlistRepository.findById(item2.getId()).orElseThrow();
        WishlistEntity loaded3 = wishlistRepository.findById(item3.getId()).orElseThrow();

        // One of the similar items should be converted_to_task, the other dismissed
        boolean check1 = loaded1.getStatus() == WishlistStatus.converted_to_task && loaded2.getStatus() == WishlistStatus.dismissed;
        boolean check2 = loaded2.getStatus() == WishlistStatus.converted_to_task && loaded1.getStatus() == WishlistStatus.dismissed;
        assertThat(check1 || check2).isTrue();

        // Independent item should be converted_to_task
        assertThat(loaded3.getStatus()).isEqualTo(WishlistStatus.converted_to_task);

        // Verify the tasks created
        List<TaskEntity> tasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId());
        // Filter out bootstrap tasks
        List<TaskEntity> nonBootstrapTasks = tasks.stream()
                .filter(t -> t.getPayload() == null || !"BOOTSTRAP-ENVIRONMENT-BOUNDARY".equals(t.getPayload().path("toc_constraint_ref").asText()))
                .filter(t -> !t.getTitle().toLowerCase().contains("bootstrap"))
                .filter(t -> !t.getTitle().contains("Runtime Contract"))
                .toList();

        assertThat(nonBootstrapTasks).hasSize(2);

        // Find the multi-role task (which contains both roles)
        TaskEntity multiRoleTask = nonBootstrapTasks.stream()
                .filter(t -> {
                    String desc = t.getDescription();
                    return desc != null && desc.contains("BARCAN-TAG-01") && desc.contains("BARCAN-TAG-11");
                })
                .findFirst()
                .orElseThrow(() -> new AssertionError("Multi-role task not found in: " + nonBootstrapTasks));

        // Verify Kano
        assertThat(multiRoleTask.getDescription()).contains("Kano: Must-Be");
    }
}
