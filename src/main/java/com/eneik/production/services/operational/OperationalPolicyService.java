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
            // Deliberately narrower than hardBlocked (2026-07-31): a task/review/stall-specific blocker
            // (BLOCKED_BY_TASK, BLOCKED_BY_REVIEW, SYSTEM_STALLED, BLOCKED_BY_FAILED_FRONTIER) means
            // something ELSEWHERE in the project needs attention - it carries no evidence that a brand-new,
            // never-dispatched queued task is unsafe to start. Confirmed live: one task with a broken
            // review link held 11 other, fully independent queued tasks hostage for 9+ hours under the old
            // "any hard-blocked state stops everything" rule. Genuinely global conditions (frozen/archived/
            // accepted/not-active, GitHub itself unavailable, a content-generation quality gate) still stop
            // new dispatch - those really do mean nothing in this project should move. Extended same-day
            // (2026-07-31, second pass) to ORCHESTRATE, DISPATCH_REVIEW_TASKS, CHECK_COVERAGE_AUDITS and
            // RUN_PROJECT_AUDIT_PIPELINE: SYSTEM_STALLED itself is defined as "no dispatch/merge progress
            // despite actionable work" (ContinuousOrchestrationService.checkForSystemStall) - using it to
            // block the very dispatch/audit actions that would CREATE that progress is circular and
            // self-perpetuating. Confirmed live on test-fortieth: a task stuck in pending_review (not the
            // `review` status Branch GC's own auto-recovery path checks for) sat blocked for 80+ minutes
            // because DISPATCH_REVIEW_TASKS still used the broad hardBlocked check, and coverage audits
            // were STILL blocked under SYSTEM_STALLED despite an earlier same-day narrowing attempt here
            // that left a redundant `!hardBlocked &&` prefix in front of the narrower Set check below,
            // silently defeating it - removed that dead prefix along with widening the narrowing to the
            // two actions that never got it at all.
            // ORCHESTRATE keeps its original, deliberate entanglement with BLOCKED_BY_REVIEW/BLOCKED_BY_TASK
            // (new decomposition genuinely waits its turn behind those) - only SYSTEM_STALLED is carved out
            // here, since that state is specifically "no dispatch/merge progress despite actionable work"
            // and a pending wishlist never getting decomposed is itself part of what keeps a project stalled.
            case ORCHESTRATE -> activeProject && !terminal
                    && (!hardBlocked || "SYSTEM_STALLED".equals(snapshot.currentState()))
                    && (!"DELIVERED".equals(snapshot.currentState()) || hasPendingScope);
            case RECOVER_FAILED_FRONTIER -> activeProject && "BLOCKED_BY_FAILED_FRONTIER".equals(snapshot.currentState());
            case DISPATCH_QUEUED_TASKS -> activeProject && snapshot.counts().queuedTasks() > 0
                    && !Set.of("FROZEN", "PROJECT_NOT_ACTIVE", "ACCEPTED", "ARCHIVED",
                            "GITHUB_RATE_LIMITED", "BLOCKED_BY_DUPLICATE_CONTENT").contains(snapshot.currentState());
            case DISPATCH_REVIEW_TASKS -> activeProject
                    && !Set.of("FROZEN", "PROJECT_NOT_ACTIVE", "ACCEPTED", "ARCHIVED",
                            "GITHUB_RATE_LIMITED", "BLOCKED_BY_DUPLICATE_CONTENT").contains(snapshot.currentState())
                    && (snapshot.counts().reviewTasks() > 0 || snapshot.evidence().openReviews() > 0);
            case CHECK_COVERAGE_AUDITS, RUN_PROJECT_AUDIT_PIPELINE -> activeProject
                    && !Set.of("FROZEN", "PROJECT_NOT_ACTIVE", "ACCEPTED", "ARCHIVED",
                            "GITHUB_RATE_LIMITED", "BLOCKED_BY_DUPLICATE_CONTENT").contains(snapshot.currentState());
            // Deliberately narrower than hardBlocked (2026-07-31, same shape as DISPATCH_QUEUED_TASKS
            // above): confirmed live, PR#13 (task 529e5252) was already approved by review with no
            // problems of its own, but sat unmerged for hours because BLOCKED_BY_REVIEW is project-wide -
            // it fired on account of a DIFFERENT, unrelated review's failure, which carries no evidence
            // that THIS already-approved PR is unsafe to merge. Genuinely global conditions still stop
            // merging (frozen/archived/accepted/not-active, GitHub itself unavailable, a content-generation
            // quality gate) - those really do mean nothing in this project should move.
            case MERGE_PR -> activeProject
                    && !Set.of("FROZEN", "PROJECT_NOT_ACTIVE", "ACCEPTED", "ARCHIVED",
                            "GITHUB_RATE_LIMITED", "BLOCKED_BY_DUPLICATE_CONTENT").contains(snapshot.currentState())
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
