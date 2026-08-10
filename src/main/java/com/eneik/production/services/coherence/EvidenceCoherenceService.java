package com.eneik.production.services.coherence;

import com.eneik.production.models.persistence.CoherenceRunEntity;
import com.eneik.production.models.persistence.CoherenceRunNodeResultEntity;
import com.eneik.production.models.persistence.EvidenceNodeEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.repositories.CoherenceRunNodeResultRepository;
import com.eneik.production.repositories.CoherenceRunRepository;
import com.eneik.production.repositories.EvidenceNodeRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.services.WishlistContentSimilarityMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Paul Thagard's ECHO (Explanatory Coherence by Harmony Optimization, 1989) applied to this system's own
 * evidence graph - reconciles findings from 3 independent sources (ProcessControlService's statistical
 * signals via DefectJournalEntity, the falsification auditor's CodeIntegrityFindingEntity, KaizenService's
 * proposals) that today are never cross-checked against each other at all.
 *
 * A real connectionist relaxation, not a heuristic dressed up with the citation: every EvidenceNodeEntity is
 * a unit with an activation in [minActivation, maxActivation]; a permanently-active "special evidence unit"
 * gives every node a small positive baseline pull (Thagard's own "data get priority" principle); two nodes
 * that corroborate each other (same polarity, sharing a real structural identifier or, more weakly, similar
 * wording) get an excitatory link, two that contradict get an inhibitory one. Activations are updated
 * synchronously (a deliberate, documented simplification of Thagard's original asynchronous-random-order
 * update - needed for this run to be deterministic and testable) until they stabilize, then binarized:
 * positive final activation = accepted.
 *
 * Simplified relative to full ECHO in one more way, honestly: canonical ECHO has a separate hypothesis layer
 * (hypotheses cohere/compete with each other, evidence anchors them via the special unit) - this system has
 * no such second tier yet, so every evidence node is treated as both evidence AND the thing being judged.
 *
 * Belief revision (Gärdenfors/AGM) is a policy applied AFTER the raw ECHO pass, not a separate service: when
 * this run's accepted nodes sharing a real structural cluster (featureId or prNumber) contain a genuine
 * polarity contradiction, the side with LOWER historical entrenchment (fewer distinct source types that have
 * ever corroborated it) is the one revised - ties preserve the status quo (AGM's minimal-change principle).
 * Revision is only ever triggered by STRONG (structurally-identified) clusters; a WEAK, Jaccard-only cluster
 * has no stable identity to track entrenchment against, so it can shape this run's ECHO weights but never
 * flips an accepted/rejected verdict on its own.
 *
 * Bayesian corroboration (Bovens & Hartmann, Phase 4) runs after AGM: every accepted node gets a real
 * confidence score, combining each corroborating source's reliability via log-odds. Reliability is a
 * 3-tier estimate (see sourceReliability's own doc) - honestly calibrated from real outcome data where it
 * exists (Kaizen's STANDARDIZED/REVERTED history) or from this same engine's own acceptance history where
 * it doesn't (every other source type), never a fabricated number.
 */
@Service
public class EvidenceCoherenceService {
    private static final Logger log = LoggerFactory.getLogger(EvidenceCoherenceService.class);

    // Typical order-of-magnitude ECHO parameters (Thagard's papers use excitatory/inhibitory weights in this
    // range, decay ~0.05, activation bounds [-1,1]) - configurable, not asserted as the exact published
    // constants verbatim, since this system's evidence graph (3 heterogeneous sources, no separate
    // hypothesis tier) is not identical to Thagard's original explanatory-coherence setup.
    @Value("${coherence.decay:0.05}")
    private double decay;
    @Value("${coherence.min-activation:-1.0}")
    private double minActivation;
    @Value("${coherence.max-activation:1.0}")
    private double maxActivation;
    @Value("${coherence.strong-edge-weight:0.06}")
    private double strongEdgeWeight;
    @Value("${coherence.weak-edge-weight:0.03}")
    private double weakEdgeWeight;
    @Value("${coherence.special-unit-weight:0.05}")
    private double specialUnitWeight;
    @Value("${coherence.max-iterations:200}")
    private int maxIterations;
    @Value("${coherence.convergence-epsilon:0.001}")
    private double convergenceEpsilon;
    @Value("${coherence.weak-edge-jaccard-threshold:0.4}")
    private double weakEdgeJaccardThreshold;
    @Value("${coherence.reconciliation-window-hours:24}")
    private int reconciliationWindowHours;
    @Value("${coherence.initial-activation:0.01}")
    private double initialActivation;

    // Phase 4 (Bovens & Hartmann Bayesian corroboration) - same specific-data -> pooled -> prior fallback
    // idiom already proven in AccountHealthService.computeCooldownMinutes, applied here to source-type
    // reliability instead of recovery-cooldown duration.
    @Value("${coherence.min-reliability-samples:10}")
    private int minReliabilitySamples;

    private final EvidenceNodeRepository evidenceNodeRepository;
    private final CoherenceRunRepository coherenceRunRepository;
    private final CoherenceRunNodeResultRepository coherenceRunNodeResultRepository;
    private final WishlistContentSimilarityMatcher similarityMatcher;
    private final ProjectRepository projectRepository;
    private final com.eneik.production.kaizen.repository.KaizenProposalRepository kaizenProposalRepository;

    public EvidenceCoherenceService(EvidenceNodeRepository evidenceNodeRepository,
                                     CoherenceRunRepository coherenceRunRepository,
                                     CoherenceRunNodeResultRepository coherenceRunNodeResultRepository,
                                     WishlistContentSimilarityMatcher similarityMatcher,
                                     ProjectRepository projectRepository,
                                     com.eneik.production.kaizen.repository.KaizenProposalRepository kaizenProposalRepository) {
        this.evidenceNodeRepository = evidenceNodeRepository;
        this.coherenceRunRepository = coherenceRunRepository;
        this.coherenceRunNodeResultRepository = coherenceRunNodeResultRepository;
        this.similarityMatcher = similarityMatcher;
        this.projectRepository = projectRepository;
        this.kaizenProposalRepository = kaizenProposalRepository;
        log.info("[COHERENCE-INIT] EvidenceCoherenceService (Thagard/ECHO + Gärdenfors/AGM + Bovens-Hartmann) initialized.");
    }

    enum EdgeRelation { COOPERATE, COMPETE, NONE }

    /**
     * Read-only projection for the frontend (Кузница/Delivery room) - reads the same
     * {@link EvidenceNodeEntity} rows and the project's most recent {@link CoherenceRunEntity} verdict
     * that {@link com.eneik.production.services.GeminiProjectObserverService#latestCoherenceRunRejectedNodeIds}
     * already relies on, so the graph the frontend shows an engineer can never disagree with what Gemini
     * herself is told. Node clustering (shared featureId/prNumber) is exposed as-is rather than
     * re-deriving {@link #buildWeightMatrix}'s edge weights - the raw cluster keys are enough for a
     * legible graph without duplicating the ECHO relaxation itself.
     */
    public record GraphNode(java.util.UUID id, String polarity, String sourceType, String summaryText,
                             java.util.UUID featureId, Integer prNumber, java.time.Instant createdAt,
                             boolean accepted, Double confidence) {}

    public record GraphSnapshot(java.util.UUID projectId, java.util.List<GraphNode> nodes,
                                 boolean hasCoherenceRun, java.time.Instant lastRunAt,
                                 double coherenceScore, int totalNodes, int acceptedNodes) {}

    public GraphSnapshot graphSnapshot(java.util.UUID projectId) {
        Instant windowStart = Instant.now().minus(reconciliationWindowHours, ChronoUnit.HOURS);
        List<EvidenceNodeEntity> nodes = evidenceNodeRepository.findByProjectIdAndCreatedAtAfter(projectId, windowStart);

        List<CoherenceRunEntity> runs = coherenceRunRepository.findByProjectIdOrderByRanAtDesc(projectId);
        java.util.Map<UUID, CoherenceRunNodeResultEntity> latestResults = java.util.Map.of();
        CoherenceRunEntity latestRun = null;
        if (!runs.isEmpty()) {
            latestRun = runs.get(0);
            latestResults = coherenceRunNodeResultRepository.findByCoherenceRunId(latestRun.getId()).stream()
                    .collect(java.util.stream.Collectors.toMap(CoherenceRunNodeResultEntity::getEvidenceNodeId, r -> r));
        }

        List<GraphNode> graphNodes = new ArrayList<>();
        for (EvidenceNodeEntity node : nodes) {
            CoherenceRunNodeResultEntity result = latestResults.get(node.getId());
            // No verdict yet (created since the last 2h cycle) - shown as accepted/live by default,
            // same "absence of a verdict is not evidence of rejection" rule as the Gemini-facing tool.
            boolean accepted = result == null || result.isAccepted();
            Double confidence = result == null ? null : result.getConfidence();
            graphNodes.add(new GraphNode(node.getId(), node.getPolarity().name(), node.sourceType(),
                    node.getSummaryText(), node.getFeatureId(), node.getPrNumber(), node.getCreatedAt(),
                    accepted, confidence));
        }

        return new GraphSnapshot(projectId, graphNodes, latestRun != null,
                latestRun == null ? null : latestRun.getRanAt(),
                latestRun == null ? 0.0 : latestRun.getCoherenceScore(),
                latestRun == null ? 0 : latestRun.getTotalNodes(),
                latestRun == null ? 0 : latestRun.getAcceptedNodes());
    }

    /** Same 2h cadence as KaizenService's own periodic cycle - not a coincidence, both reconcile the same
     * evidence window. */
    @Scheduled(fixedRate = 7200000, initialDelay = 120000)
    public void periodicCoherenceCycle() {
        try {
            for (var project : projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)) {
                runCoherenceCycle(project.getId());
            }
            // Global/factory-wide evidence (e.g. Kaizen SYSTEMIC_DEFECT proposals with no owning project)
            runCoherenceCycle(null);
        } catch (Exception e) {
            log.error("[COHERENCE-ERROR] Periodic coherence cycle encountered an error: ", e);
        }
    }

    public CoherenceRunEntity runCoherenceCycle(UUID projectId) {
        Instant windowStart = Instant.now().minus(reconciliationWindowHours, ChronoUnit.HOURS);
        List<EvidenceNodeEntity> nodes = projectId != null
                ? evidenceNodeRepository.findByProjectIdAndCreatedAtAfter(projectId, windowStart)
                : evidenceNodeRepository.findByCreatedAtAfter(windowStart).stream()
                        .filter(n -> n.getProjectId() == null).toList();

        if (nodes.isEmpty()) {
            log.debug("[COHERENCE] No evidence nodes in the last {}h for project {}; skipping this cycle.",
                    reconciliationWindowHours, projectId);
            return null;
        }

        int n = nodes.size();
        Map<UUID, Integer> indexOf = new HashMap<>();
        for (int i = 0; i < n; i++) {
            indexOf.put(nodes.get(i).getId(), i);
        }

        double[][] weights = buildWeightMatrix(nodes);
        double[] finalActivation = relax(weights, n);

        Map<UUID, Boolean> rawAccepted = new HashMap<>();
        for (int i = 0; i < n; i++) {
            rawAccepted.put(nodes.get(i).getId(), finalActivation[i] > 0);
        }

        Map<UUID, Boolean> accepted = applyEntrenchmentRevision(nodes, rawAccepted);
        Map<UUID, Double> confidences = computeConfidences(nodes, accepted);

        double coherenceScore = 0.0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (weights[i][j] == 0.0) continue;
                double signI = accepted.get(nodes.get(i).getId()) ? 1.0 : -1.0;
                double signJ = accepted.get(nodes.get(j).getId()) ? 1.0 : -1.0;
                coherenceScore += weights[i][j] * signI * signJ;
            }
        }

        CoherenceRunEntity run = new CoherenceRunEntity();
        run.setProjectId(projectId);
        run.setTotalNodes(n);
        run.setAcceptedNodes((int) accepted.values().stream().filter(Boolean::booleanValue).count());
        run.setCoherenceScore(coherenceScore);
        run = coherenceRunRepository.save(run);

        for (int i = 0; i < n; i++) {
            CoherenceRunNodeResultEntity result = new CoherenceRunNodeResultEntity();
            result.setCoherenceRunId(run.getId());
            result.setEvidenceNodeId(nodes.get(i).getId());
            result.setAccepted(accepted.get(nodes.get(i).getId()));
            result.setFinalActivation(finalActivation[i]);
            result.setConfidence(confidences.get(nodes.get(i).getId()));
            coherenceRunNodeResultRepository.save(result);
        }

        log.info("[COHERENCE] Run {} for project {}: {} node(s), {} accepted, coherenceScore={}",
                run.getId(), projectId, n, run.getAcceptedNodes(), String.format("%.4f", coherenceScore));
        return run;
    }

    double[][] buildWeightMatrix(List<EvidenceNodeEntity> nodes) {
        int n = nodes.size();
        double[][] weights = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                EvidenceNodeEntity a = nodes.get(i);
                EvidenceNodeEntity b = nodes.get(j);
                boolean strong = (a.getFeatureId() != null && a.getFeatureId().equals(b.getFeatureId()))
                        || (a.getPrNumber() != null && a.getPrNumber().equals(b.getPrNumber()));
                boolean weak = !strong && similarityMatcher.similarity(a.getSummaryText(), b.getSummaryText()) >= weakEdgeJaccardThreshold;
                if (!strong && !weak) {
                    continue;
                }
                EdgeRelation relation = edgeRelation(a.getPolarity(), b.getPolarity());
                if (relation == EdgeRelation.NONE) {
                    continue;
                }
                double magnitude = strong ? strongEdgeWeight : weakEdgeWeight;
                double signedWeight = relation == EdgeRelation.COOPERATE ? magnitude : -magnitude;
                weights[i][j] = signedWeight;
                weights[j][i] = signedWeight;
            }
        }
        return weights;
    }

    EdgeRelation edgeRelation(EvidenceNodeEntity.Polarity a, EvidenceNodeEntity.Polarity b) {
        if (a == b) {
            // Two NEUTRAL_OBSERVATION nodes assert nothing strong enough to corroborate each other.
            return a == EvidenceNodeEntity.Polarity.NEUTRAL_OBSERVATION ? EdgeRelation.NONE : EdgeRelation.COOPERATE;
        }
        boolean directContradiction =
                (a == EvidenceNodeEntity.Polarity.NEGATIVE_FINDING && b == EvidenceNodeEntity.Polarity.POSITIVE_CONFIRMATION)
                || (a == EvidenceNodeEntity.Polarity.POSITIVE_CONFIRMATION && b == EvidenceNodeEntity.Polarity.NEGATIVE_FINDING);
        return directContradiction ? EdgeRelation.COMPETE : EdgeRelation.NONE;
    }

    /** Synchronous ECHO relaxation - see class doc for why synchronous (determinism/testability) instead of
     * Thagard's original asynchronous random-order updates. */
    double[] relax(double[][] weights, int n) {
        double[] activation = new double[n];
        java.util.Arrays.fill(activation, initialActivation);

        for (int iter = 0; iter < maxIterations; iter++) {
            double[] next = new double[n];
            double maxDelta = 0.0;
            for (int i = 0; i < n; i++) {
                double net = specialUnitWeight; // special evidence unit, fixed activation 1.0
                for (int j = 0; j < n; j++) {
                    if (j != i) {
                        net += weights[i][j] * activation[j];
                    }
                }
                double a = activation[i];
                double updated = net > 0
                        ? a * (1 - decay) + net * (maxActivation - a)
                        : a * (1 - decay) + net * (a - minActivation);
                updated = Math.max(minActivation, Math.min(maxActivation, updated));
                next[i] = updated;
                maxDelta = Math.max(maxDelta, Math.abs(updated - a));
            }
            activation = next;
            if (maxDelta < convergenceEpsilon) {
                break;
            }
        }
        return activation;
    }

    /**
     * AGM entrenchment revision - see class doc. Only STRONG (featureId/prNumber) clusters participate;
     * entrenchment is a defensible, documented simplification: a source type counts as having historically
     * corroborated a cluster's polarity-group if ANY prior coherence run ever accepted one of its nodes there
     * (not strictly "its most recent status"), which is honest about being a simplification, not a
     * fabricated precision.
     */
    Map<UUID, Boolean> applyEntrenchmentRevision(List<EvidenceNodeEntity> nodes, Map<UUID, Boolean> rawAccepted) {
        Map<UUID, Boolean> result = new HashMap<>(rawAccepted);

        Map<Object, List<EvidenceNodeEntity>> clusters = new HashMap<>();
        for (EvidenceNodeEntity node : nodes) {
            Object key = clusterKey(node);
            if (key == null) {
                continue;
            }
            clusters.computeIfAbsent(key, k -> new ArrayList<>()).add(node);
        }

        for (Map.Entry<Object, List<EvidenceNodeEntity>> entry : clusters.entrySet()) {
            List<EvidenceNodeEntity> clusterNodes = entry.getValue();
            List<EvidenceNodeEntity> negativeAccepted = new ArrayList<>();
            List<EvidenceNodeEntity> positiveAccepted = new ArrayList<>();
            for (EvidenceNodeEntity node : clusterNodes) {
                if (!Boolean.TRUE.equals(rawAccepted.get(node.getId()))) {
                    continue;
                }
                if (node.getPolarity() == EvidenceNodeEntity.Polarity.NEGATIVE_FINDING) {
                    negativeAccepted.add(node);
                } else if (node.getPolarity() == EvidenceNodeEntity.Polarity.POSITIVE_CONFIRMATION) {
                    positiveAccepted.add(node);
                }
            }
            if (negativeAccepted.isEmpty() || positiveAccepted.isEmpty()) {
                continue; // no genuine contradiction within this run's accepted set for this cluster
            }

            int negEntrenchment = distinctHistoricallyCorroboratingSourceTypes(
                    clusterNodes, EvidenceNodeEntity.Polarity.NEGATIVE_FINDING);
            int posEntrenchment = distinctHistoricallyCorroboratingSourceTypes(
                    clusterNodes, EvidenceNodeEntity.Polarity.POSITIVE_CONFIRMATION);

            if (negEntrenchment > posEntrenchment) {
                positiveAccepted.forEach(node -> result.put(node.getId(), false));
                log.info("[COHERENCE-AGM] Cluster {} revised: POSITIVE_CONFIRMATION side rejected (entrenchment {} < {})",
                        entry.getKey(), posEntrenchment, negEntrenchment);
            } else if (posEntrenchment > negEntrenchment) {
                negativeAccepted.forEach(node -> result.put(node.getId(), false));
                log.info("[COHERENCE-AGM] Cluster {} revised: NEGATIVE_FINDING side rejected (entrenchment {} < {})",
                        entry.getKey(), negEntrenchment, posEntrenchment);
            }
            // Tie: AGM minimal-change principle - leave ECHO's own raw verdict alone for both sides.
        }
        return result;
    }

    Object clusterKey(EvidenceNodeEntity node) {
        if (node.getFeatureId() != null) {
            return "feature:" + node.getFeatureId();
        }
        if (node.getPrNumber() != null) {
            return "pr:" + node.getProjectId() + ":" + node.getPrNumber();
        }
        return null;
    }

    private int distinctHistoricallyCorroboratingSourceTypes(List<EvidenceNodeEntity> clusterNodes,
                                                               EvidenceNodeEntity.Polarity polarity) {
        EvidenceNodeEntity representative = clusterNodes.get(0);
        List<EvidenceNodeEntity> allClusterNodesEver = representative.getFeatureId() != null
                ? evidenceNodeRepository.findByProjectIdAndFeatureId(representative.getProjectId(), representative.getFeatureId())
                : evidenceNodeRepository.findByProjectIdAndPrNumber(representative.getProjectId(), representative.getPrNumber());

        Set<String> sourceTypes = new HashSet<>();
        for (EvidenceNodeEntity node : allClusterNodesEver) {
            if (node.getPolarity() != polarity) {
                continue;
            }
            boolean everAccepted = coherenceRunNodeResultRepository.findByEvidenceNodeId(node.getId()).stream()
                    .anyMatch(CoherenceRunNodeResultEntity::isAccepted);
            if (everAccepted) {
                sourceTypes.add(node.sourceType());
            }
        }
        return sourceTypes.size();
    }

    // ---------------------------------------------------------------------------------------------------
    // Phase 4 - Bovens & Hartmann Bayesian corroboration.
    //
    // Combined confidence for an accepted, agreeing cluster = the posterior probability that the cluster's
    // claim is correct, given each member's independent report, assuming conditional independence between
    // sources given the true state (Bovens & Hartmann's own stated simplification - documented here as an
    // assumption, not a proven fact about these 4 sources). Standard log-odds combination:
    //   logit(P) = sum_i logit(reliability(sourceType_i))
    // A lone, unclustered accepted node still gets a real confidence value - just its own source type's
    // reliability, nothing to combine it with.
    // ---------------------------------------------------------------------------------------------------

    Map<UUID, Double> computeConfidences(List<EvidenceNodeEntity> nodes, Map<UUID, Boolean> accepted) {
        Map<UUID, Double> confidences = new HashMap<>();
        Map<Object, List<EvidenceNodeEntity>> clusters = new HashMap<>();
        List<EvidenceNodeEntity> unclustered = new ArrayList<>();
        for (EvidenceNodeEntity node : nodes) {
            if (!Boolean.TRUE.equals(accepted.get(node.getId()))) {
                continue; // confidence is only meaningful for an actually-accepted conclusion
            }
            Object key = clusterKey(node);
            if (key == null) {
                unclustered.add(node);
            } else {
                clusters.computeIfAbsent(key, k -> new ArrayList<>()).add(node);
            }
        }

        for (EvidenceNodeEntity node : unclustered) {
            confidences.put(node.getId(), sigmoid(logit(sourceReliability(node.sourceType()))));
        }
        for (List<EvidenceNodeEntity> clusterNodes : clusters.values()) {
            // Only members that AGM/ECHO left accepted (agreeing) corroborate each other - a cluster can
            // still have both a small accepted-negative and accepted-positive remnant if entrenchment tied
            // and neither side got revised (see applyEntrenchmentRevision) - each polarity-group combines
            // only with its own agreeing members, never across the very contradiction AGM chose not to force.
            Map<EvidenceNodeEntity.Polarity, List<EvidenceNodeEntity>> byPolarity = new HashMap<>();
            for (EvidenceNodeEntity node : clusterNodes) {
                byPolarity.computeIfAbsent(node.getPolarity(), p -> new ArrayList<>()).add(node);
            }
            for (List<EvidenceNodeEntity> group : byPolarity.values()) {
                double combinedLogit = 0.0;
                for (EvidenceNodeEntity node : group) {
                    combinedLogit += logit(sourceReliability(node.sourceType()));
                }
                double confidence = sigmoid(combinedLogit);
                for (EvidenceNodeEntity node : group) {
                    confidences.put(node.getId(), confidence);
                }
            }
        }
        return confidences;
    }

    /**
     * Three-tier reliability estimate for one source type, same fallback shape as
     * AccountHealthService.computeCooldownMinutes (specific data -> pooled data -> prior):
     *   1. KAIZEN_PROPOSAL only: real outcome ground truth (STANDARDIZED vs REVERTED) - the strongest
     *      signal available anywhere in this system, since a proposal's post-metric is actually re-measured,
     *      not just judged by consensus.
     *   2. All 4 source types: empirical acceptance rate within this same coherence engine's own history -
     *      not "ground truth", but a real, honestly-labeled foundherentist signal (how often this source's
     *      claims have coherently agreed with the rest of the evidence graph) - available for every source,
     *      not just Kaizen, unlike the original narrower design.
     *   3. Flat 0.5 (uncalibrated) when neither tier has enough samples (< minReliabilitySamples) to trust -
     *      never fabricated from too little data.
     */
    double sourceReliability(String sourceType) {
        if ("KAIZEN_PROPOSAL".equals(sourceType)) {
            long standardized = kaizenProposalRepository.findAll().stream()
                    .filter(p -> "STANDARDIZED".equals(p.getStatus())).count();
            long reverted = kaizenProposalRepository.findAll().stream()
                    .filter(p -> "REVERTED".equals(p.getStatus())).count();
            if (standardized + reverted >= minReliabilitySamples) {
                return (double) standardized / (standardized + reverted);
            }
        }

        long accepted = 0;
        long total = 0;
        for (EvidenceNodeEntity node : evidenceNodeRepository.findAll()) {
            if (!sourceType.equals(node.sourceType())) {
                continue;
            }
            List<CoherenceRunNodeResultEntity> history = coherenceRunNodeResultRepository.findByEvidenceNodeId(node.getId());
            if (history.isEmpty()) {
                continue;
            }
            total++;
            if (history.stream().anyMatch(CoherenceRunNodeResultEntity::isAccepted)) {
                accepted++;
            }
        }
        if (total >= minReliabilitySamples) {
            return (double) accepted / total;
        }

        return 0.5; // uncalibrated prior - too little data at either tier to trust a number away from 0.5
    }

    private static double logit(double p) {
        double clamped = Math.max(0.001, Math.min(0.999, p)); // avoid +/-infinity at the boundary
        return Math.log(clamped / (1 - clamped));
    }

    private static double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }
}
