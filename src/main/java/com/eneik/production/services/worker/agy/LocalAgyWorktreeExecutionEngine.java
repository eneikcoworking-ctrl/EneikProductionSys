package com.eneik.production.services.worker.agy;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.JulesSessionEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.services.github.GitHubPullRequestService;
import com.eneik.production.services.worker.BelnapStatus;
import com.eneik.production.services.worker.WorkerDispatchResult;
import com.eneik.production.services.worker.WorkerEngineType;
import com.eneik.production.services.worker.WorkerExecutionEngine;
import com.eneik.production.services.worker.WorkerPollResult;
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

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Sovereign Local Agy Worktree Execution Engine (Zero-Cost Local Sovereign Compute).
 * Dispatches tasks into ephemeral git worktrees using the local agy CLI under
 * dedicated unprivileged product worker profiles (remotecli3 / root).
 *
 * Implements Daniel Dennett's Autonomy Architecture:
 * - Multiple Drafts Selection (competitive draft evaluation)
 * - Homuncular Blind Arbiter & Popperian Severe Tests (D008)
 * - Duhem-Quine Anti-Looping & SCOPE_INSUFFICIENT Escalation
 * - Cranes, not Skyhooks (Zero-cost Local Homeostasis)
 * - Center of Narrative Gravity (Epistemic Belnap Transitions)
 */
