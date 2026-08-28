package com.eneik.production.services;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.design.DesignAssetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sequential linear pipeline: Coverage Audit -> Single Stitch Call.
 *
 * 2026-08-05: collapsed from 5 stages to 3, then from 3 to 2 same day. Removed PHILOSOPHICAL_FALSIFICATION
 * - it was a pure log-and-advance no-op that never actually checked anything; the real
 * philosophical-falsification track (FalsificationCycleService.runWeeklyPhilosophicalFalsificationCycle)
 * already runs independently on its own cron, so a real trigger here would only create a second,
 * competing dispatch path for the same track. Removed JULES_REDESIGN - also a pure log-and-advance no-op
 * with no dispatch call.
 *
 * Removed COVERAGE_FALSIFICATION (second pass, live incident): it called
 * `projectFlowService.dispatchFalsificationAudit(project, "Coverage gap falsification", 0, "")` - the
 * literal 27-char label string as the ENTIRE task description, never the real findings from the
 * COVERAGE_AUDIT stage right before it (that stage only dispatches a Jules task and returns immediately;
 * it doesn't wait for or parse that task's eventual result, which can take many minutes to land as a
 * merged PR - an unsolved async-coordination gap, not a value that was merely forgotten to wire through).
 * Confirmed live (2026-08-04/05, test-forty-first): every single historical dispatch of this stage
 * produced only a "the request is meaningless, refused" response from the auditor role (real GitHub
 * history: PRs #55/#56/#57/#59/#60/#62, all variations of "reject/formalize/confirm falsification audit
 * refusal") - never once real audit output, because the auditor was never given anything to audit. Because
 * each of these "refusal" sessions completes and merges quickly (it's just a doc), the pipeline's
 * one-audit-at-a-time mutex (`dispatchFalsificationAudit`'s `auditAlreadyActive` check) cleared fast too,
 * so a fresh pipeline cycle immediately re-dispatched the same contentless placeholder again - a live,
 * self-perpetuating loop that burned real Jules session capacity for zero product value and was directly
 * observed contributing to the account entering `api_blocked` (HTTP 400 INVALID_ARGUMENT on a
 * dispatch) and the project stalling. Given this stage has never once produced value in its full
 * production history, removing it now (not deferring to "read real findings through" - a genuinely larger
 * async-coordination redesign) is the fail-closed choice: stop the active harm first, design the correct
 * version later rather than leave known-harmful code running while a better version is planned.
 */
@Service
public class ProjectAuditPipelineService {
    private static final Logger log = LoggerFactory.getLogger(ProjectAuditPipelineService.class);

    // Same order of magnitude as FalsificationCycleService.MAX_DIFF_CHARS_PER_PR (6000) - a prompt-size
    // class already proven to work elsewhere in this codebase, not a new arbitrary guess.
    private static final int MAX_STITCH_BRIEF_ITEMS = 15;
    private static final int MAX_STITCH_BRIEF_CHARS = 4000;

    public enum PipelineStage {
        IDLE,
        COVERAGE_AUDIT,
        STITCH_DESIGN,
        COMPLETED
    }

    private final ProjectRepository projectRepository;
    private final WishlistRepository wishlistRepository;
    private final TaskRepository taskRepository;
    private final ProjectFlowService projectFlowService;
    private final DesignAssetService designAssetService;
    private final ConcurrentHashMap<UUID, PipelineStage> projectStages = new ConcurrentHashMap<>();

    public ProjectAuditPipelineService(ProjectRepository projectRepository,
                                       WishlistRepository wishlistRepository,
                                       TaskRepository taskRepository,
                                       ProjectFlowService projectFlowService,
                                       DesignAssetService designAssetService) {
        this.projectRepository = projectRepository;
        this.wishlistRepository = wishlistRepository;
        this.taskRepository = taskRepository;
        this.projectFlowService = projectFlowService;
        this.designAssetService = designAssetService;
    }

    public PipelineStage getStage(UUID projectId) {
        return projectStages.getOrDefault(projectId, PipelineStage.IDLE);
    }

    public void startPipeline(UUID projectId) {
        projectStages.put(projectId, PipelineStage.COVERAGE_AUDIT);
    }

    /**
     * Executes the full sequential pipeline step by step for an active project.
     */
    @Transactional
    public void executeSequentialAuditPipeline(UUID projectId) {
        ProjectEntity project = projectRepository.findById(projectId).orElse(null);
        if (project == null || project.getStatus() != com.eneik.production.models.persistence.ProjectStatus.active) {
            return;
        }

        PipelineStage current = projectStages.getOrDefault(projectId, PipelineStage.IDLE);

        // Continuous non-overlapping cycling: if previous cycle is COMPLETED or IDLE,
        // start next iteration ONLY when all previous tasks are fully completed.
        if (current == PipelineStage.COMPLETED || current == PipelineStage.IDLE) {
            List<TaskEntity> activeTasks = taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                    .filter(t -> t.getStatus() != TaskStatus.done && t.getStatus() != TaskStatus.failed)
                    .toList();
            if (activeTasks.isEmpty()) {
                log.info("Project {} is active and all previous tasks completed. Starting next non-overlapping pipeline iteration...", projectId);
                current = PipelineStage.COVERAGE_AUDIT;
                projectStages.put(projectId, current);
            } else {
                return;
            }
        }

        log.info("ProjectAuditPipelineService: executing sequential tick for project {} in stage {}", projectId, current);

        try {
            switch (current) {
                case COVERAGE_AUDIT:
                    log.info("Pipeline Step 1/2 [COVERAGE_AUDIT]: Starting coverage audit for project {}", projectId);
                    projectFlowService.checkAndDispatchCoverageAudits(projectId);
                    projectStages.put(projectId, PipelineStage.STITCH_DESIGN);
                    break;

                case STITCH_DESIGN:
                    log.info("Pipeline Step 2/2 [STITCH_DESIGN]: Calling Stitch to generate a UI mockup from open falsification findings");
                    boolean stitchSuccess = triggerSingleStitchGeneration(project);
                    if (!stitchSuccess) {
                        log.warn("Pipeline Step 3/3 [STITCH_DESIGN]: Stitch generation did not succeed for project {} this cycle - "
                                + "no mockup produced, pipeline still advances (next full cycle will retry)", projectId);
                    }
                    projectStages.put(projectId, PipelineStage.COMPLETED);
                    break;

                case COMPLETED:
                    log.info("Pipeline COMPLETED for project {}", projectId);
                    break;
            }
        } catch (Exception e) {
            log.error("ProjectAuditPipelineService: error during pipeline execution in stage {}: {}", current, e.getMessage(), e);
        }
    }

    private boolean triggerSingleStitchGeneration(ProjectEntity project) {
        try {
            List<WishlistStatus> openStatuses = List.of(WishlistStatus.pending, WishlistStatus.compiling); // filtered by movable() below
            List<WishlistEntity> relevant = new ArrayList<>();
            relevant.addAll(wishlistRepository.findByProjectIdAndSourceAndStatusIn(
                    project.getId(), WishlistSource.self_falsification, openStatuses));
            relevant.addAll(wishlistRepository.findByProjectIdAndSourceAndStatusIn(
                    project.getId(), WishlistSource.philosophical_falsification, openStatuses));

            if (relevant.isEmpty()) {
                log.info("ProjectAuditPipelineService: no open self_falsification/philosophical_falsification "
                                + "findings for project {}; skipping Stitch generation instead of sending an empty/unrelated brief",
                        project.getId());
                return false;
            }

            StringBuilder combinedBrief = new StringBuilder("Redesign existing Svelte UI (+page.svelte) addressing audit critiques:\n");
            int included = 0;
            for (WishlistEntity w : relevant) {
                if (included >= MAX_STITCH_BRIEF_ITEMS || combinedBrief.length() >= MAX_STITCH_BRIEF_CHARS) {
                    break;
                }
                if (w.getContent() != null && !w.getContent().isBlank()) {
                    combinedBrief.append("- ").append(w.getContent()).append("\n");
                    included++;
                }
            }
            if (combinedBrief.length() > MAX_STITCH_BRIEF_CHARS) {
                combinedBrief.setLength(MAX_STITCH_BRIEF_CHARS);
                combinedBrief.append("\n... [truncated]");
            }

            DesignAssetService.DesignAssetResult result = designAssetService.generateAsset(
                    project,
                    null,
                    combinedBrief.toString(),
                    "homepage_redesign",
                    "high",
                    false
            );

            log.info("Stitch single generation completed for project {}: status={}, imagePath={}, itemsIncluded={}",
                    project.getId(), result.status(), result.imagePath(), included);
            return result.available();
        } catch (Exception e) {
            log.warn("Failed single Stitch generation call for project {}: {}", project.getId(), e.getMessage());
            return false;
        }
    }
}
