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
    private com.eneik.production.repositories.FeatureRepository featureRepository;

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

        // 2. Seed a queued task directly. This test exercises the PR-review/auto-merge pipeline (steps
        // 5-7), not wishlist compilation - real client wishlists now route through a dedicated Jules
        // compiler account (see ProjectFlowService.dispatchWishlistCompiler) instead of becoming a task
        // synchronously via orchestrate(), so that step is bypassed here.
        UUID projectId = project.getId();
        TaskEntity task = new TaskEntity();
        task.setProject(project);
        task.setRole(roleRepository.findById("BARCAN-TAG-00").orElseThrow());
        task.setTitle("Verification-Task-Alpha-2026");
        task.setDescription("Verification-Task-Alpha-2026");
        task.setStatus(TaskStatus.queued);
        task = taskRepository.saveAndFlush(task);

        // 5. Simulate PR Opening (AI Reviewer Dispatch)
        PrDataDto prData = new PrDataDto();
        prData.setCiStatus("success");
        prData.setDiffSummary("Some changes. CORE ARCHITECTURE VERIFIED. APPROVED.");
        prData.setLinesChanged(10);
        prData.setFilesChanged(1);
        prData.setHasTestChanges(false);
        prData.setChangedFiles(Collections.emptyList());

        JulesSessionEntity session = new JulesSessionEntity();
        session.setTaskId(task.getId());
        session.setStatus("pr_opened");
        session.setExternalSessionId("mock-session-auto-verify");
        session = julesSessionRepository.saveAndFlush(session);

        prReviewPipelineService.onPrOpened("https://github.com/auto-verify-repo/pull/1", session.getId(), prData);

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

        // Once a review's task has reached spike_completed, processAutoMerge() must stop re-evaluating it
        // every scheduled cycle - it used to log "Merging PR... Not merging branch" forever, identically,
        // because review.merged stays false (it genuinely wasn't merged) so the pendingReviews query kept
        // matching it. Re-running the cycle must be a true no-op now: same terminal state, nothing re-touched.
        autoMergeService.processAutoMerge();
        assertThat(taskRepository.findById(task.getId()).orElseThrow().getStatus()).isEqualTo(TaskStatus.spike_completed);
        assertThat(prReviewRepository.findById(review.getId()).orElseThrow().getMerged()).isFalse();
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
                // orchestrate() unconditionally ensures an environment-bootstrap task exists for every active
                // project, independent of the blocked-work recovery this test is exercising.
                .filter(task -> task.getPayload() == null
                        || !"BOOTSTRAP-ENVIRONMENT-BOUNDARY".equals(task.getPayload().path("toc_constraint_ref").asText()))
                .toList();
        assertThat(replacementTasks).hasSize(1);
        assertThat(replacementTasks.get(0).getStatus()).isIn(TaskStatus.queued, TaskStatus.claimed);
        assertThat(replacementTasks.get(0).getDescription()).contains("Role: BARCAN-TAG-11");
    }

    @Test
    void testOrchestrateDoesNotCreateBootstrapTaskWhenWishlistIsEmpty() {
        // Lean: no wishlist means no real demand yet - orchestrate() must not spend a real Jules
        // dispatch on a bootstrap task nobody asked for. Confirmed live: this used to fire on the very
        // first background orchestration cycle for a brand-new, still-empty project, confusing the
        // operator during the test-twenty-fifth experiment ("I haven't written a wishlist yet - why is
        // a session already running?").
        ProjectEntity project = new ProjectEntity();
        project.setName("Empty Wishlist Project");
        project.setSlug("empty-wishlist-project");
        project.setStatus(ProjectStatus.active);
        project.setFactoryStatus("ready_local");
        project.setRepositoryName("empty-wishlist-repo");
        project = projectRepository.saveAndFlush(project);

        projectFlowService.orchestrate(project.getId());

        List<TaskEntity> tasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId());
        assertThat(tasks).isEmpty();
    }

    @Test
    void testFalsificationCycleSkipsHonestlyWhenNoRealCodeChangesAvailable() {
        // Enable falsification cycle
        settingsService.save("falsification_cycle_enabled", "true");

        // Setup Project - GitHub is disabled by default in the test profile and no local workspace
        // exists, so there is genuinely no real diff to audit.
        ProjectEntity project = new ProjectEntity();
        project.setName("Falsification Project");
        project.setSlug("falsification-project");
        project.setStatus(ProjectStatus.active);
        project.setFactoryStatus("ready_local");
        project.setRepositoryName("falsification-repo");
        project = projectRepository.saveAndFlush(project);

        // This is the exact bug that was fixed: the old implementation would fall back to a database
        // query returning an unrelated PR-review remark string and hand it to Jules labelled as "the
        // diff to audit". The correct behavior is to skip the cycle honestly rather than audit nothing.
        falsificationCycleService.executeCycleForProject(project);

        final UUID targetProjectId = project.getId();
        List<TaskEntity> auditTasks = taskRepository.findAll().stream()
                .filter(t -> t.getProject() != null && t.getProject().getId().equals(targetProjectId))
                .filter(projectFlowService::isFalsificationAuditTask)
                .toList();
        assertThat(auditTasks).isEmpty();
    }

    @Test
    void testApplyAuditViolationsCreatesWishlistFollowUpsAndRunRecord() {
        ProjectEntity project = new ProjectEntity();
        project.setName("Falsification Apply Project");
        project.setSlug("falsification-apply-project");
        project.setStatus(ProjectStatus.active);
        project.setFactoryStatus("ready_local");
        project.setRepositoryName("falsification-apply-repo");
        project = projectRepository.saveAndFlush(project);

        // Simulates JulesDispatchService.completeFalsificationAudit parsing a real eneikdru report PR:
        // one refusal-criteria violation per active role (should be 13, since BARCAN-TAG-12 was added).
        List<FalsificationCycleService.AuditViolation> violations = roleRepository.findAll().stream()
                .filter(RoleEntity::isActive)
                .map(role -> new FalsificationCycleService.AuditViolation(
                        role.getTag(), "refusal_criteria", "hardcoded hex color used.",
                        "", "", "", "", "", ""))
                .toList();

        falsificationCycleService.applyAuditViolations(project, violations, 42);

        // 1. Roles checked count should be 13
        List<FalsificationRunEntity> runs = falsificationRunRepository.findByProjectId(project.getId());
        assertThat(runs).hasSize(1);
        FalsificationRunEntity run = runs.get(0);
        assertThat(run.getRolesCheckedCount()).isEqualTo(13);

        // 2. Falsification creates wishlist follow-ups only; task creation is reserved for Orchestrate.
        // tasksCreatedCount tracks those follow-up wishlist items (not TaskEntity rows - see the "no
        // chaotic tasks" assertion below), so with zero pre-existing suppression it equals the violation
        // count. Previously this was hardcoded to 0 regardless of what actually got created - fixed as
        // part of the test-twenty-eighth post-mortem (the field silently lied about its own effect).
        assertThat(run.getTasksCreatedCount()).isEqualTo(13);
        assertThat(run.getViolationsFoundCount()).isEqualTo(13);

        final UUID targetProjectId = project.getId();
        List<TaskEntity> tasks = taskRepository.findAll().stream()
                .filter(t -> t.getProject() != null && t.getProject().getId().equals(targetProjectId))
                .filter(t -> "chaotic".equals(t.getCynefinDomain()))
                .toList();
        assertThat(tasks).isEmpty();
        List<WishlistEntity> followUps = wishlistRepository.findByProjectId(project.getId()).stream()
                .filter(w -> w.getSource() == WishlistSource.self_falsification)
                .toList();
        assertThat(followUps).hasSize(13);
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

    @Test
    void buildTaskGraphFromSlicesGivesAllSlicesTheSameFeatureId() {
        ProjectEntity project = new ProjectEntity();
        project.setName("Feature Sharing Project");
        project.setSlug("feature-sharing-project");
        project.setStatus(ProjectStatus.active);
        project.setFactoryStatus("ready_local");
        project.setRepositoryName("feature-sharing-repo");
        project = projectRepository.saveAndFlush(project);

        WishlistEntity brief = new WishlistEntity();
        brief.setProjectId(project.getId());
        brief.setSource(WishlistSource.client);
        brief.setContent("Handle a two-sided feature: backend validation plus a data migration.");
        brief.setStatus(WishlistStatus.pending);
        brief = wishlistRepository.saveAndFlush(brief);

        // Non-UI roles deliberately - a UI/design role slice would try to generate a real design asset,
        // which this test has no need to exercise.
        MLPredictionServiceClient.TaskSliceMetadata backendSlice = new MLPredictionServiceClient.TaskSliceMetadata(
                "Backend validation", "Validate the incoming payload server-side.",
                "Given an invalid payload, When it is submitted, Then the API returns a 400.",
                "BARCAN-TAG-02", LeanValue.essential, "Must-Be", "clear",
                "API connectivity", "100% of invalid payloads rejected", false, 0);
        MLPredictionServiceClient.TaskSliceMetadata dataSlice = new MLPredictionServiceClient.TaskSliceMetadata(
                "Data migration", "Migrate the legacy records to the new schema.",
                "Given legacy records, When the migration runs, Then all records match the new schema.",
                "BARCAN-TAG-08", LeanValue.essential, "Must-Be", "clear",
                "Data integrity", "100% of records migrated", false, 0);

        boolean built = projectFlowService.buildTaskGraphFromSlices(project, List.of(brief), List.of(backendSlice, dataSlice));
        assertThat(built).isTrue();

        List<TaskEntity> tasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId()).stream()
                .filter(t -> t.getRole() != null && ("BARCAN-TAG-02".equals(t.getRole().getTag()) || "BARCAN-TAG-08".equals(t.getRole().getTag())))
                .toList();
        assertThat(tasks).hasSize(2);
        assertThat(tasks.get(0).getFeatureId()).isNotNull();
        assertThat(tasks.get(0).getFeatureId()).isEqualTo(tasks.get(1).getFeatureId());
    }

    @Test
    void buildTaskGraphFromSlicesAnchorsSameStageRolesOnTheContractStageInParallel() {
        ProjectEntity project = new ProjectEntity();
        project.setName("Parallel Stage Project");
        project.setSlug("parallel-stage-project");
        project.setStatus(ProjectStatus.active);
        project.setFactoryStatus("ready_local");
        project.setRepositoryName("parallel-stage-repo");
        project = projectRepository.saveAndFlush(project);

        WishlistEntity brief = new WishlistEntity();
        brief.setProjectId(project.getId());
        brief.setSource(WishlistSource.client);
        brief.setContent("Model first, then a shared API contract, then backend and AI work in parallel.");
        brief.setStatus(WishlistStatus.pending);
        brief = wishlistRepository.saveAndFlush(brief);

        // TAG-11/TAG-03 deliberately excluded - they trigger design-asset pregeneration, not needed to
        // prove stage anchoring. TAG-02 and TAG-04 both sit in EmsFlowStage.IMPLEMENTATION (order 30),
        // so they're the same-stage pair used here to prove parallel siblings share one anchor instead
        // of depending on each other.
        MLPredictionServiceClient.TaskSliceMetadata modelSlice = new MLPredictionServiceClient.TaskSliceMetadata(
                "Order data model", "Model the order entity and its fields.",
                "Given the domain, When the model is defined, Then it captures all required order fields.",
                "BARCAN-TAG-08", LeanValue.essential, "Must-Be", "clear",
                "Data integrity", "100% of fields modeled", false, 0);
        MLPredictionServiceClient.TaskSliceMetadata contractSlice = new MLPredictionServiceClient.TaskSliceMetadata(
                "Order API contract", "Define the shared order API contract.",
                "Given the order model, When the contract is published, Then backend and AI both build against it.",
                "BARCAN-TAG-12", LeanValue.essential, "Must-Be", "clear",
                "Contract drift", "0 drift incidents", false, 0);
        MLPredictionServiceClient.TaskSliceMetadata backendSlice = new MLPredictionServiceClient.TaskSliceMetadata(
                "Order backend", "Implement the order backend against the contract.",
                "Given the contract, When a request arrives, Then it is handled per spec.",
                "BARCAN-TAG-02", LeanValue.essential, "Must-Be", "clear",
                "API connectivity", "100% of requests handled", false, 0);
        MLPredictionServiceClient.TaskSliceMetadata aiSlice = new MLPredictionServiceClient.TaskSliceMetadata(
                "Order AI scoring", "Implement AI scoring against the contract.",
                "Given the contract, When a request arrives, Then a score is returned per spec.",
                "BARCAN-TAG-04", LeanValue.essential, "Must-Be", "clear",
                "Scoring accuracy", "100% of requests scored", false, 0);

        boolean built = projectFlowService.buildTaskGraphFromSlices(
                project, List.of(brief), List.of(modelSlice, contractSlice, backendSlice, aiSlice));
        assertThat(built).isTrue();

        List<TaskEntity> tasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId());
        TaskEntity modelTask = taskByRole(tasks, "BARCAN-TAG-08");
        TaskEntity contractTask = taskByRole(tasks, "BARCAN-TAG-12");
        TaskEntity backendTask = taskByRole(tasks, "BARCAN-TAG-02");
        TaskEntity aiTask = taskByRole(tasks, "BARCAN-TAG-04");

        assertThat(contractTask.getDependsOn().getId()).isEqualTo(modelTask.getId());
        assertThat(backendTask.getDependsOn().getId()).isEqualTo(contractTask.getId());
        assertThat(aiTask.getDependsOn().getId()).isEqualTo(contractTask.getId());
        assertThat(backendTask.getId()).isNotEqualTo(aiTask.getId());
    }

    @Test
    void buildTaskGraphFromSlicesSkipsTheContractStageWhenThereIsNoParallelPair() {
        ProjectEntity project = new ProjectEntity();
        project.setName("No Contract Needed Project");
        project.setSlug("no-contract-needed-project");
        project.setStatus(ProjectStatus.active);
        project.setFactoryStatus("ready_local");
        project.setRepositoryName("no-contract-needed-repo");
        project = projectRepository.saveAndFlush(project);

        WishlistEntity brief = new WishlistEntity();
        brief.setProjectId(project.getId());
        brief.setSource(WishlistSource.client);
        brief.setContent("Model first, then backend only - no frontend in this feature.");
        brief.setStatus(WishlistStatus.pending);
        brief = wishlistRepository.saveAndFlush(brief);

        MLPredictionServiceClient.TaskSliceMetadata modelSlice = new MLPredictionServiceClient.TaskSliceMetadata(
                "Order data model", "Model the order entity and its fields.",
                "Given the domain, When the model is defined, Then it captures all required order fields.",
                "BARCAN-TAG-08", LeanValue.essential, "Must-Be", "clear",
                "Data integrity", "100% of fields modeled", false, 0);
        MLPredictionServiceClient.TaskSliceMetadata backendSlice = new MLPredictionServiceClient.TaskSliceMetadata(
                "Order backend", "Implement the order backend.",
                "Given the model, When a request arrives, Then it is handled.",
                "BARCAN-TAG-02", LeanValue.essential, "Must-Be", "clear",
                "API connectivity", "100% of requests handled", false, 0);

        boolean built = projectFlowService.buildTaskGraphFromSlices(project, List.of(brief), List.of(modelSlice, backendSlice));
        assertThat(built).isTrue();

        List<TaskEntity> tasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId());
        TaskEntity modelTask = taskByRole(tasks, "BARCAN-TAG-08");
        TaskEntity backendTask = taskByRole(tasks, "BARCAN-TAG-02");

        assertThat(tasks.stream().anyMatch(t -> t.getRole() != null && "BARCAN-TAG-12".equals(t.getRole().getTag()))).isFalse();
        assertThat(backendTask.getDependsOn().getId()).isEqualTo(modelTask.getId());
    }

    private TaskEntity taskByRole(List<TaskEntity> tasks, String roleTag) {
        return tasks.stream()
                .filter(t -> t.getRole() != null && roleTag.equals(t.getRole().getTag()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No task found for role " + roleTag + " in: " + tasks));
    }

    @Test
    void buildTaskGraphFromSlicesBatchKeepsEachSourceWishlistAsAnIndependentGraph() {
        ProjectEntity project = new ProjectEntity();
        project.setName("Batch Independence Project");
        project.setSlug("batch-independence-project");
        project.setStatus(ProjectStatus.active);
        project.setFactoryStatus("ready_local");
        project.setRepositoryName("batch-independence-repo");
        project = projectRepository.saveAndFlush(project);

        WishlistEntity briefA = new WishlistEntity();
        briefA.setProjectId(project.getId());
        briefA.setSource(WishlistSource.client);
        briefA.setContent("Brief A: a data model for widgets.");
        briefA.setStatus(WishlistStatus.compiling);
        briefA = wishlistRepository.saveAndFlush(briefA);

        WishlistEntity briefB = new WishlistEntity();
        briefB.setProjectId(project.getId());
        briefB.setSource(WishlistSource.client);
        briefB.setContent("Brief B: an unrelated API contract for gadgets.");
        briefB.setStatus(WishlistStatus.compiling);
        briefB = wishlistRepository.saveAndFlush(briefB);

        MLPredictionServiceClient.TaskSliceMetadata sliceA = new MLPredictionServiceClient.TaskSliceMetadata(
                "Widget data model", "Model widgets.",
                "Given a widget, When it is created, Then it is persisted.",
                "BARCAN-TAG-08", LeanValue.essential, "Must-Be", "clear",
                "Data integrity", "100% persisted", false, 0);
        MLPredictionServiceClient.TaskSliceMetadata sliceB = new MLPredictionServiceClient.TaskSliceMetadata(
                "Gadget API contract", "Define the gadget API contract.",
                "Given the contract, When published, Then both sides build against it.",
                "BARCAN-TAG-12", LeanValue.essential, "Must-Be", "clear",
                "Contract drift", "0 drift", false, 1);

        boolean built = projectFlowService.buildTaskGraphFromSlices(
                project, List.of(briefA, briefB), List.of(sliceA, sliceB));
        assertThat(built).isTrue();

        List<TaskEntity> tasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId());
        TaskEntity modelTask = taskByRole(tasks, "BARCAN-TAG-08");
        TaskEntity contractTask = taskByRole(tasks, "BARCAN-TAG-12");

        // Batching is a dispatch-efficiency optimization, not a merge - two unrelated briefs' tasks must
        // never end up depending on each other, or sharing a feature, just because they shared one
        // compiler session.
        assertThat(modelTask.getDependsOn()).isNull();
        assertThat(contractTask.getDependsOn()).isNull();
        assertThat(modelTask.getFeatureId()).isNotEqualTo(contractTask.getFeatureId());

        assertThat(wishlistRepository.findById(briefA.getId()).orElseThrow().getStatus())
                .isEqualTo(WishlistStatus.converted_to_task);
        assertThat(wishlistRepository.findById(briefB.getId()).orElseThrow().getStatus())
                .isEqualTo(WishlistStatus.converted_to_task);
    }

    @Test
    void buildTaskGraphFromSlicesBatchSkipsASourceWishlistAlreadyFinishedByAnotherSession() {
        ProjectEntity project = new ProjectEntity();
        project.setName("Batch Idempotency Project");
        project.setSlug("batch-idempotency-project");
        project.setStatus(ProjectStatus.active);
        project.setFactoryStatus("ready_local");
        project.setRepositoryName("batch-idempotency-repo");
        project = projectRepository.saveAndFlush(project);

        WishlistEntity alreadyDone = new WishlistEntity();
        alreadyDone.setProjectId(project.getId());
        alreadyDone.setSource(WishlistSource.client);
        alreadyDone.setContent("Already finished by another session before this one completed.");
        // Simulates another compiler session in the same batch having already finished this one.
        alreadyDone.setStatus(WishlistStatus.converted_to_task);
        alreadyDone = wishlistRepository.saveAndFlush(alreadyDone);

        WishlistEntity stillOpen = new WishlistEntity();
        stillOpen.setProjectId(project.getId());
        stillOpen.setSource(WishlistSource.client);
        stillOpen.setContent("Still open - this one should actually get built.");
        stillOpen.setStatus(WishlistStatus.compiling);
        stillOpen = wishlistRepository.saveAndFlush(stillOpen);

        MLPredictionServiceClient.TaskSliceMetadata sliceForDone = new MLPredictionServiceClient.TaskSliceMetadata(
                "Should never be built", "Should never be built.",
                "Given this brief is already done, When processed, Then nothing new is created.",
                "BARCAN-TAG-11", LeanValue.essential, "Must-Be", "clear",
                "N/A", "N/A", false, 0);
        MLPredictionServiceClient.TaskSliceMetadata sliceForOpen = new MLPredictionServiceClient.TaskSliceMetadata(
                "Should be built", "Should be built.",
                "Given this brief is still open, When processed, Then a task is created.",
                "BARCAN-TAG-02", LeanValue.essential, "Must-Be", "clear",
                "N/A", "N/A", false, 1);

        boolean built = projectFlowService.buildTaskGraphFromSlices(
                project, List.of(alreadyDone, stillOpen), List.of(sliceForDone, sliceForOpen));
        assertThat(built).isTrue();

        List<TaskEntity> tasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId());
        assertThat(tasks.stream().anyMatch(t -> t.getRole() != null && "BARCAN-TAG-11".equals(t.getRole().getTag())))
                .as("the already-finished source wishlist must not be re-decomposed")
                .isFalse();
        assertThat(taskByRole(tasks, "BARCAN-TAG-02")).isNotNull();
    }

    @Test
    void dispatchBatchedWishlistCompilerRespectsFeatureWipLimit() {
        ProjectEntity project = new ProjectEntity();
        project.setName("Feature WIP Limit Project");
        project.setSlug("feature-wip-limit-project");
        project.setStatus(ProjectStatus.active);
        project.setFactoryStatus("ready_local");
        project.setRepositoryName("feature-wip-limit-repo");
        project = projectRepository.saveAndFlush(project);

        FeatureEntity feature = new FeatureEntity();
        feature.setProjectId(project.getId());
        feature = featureRepository.saveAndFlush(feature);
        UUID sharedFeatureId = feature.getId();

        List<WishlistEntity> candidates = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            WishlistEntity concern = new WishlistEntity();
            concern.setProjectId(project.getId());
            concern.setSource(WishlistSource.role);
            concern.setSourceRoleTag("BARCAN-TAG-03");
            concern.setFeatureId(sharedFeatureId);
            concern.setContent("Design reviewer concern (non-blocking) on design/approved/x: concern #" + i);
            concern.setStatus(WishlistStatus.pending);
            candidates.add(wishlistRepository.saveAndFlush(concern));
        }

        int admitted = projectFlowService.dispatchBatchedWishlistCompiler(project, candidates);

        // Default orchestration.wip-limit-feature-in-flight is 2: with none of the 3 already `compiling`
        // beforehand, only 2 may be admitted in this same batch - the 3rd must stay `pending` for a later
        // cycle instead of all 3 racing into the compiler at once for one feature.
        assertThat(admitted).isEqualTo(2);
        long compilingCount = candidates.stream()
                .map(w -> wishlistRepository.findById(w.getId()).orElseThrow())
                .filter(w -> w.getStatus() == WishlistStatus.compiling)
                .count();
        assertThat(compilingCount).isEqualTo(2);
    }

    @Test
    void wishlistCompilerPromptBatchInjectsCorrectionInstructionOnlyForDesignConcerns() {
        WishlistEntity designConcern = new WishlistEntity();
        designConcern.setSource(WishlistSource.role);
        designConcern.setSourceRoleTag("BARCAN-TAG-03");
        designConcern.setContent("Design reviewer concern (non-blocking) on design/approved/20260101-mockup: contrast too low");

        WishlistEntity clientBrief = new WishlistEntity();
        clientBrief.setSource(WishlistSource.client);
        clientBrief.setContent("Build a normal new feature from scratch.");

        String prompt = projectFlowService.wishlistCompilerPromptBatch(List.of(designConcern, clientBrief));

        assertThat(prompt).contains("CORRECTION TO ALREADY-APPROVED DESIGN");
        assertThat(prompt).contains("design/approved/20260101-mockup/mockup.html");
        // The correction annotation must be scoped to Brief #0 only - Brief #1 (a normal client brief) gets
        // no such instruction, otherwise a real new-UI request would wrongly be told not to build a mockup.
        int briefOneIndex = prompt.indexOf("Build a normal new feature from scratch.");
        int correctionIndex = prompt.indexOf("CORRECTION TO ALREADY-APPROVED DESIGN");
        assertThat(correctionIndex).isLessThan(briefOneIndex);
    }
}
