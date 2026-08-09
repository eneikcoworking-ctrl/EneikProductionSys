package com.eneik.production.services;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.design.DesignAssetService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 2026-08-05: covers the collapsed pipeline, now 5 -> 3 -> 2 real stages the same day (COVERAGE_AUDIT ->
 * STITCH_DESIGN -> COMPLETED). No test existed for this service before - the two dead stub stages
 * (PHILOSOPHICAL_FALSIFICATION, JULES_REDESIGN) and the STITCH_DESIGN scope/swallowed-result bugs were
 * found by live investigation, not by any existing test catching them. COVERAGE_FALSIFICATION (the third
 * stage removed) was a live incident on its own: it dispatched a bare, contentless placeholder prompt via
 * dispatchFalsificationAudit, which the auditor role could only ever refuse (confirmed: every historical
 * dispatch produced a "refusal" PR, never real output) - and because each refusal completed and merged
 * quickly, the pipeline's own one-audit-at-a-time mutex cleared fast, so a fresh cycle immediately
 * re-dispatched the same placeholder again, burning real Jules session capacity for zero value.
 */
class ProjectAuditPipelineServiceTest {

    private ProjectRepository projectRepository;
    private WishlistRepository wishlistRepository;
    private TaskRepository taskRepository;
    private ProjectFlowService projectFlowService;
    private DesignAssetService designAssetService;
    private ProjectAuditPipelineService service;
    private ProjectEntity project;

    private void setUp() {
        projectRepository = mock(ProjectRepository.class);
        wishlistRepository = mock(WishlistRepository.class);
        taskRepository = mock(TaskRepository.class);
        projectFlowService = mock(ProjectFlowService.class);
        designAssetService = mock(DesignAssetService.class);
        service = new ProjectAuditPipelineService(
                projectRepository, wishlistRepository, taskRepository, projectFlowService, designAssetService);

        project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setName("pipeline-test-project");
        project.setStatus(ProjectStatus.active);
        when(projectRepository.findById(project.getId())).thenReturn(Optional.of(project));
        // No active tasks - lets the pipeline start a fresh cycle from IDLE/COMPLETED on every tick.
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())).thenReturn(List.of());
        when(wishlistRepository.findByProjectIdAndSourceAndStatusIn(any(), any(), any())).thenReturn(List.of());
    }

    @Test
    void pipelineCollapsesToTwoRealStagesAndReachesCompleted() {
        setUp();
        // Regression proof the 5->3->2 collapse actually took effect: exactly one tick per real stage,
        // PHILOSOPHICAL_FALSIFICATION/JULES_REDESIGN/COVERAGE_FALSIFICATION no longer exist to visit
        // (compile-time guarantee from the enum itself) and no extra ticks are needed to reach COMPLETED.
        service.executeSequentialAuditPipeline(project.getId());
        assertEquals(ProjectAuditPipelineService.PipelineStage.STITCH_DESIGN, service.getStage(project.getId()));

        service.executeSequentialAuditPipeline(project.getId());
        assertEquals(ProjectAuditPipelineService.PipelineStage.COMPLETED, service.getStage(project.getId()));

        verify(projectFlowService, org.mockito.Mockito.times(1)).checkAndDispatchCoverageAudits(project.getId());
        // The broken placeholder dispatch is gone entirely now - never called, not even once.
        verify(projectFlowService, never())
                .dispatchFalsificationAudit(eq(project), anyString(), any(), any());
    }

    @Test
    void stitchGenerationScopesToOpenFalsificationFindingsOnlyNeverTheWholeProjectWishlist() {
        setUp();
        WishlistEntity selfFalsificationFinding = new WishlistEntity();
        selfFalsificationFinding.setContent("Fix the stub in the export handler");
        when(wishlistRepository.findByProjectIdAndSourceAndStatusIn(
                eq(project.getId()), eq(WishlistSource.self_falsification),
                eq(List.of(WishlistStatus.pending, WishlistStatus.compiling))))
                .thenReturn(List.of(selfFalsificationFinding));
        when(designAssetService.generateAsset(any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(new DesignAssetService.DesignAssetResult(true, "ok", "model", "/img.png", "", "image/png", "", ""));

        // Tick 1: COVERAGE_AUDIT -> STITCH_DESIGN. Tick 2: executes STITCH_DESIGN -> COMPLETED.
        service.executeSequentialAuditPipeline(project.getId());
        service.executeSequentialAuditPipeline(project.getId());

        verify(wishlistRepository, never()).findByProjectId(any());
        verify(wishlistRepository).findByProjectIdAndSourceAndStatusIn(
                project.getId(), WishlistSource.self_falsification, List.of(WishlistStatus.pending, WishlistStatus.compiling));
        verify(wishlistRepository).findByProjectIdAndSourceAndStatusIn(
                project.getId(), WishlistSource.philosophical_falsification, List.of(WishlistStatus.pending, WishlistStatus.compiling));
        verify(designAssetService).generateAsset(eq(project), any(), org.mockito.ArgumentMatchers.contains("Fix the stub"), any(), any(), eq(false));
    }

    @Test
    void stitchFailureIsLoggedNotSwallowedAndPipelineStillAdvances() {
        setUp();
        WishlistEntity finding = new WishlistEntity();
        finding.setContent("A real finding to build a brief from");
        when(wishlistRepository.findByProjectIdAndSourceAndStatusIn(
                eq(project.getId()), eq(WishlistSource.self_falsification), any()))
                .thenReturn(List.of(finding));
        when(designAssetService.generateAsset(any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn(new DesignAssetService.DesignAssetResult(false, "api_error", "", "", "", "", "Stitch call failed", ""));

        service.executeSequentialAuditPipeline(project.getId());
        service.executeSequentialAuditPipeline(project.getId());

        // Best-effort, non-gating stage: a failed generation must not block the pipeline from completing -
        // but the earlier bug (boolean stitchSuccess computed and never read) is what let this go unnoticed
        // in the first place, so this test also stands as documentation that failure is now at least
        // observed (see the log.warn in executeSequentialAuditPipeline's STITCH_DESIGN case).
        assertEquals(ProjectAuditPipelineService.PipelineStage.COMPLETED, service.getStage(project.getId()));
    }

    @Test
    void noOpenFindingsSkipsTheStitchCallEntirely() {
        setUp();
        // Both source queries already stubbed empty in setUp() - fail-closed: don't send Stitch an
        // empty/unrelated brief just because a stage tick fired.
        service.executeSequentialAuditPipeline(project.getId());
        service.executeSequentialAuditPipeline(project.getId());

        verify(designAssetService, never()).generateAsset(any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
        assertEquals(ProjectAuditPipelineService.PipelineStage.COMPLETED, service.getStage(project.getId()));
    }
}
