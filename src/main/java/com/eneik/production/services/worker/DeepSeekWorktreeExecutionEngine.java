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

        // 1. Initialize or reuse pre-dispatched session entity (from JulesDispatchService) with Belnap NEITHER
        JulesSessionEntity session = null;
        UUID worktreeToken = null;
        List<JulesSessionEntity> existingSessions = sessionRepository.findByTaskId(taskId);
        if (existingSessions != null) {
            for (JulesSessionEntity s : existingSessions) {
                if (("running".equalsIgnoreCase(s.getStatus()) || "queued".equalsIgnoreCase(s.getStatus()))
                        && s.getWorktreePath() == null
                        && s.getExternalSessionId() != null
                        && (s.getExternalSessionId().startsWith("local-agy-") || s.getExternalSessionId().startsWith("deepseek-"))) {
                    String extId = s.getExternalSessionId();
                    String suffix = extId.startsWith("local-agy-")
                            ? extId.substring("local-agy-".length())
                            : extId.substring("deepseek-".length());
                    try {
                        worktreeToken = UUID.fromString(suffix);
                    } catch (IllegalArgumentException ignored) {
                        worktreeToken = UUID.randomUUID();
                    }
                    session = s;
                    session.setWorkerEngine("DEEPSEEK_WORKTREE");
                    session.setExternalSessionId("deepseek-" + worktreeToken);
                    if (account != null) {
                        session.setAccountId(account.getId());
                    }
                    session.setUpdatedAt(Instant.now());
                    session.setLastProgressAt(Instant.now());
                    session = sessionRepository.save(session);
                    break;
                }
            }
        }
        if (session == null) {
            worktreeToken = UUID.randomUUID();
            session = new JulesSessionEntity();
            session.setTaskId(taskId);
            if (account != null) {
                session.setAccountId(account.getId());
            }
            session.setWorkerEngine("DEEPSEEK_WORKTREE");
            session.setExternalSessionId("deepseek-" + worktreeToken);
            session.setStatus("queued");
            session.setBelnapStatus(BelnapStatus.NEITHER.name());
            session.setCreatedAt(Instant.now());
            session.setUpdatedAt(Instant.now());
            session.setLastProgressAt(Instant.now());
            session = sessionRepository.save(session);
        }
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

            // Mechanism #13 (4B): Ground prompt with actual repository file tree (git ls-files)
            List<String> repoFiles = worktreeManager.listRepositoryFiles(worktreePath);
            if (repoFiles == null) {
                repoFiles = List.of();
            }
            String repoTreeBlock = buildRepositoryFileTreeBlock(repoFiles);
            List<String> declaredScope = fileScopeEnforcer.parseDeclaredScope(task.getFileScope());
            String rawScopedContent = worktreeManager.readScopedFiles(worktreePath, declaredScope);
            String scopedFilesContent = repoTreeBlock.isEmpty()
                    ? (rawScopedContent != null ? rawScopedContent : "")
                    : (rawScopedContent == null || rawScopedContent.isBlank() ? repoTreeBlock : repoTreeBlock + "\n\n" + rawScopedContent);

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

                // Duhem-Quine check 1: Scope Insufficient escalation & Mechanism #13 (4B) cross-stack classification
                if (response.scopeStatus() == ScopeStatus.SCOPE_INSUFFICIENT) {
                    boolean poisonedCrossStack = isCrossStackPoisonedScope(response.missingFiles(), repoFiles);
                    boolean allMissingNonExistent = areAllMissingFilesAbsent(response.missingFiles(), repoFiles);
                    String reason;
                    if (poisonedCrossStack) {
                        reason = "POISONED_CROSS_STACK_SCOPE_INSUFFICIENT: missing foreign-stack files "
                                + response.missingFiles() + " do not exist in repository";
                        session.setBelnapStatus(BelnapStatus.FALSE.name());
                    } else if (allMissingNonExistent) {
                        reason = "SCOPE_INSUFFICIENT (NON_EXISTENT_FILES): missing " + response.missingFiles();
                        session.setBelnapStatus(BelnapStatus.NEITHER.name());
                    } else {
                        reason = "SCOPE_INSUFFICIENT: missing " + response.missingFiles();
                        session.setBelnapStatus(BelnapStatus.NEITHER.name());
                    }
                    log.warn("Task {}: DeepSeek returned {}. Missing files: {}",
                            taskId, poisonedCrossStack ? "POISONED_CROSS_STACK_SCOPE_INSUFFICIENT" : "SCOPE_INSUFFICIENT",
                            response.missingFiles());
                    session.setStatus("scope_insufficient");
                    session.setClosureReason(reason);
                    session.setClosedAt(Instant.now());
                    session.setUpdatedAt(Instant.now());
                    sessionRepository.save(session);
                    return WorkerDispatchResult.failed(sessionId, reason, WorkerEngineType.DEEPSEEK_WORKTREE);
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
                // Epistemic Pulse: PR_OPENED (Mechanism #13, 4A: strictly honest PR publication, zero simulacra)
                String workerBranch = "worker/" + taskId;
                worktreeManager.commit(worktreePath, "feat(" + (task.getTitle() != null ? task.getTitle() : "worker") + "): implement task " + taskId);

                // Verify HEAD shares common ancestry with origin/<startingBranch> (reject orphan commits before push/PR)
                try {
                    worktreeManager.verifyCommonHistoryWithBase(worktreePath, startingBranch);
                } catch (Exception orphanEx) {
                    log.error("Task {}: Orphan history detected before PR creation: {}", taskId, orphanEx.getMessage());
                    if (task.getProject() != null) {
                        worktreeManager.resyncMirrorWithOriginMain(task.getProject());
                    }
                    String err = "PR_CREATION_FAILED: " + orphanEx.getMessage();
                    session.setPrUrl(null);
                    session.setStatus("pr_creation_failed");
                    session.setBelnapStatus(BelnapStatus.FALSE.name());
                    session.setClosureReason(err);
                    session.setClosedAt(Instant.now());
                    session.setUpdatedAt(Instant.now());
                    sessionRepository.save(session);
                    return WorkerDispatchResult.failed(sessionId, err, WorkerEngineType.DEEPSEEK_WORKTREE);
                }

                // Push MUST succeed; never swallow push failure
                worktreeManager.push(worktreePath, workerBranch);

                PrOpenOutcome prOutcome = openPullRequest(task, workerBranch, startingBranch);
                if (!prOutcome.created()) {
                    if (task.getProject() != null) {
                        worktreeManager.resyncMirrorWithOriginMain(task.getProject());
                    }
                    String err = "PR_CREATION_FAILED: " + prOutcome.errorReason();
                    session.setPrUrl(null);
                    session.setStatus("pr_creation_failed");
                    session.setBelnapStatus(BelnapStatus.FALSE.name());
                    session.setClosureReason(err);
                    session.setClosedAt(Instant.now());
                    session.setUpdatedAt(Instant.now());
                    sessionRepository.save(session);

                    log.error("DeepSeekWorktreeEngine: Task {} PR creation FAILED (honest D010 refusal, no fake URL): {}", taskId, err);
                    return WorkerDispatchResult.failed(sessionId, err, WorkerEngineType.DEEPSEEK_WORKTREE);
                }

                pullRequestUrl = prOutcome.url();
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
        if (task != null && task.getProject() != null
                && task.getProject().getDefaultBranch() != null
                && !task.getProject().getDefaultBranch().isBlank()) {
            return task.getProject().getDefaultBranch().trim();
        }
        return "main";
    }

    private record PrOpenOutcome(String url, String errorReason) {
        boolean created() {
            return url != null && !url.isBlank();
        }
    }

    private PrOpenOutcome openPullRequest(TaskEntity task, String workerBranch, String startingBranch) {
        if (task == null || task.getProject() == null) {
            return new PrOpenOutcome(null, "Task project is null; cannot create GitHub PR");
        }
        if (githubPrService == null) {
            return new PrOpenOutcome(null, "GitHubPullRequestService is unavailable");
        }
        try {
            var prOpt = githubPrService.createPullRequest(
                    task.getProject(),
                    workerBranch,
                    startingBranch,
                    "feat: " + (task.getTitle() != null ? task.getTitle() : "Task " + task.getId()),
                    "Autonomous implementation by Sovereign DeepSeek Worker.\nTask ID: " + task.getId()
            );
            if (prOpt != null && prOpt.isPresent() && prOpt.get().url() != null && !prOpt.get().url().isBlank()) {
                return new PrOpenOutcome(prOpt.get().url(), null);
            }
            var failureOpt = githubPrService.lastCreatePullRequestFailure();
            String detail = (failureOpt != null && failureOpt.isPresent())
                    ? failureOpt.get().summary()
                    : "GitHubPullRequestService.createPullRequest returned empty Optional for branch " + workerBranch + " -> " + startingBranch;
            return new PrOpenOutcome(null, detail);
        } catch (Exception e) {
            log.warn("Failed to create GitHub PR via API: {}", e.getMessage());
            return new PrOpenOutcome(null, "Exception calling GitHub createPullRequest: " + e.getMessage());
        }
    }

    static String buildRepositoryFileTreeBlock(List<String> repoFiles) {
        if (repoFiles == null || repoFiles.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("### REPOSITORY FILE TREE (git ls-files — use ONLY real package/directory paths from this tree):\n");
        int count = 0;
        for (String f : repoFiles) {
            if (count >= 120) {
                sb.append("... (truncated)\n");
                break;
            }
            sb.append("- ").append(f).append("\n");
            count++;
        }
        return sb.toString();
    }

    /**
     * Mechanism #13 (4B): Detects poisoned cross-stack SCOPE_INSUFFICIENT (e.g. missing *.py files
     * requested in a Java/Maven repository containing pom.xml and zero *.py files).
     */
    public static boolean isCrossStackPoisonedScope(List<String> missingFiles, List<String> repoFiles) {
        if (missingFiles == null || missingFiles.isEmpty() || repoFiles == null || repoFiles.isEmpty()) {
            return false;
        }
        if (!areAllMissingFilesAbsent(missingFiles, repoFiles)) {
            return false;
        }

        boolean repoHasPomOrGradle = false;
        boolean repoHasJava = false;
        boolean repoHasPython = false;
        boolean repoHasGo = false;
        boolean repoHasRust = false;
        for (String rf : repoFiles) {
            if (rf == null) continue;
            String lower = rf.trim().toLowerCase(java.util.Locale.ROOT);
            if (lower.equals("pom.xml") || lower.endsWith("/pom.xml") || lower.equals("build.gradle") || lower.equals("build.gradle.kts")) {
                repoHasPomOrGradle = true;
            }
            if (lower.endsWith(".java")) repoHasJava = true;
            if (lower.endsWith(".py")) repoHasPython = true;
            if (lower.endsWith(".go")) repoHasGo = true;
            if (lower.endsWith(".rs")) repoHasRust = true;
        }

        boolean missingHasPython = false;
        boolean missingHasJava = false;
        boolean missingHasGo = false;
        boolean missingHasRust = false;
        for (String mf : missingFiles) {
            if (mf == null) continue;
            String lower = mf.trim().toLowerCase(java.util.Locale.ROOT);
            if (lower.endsWith(".py")) missingHasPython = true;
            if (lower.endsWith(".java")) missingHasJava = true;
            if (lower.endsWith(".go")) missingHasGo = true;
            if (lower.endsWith(".rs")) missingHasRust = true;
        }

        if ((repoHasPomOrGradle || repoHasJava) && !repoHasPython && missingHasPython) {
            return true;
        }
        if ((repoHasPomOrGradle || repoHasJava) && !repoHasGo && missingHasGo) {
            return true;
        }
        if ((repoHasPomOrGradle || repoHasJava) && !repoHasRust && missingHasRust) {
            return true;
        }
        if (repoHasPython && !repoHasPomOrGradle && !repoHasJava && missingHasJava) {
            return true;
        }
        return false;
    }

    public static boolean areAllMissingFilesAbsent(List<String> missingFiles, List<String> repoFiles) {
        if (missingFiles == null || missingFiles.isEmpty()) {
            return false;
        }
        if (repoFiles == null || repoFiles.isEmpty()) {
            return true;
        }
        java.util.Set<String> exactPaths = new java.util.HashSet<>();
        java.util.Set<String> baseNames = new java.util.HashSet<>();
        for (String rf : repoFiles) {
            if (rf == null || rf.isBlank()) continue;
            String norm = rf.trim().replace('\\', '/').toLowerCase(java.util.Locale.ROOT);
            exactPaths.add(norm);
            int slash = norm.lastIndexOf('/');
            baseNames.add(slash >= 0 ? norm.substring(slash + 1) : norm);
        }
        for (String mf : missingFiles) {
            if (mf == null || mf.isBlank()) continue;
            String norm = mf.trim().replace('\\', '/').toLowerCase(java.util.Locale.ROOT);
            int slash = norm.lastIndexOf('/');
            String base = slash >= 0 ? norm.substring(slash + 1) : norm;
            if (exactPaths.contains(norm) || baseNames.contains(base)) {
                return false;
            }
        }
        return true;
    }

    private String truncateLogs(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}
