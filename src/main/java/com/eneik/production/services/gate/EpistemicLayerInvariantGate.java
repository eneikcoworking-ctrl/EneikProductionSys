package com.eneik.production.services.gate;

import com.eneik.production.models.persistence.FeatureEntity;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.FeatureRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.services.github.GitHubPullRequestService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * E3 Epistemic Engine (Phase 3): Quine Web of Belief & Epistemic Layer Invariant Gate
 * (UILLARD_KUAYN_01_HOLISM_IMPACT_MAP / D003; ALFRED_TARSKIY_01_FALSIFICATION_HARNESS / D008).
 *
 * <p>Enforces the Principle of Minimum Mutilation: tasks belonging to the PERIPHERY layer
 * (e.g. BARCAN-TAG-11 UI, BARCAN-TAG-05 docs, BARCAN-TAG-06 test, or features whose epistemic layer is PERIPHERY)
 * are strictly forbidden from mutating CORE layer files (database migrations, core security configs,
 * or injecting factory orchestrator entities).
 *
 * <p>Architectural reachability (Prescription 33, measured 2026-08-28 on test-fiftieth over 365 tasks):
 * {@code GateOrchestrator.runQualityGate} stands on exactly <b>1 of the 5 paths</b> that write {@link TaskStatus#done}
 * (specifically {@code ClaimService:349} during review admission) and covers only 5 of the 13 roles.
 * The other four paths to {@code done} (two in {@code AutoMergeService}, one in {@code PlannedWorkRecoveryService},
 * and one in {@code ProjectFlowService}) do not run this quality gate.
 *
 * <p>Therefore, machine-wide Quinean epistemic demarcation is enforced directly on the merge path in
 * {@link com.eneik.production.services.AutoMergeService#rejectByFactoryPokaYoke} and
 * {@link com.eneik.production.services.AutoMergeService#judgeQuineanEpistemicBoundary} on real PR files before merging.
 */
@Service
@Order(230)
public class EpistemicLayerInvariantGate implements GateCheck {
    private static final String CHECK_NAME = "epistemic_layer_invariant";
    public static final Set<String> PERIPHERY_ROLES = Set.of("BARCAN-TAG-11", "BARCAN-TAG-05", "BARCAN-TAG-06");

    private final FeatureRepository featureRepository;
    private final JulesSessionRepository julesSessionRepository;
    private final GitHubPullRequestService gitHubPullRequestService;

    public EpistemicLayerInvariantGate(FeatureRepository featureRepository,
                                       JulesSessionRepository julesSessionRepository,
                                       GitHubPullRequestService gitHubPullRequestService) {
        this.featureRepository = featureRepository;
        this.julesSessionRepository = julesSessionRepository;
        this.gitHubPullRequestService = gitHubPullRequestService;
    }

    @Override
    public GateStage stage() {
        return GateStage.IMPLEMENTATION_RESULT;
    }

    @Override
    public boolean supports(TaskEntity task) {
        if (task == null || task.getRole() == null) {
            return false;
        }
        return PERIPHERY_ROLES.contains(task.getRole().getTag());
    }

    @Override
    public boolean isBuildPhaseExempt() {
        return false;
    }

    @Override
    public GateResult check(TaskEntity task) {
        if (task == null || task.getProject() == null || task.getFeatureId() == null) {
            return new GateResult(true, CHECK_NAME, List.of());
        }

        FeatureEntity feature = featureRepository.findById(task.getFeatureId()).orElse(null);
        if (feature == null || !"PERIPHERY".equalsIgnoreCase(feature.getEpistemicLayer())) {
            return new GateResult(true, CHECK_NAME, List.of());
        }

        // Validate PR files if session exists
        List<JulesSessionEntity> sessions = julesSessionRepository.findByTaskId(task.getId());
        if (sessions == null || sessions.isEmpty()) {
            return new GateResult(true, CHECK_NAME, List.of());
        }

        List<String> violations = new ArrayList<>();
        if (task.getFileScope() != null) {
            String scope = task.getFileScope().toLowerCase(java.util.Locale.ROOT);
            if (scope.contains("migration") || scope.contains("securityconfig") || scope.contains("security_config")) {
                violations.add("PERIPHERY task attempted to modify CORE scope: " + task.getFileScope());
            }
            if (scope.contains("automerge") || scope.contains("sixsigma") || scope.contains("orchestrator") || scope.contains("jules")) {
                violations.add("ONTOLOGICAL_CONTAMINATION: Task attempted to create factory orchestrator entities in client codebase: " + task.getFileScope());
            }
        }

        List<String> changedFiles = realChangedFiles(task);
        for (String file : changedFiles) {
            if (file == null || file.isBlank() || file.startsWith(".eneik/") || file.contains("/.eneik/")) {
                continue;
            }
            String lower = file.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("migration") || lower.contains("securityconfig") || lower.contains("security_config")) {
                violations.add("PERIPHERY task attempted to modify CORE file: " + file);
            }
            if (lower.contains("automerge") || lower.contains("sixsigma") || lower.contains("orchestrator") || lower.contains("jules")) {
                violations.add("ONTOLOGICAL_CONTAMINATION: Task attempted to create factory orchestrator entities in client codebase: " + file);
            }
        }

        boolean passed = violations.isEmpty();
        return new GateResult(passed, CHECK_NAME, violations);
    }

    private List<String> realChangedFiles(TaskEntity task) {
        if (gitHubPullRequestService == null || task == null || task.getId() == null || task.getProject() == null) {
            return List.of();
        }
        List<JulesSessionEntity> sessions = julesSessionRepository.findByTaskId(task.getId());
        if (sessions == null || sessions.isEmpty()) {
            return List.of();
        }
        JulesSessionEntity session = sessions.stream()
                .filter(s -> s.getPrUrl() != null && !s.getPrUrl().isBlank())
                .findFirst()
                .orElse(null);
        if (session == null) {
            return List.of();
        }
        Integer pullNumber = gitHubPullRequestService.parsePullNumber(session.getPrUrl());
        if (pullNumber == null) {
            return List.of();
        }
        return gitHubPullRequestService.fetchDiffText(task.getProject(), pullNumber)
                .map(GitHubPullRequestService::changedFilePathsFromDiff)
                .orElse(List.of());
    }
}
