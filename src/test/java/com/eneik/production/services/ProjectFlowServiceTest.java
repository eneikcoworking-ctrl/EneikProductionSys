package com.eneik.production.services;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.ClaimRepository;
import com.eneik.production.repositories.JulesActivityResponseRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.ProjectFinalReportRepository;
import com.eneik.production.repositories.ProjectGenerationStateRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.RoleRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.compiler.TechnicalLeadCompiler;
import com.eneik.production.services.dashboard.ClientDeliveryService;
import com.eneik.production.services.dashboard.EmsMetricsService;
import com.eneik.production.services.dashboard.ProjectOperationalContextService;
import com.eneik.production.services.design.DesignAssetService;
import com.eneik.production.services.github.GitHubPullRequestService;
import com.eneik.production.services.jules.JulesDispatchService;
import com.eneik.production.services.onboarding.OnboardingAuditService;
import com.eneik.production.services.operational.OperationalPolicyService;
import com.eneik.production.services.settings.SystemSettingsService;
import com.eneik.production.services.projectfactory.GitHubProjectFactoryClient;
import com.eneik.production.services.projectfactory.ProjectFactoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectFlowServiceTest {

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final GitHubPullRequestService gitHubPullRequestService = mock(GitHubPullRequestService.class);
    private final com.eneik.production.repositories.ProjectFileClaimRepository projectFileClaimRepository =
            mock(com.eneik.production.repositories.ProjectFileClaimRepository.class);

    @Test
    void deliverableMergeRatioUsesMergedPlannedTasksNotFeatureReadiness() {
        var readiness = new ClientDeliverableReadinessService.Readiness(
                5,
                3,
                19,
                17,
                0.6,
                true);

        assertEquals(17.0 / 19.0, ProjectFlowService.deliverableMergeRatio(readiness), 0.0001);
    }

    @Test
    void terminalSpikeCompletedIsNotAnActionableBlockedStatus() {
        assertFalse(ProjectFlowService.isActionableBlockedStatus(TaskStatus.failed));
        assertFalse(ProjectFlowService.isActionableBlockedStatus(TaskStatus.spike_completed));
        assertTrue(ProjectFlowService.isActionableBlockedStatus(TaskStatus.claimed));
        assertTrue(ProjectFlowService.isActionableBlockedStatus(TaskStatus.done));
    }

    // Smart-decomposition fix (2026-07-31), Part A: mirrors the already-proven
    // commitDeterministicJavaScaffoldIfAbsent backend fix (test-thirty-fifth) for the frontend side
    // (test-fortieth: three эпики each independently rewrote frontend/src/App.svelte from scratch).

    @Test
    void frontendScaffoldSkippedForBrownfieldProject() {
        ProjectFlowService service = service();
        ProjectEntity project = greenfieldProject();
        project.setOnboardingMode("brownfield");

        service.commitDeterministicFrontendScaffoldIfAbsent(project);

        verify(gitHubPullRequestService, never()).upsertFile(eq(project), anyString(), any(), anyString());
    }

    @Test
    void frontendScaffoldSkippedWhenAppSvelteAlreadyExists() {
        ProjectFlowService service = service();
        ProjectEntity project = greenfieldProject();
        stubNoManifestsExist(project);
        when(gitHubPullRequestService.fetchFileContent(project, "main", "frontend/src/App.svelte"))
                .thenReturn(Optional.of("<script>already here</script>"));

        service.commitDeterministicFrontendScaffoldIfAbsent(project);

        verify(gitHubPullRequestService, never()).upsertFile(eq(project), anyString(), any(), anyString());
    }

    @Test
    void frontendScaffoldSkippedWhenFrontendPackageJsonAlreadyExists() {
        ProjectFlowService service = service();
        ProjectEntity project = greenfieldProject();
        stubNoManifestsExist(project);
        when(gitHubPullRequestService.fetchFileContent(project, "main", "frontend/package.json"))
                .thenReturn(Optional.of("{}"));

        service.commitDeterministicFrontendScaffoldIfAbsent(project);

        verify(gitHubPullRequestService, never()).upsertFile(eq(project), anyString(), any(), anyString());
    }

    @Test
    void frontendScaffoldSkippedWhenBackendManifestAlreadyExists() {
        ProjectFlowService service = service();
        ProjectEntity project = greenfieldProject();
        stubNoManifestsExist(project);
        when(gitHubPullRequestService.fetchFileContent(project, "main", "pom.xml"))
                .thenReturn(Optional.of("<project></project>"));

        service.commitDeterministicFrontendScaffoldIfAbsent(project);

        verify(gitHubPullRequestService, never()).upsertFile(eq(project), anyString(), any(), anyString());
    }

    @Test
    void frontendScaffoldCommitsShellAndRoutesRegistryWhenAbsent() {
        ProjectFlowService service = service();
        ProjectEntity project = greenfieldProject();
        stubNoManifestsExist(project);
        when(gitHubPullRequestService.upsertFile(eq(project), anyString(), any(), anyString())).thenReturn(true);

        service.commitDeterministicFrontendScaffoldIfAbsent(project);

        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        verify(gitHubPullRequestService, times(6)).upsertFile(eq(project), pathCaptor.capture(), any(), anyString());
        List<String> committedPaths = pathCaptor.getAllValues();

        assertTrue(committedPaths.contains("frontend/package.json"));
        assertTrue(committedPaths.contains("frontend/vite.config.js"));
        assertTrue(committedPaths.contains("frontend/index.html"));
        assertTrue(committedPaths.contains("frontend/src/main.js"));
        assertTrue(committedPaths.contains("frontend/src/routes.js"));
        assertTrue(committedPaths.contains("frontend/src/App.svelte"));
    }

    // Smart-decomposition v2 (2026-07-31): the bootstrap scaffold now feeds the general cross-эпик
    // collision-guard ledger directly, instead of relying on an LLM prompt rule - every path it commits
    // becomes a project-wide claim (taskId=null, featureId=null) that TechnicalLeadCompiler's
    // applyCrossEpicCollisionGuard will strip out of any эпик's predicted fileScope, regardless of эпик.
    @Test
    void frontendScaffoldRecordsGlobalClaimsForEveryCommittedPath() {
        ProjectFlowService service = service();
        ProjectEntity project = greenfieldProject();
        stubNoManifestsExist(project);
        when(gitHubPullRequestService.upsertFile(eq(project), anyString(), any(), anyString())).thenReturn(true);

        service.commitDeterministicFrontendScaffoldIfAbsent(project);

        ArgumentCaptor<com.eneik.production.models.persistence.ProjectFileClaimEntity> claimCaptor =
                ArgumentCaptor.forClass(com.eneik.production.models.persistence.ProjectFileClaimEntity.class);
        verify(projectFileClaimRepository, times(6)).save(claimCaptor.capture());
        List<com.eneik.production.models.persistence.ProjectFileClaimEntity> claims = claimCaptor.getAllValues();

        assertTrue(claims.stream().anyMatch(c -> "frontend/src/App.svelte".equals(c.getFilePath())));
        assertTrue(claims.stream().anyMatch(c -> "frontend/src/routes.js".equals(c.getFilePath())));
        assertTrue(claims.stream().allMatch(c -> c.getTaskId() == null && c.getFeatureId() == null));
    }

    // --- O-15 (2026-08-21): F42's termination guard, in the one place both admission paths reach --------
    //
    // Deliberately written against the guard rather than against one admission path: the defect being
    // covered here IS that the guard lived on one path and not the other, so a test bound to a single
    // caller would reproduce the very mistake it is meant to catch.

    @Test
    void aBriefThatExhaustedItsDecompositionBudgetIsWithheldFromDispatch() {
        ProjectFlowService service = service();
        ProjectEntity project = greenfieldProject();
        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setId(UUID.randomUUID());
        wishlist.setProjectId(project.getId());
        wishlist.setCompileAttempts(3); // == WISHLIST_COMPILE_ATTEMPT_BUDGET, so mu = 0

        Boolean withheld = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                service, "withholdFromCompileDispatch", project, wishlist);

        assertTrue(Boolean.TRUE.equals(withheld));
        // F39: a finding nobody can retrieve is not a finding, so the withhold has to land where a human
        // actually looks - not only in a log line.
        assertTrue(project.getFactoryReport() != null
                        && project.getFactoryReport().contains(wishlist.getId().toString()),
                "the exhausted budget must be reported against this wishlist; factoryReport was: "
                        + project.getFactoryReport());
        verify(projectRepository).save(project);
    }

    @Test
    void aBriefStillInsideItsDecompositionBudgetIsNotWithheld() {
        // The guard is restrictive-only and must stay that way: it may remove a dispatch, never add one,
        // and it must not remove one that F42's own budget still permits.
        ProjectFlowService service = service();
        ProjectEntity project = greenfieldProject();
        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setId(UUID.randomUUID());
        wishlist.setProjectId(project.getId());
        wishlist.setCompileAttempts(2); // mu = 1

        Boolean withheld = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                service, "withholdFromCompileDispatch", project, wishlist);

        assertFalse(Boolean.TRUE.equals(withheld));
        verify(projectRepository, never()).save(project);
    }

    @Test
    void theBootstrapScaffoldDeclaresNoDatastoreAndWritesTheQuestionInstead() {
        // ACP-104. The scaffold used to write jdbc:h2 with ddl-auto=validate and Flyway on, deciding a
        // contingent fact about ONE brief before the stage that owns it had read the brief - and once it
        // is a file on main, ARCHITECTURE can only contradict it, so the contract stayed silent about a
        // question that looked answered. Measured cost: an H2-only CREATE ALIAS survived 144 merged
        // reviews and killed the product at character 8 of its first migration.
        String props = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                service(), "javaScaffoldApplicationProperties");
        assertFalse(props.contains("datasource"),
                "the scaffold may only contain what is true of EVERY product; a datastore is not: " + props);
        assertFalse(props.contains("h2"), props);

        String contract = ProjectFlowService.javaScaffoldRuntimeContract();
        assertTrue(contract.contains("datastore: UNDECLARED"),
                "the open question must be written down as an actual object - an absent line is silence, "
                        + "and a silent system is unrefutable");
        assertTrue(contract.contains("BARCAN-TAG-01"), "the question must name its owner");
    }

    @Test
    void theClientBriefQuotesOnlyEntriesTheClientActuallyWrote() {
        // Measured 2026-08-22: 19 rows on test-forty-ninth carried source=client, but 18 of them read
        // "Internal work item N (BARCAN-TAG-09) from wishlist ..." and carried a role tag. A client does
        // not address a role - it does not know this factory has any. Quoting all 19 buries the referent
        // under the factory's own decomposition, which §8.2 forbids by name.
        java.util.UUID projectId = java.util.UUID.randomUUID();
        ProjectEntity project = greenfieldProject();
        org.springframework.test.util.ReflectionTestUtils.setField(project, "id", projectId);
        project.setStatus(com.eneik.production.models.persistence.ProjectStatus.active);

        WishlistEntity fromClient = new WishlistEntity();
        fromClient.setProjectId(projectId);
        fromClient.setSource(com.eneik.production.models.persistence.WishlistSource.client);
        fromClient.setContent("Разработать веб-систему для каталогизации материалов");

        WishlistEntity fromFactory = new WishlistEntity();
        fromFactory.setProjectId(projectId);
        fromFactory.setSource(com.eneik.production.models.persistence.WishlistSource.client);
        fromFactory.setSourceRoleTag("BARCAN-TAG-09");
        fromFactory.setContent("Internal work item 1 (BARCAN-TAG-09) from wishlist 56484b6d");

        WishlistRepository wishlists = mock(WishlistRepository.class);
        when(wishlists.findByProjectId(projectId)).thenReturn(List.of(fromClient, fromFactory));
        ProjectFlowService service = serviceWithWishlists(wishlists);
        when(gitHubPullRequestService.fetchFileContent(any(), any(), eq("docs/PROJECT_BRIEF.md")))
                .thenReturn(Optional.empty());

        service.syncClientBriefToRepository(project);

        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        verify(gitHubPullRequestService).upsertFile(any(), eq("docs/PROJECT_BRIEF.md"), captor.capture(), anyString());
        String written = new String(captor.getValue(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(written.contains("каталогизации"), "the client's own words must be there: " + written);
        assertFalse(written.contains("BARCAN-TAG-09"),
                "the factory's own decomposition must not be quoted as the client's brief: " + written);
    }

    private void stubNoManifestsExist(ProjectEntity project) {
        when(gitHubPullRequestService.fetchFileContent(eq(project), eq("main"), anyString()))
                .thenReturn(Optional.empty());
    }

    private ProjectEntity greenfieldProject() {
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("Test Project");
        project.setSlug("test-project");
        project.setOnboardingMode("greenfield");
        return project;
    }

    @Test
    void purgesOrchestratorRecordsFromMainAndLeavesProductCodeAlone() {
        // 2026-08-27, strict onto-separation. Measured on test-fiftieth: 168 of 491 files on main lived
        // under `.eneik/` - the factory's own coverage audits and falsification reports inside the client's
        // product. Nothing reads them from main; every consumer takes a headRef, so the branch is the
        // transport and the merge was never part of it.
        ProjectEntity project = new ProjectEntity();
        org.springframework.test.util.ReflectionTestUtils.setField(project, "id", java.util.UUID.randomUUID());
        project.setName("test-fiftieth");

        ProjectFlowService service = service();
        // Without this the @Value field is 0 in a unit test, the loop breaks on its first iteration, and the
        // assertions below would pass against a method that did nothing.
        org.springframework.test.util.ReflectionTestUtils.setField(service, "orchestratorRecordPurgeBatch", 25);

        when(gitHubPullRequestService.listFilePaths(eq(project), eq("main"), eq(".eneik/")))
                .thenReturn(java.util.List.of(
                        ".eneik/records/coverage-audit-1.json",
                        ".eneik/records/philosophical-falsification-2.json"));
        when(gitHubPullRequestService.deleteFile(eq(project), anyString(), anyString())).thenReturn(true);

        service.purgeOrchestratorRecordsFromClientRepo(project);

        verify(gitHubPullRequestService).deleteFile(eq(project), eq(".eneik/records/coverage-audit-1.json"), anyString());
        verify(gitHubPullRequestService).deleteFile(eq(project), eq(".eneik/records/philosophical-falsification-2.json"), anyString());
    }

    @Test
    void purgeNeverDeletesProductCodeEvenWhenItIsHandedSome() {
        // The other side, without which the test above proves only that a method can delete something.
        ProjectEntity project = new ProjectEntity();
        org.springframework.test.util.ReflectionTestUtils.setField(project, "id", java.util.UUID.randomUUID());
        project.setName("test-fiftieth");

        ProjectFlowService service = service();
        org.springframework.test.util.ReflectionTestUtils.setField(service, "orchestratorRecordPurgeBatch", 25);

        // Product paths are fed in deliberately. listFilePaths filters by prefix in production, so trusting
        // it here would test the mock rather than the method: this pins the guard inside the loop, which is
        // what stands between a purge and deleting the client's delivered work - scripts/backup.sh is the
        // shipped result of the closed "System Backups and Resilience" epic.
        when(gitHubPullRequestService.listFilePaths(eq(project), eq("main"), eq(".eneik/")))
                .thenReturn(java.util.List.of(
                        "scripts/backup.sh",
                        "src/main/java/com/eneik/epidemiology/document/Document.java",
                        "docker-compose.yml"));

        service.purgeOrchestratorRecordsFromClientRepo(project);

        verify(gitHubPullRequestService, never()).deleteFile(eq(project), anyString(), anyString());
    }

    private ProjectFlowService service() {
        return serviceWithWishlists(mock(WishlistRepository.class));
    }

    private ProjectFlowService serviceWithWishlists(WishlistRepository wishlistRepository) {
        return serviceWithWishlistsAndWorker(wishlistRepository, mock(PersistentWorkerSessionService.class));
    }

    private ProjectFlowService serviceWithWishlistsAndWorker(WishlistRepository wishlistRepository,
            PersistentWorkerSessionService persistentWorkerSessionService) {
        return serviceWithWishlistsAndWorker(wishlistRepository, persistentWorkerSessionService,
                mock(JulesSessionRepository.class));
    }

    private ProjectFlowService serviceWithWishlistsAndWorker(WishlistRepository wishlistRepository,
            PersistentWorkerSessionService persistentWorkerSessionService,
            JulesSessionRepository julesSessionRepository) {
        ProjectFlowService service = new ProjectFlowService(
                projectRepository,
                wishlistRepository,
                mock(AccountRepository.class),
                mock(TaskRepository.class),
                mock(ClaimRepository.class),
                mock(RoleRepository.class),
                mock(ClaimService.class),
                mock(JulesDispatchService.class),
                mock(ProjectFactoryService.class),
                mock(GitHubProjectFactoryClient.class),
                mock(SystemSettingsService.class),
                null,
                null,
                mock(TechnicalLeadCompiler.class),
                mock(ClientDeliveryService.class),
                mock(ProjectFinalReportRepository.class),
                julesSessionRepository,
                mock(JulesActivityResponseRepository.class),
                mock(ProjectGenerationStateRepository.class),
                new ObjectMapper(),
                "eneik-test-org",
                mock(OnboardingAuditService.class),
                mock(EmsMetricsService.class),
                mock(ProjectOperationalContextService.class),
                mock(DesignAssetService.class),
                gitHubPullRequestService,
                mock(ClientDeliverableReadinessService.class),
                mock(FeatureService.class),
                persistentWorkerSessionService,
                mock(SelfFalsificationEpicMatcher.class),
                mock(OperationalPolicyService.class),
                projectFileClaimRepository,
                mock(RequirementGroundingService.class),
                mock(GeminiContextService.class),
                mock(com.eneik.production.repositories.TaskConflictRepository.class),
                mock(com.eneik.production.repositories.NeedsHumanReviewRepository.class),
                mock(com.eneik.production.repositories.LinearIssueMetadataRepository.class),
                mock(com.eneik.production.repositories.FeatureRepository.class),
                mock(com.eneik.production.repositories.FeatureThreadRepository.class),
                null);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "self", service);
        return service;
    }

    // --- queuedDispatchClass (§4.8): the ordering that replaced the BUILD-phase boolean hold ----------
    //
    // Measured 2026-08-29: 26 queued tasks, none client-rooted, 17 of them self-generated and untried since
    // 28.08 04:07, 7 more waiting on those 17. The hold lifts only when client deliverables merge, and the
    // client brief's four tasks were already done - so the event it waited for could no longer occur. The
    // order below has to keep client work ahead of self-generated work (the intent) while still admitting
    // self-generated work when no client work is queued (the deadlock).

    private TaskEntity taskFromWishlist(WishlistRepository wishlistRepository,
            com.eneik.production.models.persistence.WishlistSource source) {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        if (source != null) {
            WishlistEntity w = new WishlistEntity();
            w.setId(UUID.randomUUID());
            w.setSource(source);
            task.setSourceWishlistId(w.getId());
            when(wishlistRepository.findById(w.getId())).thenReturn(Optional.of(w));
        }
        return task;
    }

    @Test
    void clientRootedWorkIsOfferedCapacityBeforeSelfGeneratedWork() {
        WishlistRepository wishlistRepository = mock(WishlistRepository.class);
        ProjectFlowService service = serviceWithWishlists(wishlistRepository);

        TaskEntity client = taskFromWishlist(wishlistRepository,
                com.eneik.production.models.persistence.WishlistSource.client);
        TaskEntity selfGenerated = taskFromWishlist(wishlistRepository,
                com.eneik.production.models.persistence.WishlistSource.delivery_never_reached_main);

        assertTrue(service.queuedDispatchClass(client) < service.queuedDispatchClass(selfGenerated),
                "client-rooted work must be offered capacity before self-generated work");
    }

    @Test
    void selfGeneratedWorkStillOutranksHousekeepingCarriers() {
        WishlistRepository wishlistRepository = mock(WishlistRepository.class);
        ProjectFlowService service = serviceWithWishlists(wishlistRepository);

        TaskEntity selfGenerated = taskFromWishlist(wishlistRepository,
                com.eneik.production.models.persistence.WishlistSource.delivery_never_reached_main);
        TaskEntity reviewFallback = new TaskEntity();
        reviewFallback.setId(UUID.randomUUID());
        com.fasterxml.jackson.databind.node.ObjectNode payload = new ObjectMapper().createObjectNode();
        payload.put("taskType", "pr_review_fallback");
        reviewFallback.setPayload(payload);

        assertTrue(service.queuedDispatchClass(selfGenerated) < service.queuedDispatchClass(reviewFallback));
    }

    /** The whole point of the change: nothing in the order can express "never". */
    @Test
    void everyClassIsDispatchableRatherThanHeld() {
        WishlistRepository wishlistRepository = mock(WishlistRepository.class);
        ProjectFlowService service = serviceWithWishlists(wishlistRepository);

        TaskEntity selfGenerated = taskFromWishlist(wishlistRepository,
                com.eneik.production.models.persistence.WishlistSource.delivery_never_reached_main);
        TaskEntity noWishlist = taskFromWishlist(wishlistRepository, null);

        // A rank, not a hold: both sort, both are reached by the loop.
        assertTrue(service.queuedDispatchClass(selfGenerated) >= 0);
        assertTrue(service.queuedDispatchClass(noWishlist) >= 0);
        assertTrue(service.queuedDispatchClass(noWishlist) < service.queuedDispatchClass(selfGenerated),
                "a task with no wishlist at all is not self-generated work and must not be ranked as such");
    }

    // --- restoreUnreachedBriefs (§4.2): the restoring half of the REFUSED/UNREACHED split -------------
    //
    // Written 2026-08-28 because it had no test at all, and it is the half that must not be got wrong in
    // either direction. Reviving a brief the compiler DID answer would resurrect an absorbing state and
    // break the variant function that gives decomposition its termination (§4.2). Failing to revive one
    // the compiler never saw would leave the factory's own busy-ness recorded as a verdict about the
    // brief - the defect the whole split exists to fix.

    private WishlistEntity brief(UUID projectId, int attempts, java.time.Instant dispatchedAt,
            java.time.Instant reachedAt) {
        WishlistEntity w = new WishlistEntity();
        w.setId(UUID.randomUUID());
        w.setStatus(com.eneik.production.models.persistence.WishlistStatus.pending);
        w.setCompileAttempts(attempts);
        w.setLastCompileDispatchedAt(dispatchedAt);
        w.setLastCompileReachedAt(reachedAt);
        return w;
    }

    private void worker(PersistentWorkerSessionService workerService, UUID projectId,
            java.time.Instant lastMessageSentAt) {
        com.eneik.production.models.persistence.PersistentWorkerSessionEntity session =
                new com.eneik.production.models.persistence.PersistentWorkerSessionEntity();
        session.setLastMessageSentAt(lastMessageSentAt);
        when(workerService.findActiveWorker(eq(projectId),
                eq(com.eneik.production.models.persistence.PersistentWorkerPurpose.WISHLIST_COMPILER)))
                .thenReturn(Optional.of(session));
    }

    @Test
    void unreachedBriefIsRestoredOnceTheChannelHasBeenLiveSinceItsLastAttempt() {
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);

        java.time.Instant dispatched = java.time.Instant.parse("2026-08-28T10:00:00Z");
        java.time.Instant watermark = dispatched.plusSeconds(3600);

        WishlistRepository wishlistRepository = mock(WishlistRepository.class);
        PersistentWorkerSessionService workerService = mock(PersistentWorkerSessionService.class);
        worker(workerService, projectId, watermark);

        WishlistEntity unreached = brief(projectId, 3, dispatched, null);
        when(wishlistRepository.findByProjectId(projectId)).thenReturn(List.of(unreached));

        int restored = serviceWithWishlistsAndWorker(wishlistRepository, workerService)
                .restoreUnreachedBriefs(project);

        assertEquals(1, restored);
        assertEquals(0, unreached.getCompileAttempts());
        verify(wishlistRepository).saveAll(List.of(unreached));
    }

    // 2026-08-29. The two tests above pass with the persistent worker's watermark, and that watermark is
    // never written on this factory: measured that day, persistent_worker_sessions held one row, purpose
    // PHILOSOPHICAL_AUDIT, retired 25.08 - no WISHLIST_COMPILER worker had ever existed, so the restoration
    // returned 0 on every call while six briefs sat with a spent budget the compiler had never answered.
    // These two fix the case the live system is actually in.

    @Test
    void unreachedBriefIsRestoredFromTheOneShotChannelWithNoPersistentWorker() {
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);

        java.time.Instant dispatched = java.time.Instant.parse("2026-08-28T05:10:40Z");
        java.time.Instant accepted = java.time.Instant.parse("2026-08-29T00:18:17Z");

        WishlistRepository wishlistRepository = mock(WishlistRepository.class);
        PersistentWorkerSessionService workerService = mock(PersistentWorkerSessionService.class);
        // No worker at all - exactly what the live table holds.
        when(workerService.findActiveWorker(eq(projectId),
                eq(com.eneik.production.models.persistence.PersistentWorkerPurpose.WISHLIST_COMPILER)))
                .thenReturn(Optional.empty());
        JulesSessionRepository sessions = mock(JulesSessionRepository.class);
        when(sessions.latestAcceptedSessionAtForAccount("eneikdru")).thenReturn(accepted);

        WishlistEntity unreached = brief(projectId, 3, dispatched, null);
        when(wishlistRepository.findByProjectId(projectId)).thenReturn(List.of(unreached));

        assertEquals(1, serviceWithWishlistsAndWorker(wishlistRepository, workerService, sessions)
                .restoreUnreachedBriefs(project));
        assertEquals(0, unreached.getCompileAttempts());
    }

    @Test
    void noChannelEventAtAllRestoresNothing() {
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);

        WishlistRepository wishlistRepository = mock(WishlistRepository.class);
        PersistentWorkerSessionService workerService = mock(PersistentWorkerSessionService.class);
        when(workerService.findActiveWorker(eq(projectId),
                eq(com.eneik.production.models.persistence.PersistentWorkerPurpose.WISHLIST_COMPILER)))
                .thenReturn(Optional.empty());
        JulesSessionRepository sessions = mock(JulesSessionRepository.class);
        when(sessions.latestAcceptedSessionAtForAccount("eneikdru")).thenReturn(null);

        WishlistEntity unreached = brief(projectId, 3, java.time.Instant.parse("2026-08-28T05:10:40Z"), null);
        when(wishlistRepository.findByProjectId(projectId)).thenReturn(List.of(unreached));

        assertEquals(0, serviceWithWishlistsAndWorker(wishlistRepository, workerService, sessions)
                .restoreUnreachedBriefs(project));
        assertEquals(3, unreached.getCompileAttempts());
    }

    /** A refusal writes no external session id, so a brief's own failed attempt cannot buy it more budget. */
    @Test
    void aBriefWhoseOwnAttemptFailedDoesNotAdvanceItsOwnWatermark() {
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);

        java.time.Instant dispatched = java.time.Instant.parse("2026-08-28T05:10:40Z");

        WishlistRepository wishlistRepository = mock(WishlistRepository.class);
        PersistentWorkerSessionService workerService = mock(PersistentWorkerSessionService.class);
        when(workerService.findActiveWorker(eq(projectId),
                eq(com.eneik.production.models.persistence.PersistentWorkerPurpose.WISHLIST_COMPILER)))
                .thenReturn(Optional.empty());
        JulesSessionRepository sessions = mock(JulesSessionRepository.class);
        // The channel last accepted a session BEFORE this brief's attempt; the attempt itself was refused
        // and wrote no external id, so the mark did not move.
        when(sessions.latestAcceptedSessionAtForAccount("eneikdru")).thenReturn(dispatched.minusSeconds(600));

        WishlistEntity unreached = brief(projectId, 3, dispatched, null);
        when(wishlistRepository.findByProjectId(projectId)).thenReturn(List.of(unreached));

        assertEquals(0, serviceWithWishlistsAndWorker(wishlistRepository, workerService, sessions)
                .restoreUnreachedBriefs(project));
        assertEquals(3, unreached.getCompileAttempts());
    }

    /**
     * Action plan 4.10. The watermark was project-wide for one hour on 2026-08-29 and the live circuit
     * showed what that costs: six healthy accounts kept advancing it on general-pool work while the
     * compiler's own account had accepted nothing since 28.08 21:36, so the same six exhausted briefs were
     * restored every cycle and the compiler repeated fourteen refusals every ~13 minutes. Evidence from an
     * account the brief does not travel through is not evidence about the brief.
     */
    @Test
    void anAcceptanceOnAnotherAccountDoesNotRestoreTheBrief() {
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);

        java.time.Instant dispatched = java.time.Instant.parse("2026-08-29T02:02:48Z");

        WishlistRepository wishlistRepository = mock(WishlistRepository.class);
        PersistentWorkerSessionService workerService = mock(PersistentWorkerSessionService.class);
        when(workerService.findActiveWorker(eq(projectId),
                eq(com.eneik.production.models.persistence.PersistentWorkerPurpose.WISHLIST_COMPILER)))
                .thenReturn(Optional.empty());
        JulesSessionRepository sessions = mock(JulesSessionRepository.class);
        // The compiler's account has accepted nothing; only other accounts have, and they are not asked.
        when(sessions.latestAcceptedSessionAtForAccount("eneikdru")).thenReturn(null);

        WishlistEntity unreached = brief(projectId, 3, dispatched, null);
        when(wishlistRepository.findByProjectId(projectId)).thenReturn(List.of(unreached));

        assertEquals(0, serviceWithWishlistsAndWorker(wishlistRepository, workerService, sessions)
                .restoreUnreachedBriefs(project));
        assertEquals(3, unreached.getCompileAttempts());
    }

    @Test
    void briefTheCompilerActuallyAnsweredIsNeverRestored() {
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);

        java.time.Instant dispatched = java.time.Instant.parse("2026-08-28T10:00:00Z");
        java.time.Instant watermark = dispatched.plusSeconds(3600);

        WishlistRepository wishlistRepository = mock(WishlistRepository.class);
        PersistentWorkerSessionService workerService = mock(PersistentWorkerSessionService.class);
        worker(workerService, projectId, watermark);

        // Budget spent AND the compiler was reached: decompositionRefused, an absorbing verdict about the
        // brief itself. Restoring it would make an absorbing state non-absorbing.
        WishlistEntity refused = brief(projectId, 3, dispatched, dispatched.plusSeconds(1));
        when(wishlistRepository.findByProjectId(projectId)).thenReturn(List.of(refused));

        assertEquals(0, serviceWithWishlistsAndWorker(wishlistRepository, workerService)
                .restoreUnreachedBriefs(project));
        assertEquals(3, refused.getCompileAttempts());
        verify(wishlistRepository, never()).saveAll(any());
    }

    @Test
    void unreachedBriefIsNotRestoredWhileTheChannelHasNotMovedSinceItsAttempt() {
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);

        // Watermark BEFORE the attempt: nothing has gone down the channel since, so there is no new
        // evidence and restoring would be a retry loop with no monotone marker behind it (§2, invariant 7).
        java.time.Instant watermark = java.time.Instant.parse("2026-08-28T10:00:00Z");
        java.time.Instant dispatched = watermark.plusSeconds(3600);

        WishlistRepository wishlistRepository = mock(WishlistRepository.class);
        PersistentWorkerSessionService workerService = mock(PersistentWorkerSessionService.class);
        worker(workerService, projectId, watermark);

        when(wishlistRepository.findByProjectId(projectId))
                .thenReturn(List.of(brief(projectId, 3, dispatched, null)));

        assertEquals(0, serviceWithWishlistsAndWorker(wishlistRepository, workerService)
                .restoreUnreachedBriefs(project));
        verify(wishlistRepository, never()).saveAll(any());
    }

    @Test
    void noActiveCompilerWorkerMeansNoWatermarkAndNothingIsRestored() {
        UUID projectId = UUID.randomUUID();
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);

        WishlistRepository wishlistRepository = mock(WishlistRepository.class);
        PersistentWorkerSessionService workerService = mock(PersistentWorkerSessionService.class);
        when(workerService.findActiveWorker(any(), any())).thenReturn(Optional.empty());

        assertEquals(0, serviceWithWishlistsAndWorker(wishlistRepository, workerService)
                .restoreUnreachedBriefs(project));
        verify(wishlistRepository, never()).saveAll(any());
    }
}