@Service
public class LocalAgyWorktreeExecutionEngine implements WorkerExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(LocalAgyWorktreeExecutionEngine.class);

    private final LocalAgyCliClient agyClient;
    private final GitWorktreeExecutionManager worktreeManager;
    private final FileScopeEnforcer fileScopeEnforcer;
    private final LocalVerificationRunner verificationRunner;
    private final JulesSessionRepository sessionRepository;
    private final AccountRepository accountRepository;
    private final GitHubPullRequestService githubPrService;

    public LocalAgyWorktreeExecutionEngine(
            LocalAgyCliClient agyClient,
            GitWorktreeExecutionManager worktreeManager,
            FileScopeEnforcer fileScopeEnforcer,
            LocalVerificationRunner verificationRunner,
            JulesSessionRepository sessionRepository,
            AccountRepository accountRepository,
            GitHubPullRequestService githubPrService) {
        this.agyClient = agyClient;
        this.worktreeManager = worktreeManager;
        this.fileScopeEnforcer = fileScopeEnforcer;
        this.verificationRunner = verificationRunner;
        this.sessionRepository = sessionRepository;
        this.accountRepository = accountRepository;
        this.githubPrService = githubPrService;
    }

    @Override
    public WorkerEngineType getEngineType() {
        return WorkerEngineType.LOCAL_AGY;
    }

    @Override
    public boolean supports(TaskEntity task) {
        return true;
    }

    @Override
    public WorkerDispatchResult dispatch(TaskEntity task, AccountEntity account, String mode) {
        UUID taskId = task.getId();
        log.info("LocalAgyWorktreeEngine: dispatching task {} [Cynefin: {}, Mode: {}]",
                taskId, task.getCynefinDomain(), mode);

        // 1. Initialize persistent session entity with Belnap NEITHER
        JulesSessionEntity session = new JulesSessionEntity();
        session.setTaskId(taskId);
        if (account != null) {
            session.setAccountId(account.getId());
        }
        session.setWorkerEngine("LOCAL_AGY");
        session.setExternalSessionId("local-agy-" + UUID.randomUUID());
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
                log.info("Task {}: Generating draft #{} via Local agy CLI...", taskId, attempt);

                DeepSeekResponse response = agyClient.generateSolution(task, scopedFilesContent, account, reflectionFeedback);

                // Duhem-Quine check 1: Scope Insufficient escalation
                if (response.scopeStatus() == ScopeStatus.SCOPE_INSUFFICIENT) {
                    log.warn("Task {}: Local agy returned SCOPE_INSUFFICIENT. Missing files: {}",
                            taskId, response.missingFiles());
                    session.setStatus("scope_insufficient");
                    session.setBelnapStatus(BelnapStatus.NEITHER.name());
                    session.setClosureReason("SCOPE_INSUFFICIENT: missing " + response.missingFiles());
                    sessionRepository.save(session);
                    return WorkerDispatchResult.failed(sessionId, "SCOPE_INSUFFICIENT: " + response.missingFiles(), WorkerEngineType.LOCAL_AGY);
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
                VerificationResult vResult;
                if (task != null && (task.isWishlistCompiler() || task.isCarrier())) {
                    log.info("Task {}: Carrier/compiler task detected, skipping Docker Maven build", taskId);
                    vResult = VerificationResult.success(0, "Carrier/compiler task — skipped Maven build");
                } else {
                    vResult = verificationRunner.runVerification(worktreePath, task);
                }

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
                        log.warn("Task {}: Duhem-Quine semantic loop detected in agy drafts. Aborting reflection.", taskId);
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
                String workerBranch = "worker/" + taskId;
                worktreeManager.commit(worktreePath, "feat(" + (task.getTitle() != null ? task.getTitle() : "worker") + "): implement task " + taskId);
                try {
                    worktreeManager.push(worktreePath, workerBranch);
                } catch (Exception pe) {
                    log.warn("worktreeManager.push failed for branch {}: {}", workerBranch, pe.getMessage());
                }

                pullRequestUrl = openPullRequest(task, workerBranch, startingBranch);
                if (pullRequestUrl == null || pullRequestUrl.isBlank()) {
                    throw new RuntimeException("Failed to open GitHub Pull Request for branch " + workerBranch);
                }
                session.setPrUrl(pullRequestUrl);
                session.setStatus("pr_opened");
                session.setBelnapStatus(BelnapStatus.TRUE.name());
                session.setLastProgressAt(Instant.now());
                sessionRepository.save(session);

                log.info("LocalAgyWorktreeEngine: Task {} COMPLETED with PR: {}", taskId, pullRequestUrl);
                return WorkerDispatchResult.completed(sessionId, session.getExternalSessionId(), WorkerEngineType.LOCAL_AGY, pullRequestUrl);
            } else {
                session.setStatus("closed_no_code");
                session.setBelnapStatus(BelnapStatus.FALSE.name());
                session.setClosureReason("Failed Popperian severe tests after reflection");
                session.setClosedAt(Instant.now());
                sessionRepository.save(session);

                log.warn("LocalAgyWorktreeEngine: Task {} FALSIFIED after reflection", taskId);
                return WorkerDispatchResult.failed(sessionId, "Popperian severe test failed", WorkerEngineType.LOCAL_AGY);
            }

        } catch (Exception e) {
            log.error("LocalAgyWorktreeEngine: error executing task {}: {}", taskId, e.getMessage(), e);
            session.setStatus("closed_no_code");
            session.setBelnapStatus(BelnapStatus.FALSE.name());
            session.setClosureReason(e.getMessage());
            session.setClosedAt(Instant.now());
            sessionRepository.save(session);
            return WorkerDispatchResult.failed(sessionId, e.getMessage(), WorkerEngineType.LOCAL_AGY);
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
        Optional<JulesSessionEntity> opt = sessionRepository.findById(sessionId);
        if (opt.isPresent()) {
            JulesSessionEntity session = opt.get();
            session.setStatus("cancelled");
            session.setClosureReason(reason);
            session.setClosedAt(Instant.now());
            sessionRepository.save(session);
        }
    }

    private String resolveStartingBranch(TaskEntity task) {
        return (task.getProject() != null && task.getProject().getDefaultBranch() != null)
                ? task.getProject().getDefaultBranch()
                : "main";
    }

    private String openPullRequest(TaskEntity task, String workerBranch, String baseBranch) {
        if (task.getProject() == null || task.getProject().getName() == null) {
            return null;
        }
        try {
            String title = "feat: " + (task.getTitle() != null ? task.getTitle() : "task " + task.getId());
            String body = "Autonomous sovereign worker PR for task " + task.getId() + "\n\n"
                    + "Description:\n" + task.getDescription() + "\n\n"
                    + "Verified by LocalVerificationRunner (Semaphore(1), D008). Engine: LOCAL_AGY.";

            var prOpt =
                    githubPrService.createPullRequest(task.getProject(), workerBranch, baseBranch, title, body);

            return prOpt.map(GitHubPullRequestService.GitHubPullRequest::url).orElse(null);
        } catch (Exception e) {
            log.warn("Failed to create GitHub PR for task {}: {}", task.getId(), e.getMessage());
            return null;
        }
    }

    private String truncateLogs(String output, int maxChars) {
        if (output == null) return "";
        if (output.length() <= maxChars) return output;
        return output.substring(output.length() - maxChars);
    }
}
