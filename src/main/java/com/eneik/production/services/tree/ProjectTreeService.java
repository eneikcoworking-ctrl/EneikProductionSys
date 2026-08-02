package com.eneik.production.services.tree;

import com.eneik.production.dto.tree.AnnotationDto;
import com.eneik.production.dto.tree.FeatureBranchDto;
import com.eneik.production.dto.tree.HealthDto;
import com.eneik.production.dto.tree.ProjectTreeDto;
import com.eneik.production.dto.tree.SeedDto;
import com.eneik.production.kaizen.model.KaizenProposal;
import com.eneik.production.kaizen.service.KaizenService;
import com.eneik.production.models.persistence.FeatureEntity;
import com.eneik.production.models.persistence.FeatureThreadEntity;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.FeatureRepository;
import com.eneik.production.repositories.FeatureThreadRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.ClientDeliverableReadinessService;
import com.eneik.production.services.audit.SixSigmaAuditService;
import com.eneik.production.services.jules.JulesDispatchService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Backend for the "living tree" primary frontend view (2026-08-02, operator-directed frontend
 * redesign): one read-only, additive endpoint assembling real per-feature data that already exists
 * or is already computed server-side but had no route out - no new computation, only a new
 * projection. Deliberately reuses {@link ClientDeliverableReadinessService#listEpicDiagnostics} as the
 * base feature list (the same already-trusted, already-filtered computation behind
 * GET /{projectId}/epics - PRODUCT_ITERATION_SOURCES-scoped, so this tree automatically excludes
 * gemini_observer noise the same way that endpoint already does) rather than re-deriving feature
 * filtering a second time.
 */
@Service
public class ProjectTreeService {

    private final ClientDeliverableReadinessService readinessService;
    private final FeatureRepository featureRepository;
    private final TaskRepository taskRepository;
    private final JulesSessionRepository julesSessionRepository;
    private final WishlistRepository wishlistRepository;
    private final FeatureThreadRepository featureThreadRepository;
    private final SixSigmaAuditService sixSigmaAuditService;
    private final KaizenService kaizenService;

    public ProjectTreeService(ClientDeliverableReadinessService readinessService,
                               FeatureRepository featureRepository,
                               TaskRepository taskRepository,
                               JulesSessionRepository julesSessionRepository,
                               WishlistRepository wishlistRepository,
                               FeatureThreadRepository featureThreadRepository,
                               SixSigmaAuditService sixSigmaAuditService,
                               KaizenService kaizenService) {
        this.readinessService = readinessService;
        this.featureRepository = featureRepository;
        this.taskRepository = taskRepository;
        this.julesSessionRepository = julesSessionRepository;
        this.wishlistRepository = wishlistRepository;
        this.featureThreadRepository = featureThreadRepository;
        this.sixSigmaAuditService = sixSigmaAuditService;
        this.kaizenService = kaizenService;
    }

    public ProjectTreeDto getTree(UUID projectId) {
        List<ClientDeliverableReadinessService.EpicDiagnostic> epics = readinessService.listEpicDiagnostics(projectId);
        Map<UUID, FeatureEntity> featuresById = featureRepository.findByProjectId(projectId).stream()
                .collect(Collectors.toMap(FeatureEntity::getId, f -> f));

        List<FeatureBranchDto> branches = new ArrayList<>();
        for (ClientDeliverableReadinessService.EpicDiagnostic epic : epics) {
            FeatureEntity feature = featuresById.get(epic.id());
            if (feature == null) {
                // Defensive only: listEpicDiagnostics reads from the same featureRepository, this
                // should be unreachable in practice.
                continue;
            }
            branches.add(toBranch(projectId, epic, feature));
        }

        List<WishlistEntity> allWishlist = wishlistRepository.findByProjectId(projectId);
        List<SeedDto> seeds = allWishlist.stream()
                .filter(w -> w.getStatus() != WishlistStatus.dismissed)
                .filter(w -> w.getFeatureId() == null)
                .map(w -> new SeedDto(w.getId(), w.getContent(), w.getStatus(), w.getCreatedAt(), w.getFeatureId()))
                .toList();

        return new ProjectTreeDto(projectId, branches, seeds, trunkAnnotations(projectId));
    }

    private FeatureBranchDto toBranch(UUID projectId, ClientDeliverableReadinessService.EpicDiagnostic epic, FeatureEntity feature) {
        List<TaskEntity> featureTasks = taskRepository.findByFeatureId(epic.id());
        List<UUID> taskIds = featureTasks.stream().map(TaskEntity::getId).toList();
        List<JulesSessionEntity> sessions = taskIds.isEmpty() ? List.of() : julesSessionRepository.findByTaskIdIn(taskIds);
        boolean livePulse = sessions.stream()
                .anyMatch(s -> JulesDispatchService.ACTIVE_SESSION_STATUSES.contains(s.getStatus()));

        SixSigmaAuditService.DefectOpportunityCount qgCounts = sixSigmaAuditService.computeQualityGateCounts(null, epic.id());
        SixSigmaAuditService.DefectOpportunityCount conflictCounts = sixSigmaAuditService.computePrConflictCounts(null, epic.id());
        long defects = qgCounts.defects() + conflictCounts.defects();
        long opportunities = qgCounts.opportunities() + conflictCounts.opportunities();
        double dpmo = SixSigmaAuditService.calculateDpmo(defects, opportunities);
        double sigmaLevel = SixSigmaAuditService.calculateSigmaLevel(dpmo);
        HealthDto health = new HealthDto(qgCounts.defects(), qgCounts.opportunities(),
                conflictCounts.defects(), conflictCounts.opportunities(), dpmo, sigmaLevel);

        List<AnnotationDto> annotations = new ArrayList<>();
        featureThreadRepository.findByProjectIdAndFeatureId(projectId, epic.id()).ifPresent(thread ->
                annotations.add(threadAnnotation(thread)));

        return new FeatureBranchDto(
                epic.id(), epic.title(), epic.rootWishlistId(), epic.createdAt(),
                feature.getKanoClass(), feature.getCynefinDomain(), feature.getTocConstraintRef(),
                epic.complete(), epic.codeProducingItemCount(), epic.mergedItemCount(),
                livePulse, health, annotations
        );
    }

    // Real, already-happened autonomous events (Charter Pattern #12 spirit extended to the UI: never
    // narrate a pending decision, only what the system already resolved on its own).
    private AnnotationDto threadAnnotation(FeatureThreadEntity thread) {
        if (thread.getAbandonedAt() != null) {
            return new AnnotationDto("abandoned",
                    "After " + thread.getCloseoutConflictAttempts() + " merge attempts the conflict could not be resolved; branch closed automatically",
                    thread.getAbandonedAt(), thread.getCloseoutPrUrl());
        }
        if (thread.getCloseoutConflictAttempts() > 0) {
            return new AnnotationDto("conflict_resolved",
                    "Merge conflict found and resolved automatically (attempts: " + thread.getCloseoutConflictAttempts() + ")",
                    thread.getUpdatedAt(), thread.getLastPrUrl());
        }
        return new AnnotationDto("active", "Branch in progress", thread.getUpdatedAt(), thread.getLastPrUrl());
    }

    /**
     * Trunk-level narration only (not per-branch) - {@link KaizenProposal} has no featureId, only a
     * free-text targetComponent, and guess-matching it to a branch risks a wrong attribution, which
     * matters more than an unattached annotation for an autonomous system's trust story.
     */
    public List<AnnotationDto> trunkAnnotations(UUID projectId) {
        return kaizenService.getProposalsForProject(projectId).stream()
                .map(p -> new AnnotationDto(p.getCategory().name(), p.getTitle() + ": " + p.getActionDescription(),
                        p.getCreatedAt(), null))
                .toList();
    }
}
