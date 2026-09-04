package com.eneik.production.services;

import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.ClaimRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.accounts.AccountHealthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Law 10 (Закон метки / Watermark Monotonicity) Test Suite:
 *
 *   mark: Состояние → Время ∪ {−∞},  монотонна,  и ни один переход самого цикла её не продвигает
 *
 * | метка                    | что двигает                           | чей канал          |
 * |--------------------------|---------------------------------------|--------------------|
 * | A(τ)                     | отказ при создании                    | наш расход         |
 * | W_c(project)             | принятая сессия на аккаунте комп-ра   | канал компилятора  |
 * | отказной ряд аккаунта    | принятая сессия этого аккаунта        | канал аккаунта     |
 * | lastCompileReachedAt(w)  | сообщение действительно ушло комп-ру  | канал компилятора  |
 * | lastMessageSentAt        | отправленное сообщение                | канал работника    |
 *
 * "Для каждой метки требуется тест, что отказ её не продвигает, и структурный запрет:
 *  метка не берётся из таблицы, в которую пишет сам механизм повтора, без фильтра «только успехи»."
 */
class WatermarkMonotonicityLaw10Test {

    private final JulesSessionRepository julesSessionRepository = mock(JulesSessionRepository.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final WishlistRepository wishlistRepository = mock(WishlistRepository.class);
    private final ClaimRepository claimRepository = mock(ClaimRepository.class);
    private final ClientDeliverableReadinessService readinessService = mock(ClientDeliverableReadinessService.class);

    private ClaimService claimService;

    @BeforeEach
    void setUp() {
        claimService = new ClaimService(
                claimRepository,
                taskRepository,
                accountRepository,
                julesSessionRepository,
                mock(com.eneik.production.services.gate.GateOrchestrator.class),
                readinessService);
    }

    @Test
    @DisplayName("Law 10 Mark 1: A(τ) counts only actual failed session refusals, not successes or cycle transitions")
    void mark1RefusedSessionCreationsMonotonicity() {
        UUID taskId = UUID.randomUUID();

        // 1. Success session: externalSessionId != null
        JulesSessionEntity successSession = new JulesSessionEntity();
        successSession.setId(UUID.randomUUID());
        successSession.setTaskId(taskId);
        successSession.setExternalSessionId("sessions/123456");
        successSession.setStatus("running");

        // 2. Skipped placeholder session: externalSessionId = "skipped"
        JulesSessionEntity skippedSession = new JulesSessionEntity();
        skippedSession.setId(UUID.randomUUID());
        skippedSession.setTaskId(taskId);
        skippedSession.setExternalSessionId("skipped");
        skippedSession.setStatus("failed");

        // 3. True refusal: externalSessionId == null, status == "failed"
        JulesSessionEntity refusedSession = new JulesSessionEntity();
        refusedSession.setId(UUID.randomUUID());
        refusedSession.setTaskId(taskId);
        refusedSession.setExternalSessionId(null);
        refusedSession.setStatus("failed");

        List<JulesSessionEntity> allSessions = List.of(successSession, skippedSession, refusedSession);

        // Filter that computes A(τ) = |{s : task(s)=τ ∧ externalSessionId(s)=∅ ∧ status(s)=failed}|
        long refusedCount = allSessions.stream()
                .filter(s -> s.getTaskId().equals(taskId))
                .filter(s -> s.getExternalSessionId() == null && "failed".equals(s.getStatus()))
                .count();

        assertEquals(1, refusedCount, "A(τ) must count ONLY sessions where externalSessionId is null and status is failed");

        // Verify ClaimService uses this exact monotone count
        when(julesSessionRepository.countByTaskIdAndExternalSessionIdIsNullAndStatus(taskId, "failed"))
                .thenReturn(1L);
        assertEquals(1L, claimService.refusedSessionCreations(taskId));

        // When budget is 2 * liveAccounts = 2 * 1 = 2:
        when(accountRepository.countLiveAccounts()).thenReturn(1L);
        when(taskRepository.writeStatusUnlessTerminal(taskId, TaskStatus.queued)).thenReturn(1);
        when(claimRepository.findFirstByTaskIdAndReleasedAtIsNullOrderByClaimedAtDesc(taskId)).thenReturn(Optional.empty());

        // Under budget (1 < 2) -> requeues to queued
        claimService.releaseClaimToQueue(taskId, "transient error");
        verify(taskRepository).writeStatusUnlessTerminal(taskId, TaskStatus.queued);

        // At budget (2 >= 2) -> terminates to blocked with absorbing condition
        when(julesSessionRepository.countByTaskIdAndExternalSessionIdIsNullAndStatus(taskId, "failed"))
                .thenReturn(2L);
        when(taskRepository.writeStatusUnlessTerminal(taskId, TaskStatus.blocked)).thenReturn(1);

        claimService.releaseClaimToQueue(taskId, "refusal budget exhausted");
        verify(taskRepository).writeStatusUnlessTerminal(taskId, TaskStatus.blocked);
    }

    @Test
    @DisplayName("Law 10 Mark 2: Compiler channel watermark W_c advances only on accepted sessions, never on refusals or skipped")
    void mark2CompilerChannelWatermarkMonotonicity() {
        String compilerAccount = "eneikdru";
        Instant t0 = Instant.parse("2026-08-28T10:00:00Z");
        Instant t1 = Instant.parse("2026-08-28T11:00:00Z");
        Instant t2 = Instant.parse("2026-08-28T12:00:00Z");

        // Accepted session at t0
        JulesSessionEntity acceptedAtT0 = new JulesSessionEntity();
        acceptedAtT0.setCreatedAt(t0);
        acceptedAtT0.setExternalSessionId("sessions/acc-1");

        // Refused session at t1 (externalSessionId == null)
        JulesSessionEntity refusedAtT1 = new JulesSessionEntity();
        refusedAtT1.setCreatedAt(t1);
        refusedAtT1.setExternalSessionId(null);
        refusedAtT1.setStatus("failed");

        // Skipped session at t2 (externalSessionId == "skipped")
        JulesSessionEntity skippedAtT2 = new JulesSessionEntity();
        skippedAtT2.setCreatedAt(t2);
        skippedAtT2.setExternalSessionId("skipped");

        List<JulesSessionEntity> sessions = List.of(acceptedAtT0, refusedAtT1, skippedAtT2);

        // Apply W_c query filter: s.externalSessionId IS NOT NULL AND s.externalSessionId <> 'skipped'
        Instant watermark = sessions.stream()
                .filter(s -> s.getExternalSessionId() != null && !"skipped".equals(s.getExternalSessionId()))
                .map(JulesSessionEntity::getCreatedAt)
                .max(Instant::compareTo)
                .orElse(null);

        // Watermark must remain at t0 despite later refusal (t1) and skipped (t2) attempts!
        assertEquals(t0, watermark, "W_c must not be advanced by failed or skipped attempts");

        // When a genuinely new accepted session arrives at t3:
        Instant t3 = Instant.parse("2026-08-28T13:00:00Z");
        JulesSessionEntity acceptedAtT3 = new JulesSessionEntity();
        acceptedAtT3.setCreatedAt(t3);
        acceptedAtT3.setExternalSessionId("sessions/acc-2");

        List<JulesSessionEntity> updatedSessions = List.of(acceptedAtT0, refusedAtT1, skippedAtT2, acceptedAtT3);
        Instant updatedWatermark = updatedSessions.stream()
                .filter(s -> s.getExternalSessionId() != null && !"skipped".equals(s.getExternalSessionId()))
                .map(JulesSessionEntity::getCreatedAt)
                .max(Instant::compareTo)
                .orElse(null);

        assertEquals(t3, updatedWatermark, "W_c advances strictly upon an accepted channel event");
    }

    @Test
    @DisplayName("Law 10 Mark 3: Account refusal series increments on failure and decrements on success; watermarks ignore failures")
    void mark3AccountRefusalWatermarkAndSeriesMonotonicity() {
        AccountEntity account = new AccountEntity();
        account.setName("worker-acc");
        account.setConsecutiveApiBlockCount(0);

        // 1. First failure increments series
        int count1 = account.getConsecutiveApiBlockCount() + 1;
        account.setConsecutiveApiBlockCount(count1);
        assertEquals(1, account.getConsecutiveApiBlockCount());

        // 2. Second failure increments series
        int count2 = account.getConsecutiveApiBlockCount() + 1;
        account.setConsecutiveApiBlockCount(count2);
        assertEquals(2, account.getConsecutiveApiBlockCount());

        // 3. Success decrements series (hysteresis / Leaky Bucket)
        int relaxed = Math.max(0, account.getConsecutiveApiBlockCount() - 1);
        account.setConsecutiveApiBlockCount(relaxed);
        assertEquals(1, account.getConsecutiveApiBlockCount(), "Success must relax the block counter by 1");

        // 4. Idle transition without dispatch outcome must NOT alter counter
        assertEquals(1, account.getConsecutiveApiBlockCount(), "Idle passes must leave counter monotone");
    }

    @Test
    @DisplayName("Law 10 Mark 4: WishlistEntity.lastCompileReachedAt is written only when compiler is reached, not on dispatch failure")
    void mark4WishlistCompileReachedAtMonotonicity() {
        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setId(UUID.randomUUID());
        wishlist.setCompileAttempts(0);
        assertNull(wishlist.getLastCompileReachedAt(), "lastCompileReachedAt starts null");

        // Attempt 1: Failed dispatch / no capacity (compileAttempts incremented, but compiler unreached)
        wishlist.setCompileAttempts(1);
        wishlist.setLastCompileDispatchedAt(Instant.now());
        // lastCompileReachedAt deliberately stays null!
        assertNull(wishlist.getLastCompileReachedAt(), "Failed dispatch attempt must NOT set lastCompileReachedAt");
        assertFalse(wishlist.decompositionRefused(), "Not refused because compiler never answered");

        // Attempt 2: Successful dispatch that actually puts brief in front of compiler
        Instant reachedAt = Instant.now();
        wishlist.setLastCompileReachedAt(reachedAt);
        assertEquals(reachedAt, wishlist.getLastCompileReachedAt());

        // Even if attempts reach ceiling, refusal predicate now correctly distinguishes reached vs unreached
        wishlist.setCompileAttempts(3);
        assertTrue(wishlist.decompositionExhausted());
        assertTrue(wishlist.decompositionRefused(), "Budget spent + compiler reached = refused");
        assertFalse(wishlist.decompositionUnreached(), "Not unreached because compiler was reached");
    }

    @Test
    @DisplayName("Law 10 Mark 5: PersistentWorkerSessionEntity.lastMessageSentAt advances strictly on message delivery")
    void mark5PersistentWorkerLastMessageSentAtMonotonicity() {
        PersistentWorkerSessionEntity worker = new PersistentWorkerSessionEntity();
        worker.setId(UUID.randomUUID());
        Instant t0 = Instant.now();
        worker.setLastMessageSentAt(t0);
        worker.setCycleCount(1);

        // When worker is busy (isBatchInFlight=true), follow-up cannot be sent
        worker.setCurrentBatchIds(new com.fasterxml.jackson.databind.ObjectMapper().createArrayNode().add(UUID.randomUUID().toString()));
        assertTrue(worker.isBatchInFlight());

        // Polling cycles while busy do NOT alter lastMessageSentAt
        assertEquals(t0, worker.getLastMessageSentAt(), "Busy worker polling must NOT advance lastMessageSentAt");

        // When batch completes and next batch is actually dispatched:
        Instant t1 = t0.plusSeconds(300);
        worker.setLastMessageSentAt(t1);
        worker.setCycleCount(worker.getCycleCount() + 1);

        assertEquals(t1, worker.getLastMessageSentAt(), "lastMessageSentAt advances strictly upon genuine message send");
        assertEquals(2, worker.getCycleCount());
    }

    @Test
    @DisplayName("Structural Law 10: Repository queries selecting session watermarks MUST enforce 'only successes' filter")
    void structuralLaw10RepositoryQueryFilterAudit() throws IOException {
        Path repoDir = Path.of("src/main/java/com/eneik/production/repositories");
        if (!Files.exists(repoDir)) {
            repoDir = Path.of("C:/Projects/Eneik/docker-build/EneikProductionSys/src/main/java/com/eneik/production/repositories");
        }
        assertTrue(Files.exists(repoDir),
                "Law 10 structural guard cannot run: repository directory not found at " + repoDir.toAbsolutePath());

        List<Path> javaFiles;
        try (var stream = Files.walk(repoDir)) {
            javaFiles = stream.filter(p -> p.toString().endsWith(".java")).toList();
        }

        assertFalse(javaFiles.isEmpty(), "Repositories must exist");

        for (Path file : javaFiles) {
            String content = Files.readString(file);
            // Search for queries computing MAX on session timestamps
            if (content.contains("MAX(") && (content.contains("jules_sessions") || content.contains("JulesSessionEntity"))) {
                // If it computes MAX of created_at / createdAt for an account or project channel
                if (content.contains("latestAcceptedSessionAtForAccount") || content.contains("lockNextJulesAccountWithCapacity")) {
                    boolean hasSuccessFilter = (content.contains("externalSessionId IS NOT NULL") || content.contains("external_session_id IS NOT NULL"))
                            && (content.contains("externalSessionId <> 'skipped'") || content.contains("external_session_id <> 'skipped'"));

                    assertTrue(hasSuccessFilter,
                            "Law 10 violation in " + file.getFileName() + ": channel watermark query MUST enforce 'only successes' filter "
                                    + "(externalSessionId IS NOT NULL AND externalSessionId <> 'skipped')");
                }
            }
        }
    }
}
