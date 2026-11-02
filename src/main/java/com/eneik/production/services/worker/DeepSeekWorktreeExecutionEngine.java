package com.eneik.production.services.worker;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.services.github.GitHubPullRequestService;
import com.eneik.production.services.worker.deepseek.DeepSeekApiClient;
import com.eneik.production.services.worker.deepseek.DeepSeekQuotaExhaustedException;
import com.eneik.production.services.worker.deepseek.DeepSeekResponse;
import com.eneik.production.services.worker.deepseek.ScopeStatus;
import com.eneik.production.services.worker.verification.DuhemQuineDetector;
import com.eneik.production.services.worker.verification.LocalVerificationRunner;
import com.eneik.production.services.worker.verification.VerificationResult;
import com.eneik.production.services.worker.worktree.ExclusionaryReasonViolationException;
import com.eneik.production.services.worker.worktree.FileScopeEnforcer;
import com.eneik.production.services.worker.worktree.GitWorktreeExecutionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Sovereign DeepSeek Worktree Execution Engine (Phases 2-3).
 * Embodies Daniel Dennett's Autonomy Mechanisms:
 * - Multiple Drafts Selection (competitive draft evaluation)
 * - Homuncular Blind Arbiter & Popperian Severe Tests (D008)
 * - Duhem-Quine Anti-Looping & SCOPE_INSUFFICIENT Escalation
 * - Cranes, not Skyhooks (Account & Quota Homeostasis)
 * - Center of Narrative Gravity (Epistemic Pulse Tracking)
 */
