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
 * E3 Epistemic Engine (Phase 3): Quine Web of Belief & Epistemic Layer Invariant Gate.
 * Enforces the Principle of Minimum Mutilation: tasks belonging to the PERIPHERY layer
 * (e.g. BARCAN-TAG-11 UI or experimental features) are strictly forbidden from mutating
 * the CORE layer files (database migrations, core security configs).
 */
@Service
@Order(230)
public class EpistemicLayerInvariantGate implements GateCheck {
    private static final String CHECK_NAME = "epistemic_layer_invariant";
    private static final Set<String> PERIPHERY_ROLES = Set.of("BARCAN-TAG-11", "BARCAN-TAG-05", "BARCAN-TAG-06");

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
            String scope = task.getFileScope().toLowerCase();
            if (scope.contains("migration") || scope.contains("securityconfig")) {
                violations.add("PERIPHERY task attempted to modify CORE scope: " + task.getFileScope());
            }
            if (scope.contains("automerge") || scope.contains("sixsigma") || scope.contains("orchestrator") || scope.contains("jules")) {
                violations.add("ONTOLOGICAL_CONTAMINATION: Task attempted to create factory orchestrator entities in client codebase: " + task.getFileScope());
            }
        }

        boolean passed = violations.isEmpty();
        return new GateResult(passed, CHECK_NAME, violations);
    }
}
