package com.eneik.production.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.FeatureRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.PrReviewRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClientDeliverableReadinessServiceTest {

    private final WishlistRepository wishlistRepository = mock(WishlistRepository.class);
    private final FeatureRepository featureRepository = mock(FeatureRepository.class);
    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final JulesSessionRepository julesSessionRepository = mock(JulesSessionRepository.class);
    private final PrReviewRepository prReviewRepository = mock(PrReviewRepository.class);
    private final com.eneik.production.repositories.FeatureThreadRepository featureThreadRepository =
            mock(com.eneik.production.repositories.FeatureThreadRepository.class);
    private final com.eneik.production.repositories.ProjectRepository projectRepository =
            mock(com.eneik.production.repositories.ProjectRepository.class);
    private final com.eneik.production.services.operational.OperationalPolicyService operationalPolicyService =
            mock(com.eneik.production.services.operational.OperationalPolicyService.class);

    private final ClientDeliverableReadinessService service = new ClientDeliverableReadinessService(
            wishlistRepository, featureRepository, taskRepository, julesSessionRepository, prReviewRepository,
            featureThreadRepository, projectRepository, operationalPolicyService);

    ClientDeliverableReadinessServiceTest() {
        // Default: dispatch is allowed, matching the steady-state every existing test in this file assumes.
        // Tests that specifically exercise the project-wide-freeze guard override this per-case.
        when(operationalPolicyService.authorize(any(UUID.class), eq(com.eneik.production.services.operational.OperationalAction.DISPATCH_QUEUED_TASKS)))
                .thenReturn(new com.eneik.production.services.operational.OperationalPolicyService.OperationalDecision(
                        null, com.eneik.production.services.operational.OperationalAction.DISPATCH_QUEUED_TASKS,
                        true, "ACTIVE", "authorized", "allowed", List.of(), null));
    }

    @Test
    void oneMergedTaskDoesNotCompleteFourItemFeature() {
        UUID projectId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        FeatureEntity feature = feature(projectId, rootId);
        WishlistEntity root = root(projectId, rootId, WishlistStatus.converted_to_task);
        List<WishlistEntity> items = plannedItems(projectId, feature.getId(), 4);
        List<TaskEntity> tasks = tasksFor(projectId, feature.getId(), items, "BARCAN-TAG-02");
        stubPlan(projectId, root, feature, items, tasks);
        stubMerged(tasks.get(0), true);

        ClientDeliverableReadinessService.Readiness readiness = service.computeForProject(projectId);

        assertTrue(readiness.decompositionComplete());
        assertEquals(1, readiness.totalFeatures());
        assertEquals(0, readiness.completeFeatures());
        assertEquals(4, readiness.totalDeliverables());
        assertEquals(1, readiness.mergedDeliverables());
        // 2026-07-26 operator directive ("считать по фичам, а не по таскам!"): ratio() is now
        // completeFeatures/totalFeatures, not mergedDeliverables/totalDeliverables - the feature isn't
        // complete (only 1 of its 4 items merged), so ratio is 0, even though mergedDeliverables=1.
        assertEquals(0.0, readiness.ratio(), 0.0001);
    }

    @Test
    void featureCompletesOnlyWhenEveryPlannedItemHasOwnMerge() {
        UUID projectId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        FeatureEntity feature = feature(projectId, rootId);
        WishlistEntity root = root(projectId, rootId, WishlistStatus.converted_to_task);
        List<WishlistEntity> items = plannedItems(projectId, feature.getId(), 3);
        List<TaskEntity> tasks = tasksFor(projectId, feature.getId(), items, "BARCAN-TAG-02");
        stubPlan(projectId, root, feature, items, tasks);
        tasks.forEach(task -> stubMerged(task, true));

        ClientDeliverableReadinessService.Readiness readiness = service.computeForProject(projectId);

        assertEquals(1, readiness.completeFeatures());
        assertEquals(3, readiness.mergedDeliverables());
        assertEquals(1.0, readiness.ratio(), 0.0001);
    }

    @Test
    void engineeringMergeWithoutCodeDoesNotCount() {
        UUID projectId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        FeatureEntity feature = feature(projectId, rootId);
        WishlistEntity root = root(projectId, rootId, WishlistStatus.converted_to_task);
        List<WishlistEntity> items = plannedItems(projectId, feature.getId(), 1);
        List<TaskEntity> tasks = tasksFor(projectId, feature.getId(), items, "BARCAN-TAG-02");
        stubPlan(projectId, root, feature, items, tasks);
        stubMerged(tasks.get(0), false);

        assertEquals(0, service.computeForProject(projectId).mergedDeliverables());
    }

    // --- BARCAN-TAG-03 build-time exemption (2026-08-04, live incident: "Core Knowledge Base Portal"
    // epic stuck at 6/11 - 3 of the 5 unfulfilled items were TAG-03 design-brief tasks whose real,
    // correct deliverable is a static mockup, never source code) -------------------------------------

    @Test
    void designBriefMergeWithoutCodeCountsAsFulfilled() {
        UUID projectId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        FeatureEntity feature = feature(projectId, rootId);
        WishlistEntity root = root(projectId, rootId, WishlistStatus.converted_to_task);
        List<WishlistEntity> items = plannedItems(projectId, feature.getId(), 1);
        List<TaskEntity> tasks = tasksFor(projectId, feature.getId(), items, "BARCAN-TAG-03");
        stubPlan(projectId, root, feature, items, tasks);
        stubMerged(tasks.get(0), false);

        ClientDeliverableReadinessService.Readiness readiness = service.computeForProject(projectId);
        assertEquals(1, readiness.mergedDeliverables());
        assertEquals(1, readiness.completeFeatures());
    }

    @Test
    void uiImplementationMergeWithoutCodeStillDoesNotCount() {
        // Regression guard: BARCAN-TAG-11 shares TAG-03's EXPERIENCE EmsFlowStage but writes real
        // frontend code and must NOT be swept up by a careless widening to the whole stage.
        UUID projectId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        FeatureEntity feature = feature(projectId, rootId);
        WishlistEntity root = root(projectId, rootId, WishlistStatus.converted_to_task);
        List<WishlistEntity> items = plannedItems(projectId, feature.getId(), 1);
        List<TaskEntity> tasks = tasksFor(projectId, feature.getId(), items, "BARCAN-TAG-11");
        stubPlan(projectId, root, feature, items, tasks);
        stubMerged(tasks.get(0), false);

        assertEquals(0, service.computeForProject(projectId).mergedDeliverables());
    }

    @Test
    void designBriefTaskNotReachedMainStillDoesNotCount() {
        // The exemption only skips the hasCode requirement - it never bypasses reachedMain.
        UUID projectId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        FeatureEntity feature = feature(projectId, rootId);
        WishlistEntity root = root(projectId, rootId, WishlistStatus.converted_to_task);
        List<WishlistEntity> items = plannedItems(projectId, feature.getId(), 1);
        List<TaskEntity> tasks = tasksFor(projectId, feature.getId(), items, "BARCAN-TAG-03");
        stubPlan(projectId, root, feature, items, tasks);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(UUID.randomUUID());
        PrReviewEntity review = new PrReviewEntity();
        review.setMerged(true);
        review.setHasCode(false);
        review.setBaseRef("feat/some-thread-branch");
        when(julesSessionRepository.findByTaskId(tasks.get(0).getId())).thenReturn(List.of(session));
        when(prReviewRepository.findByJulesSessionIdInAndMergedTrue(List.of(session.getId())))
                .thenReturn(List.of(review));
        when(featureThreadRepository.findByProjectIdAndFeatureId(projectId, feature.getId()))
                .thenReturn(java.util.Optional.of(new com.eneik.production.models.persistence.FeatureThreadEntity()));

        assertEquals(0, service.computeForProject(projectId).mergedDeliverables());
        assertFalse(service.reachedMain(tasks.get(0)));
    }

    @Test
    void listEpicDiagnosticsAgreesWithComputeForProjectForDesignBriefExemption() {
        UUID projectId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        FeatureEntity feature = feature(projectId, rootId);
        WishlistEntity root = root(projectId, rootId, WishlistStatus.converted_to_task);
        List<WishlistEntity> items = plannedItems(projectId, feature.getId(), 1);
        List<TaskEntity> tasks = tasksFor(projectId, feature.getId(), items, "BARCAN-TAG-03");
        stubPlan(projectId, root, feature, items, tasks);
        stubMerged(tasks.get(0), false);

        List<ClientDeliverableReadinessService.EpicDiagnostic> diagnostics = service.listEpicDiagnostics(projectId);

        assertEquals(1, diagnostics.size());
        assertEquals(1, diagnostics.get(0).mergedItemCount());
        assertTrue(diagnostics.get(0).complete());
    }

    // --- listEpicsWithMergedUiCode (2026-08-04, Phase B): the OPPOSITE signal from the TAG-03 build-time
    // exemption above - here hasCode=true is REQUIRED, because this is the falsification-stage eligibility
    // check for applying a real Stitch design system against already-shipped UI, not the build-time draft.

    @Test
    void designBriefWithRealMergedCodeIsEligibleForDesignSystemFalsification() {
        UUID projectId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        FeatureEntity feature = feature(projectId, rootId);
        feature.setTitle("Core Knowledge Base Portal");
        WishlistEntity root = root(projectId, rootId, WishlistStatus.converted_to_task);
        List<WishlistEntity> items = plannedItems(projectId, feature.getId(), 1);
        List<TaskEntity> tasks = tasksFor(projectId, feature.getId(), items, "BARCAN-TAG-03");
        stubPlan(projectId, root, feature, items, tasks);
        stubMerged(tasks.get(0), true);
        when(taskRepository.findByFeatureId(feature.getId())).thenReturn(tasks);

        List<ClientDeliverableReadinessService.UiCodeEpic> eligible = service.listEpicsWithMergedUiCode(projectId);

        assertEquals(1, eligible.size());
        assertEquals(feature.getId(), eligible.get(0).featureId());
        assertEquals("Core Knowledge Base Portal", eligible.get(0).title());
    }

    @Test
    void designBriefWithOnlyTheBuildTimeDraftIsNotEligibleForDesignSystemFalsification() {
        // Same fixture as designBriefMergeWithoutCodeCountsAsFulfilled (Phase A's exemption case) - must
        // NOT also satisfy Phase B's eligibility, which requires real hasCode=true.
        UUID projectId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        FeatureEntity feature = feature(projectId, rootId);
        WishlistEntity root = root(projectId, rootId, WishlistStatus.converted_to_task);
        List<WishlistEntity> items = plannedItems(projectId, feature.getId(), 1);
        List<TaskEntity> tasks = tasksFor(projectId, feature.getId(), items, "BARCAN-TAG-03");
        stubPlan(projectId, root, feature, items, tasks);
        stubMerged(tasks.get(0), false);
        when(taskRepository.findByFeatureId(feature.getId())).thenReturn(tasks);

        assertTrue(service.listEpicsWithMergedUiCode(projectId).isEmpty());
    }

    @Test
    void nonUiRoleWithRealMergedCodeIsNotEligibleForDesignSystemFalsification() {
        UUID projectId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        FeatureEntity feature = feature(projectId, rootId);
        WishlistEntity root = root(projectId, rootId, WishlistStatus.converted_to_task);
        List<WishlistEntity> items = plannedItems(projectId, feature.getId(), 1);
        List<TaskEntity> tasks = tasksFor(projectId, feature.getId(), items, "BARCAN-TAG-02");
        stubPlan(projectId, root, feature, items, tasks);
        stubMerged(tasks.get(0), true);
        when(taskRepository.findByFeatureId(feature.getId())).thenReturn(tasks);

        assertTrue(service.listEpicsWithMergedUiCode(projectId).isEmpty());
    }

    // --- BARCAN-TAG-06 verification-evidence acceptance (2026-08-04, Phase C: the other half of the live
    // incident - a real, correct zero-file-diff QA merge must count once it carries a passing
    // verification_evidence gate check, exactly the evidence VerificationEvidenceGate writes) -----------

    @Test
    void qaTaskWithPassingVerificationEvidenceCountsAsMerged() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        FeatureEntity feature = feature(projectId, rootId);
        WishlistEntity root = root(projectId, rootId, WishlistStatus.converted_to_task);
        List<WishlistEntity> items = plannedItems(projectId, feature.getId(), 1);
        List<TaskEntity> tasks = tasksFor(projectId, feature.getId(), items, "BARCAN-TAG-06");
        stubPlan(projectId, root, feature, items, tasks);
        // hasCode=false: the merge itself has no code diff (the live-incident shape) - reachedMain must
        // still be true (a real merge happened), only the acceptance criterion differs for this role.
        stubMerged(tasks.get(0), false);
        tasks.get(0).setQualityGateReport(new ObjectMapper().readTree(
                "{\"checks\":[{\"name\":\"verification_evidence\",\"passed\":true}]}"));

        ClientDeliverableReadinessService.Readiness readiness = service.computeForProject(projectId);
        assertEquals(1, readiness.mergedDeliverables());
    }

    @Test
    void qaTaskWithoutPassingVerificationEvidenceDoesNotCount() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        FeatureEntity feature = feature(projectId, rootId);
        WishlistEntity root = root(projectId, rootId, WishlistStatus.converted_to_task);
        List<WishlistEntity> items = plannedItems(projectId, feature.getId(), 1);
        List<TaskEntity> tasks = tasksFor(projectId, feature.getId(), items, "BARCAN-TAG-06");
        stubPlan(projectId, root, feature, items, tasks);
        stubMerged(tasks.get(0), false);
        tasks.get(0).setQualityGateReport(new ObjectMapper().readTree(
                "{\"checks\":[{\"name\":\"verification_evidence\",\"passed\":false}]}"));

        assertEquals(0, service.computeForProject(projectId).mergedDeliverables());
    }

    @Test
    void qaTaskWithRealCodeStillCountsEvenWithoutAVerificationEvidenceCheck() {
        // Regression test for a live incident (2026-08-04): the first version of the TAG-06 branch used
        // `return hasPassingGateCheck(...)`, which replaced the hasCode path entirely instead of adding to
        // it - silently un-counting every pre-existing QA task that legitimately touched real code but
        // predates VerificationEvidenceGate (so has no verification_evidence entry yet). Confirmed live:
        // two previously-100%-complete epics dropped to below 100% the moment that version deployed. A QA
        // task must pass via EITHER path, never lose the one it already had.
        UUID projectId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        FeatureEntity feature = feature(projectId, rootId);
        WishlistEntity root = root(projectId, rootId, WishlistStatus.converted_to_task);
        List<WishlistEntity> items = plannedItems(projectId, feature.getId(), 1);
        List<TaskEntity> tasks = tasksFor(projectId, feature.getId(), items, "BARCAN-TAG-06");
        stubPlan(projectId, root, feature, items, tasks);
        stubMerged(tasks.get(0), true); // hasCode=true, no qualityGateReport set at all
        assertEquals(null, tasks.get(0).getQualityGateReport());

        assertEquals(1, service.computeForProject(projectId).mergedDeliverables());
    }

    @Test
    void mergeIntoOpenFeatureThreadDoesNotCountUntilThreadClosesIntoMain() {
        UUID projectId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        FeatureEntity feature = feature(projectId, rootId);
        WishlistEntity root = root(projectId, rootId, WishlistStatus.converted_to_task);
        List<WishlistEntity> items = plannedItems(projectId, feature.getId(), 1);
        List<TaskEntity> tasks = tasksFor(projectId, feature.getId(), items, "BARCAN-TAG-02");
        stubPlan(projectId, root, feature, items, tasks);

        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(UUID.randomUUID());
        PrReviewEntity review = new PrReviewEntity();
        review.setMerged(true);
        review.setHasCode(true);
        review.setBaseRef("feat/some-thread-branch");
        when(julesSessionRepository.findByTaskId(tasks.get(0).getId())).thenReturn(List.of(session));
        when(prReviewRepository.findByJulesSessionIdInAndMergedTrue(List.of(session.getId())))
                .thenReturn(List.of(review));

        com.eneik.production.models.persistence.FeatureThreadEntity thread =
                new com.eneik.production.models.persistence.FeatureThreadEntity();
        when(featureThreadRepository.findByProjectIdAndFeatureId(projectId, feature.getId()))
                .thenReturn(java.util.Optional.of(thread));

        assertEquals(0, service.computeForProject(projectId).mergedDeliverables());
        assertFalse(service.reachedMain(tasks.get(0)));

        thread.setMergedToMainAt(java.time.Instant.now());

        assertEquals(1, service.computeForProject(projectId).mergedDeliverables());
        assertTrue(service.reachedMain(tasks.get(0)));
    }

    @Test
    void featureIsReadyForCloseoutAsSoonAsAnyTerminalTaskHasRealMergedWorkEvenWithSiblingsStillInReview() {
        // 2026-07-26 operator directive ("убрать блокировку закрытия"): closeout used to wait for EVERY
        // sibling task to reach a terminal status before folding ANY already-merged work into main -
        // confirmed live (test-thirty-eighth) holding completeFeatures at 0 indefinitely behind ordinary
        // review-stage siblings. A sibling still in review has no commits on the thread branch yet, so
        // there is nothing of theirs to wait for.
        UUID projectId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();
        TaskEntity mergedDone = task(UUID.randomUUID(), projectId, featureId, UUID.randomUUID(), "BARCAN-TAG-02");
        stubMerged(mergedDone, true);
        TaskEntity siblingStillInReview = task(UUID.randomUUID(), projectId, featureId, UUID.randomUUID(), "BARCAN-TAG-02");
        siblingStillInReview.setStatus(TaskStatus.review);
        when(taskRepository.findByFeatureId(featureId)).thenReturn(List.of(mergedDone, siblingStillInReview));
        when(wishlistRepository.findByFeatureId(featureId)).thenReturn(List.of());

        assertTrue(service.isFeatureReadyForCloseout(projectId, featureId));
    }

    @Test
    void featureIsNotReadyForCloseoutWhenNoTerminalTaskHasRealMergedWork() {
        UUID projectId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();
        TaskEntity doneWithoutCode = task(UUID.randomUUID(), projectId, featureId, UUID.randomUUID(), "BARCAN-TAG-02");
        stubMerged(doneWithoutCode, false);
        TaskEntity stillInReview = task(UUID.randomUUID(), projectId, featureId, UUID.randomUUID(), "BARCAN-TAG-02");
        stillInReview.setStatus(TaskStatus.review);
        when(taskRepository.findByFeatureId(featureId)).thenReturn(List.of(doneWithoutCode, stillInReview));

        assertFalse(service.isFeatureReadyForCloseout(projectId, featureId));
    }

    @Test
    void deliveryDecisionRecordIsExcludedFromCodeMergeRatioEntirely() {
        // Operator directive 2026-07-24 (sharpened over two rounds of correction): the readiness ratio
        // must only count tasks that produce code, not decision/spike/review/other auxiliary work.
        // Superseded behavior: a BARCAN-TAG-09 (EmsFlowStage.DECISION) task used to count as "merged"
        // via a special-case in hasRequiredMergeEvidence even without code - now it is excluded from the
        // ratio entirely (contributes to neither total nor merged), not force-counted as done.
        UUID projectId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        FeatureEntity feature = feature(projectId, rootId);
        WishlistEntity root = root(projectId, rootId, WishlistStatus.converted_to_task);
        List<WishlistEntity> items = plannedItems(projectId, feature.getId(), 1);
        List<TaskEntity> tasks = tasksFor(projectId, feature.getId(), items, "BARCAN-TAG-09");
        stubPlan(projectId, root, feature, items, tasks);
        stubMerged(tasks.get(0), false);

        ClientDeliverableReadinessService.Readiness readiness = service.computeForProject(projectId);
        assertEquals(0, readiness.totalDeliverables());
        assertEquals(0, readiness.mergedDeliverables());
        // Nothing left in scope to measure - must read as "not applicable", never as "0% done".
        assertEquals(1.0, readiness.ratio(), 0.0001);
    }

    @Test
    void spikeTaskIsExcludedFromCodeMergeRatioEvenThoughItsPrNeverMerges() {
        // The live bug that triggered this fix: AutoMergeService deliberately never merges a
        // `complex`-Cynefin spike's PR (its deliverable is a decision record, not shippable code), so the
        // old formula's "requires a merged review" check could never pass for it - permanently deflating
        // the denominator with zero relation to duplication or any other confirmed bug. A code-producing
        // role (BARCAN-TAG-02) makes this deliberate, not a DECISION-stage coincidence.
        UUID projectId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        FeatureEntity feature = feature(projectId, rootId);
        WishlistEntity root = root(projectId, rootId, WishlistStatus.converted_to_task);
        List<WishlistEntity> items = plannedItems(projectId, feature.getId(), 1);
        List<TaskEntity> tasks = tasksFor(projectId, feature.getId(), items, "BARCAN-TAG-02");
        tasks.get(0).setCynefinDomain("complex");
        tasks.get(0).setStatus(TaskStatus.spike_completed);
        stubPlan(projectId, root, feature, items, tasks);
        // Deliberately no stubMerged() - a spike's review is never merged=true by design.

        ClientDeliverableReadinessService.Readiness readiness = service.computeForProject(projectId);
        assertEquals(0, readiness.totalDeliverables());
        assertEquals(0, readiness.mergedDeliverables());
        assertTrue(readiness.decompositionComplete());
    }

    @Test
    void pendingIterationRootBlocksFalsificationEvenIfExistingPlanMerged() {
        UUID projectId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        FeatureEntity feature = feature(projectId, rootId);
        WishlistEntity root = root(projectId, rootId, WishlistStatus.pending);
        List<WishlistEntity> items = plannedItems(projectId, feature.getId(), 1);
        List<TaskEntity> tasks = tasksFor(projectId, feature.getId(), items, "BARCAN-TAG-02");
        stubPlan(projectId, root, feature, items, tasks);
        stubMerged(tasks.get(0), true);

        ClientDeliverableReadinessService.Readiness readiness = service.computeForProject(projectId);
        assertFalse(readiness.decompositionComplete());
        assertEquals(1.0, readiness.ratio(), 0.0001);
    }

    @Test
    void dismissedAuditRootDoesNotBlockCompletedProductDecomposition() {
        UUID projectId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        FeatureEntity feature = feature(projectId, rootId);
        WishlistEntity root = root(projectId, rootId, WishlistStatus.converted_to_task);
        WishlistEntity dismissedAudit = root(projectId, UUID.randomUUID(), WishlistStatus.dismissed);
        dismissedAudit.setSource(WishlistSource.coverage_gap);
        List<WishlistEntity> items = plannedItems(projectId, feature.getId(), 1);
        List<TaskEntity> tasks = tasksFor(projectId, feature.getId(), items, "BARCAN-TAG-02");
        List<WishlistEntity> all = new ArrayList<>();
        all.add(root);
        all.add(dismissedAudit);
        all.addAll(items);
        when(wishlistRepository.findByProjectId(projectId)).thenReturn(all);
        when(featureRepository.findByProjectIdAndDismissedAtIsNull(projectId)).thenReturn(List.of(feature));
        when(taskRepository.findBySourceWishlistIdIn(items.stream().map(WishlistEntity::getId).toList()))
                .thenReturn(tasks);

        assertTrue(service.computeForProject(projectId).decompositionComplete());
    }

    @Test
    void dependencyReplacementMustShareSemanticKey() {
        UUID projectId = UUID.randomUUID();
        UUID featureId = UUID.randomUUID();
        TaskEntity failed = task(UUID.randomUUID(), projectId, featureId, UUID.randomUUID(), "BARCAN-TAG-02");
        failed.setStatus(TaskStatus.failed);
        failed.setPayload(payload("ems:one"));
        TaskEntity otherSlice = task(UUID.randomUUID(), projectId, featureId, UUID.randomUUID(), "BARCAN-TAG-02");
        otherSlice.setPayload(payload("ems:other"));
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of(failed, otherSlice));
        stubMerged(otherSlice, true);

        assertFalse(service.isDependencySatisfied(failed));

        otherSlice.setPayload(payload("ems:one"));
        assertTrue(service.isDependencySatisfied(failed));
    }

    @Test
    void taskWithoutSessionCannotBeMerged() {
        UUID taskId = UUID.randomUUID();
        when(julesSessionRepository.findByTaskId(taskId)).thenReturn(List.of());
        assertFalse(service.isTaskMerged(taskId));
    }

    @Test
    void apiContractTaskWithOpenPrIsEarlyUnblockable() {
        TaskEntity contractTask = task(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "BARCAN-TAG-12");
        contractTask.setStatus(TaskStatus.review);
        assertTrue(service.isSpecDependencyPrOpenButUnmerged(contractTask));

        contractTask.setStatus(TaskStatus.pending_review);
        assertTrue(service.isSpecDependencyPrOpenButUnmerged(contractTask));

        contractTask.setStatus(TaskStatus.done);
        assertTrue(service.isSpecDependencyPrOpenButUnmerged(contractTask));
    }

    @Test
    void apiContractTaskStillQueuedIsNotEarlyUnblockable() {
        TaskEntity contractTask = task(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "BARCAN-TAG-12");
        contractTask.setStatus(TaskStatus.queued);
        assertFalse(service.isSpecDependencyPrOpenButUnmerged(contractTask));

        contractTask.setStatus(TaskStatus.claimed);
        assertFalse(service.isSpecDependencyPrOpenButUnmerged(contractTask));

        contractTask.setStatus(TaskStatus.failed);
        assertFalse(service.isSpecDependencyPrOpenButUnmerged(contractTask));
    }

    @Test
    void nonSpecRoleIsNeverEarlyUnblockableEvenInReview() {
        TaskEntity backendTask = task(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "BARCAN-TAG-02");
        backendTask.setStatus(TaskStatus.review);
        assertFalse(service.isSpecDependencyPrOpenButUnmerged(backendTask));
    }

    @Test
    void nullDependencyOrRoleIsNeverEarlyUnblockable() {
        assertFalse(service.isSpecDependencyPrOpenButUnmerged(null));

        TaskEntity noRole = task(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "BARCAN-TAG-12");
        noRole.setRole(null);
        noRole.setStatus(TaskStatus.review);
        assertFalse(service.isSpecDependencyPrOpenButUnmerged(noRole));
    }

    @Test
    void decisionArchitectureAndComplianceRolesAreEarlyUnblockableWhenPrIsOpen() {
        for (String roleTag : List.of("BARCAN-TAG-09", "BARCAN-TAG-01", "BARCAN-TAG-10")) {
            TaskEntity specTask = task(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), roleTag);

            specTask.setStatus(TaskStatus.review);
            assertTrue(service.isSpecDependencyPrOpenButUnmerged(specTask), roleTag + " in review");

            specTask.setStatus(TaskStatus.pending_review);
            assertTrue(service.isSpecDependencyPrOpenButUnmerged(specTask), roleTag + " in pending_review");

            specTask.setStatus(TaskStatus.done);
            assertTrue(service.isSpecDependencyPrOpenButUnmerged(specTask), roleTag + " when done");
        }
    }

    @Test
    void operationsAndVerificationRolesAreNeverEarlyUnblockableEvenInReview() {
        // Not previously covered at all - explicit re-confirmation these two stay excluded (real deploy
        // config / real test results, not a reference document a dependent can build against pre-merge).
        for (String roleTag : List.of("BARCAN-TAG-05", "BARCAN-TAG-06")) {
            TaskEntity task = task(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), roleTag);
            task.setStatus(TaskStatus.review);
            assertFalse(service.isSpecDependencyPrOpenButUnmerged(task), roleTag + " must stay excluded");
        }
    }

    private void stubPlan(UUID projectId, WishlistEntity root, FeatureEntity feature,
                          List<WishlistEntity> items, List<TaskEntity> tasks) {
        List<WishlistEntity> all = new ArrayList<>();
        all.add(root);
        all.addAll(items);
        when(wishlistRepository.findByProjectId(projectId)).thenReturn(all);
        when(featureRepository.findByProjectIdAndDismissedAtIsNull(projectId)).thenReturn(List.of(feature));
        when(taskRepository.findBySourceWishlistIdIn(items.stream().map(WishlistEntity::getId).toList()))
                .thenReturn(tasks);
    }

    private FeatureEntity feature(UUID projectId, UUID rootId) {
        FeatureEntity feature = new FeatureEntity();
        feature.setId(UUID.randomUUID());
        feature.setProjectId(projectId);
        feature.setRootWishlistId(rootId);
        return feature;
    }

    private WishlistEntity root(UUID projectId, UUID rootId, WishlistStatus status) {
        WishlistEntity root = new WishlistEntity();
        root.setId(rootId);
        root.setProjectId(projectId);
        root.setSource(WishlistSource.client);
        root.setContent("root brief");
        root.setStatus(status);
        return root;
    }

    private List<WishlistEntity> plannedItems(UUID projectId, UUID featureId, int count) {
        List<WishlistEntity> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            WishlistEntity item = new WishlistEntity();
            item.setId(UUID.randomUUID());
            item.setProjectId(projectId);
            item.setSource(WishlistSource.client);
            item.setContent("planned item " + i);
            item.setStatus(WishlistStatus.converted_to_task);
            item.setCompiledByRole("BARCAN-TAG-09");
            item.setFeatureId(featureId);
            items.add(item);
        }
        return items;
    }

    private List<TaskEntity> tasksFor(UUID projectId, UUID featureId, List<WishlistEntity> items, String roleTag) {
        return items.stream().map(item -> task(UUID.randomUUID(), projectId, featureId, item.getId(), roleTag)).toList();
    }

    private TaskEntity task(UUID id, UUID projectId, UUID featureId, UUID wishlistId, String roleTag) {
        TaskEntity task = new TaskEntity();
        task.setId(id);
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        task.setProject(project);
        task.setFeatureId(featureId);
        task.setSourceWishlistId(wishlistId);
        RoleEntity role = new RoleEntity();
        role.setTag(roleTag);
        task.setRole(role);
        task.setStatus(TaskStatus.done);
        return task;
    }

    private void stubMerged(TaskEntity task, boolean hasCode) {
        JulesSessionEntity session = new JulesSessionEntity();
        session.setId(UUID.randomUUID());
        PrReviewEntity review = new PrReviewEntity();
        review.setMerged(true);
        review.setHasCode(hasCode);
        when(julesSessionRepository.findByTaskId(task.getId())).thenReturn(List.of(session));
        when(prReviewRepository.findByJulesSessionIdInAndMergedTrue(List.of(session.getId())))
                .thenReturn(List.of(review));
    }

    private ObjectNode payload(String semanticKey) {
        ObjectNode payload = new ObjectMapper().createObjectNode();
        payload.put("ems_semantic_key", semanticKey);
        return payload;
    }

    // --- Valueless-epic cleanup (2026-07-25, live incident: two epics permanently stuck at 0 code items -
    // one from a dismissed wishlist, one whose only task was a resolved DECISION-stage record - dragged
    // down completeFeatures/totalFeatures forever with no way to resolve themselves) -------------------

    private static final java.time.Instant OLD_ENOUGH = java.time.Instant.now().minus(java.time.Duration.ofHours(1));

    @Test
    void deleteValuelessEpicsRemovesAnEpicWhoseOnlySourceWishlistWasDismissed() {
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        UUID rootId = UUID.randomUUID();
        WishlistEntity root = root(projectId, rootId, WishlistStatus.converted_to_task);
        FeatureEntity feature = feature(projectId, rootId);
        feature.setCreatedAt(OLD_ENOUGH);

        WishlistEntity dismissedItem = plannedItems(projectId, feature.getId(), 1).get(0);
        dismissedItem.setStatus(WishlistStatus.dismissed);

        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));
        stubPlan(projectId, root, feature, List.of(dismissedItem), List.of());
        when(featureThreadRepository.findByProjectIdAndFeatureId(projectId, feature.getId())).thenReturn(java.util.Optional.empty());
        when(featureRepository.findById(feature.getId())).thenReturn(java.util.Optional.of(feature));

        service.deleteValuelessEpics();

        // 2026-08-04 (3-layer model): soft-delete now, not deleteById - the row survives with its
        // originFeatureId lineage intact, only dismissedAt marks it as excluded from active readiness.
        verify(featureRepository, never()).deleteById(any());
        verify(featureRepository).save(feature);
        assertNotNull(feature.getDismissedAt());
    }

    @Test
    void deleteValuelessEpicsSkipsCleanupEntirelyWhenDispatchIsBlockedProjectWide() {
        // Live incident, 2026-08-07 (test-forty-third): this cron ran 5x during a project-wide dispatch
        // freeze (BLOCKED_BY_DUPLICATE_CONTENT) and wrongly dismissed real epics whose tasks simply hadn't
        // been allowed to dispatch yet - "zero code-producing items" is not real evidence of valuelessness
        // while nothing can dispatch at all. Same fixture as the test above (which WOULD normally dismiss
        // this epic), but with dispatch currently denied - cleanup must not touch it this cycle.
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        UUID rootId = UUID.randomUUID();
        WishlistEntity root = root(projectId, rootId, WishlistStatus.converted_to_task);
        FeatureEntity feature = feature(projectId, rootId);
        feature.setCreatedAt(OLD_ENOUGH);

        WishlistEntity dismissedItem = plannedItems(projectId, feature.getId(), 1).get(0);
        dismissedItem.setStatus(WishlistStatus.dismissed);

        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));
        stubPlan(projectId, root, feature, List.of(dismissedItem), List.of());
        when(operationalPolicyService.authorize(eq(projectId), eq(com.eneik.production.services.operational.OperationalAction.DISPATCH_QUEUED_TASKS)))
                .thenReturn(new com.eneik.production.services.operational.OperationalPolicyService.OperationalDecision(
                        projectId, com.eneik.production.services.operational.OperationalAction.DISPATCH_QUEUED_TASKS,
                        false, "BLOCKED_BY_DUPLICATE_CONTENT", "authorized", "denied", List.of(), null));

        service.deleteValuelessEpics();

        verify(featureRepository, never()).findById(any());
        verify(featureRepository, never()).save(any());
        assertNull(feature.getDismissedAt());
    }

    @Test
    void unDismissFeatureIfNeededClearsDismissedAtWhenRealWorkResumesUnderIt() {
        // Self-healing counterpart, called from JulesDispatchService right when a task actually starts
        // dispatching - proof the earlier "valueless" dismissal no longer holds.
        FeatureEntity feature = feature(UUID.randomUUID(), UUID.randomUUID());
        feature.setDismissedAt(java.time.Instant.now());
        when(featureRepository.findById(feature.getId())).thenReturn(java.util.Optional.of(feature));

        service.unDismissFeatureIfNeeded(feature.getId());

        assertNull(feature.getDismissedAt());
        verify(featureRepository).save(feature);
    }

    @Test
    void unDismissFeatureIfNeededIsANoOpForAFeatureThatWasNeverDismissed() {
        FeatureEntity feature = feature(UUID.randomUUID(), UUID.randomUUID());
        when(featureRepository.findById(feature.getId())).thenReturn(java.util.Optional.of(feature));

        service.unDismissFeatureIfNeeded(feature.getId());

        assertNull(feature.getDismissedAt());
        verify(featureRepository, never()).save(any());
    }

    @Test
    void unDismissFeatureIfNeededIsANoOpForANullFeatureId() {
        service.unDismissFeatureIfNeeded(null);

        verify(featureRepository, never()).findById(any());
        verify(featureRepository, never()).save(any());
    }

    @Test
    void deleteValuelessEpicsRemovesAnEpicWhoseOnlyTaskIsAuxiliaryAndAlsoDeletesItsFeatureThread() {
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        UUID rootId = UUID.randomUUID();
        WishlistEntity root = root(projectId, rootId, WishlistStatus.converted_to_task);
        FeatureEntity feature = feature(projectId, rootId);
        feature.setCreatedAt(OLD_ENOUGH);

        List<WishlistEntity> items = plannedItems(projectId, feature.getId(), 1);
        // BARCAN-TAG-09 = DECISION stage - structurally never produces mergeable code (isAuxiliaryTask).
        List<TaskEntity> tasks = tasksFor(projectId, feature.getId(), items, "BARCAN-TAG-09");

        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));
        stubPlan(projectId, root, feature, items, tasks);
        FeatureThreadEntity thread = new FeatureThreadEntity();
        thread.setFeatureId(feature.getId());
        when(featureThreadRepository.findByProjectIdAndFeatureId(projectId, feature.getId())).thenReturn(java.util.Optional.of(thread));
        when(featureRepository.findById(feature.getId())).thenReturn(java.util.Optional.of(feature));

        service.deleteValuelessEpics();

        verify(featureThreadRepository).delete(thread);
        verify(featureRepository, never()).deleteById(any());
        verify(featureRepository).save(feature);
        assertNotNull(feature.getDismissedAt());
    }

    @Test
    void deleteValuelessEpicsNeverTouchesAnEpicWithRealCodeValue() {
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        UUID rootId = UUID.randomUUID();
        WishlistEntity root = root(projectId, rootId, WishlistStatus.converted_to_task);
        FeatureEntity feature = feature(projectId, rootId);
        feature.setCreatedAt(OLD_ENOUGH);

        List<WishlistEntity> items = plannedItems(projectId, feature.getId(), 1);
        List<TaskEntity> tasks = tasksFor(projectId, feature.getId(), items, "BARCAN-TAG-02"); // real code role

        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));
        stubPlan(projectId, root, feature, items, tasks);

        service.deleteValuelessEpics();

        verify(featureRepository, never()).deleteById(any());
        verify(featureRepository, never()).save(any());
        assertNull(feature.getDismissedAt());
    }

    @Test
    void deleteValuelessEpicsLeavesATooNewEpicAloneEvenWithZeroItems() {
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        UUID rootId = UUID.randomUUID();
        WishlistEntity root = root(projectId, rootId, WishlistStatus.converted_to_task);
        FeatureEntity feature = feature(projectId, rootId);
        feature.setCreatedAt(java.time.Instant.now()); // freshly created - give the compiler a chance

        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));
        stubPlan(projectId, root, feature, List.of(), List.of());

        service.deleteValuelessEpics();

        verify(featureRepository, never()).deleteById(any());
        verify(featureRepository, never()).save(any());
    }

    @Test
    void deleteValuelessEpicsLeavesAnEpicAloneWhileAWishlistItemIsStillPendingCompilation() {
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        project.setStatus(ProjectStatus.active);

        UUID rootId = UUID.randomUUID();
        WishlistEntity root = root(projectId, rootId, WishlistStatus.converted_to_task);
        FeatureEntity feature = feature(projectId, rootId);
        feature.setCreatedAt(OLD_ENOUGH);

        WishlistEntity pendingItem = plannedItems(projectId, feature.getId(), 1).get(0);
        pendingItem.setStatus(WishlistStatus.pending); // still might produce real code later

        when(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)).thenReturn(List.of(project));
        stubPlan(projectId, root, feature, List.of(pendingItem), List.of());

        service.deleteValuelessEpics();

        verify(featureRepository, never()).deleteById(any());
        verify(featureRepository, never()).save(any());
    }

    // 2026-08-20, order item 3: delivery had two sources of truth about one role.
    //
    //   EmsFlowStage.EXPERIENCE(30, "experience", specOnly=false, "BARCAN-TAG-03", "BARCAN-TAG-11")
    //   ClientDeliverableReadinessService.HAS_CODE_EXEMPT_ROLE_TAGS = Set.of("BARCAN-TAG-03")
    //
    // The enum says TAG-03 produces code; the private set says it does not. The set wins because it is
    // consulted first, so the enum - which every other part of the flow treats as the single source of
    // truth for what a role is - is silently wrong about delivery. The cause is that `specOnly` is a
    // criterion standing in for a different question: "does this role produce a specification" is not
    // "what artifact constitutes this role's delivery" (ACP-102, criterion is not the concept). TAG-03's
    // artifacts land in design/draft and design/approved, which CodeChangeClassifier already treats as
    // non-code - it delivers design, and neither `specOnly=true` nor `specOnly=false` says that.
    //
    // This test asserts the property that must hold however the answer is expressed: the delivery
    // predicate is fully determined by the role's declared stage, with no second list anywhere.
    @Test
    void everyRolesDeliveryArtifactIsDeclaredInExactlyOnePlace() {
        for (String roleTag : List.of("BARCAN-TAG-00", "BARCAN-TAG-01", "BARCAN-TAG-02", "BARCAN-TAG-03",
                "BARCAN-TAG-04", "BARCAN-TAG-05", "BARCAN-TAG-06", "BARCAN-TAG-07", "BARCAN-TAG-08",
                "BARCAN-TAG-09", "BARCAN-TAG-10", "BARCAN-TAG-11", "BARCAN-TAG-12")) {
            TaskEntity task = new TaskEntity();
            RoleEntity role = new RoleEntity();
            role.setTag(roleTag);
            task.setRole(role);

            boolean serviceSays = service.requiresCodeForDelivery(task);
            boolean stageSays = com.eneik.production.services.EmsFlowStage.requiresCodeForDelivery(roleTag);

            assertEquals(stageSays, serviceSays,
                    "role " + roleTag + ": the delivery predicate and the flow stage disagree, so delivery "
                            + "has two sources of truth for this role");
        }
    }

}