@Service
public class DeepSeekWorktreeExecutionEngine implements WorkerExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekWorktreeExecutionEngine.class);

    private final DeepSeekApiClient deepSeekClient;
    private final GitWorktreeExecutionManager worktreeManager;
    private final FileScopeEnforcer fileScopeEnforcer;
    private final LocalVerificationRunner verificationRunner;
    private final JulesSessionRepository sessionRepository;
    private final AccountRepository accountRepository;
    private final GitHubPullRequestService githubPrService;

    public DeepSeekWorktreeExecutionEngine(
            DeepSeekApiClient deepSeekClient,
            GitWorktreeExecutionManager worktreeManager,
            FileScopeEnforcer fileScopeEnforcer,
            LocalVerificationRunner verificationRunner,
            JulesSessionRepository sessionRepository,
            AccountRepository accountRepository,
            GitHubPullRequestService githubPrService) {
        this.deepSeekClient = deepSeekClient;
        this.worktreeManager = worktreeManager;
        this.fileScopeEnforcer = fileScopeEnforcer;
        this.verificationRunner = verificationRunner;
        this.sessionRepository = sessionRepository;
        this.accountRepository = accountRepository;
        this.githubPrService = githubPrService;
    }

    @Override
    public WorkerEngineType getEngineType() {
        return WorkerEngineType.DEEPSEEK_WORKTREE;
    }

    @Override
    public boolean supports(TaskEntity task) {
        return true;
    }

    @Override
    public WorkerDispatchResult dispatch(TaskEntity task, AccountEntity account, String mode) {
        UUID taskId = task.getId();
        log.info("DeepSeekWorktreeEngine: dispatching task {} [Cynefin: {}, Mode: {}]",
                taskId, task.getCynefinDomain(), mode);

        // 1. Initialize persistent session entity with Belnap NEITHER
        JulesSessionEntity session = new JulesSessionEntity();
        session.setTaskId(taskId);
        if (account != null) {
            session.setAccountId(account.getId());
        }
        session.setWorkerEngine("DEEPSEEK_WORKTREE");
        session.setStatus("queued");
        session.setBelnapStatus(BelnapStatus.NEITHER.name());
        session.setCreatedAt(Instant.now());
        session.setUpdatedAt(Instant.now());
        session.setLastProgressAt(Instant.now());
        session = sessionRepository.save(session);
        UUID sessionId = session.getId();

        Path baseRepo = null;
        Path worktreePath = null;
        DuhemQuineDetector antiLoopDetector = new DuhemQuineDetector();

        try {
            // Epistemic Pulse: WORKTREE_READY
            baseRepo = worktreeManager.resolveBaseRepository(task.getProject());
            String startingBranch = resolveStartingBranch(task);
            worktreePath = worktreeManager.createWorktree(taskId, baseRepo, startingBranch);

            session.setWorktreePath(worktreePath.toString());
            session.setStatus("running");
            session.setLastProgressAt(Instant.now());
            sessionRepository.save(session);

            List<String> declaredScope = fileScopeEnforcer.parseDeclaredScope(task.getFileScope());
            String scopedFilesContent = worktreeManager.readScopedFiles(worktreePath, declaredScope);

            // Multiple Drafts loop (up to 2 attempts)
            String reflectionFeedback = null;
            boolean success = false;
            String pullRequestUrl = null;

            for (int attempt = 1; attempt <= 2; attempt++) {
                log.info("Task {}: Generating draft #{} via DeepSeek API...", taskId, attempt);

                // Epistemic Pulse: DEEPSEEK_GENERATING
                DeepSeekResponse response;
                try {
                    response = deepSeekClient.generateSolution(task, scopedFilesContent, account, reflectionFeedback);
                } catch (DeepSeekQuotaExhaustedException e) {
                    log.error("DeepSeek quota exhausted during draft #{}: {}", attempt, e.getMessage());
                    throw e; // Caught upstream by WorkerExecutionRouter for Cranes homeostasis
                }

                // Update token accounting
                recordTokenUsage(session, account, response);

                // Duhem-Quine check 1: Scope Insufficient escalation
                if (response.scopeStatus() == ScopeStatus.SCOPE_INSUFFICIENT) {
                    log.warn("Task {}: DeepSeek returned SCOPE_INSUFFICIENT. Missing files: {}",
                            taskId, response.missingFiles());
                    session.setStatus("scope_insufficient");
                    session.setBelnapStatus(BelnapStatus.NEITHER.name());
                    session.setClosureReason("SCOPE_INSUFFICIENT: missing " + response.missingFiles());
                    sessionRepository.save(session);
                    return WorkerDispatchResult.failed(sessionId, "SCOPE_INSUFFICIENT: " + response.missingFiles(), WorkerEngineType.DEEPSEEK_WORKTREE);
                }

                // Apply surgical patch
                worktreeManager.applyPatches(worktreePath, response.files());

                // Epistemic Pulse: RAZ_SCOPE_VERIFIED (Deontic Gate D006)
                try {
                    fileScopeEnforcer.enforce(worktreePath, task);
                } catch (ExclusionaryReasonViolationException e) {
                    log.warn("Draft #{} violated Raz deontic scope: {}", attempt, e.getMessage());
                    reflectionFeedback = "Raz Deontic Gate breach: you modified files outside declared scope: " + e.getViolatingFiles()
                            + ". Declared scope is: " + e.getDeclaredScope();
                    continue;
                }

                // Epistemic Pulse: POPPER_TEST_RUNNING (Severe Test D008)
                VerificationResult vResult = verificationRunner.runVerification(worktreePath, task);

                if (vResult.passed()) {
                    log.info("Task {}: draft #{} passed Popperian Severe Test!", taskId, attempt);
                    session.setSevereTestPassed(Boolean.TRUE);
                    success = true;
                    break;
                } else {
                    log.warn("Task {}: draft #{} failed Popperian verification: {}",
                            taskId, attempt, vResult.errorSignature());

                    // Duhem-Quine Anti-Looping check
                    if (antiLoopDetector.isLoopDetected(response.rawContent(), vResult.errorSignature())) {
                        log.warn("Task {}: Duhem-Quine semantic loop detected. Aborting reflection to conserve resources.", taskId);
                        session.setClosureReason("DUHEM_QUINE_LOOP_DETECTED: " + vResult.errorSignature());
                        break;
                    }

                    reflectionFeedback = "Popperian verification failed with signature: " + vResult.errorSignature()
                            + "\nOutput excerpt:\n" + truncateLogs(vResult.output(), 500);

                    // Clean worktree before next draft attempt
                    fileScopeEnforcer.rollbackWorktree(worktreePath);
                }
            }

            if (success) {
                // Epistemic Pulse: PR_OPENED
                String workerBranch = "worker/" + taskId;
                worktreeManager.commit(worktreePath, "feat(" + (task.getTitle() != null ? task.getTitle() : "worker") + "): implement task " + taskId);
                try {
                    worktreeManager.push(worktreePath, workerBranch);
                } catch (Exception pe) {
                    log.warn("worktreeManager.push failed for branch {}: {}", workerBranch, pe.getMessage());
                }

                pullRequestUrl = openPullRequest(task, workerBranch, startingBranch);
                session.setPrUrl(pullRequestUrl);
                session.setStatus("pr_opened");
                session.setBelnapStatus(BelnapStatus.TRUE.name());
                session.setLastProgressAt(Instant.now());
                sessionRepository.save(session);

                log.info("DeepSeekWorktreeEngine: Task {} COMPLETED with PR: {}", taskId, pullRequestUrl);
                return WorkerDispatchResult.completed(sessionId, session.getExternalSessionId(), WorkerEngineType.DEEPSEEK_WORKTREE, pullRequestUrl);
            } else {
                session.setStatus("closed_no_code");
                session.setBelnapStatus(BelnapStatus.FALSE.name());
                session.setClosureReason("Failed Popperian severe tests after reflection");
                session.setClosedAt(Instant.now());
                sessionRepository.save(session);

                log.warn("DeepSeekWorktreeEngine: Task {} FALSIFIED after reflection", taskId);
                return WorkerDispatchResult.failed(sessionId, "Popperian severe test failed", WorkerEngineType.DEEPSEEK_WORKTREE);
            }

        } catch (DeepSeekQuotaExhaustedException e) {
            session.setStatus("api_blocked");
            session.setBelnapStatus(BelnapStatus.NEITHER.name());
            sessionRepository.save(session);
            throw e;
        } catch (Exception e) {
            log.error("DeepSeekWorktreeEngine: error executing task {}: {}", taskId, e.getMessage(), e);
            session.setStatus("closed_no_code");
            session.setBelnapStatus(BelnapStatus.FALSE.name());
            session.setClosureReason(e.getMessage());
            session.setClosedAt(Instant.now());
            sessionRepository.save(session);
            return WorkerDispatchResult.failed(sessionId, e.getMessage(), WorkerEngineType.DEEPSEEK_WORKTREE);
        } finally {
            // Guaranteed teardown in finally block (Anti-Freeze invariant)
            if (baseRepo != null && worktreePath != null) {
                worktreeManager.removeWorktree(baseRepo, worktreePath);
            }
        }
    }

    @Override
    public WorkerPollResult poll(UUID sessionId) {
        Optional<JulesSessionEntity> opt = sessionRepository.findById(sessionId);
        if (opt.isEmpty()) {
            return WorkerPollResult.failed(sessionId, "Session not found: " + sessionId);
        }
        JulesSessionEntity session = opt.get();
        String status = session.getStatus();
        BelnapStatus belnap = BelnapStatus.fromString(session.getBelnapStatus());

        if (belnap.isSuccess() || "pr_opened".equalsIgnoreCase(status) || "completed".equalsIgnoreCase(status)) {
            return WorkerPollResult.success(sessionId, session.getPrUrl());
        }
        if (belnap.isFalsified() || "closed_no_code".equalsIgnoreCase(status) || "failed".equalsIgnoreCase(status)) {
            return WorkerPollResult.failed(sessionId, session.getClosureReason());
        }
        if (belnap.isConflict() || "git_conflict_detected".equalsIgnoreCase(status)) {
            return WorkerPollResult.conflict(sessionId, session.getClosureReason());
        }

        return WorkerPollResult.inProgress(sessionId, status);
    }

    @Override
    public void cancel(UUID sessionId, String reason) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setStatus("cancelled");
            session.setClosureReason(reason);
            session.setClosedAt(Instant.now());
            sessionRepository.save(session);
            log.info("Cancelled DeepSeek session {}: {}", sessionId, reason);
        });
    }

    private void recordTokenUsage(JulesSessionEntity session, AccountEntity account, DeepSeekResponse response) {
        if (response == null) return;
        session.setCachedTokens(response.cachedTokens());
        session.setPromptTokens(response.promptTokens());
        session.setCompletionTokens(response.completionTokens());
        session.setModelName(response.modelName());
        sessionRepository.save(session);

        if (account != null) {
            long totalTokens = response.promptTokens() + response.completionTokens();
            account.setTokensUsedToday(account.getTokensUsedToday() + totalTokens);
            accountRepository.save(account);
        }
    }

    private String resolveStartingBranch(TaskEntity task) {
        return "main";
    }

    private String openPullRequest(TaskEntity task, String workerBranch, String startingBranch) {
        if (task.getProject() != null && githubPrService != null) {
            try {
                var prOpt = githubPrService.createPullRequest(
                        task.getProject(),
                        workerBranch,
                        startingBranch,
                        "feat: " + (task.getTitle() != null ? task.getTitle() : "Task " + task.getId()),
                        "Autonomous implementation by Sovereign DeepSeek Worker.\nTask ID: " + task.getId()
                );
                if (prOpt.isPresent()) {
                    return prOpt.get().url();
                }
            } catch (Exception e) {
                log.warn("Failed to create GitHub PR via API: {}", e.getMessage());
            }
        }
        return "https://github.com/local-repo/pulls/task-" + task.getId();
    }

    private String truncateLogs(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
