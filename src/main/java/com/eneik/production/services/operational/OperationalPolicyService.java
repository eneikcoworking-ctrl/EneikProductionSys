package com.eneik.production.services.operational;

import com.eneik.production.dto.operational.FlowCoreDto;
import com.eneik.production.dto.operational.FlowSpineDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class OperationalPolicyService {
    private final OperationalFlowCoreService flowCoreService;

    public OperationalPolicyService(OperationalFlowCoreService flowCoreService) {
        this.flowCoreService = flowCoreService;
    }

    @Transactional(readOnly = true)
    public OperationalDecision authorize(UUID projectId, OperationalAction action) {
        return authorize(flowCoreService.build(projectId), action);
    }

    public OperationalDecision authorize(FlowCoreDto core, OperationalAction action) {
        FlowSpineDto snapshot = core.snapshot();
        FlowCoreDto.Authorization authorization = core.authorization();
        boolean activeProject = "active".equalsIgnoreCase(core.project().status());
        boolean hardBlocked = isHardBlocked(snapshot.currentState());
        boolean terminal = isTerminal(snapshot.currentState());
        boolean hasPendingScope = snapshot.evidence().pendingWishlist() > 0
                || snapshot.evidence().compilingWishlist() > 0
                || !snapshot.counts().decompositionComplete();

        boolean allowed = switch (action) {
            case OBSERVE -> authorization.journalAppendAllowed();
            case ADD_WISHLIST -> activeProject && !terminal;
            case ORCHESTRATE -> activeProject && !hardBlocked && !terminal
                    && (!"DELIVERED".equals(snapshot.currentState()) || hasPendingScope);
            case RECOVER_FAILED_FRONTIER -> activeProject && "BLOCKED_BY_FAILED_FRONTIER".equals(snapshot.currentState());
            case DISPATCH_QUEUED_TASKS -> activeProject && !hardBlocked && snapshot.counts().queuedTasks() > 0;
            case DISPATCH_REVIEW_TASKS -> activeProject && !hardBlocked
                    && (snapshot.counts().reviewTasks() > 0 || snapshot.evidence().openReviews() > 0);
            case CHECK_COVERAGE_AUDITS, RUN_PROJECT_AUDIT_PIPELINE -> activeProject && !hardBlocked
                    && !Set.of("FROZEN", "PROJECT_NOT_ACTIVE", "ACCEPTED", "ARCHIVED").contains(snapshot.currentState());
            case MERGE_PR -> activeProject && !hardBlocked
                    && (authorization.mergeAllowed() || snapshot.evidence().openReviews() > 0);
            case SYNC_GITHUB -> activeProject && !"GITHUB_RATE_LIMITED".equals(snapshot.currentState());
            case CLEANUP_TERMINAL_PROJECT -> true;
            case NUDGE_SESSION, BOOST_PRIORITY -> activeProject && !hardBlocked;
            case DISMISS_WISHLIST, ABANDON_CONFLICT -> activeProject && !terminal;
        };

        String reason = allowed
                ? "Operational action " + action + " is allowed in state " + snapshot.currentState() + "."
                : denialReason(action, core);
        return new OperationalDecision(
                core.project().id(),
                action,
                allowed,
                snapshot.currentState(),
                authorization.status(),
                reason,
                snapshot.bottlenecks().stream().map(FlowSpineDto.Bottleneck::type).toList(),
                core
        );
    }

    @Transactional(readOnly = true)
    public void requireAllowed(UUID projectId, OperationalAction action) {
        OperationalDecision decision = authorize(projectId, action);
        if (!decision.allowed()) {
            throw new OperationalPolicyDeniedException(
                    projectId,
                    action,
                    decision.state(),
                    decision.authorizationStatus(),
                    decision.reason()
            );
        }
    }

    private static String denialReason(OperationalAction action, FlowCoreDto core) {
        FlowSpineDto snapshot = core.snapshot();
        String blockingReason = snapshot.blockingReason();
        if (blockingReason != null && !blockingReason.isBlank()) {
            return "Operational action " + action + " denied by Flow Core state "
                    + snapshot.currentState() + ": " + blockingReason;
        }
        return "Operational action " + action + " denied by Flow Core state "
                + snapshot.currentState() + " with authorization " + core.authorization().status() + ".";
    }

    private static boolean isHardBlocked(String state) {
        return Set.of(
                "FROZEN",
                "PROJECT_NOT_ACTIVE",
                "BLOCKED_BY_DUPLICATE_CONTENT",
                "GITHUB_RATE_LIMITED",
                "SYSTEM_STALLED",
                "BLOCKED_BY_TASK",
                "BLOCKED_BY_REVIEW",
                "BLOCKED_BY_FAILED_FRONTIER",
                "ACCEPTED",
                "ARCHIVED"
        ).contains(state);
    }

    private static boolean isTerminal(String state) {
        return Set.of("ACCEPTED", "ARCHIVED", "FROZEN", "PROJECT_NOT_ACTIVE").contains(state);
    }

    public record OperationalDecision(
            UUID projectId,
            OperationalAction action,
            boolean allowed,
            String state,
            String authorizationStatus,
            String reason,
            List<String> blockers,
            FlowCoreDto core
    ) {
    }
}
