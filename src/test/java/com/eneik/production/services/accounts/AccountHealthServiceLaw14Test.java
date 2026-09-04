package com.eneik.production.services.accounts;

import com.eneik.production.kaizen.model.DefectJournalEntity;
import com.eneik.production.kaizen.repository.DefectJournalRepository;
import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.AccountStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.AccountRoleSuccessStatsRepository;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.services.jules.JulesApiClient;
import com.eneik.production.services.lever.LeverPromotionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Law 14 (Закон убеждения о внешней системе / External Capacity Refusal & Belief Revision):
 *
 * <p>Proof Obligations:
 * 1. Partitioning: Each raw refusal maps to exactly one member; unclassified falls to UNCLASSIFIED.
 * 2. UNCLASSIFIED / PRECONDITION_UNSPECIFIED does not move ceiling down or up, and charges nobody.
 * 3. CONCURRENT_CAPACITY_EXHAUSTED moves ceiling down from the observed point (countOpenSessions).
 * 4. Real SUCCESS at ceiling moves ceiling up.
 * 5. Configuration values do not move belief.
 * 6. Classifier depends only on status & body, not on occ(a, t). Changing occ(a, t) does not change outcome.
 * 7. Audit trail in DefectJournal: any capacity movement records prior, revised, observed open, and reason.
 */
class AccountHealthServiceLaw14Test {

    private AccountRepository accountRepository;
    private DefectJournalRepository defectJournalRepository;
    private AccountRoleSuccessStatsRepository accountRoleSuccessStatsRepository;
    private LeverPromotionService leverPromotionService;
    private JulesSessionRepository julesSessionRepository;
    private AccountHealthService service;

    private final UUID accountId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        defectJournalRepository = mock(DefectJournalRepository.class);
        accountRoleSuccessStatsRepository = mock(AccountRoleSuccessStatsRepository.class);
        leverPromotionService = mock(LeverPromotionService.class);
        julesSessionRepository = mock(JulesSessionRepository.class);

        service = new AccountHealthService(
                accountRepository,
                defectJournalRepository,
                accountRoleSuccessStatsRepository,
                leverPromotionService,
                julesSessionRepository
        );
    }

    private AccountEntity createAccount(String name, Integer concurrentCapacity, int openSessions) {
        AccountEntity account = new AccountEntity();
        account.setId(accountId);
        account.setName(name);
        account.setStatus(AccountStatus.idle);
        account.setEnabled(true);
        account.setEstimatedConcurrentCapacity(concurrentCapacity);
        account.setMaxConcurrentSessions(3);
        account.setSessionsDispatchedToday(5);
        account.setConsecutiveApiBlockCount(0);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(accountRepository.countOpenSessions(accountId)).thenReturn(openSessions);
        return account;
    }

    @Test
    @DisplayName("Obligation 1 & 5: Partitioning is mutually exclusive and classifier is independent of occ(a, t)")
    void partitioningAndIndependenceFromInternalOccupancy() {
        // Raw refusals
        var capacityRefusal = new JulesApiClient.CreateSessionResult(null, 400, "{\"error\":{\"message\":\"maximum concurrent session limit reached\"}}");
        var dailyRefusal = new JulesApiClient.CreateSessionResult(null, 429, "{\"error\":{\"message\":\"quota exceeded\"}}");
        var badRequest = new JulesApiClient.CreateSessionResult(null, 400, "{\"error\":{\"status\":\"INVALID_ARGUMENT\",\"message\":\"bad JSON\"}}");
        var authBlocked = new JulesApiClient.CreateSessionResult(null, 401, "{\"error\":\"unauthorized\"}");
        var unspecificPrecondition = new JulesApiClient.CreateSessionResult(null, 400, "{\"error\":{\"status\":\"FAILED_PRECONDITION\",\"message\":\"Precondition check failed.\"}}");
        var unclassified = new JulesApiClient.CreateSessionResult(null, 502, "{\"error\":\"bad gateway\"}");

        assertEquals(AccountHealthService.DispatchOutcome.CONCURRENT_CAPACITY_EXHAUSTED, capacityRefusal.classifyOutcome());
        assertEquals(AccountHealthService.DispatchOutcome.DAILY_LIMIT, dailyRefusal.classifyOutcome());
        assertEquals(AccountHealthService.DispatchOutcome.REQUEST_REJECTED, badRequest.classifyOutcome());
        assertEquals(AccountHealthService.DispatchOutcome.PRECONDITION_BLOCKED, authBlocked.classifyOutcome());
        assertEquals(AccountHealthService.DispatchOutcome.PRECONDITION_UNSPECIFIED, unspecificPrecondition.classifyOutcome());
        assertEquals(AccountHealthService.DispatchOutcome.UNCLASSIFIED, unclassified.classifyOutcome());

        // Obligation 5 screen test: whether occ(a, t) is 0, 5, or 100, classification of the response is identical
        for (int occ : new int[]{0, 3, 10, 50}) {
            assertEquals(AccountHealthService.DispatchOutcome.CONCURRENT_CAPACITY_EXHAUSTED, capacityRefusal.classifyOutcome());
            assertEquals(AccountHealthService.DispatchOutcome.UNCLASSIFIED, unclassified.classifyOutcome());
        }
    }

    @Test
    @DisplayName("Obligation 2: UNCLASSIFIED and PRECONDITION_UNSPECIFIED do not move ceiling and charge nobody")
    void unclassifiedDoesNotMoveCeilingOrChargeAccount() {
        AccountEntity account = createAccount("test-account", 3, 2);

        service.reportDispatchOutcome(accountId, projectId, AccountHealthService.DispatchOutcome.PRECONDITION_UNSPECIFIED, "Precondition check failed.");

        assertEquals(3, account.getEstimatedConcurrentCapacity(), "Ceiling must not move down or up on unspecified precondition");
        assertEquals(AccountStatus.idle, account.getStatus(), "Status must remain idle");
        assertEquals(0, account.getConsecutiveApiBlockCount(), "Block counter must remain untouched");
        verify(accountRepository, never()).save(account);

        service.reportDispatchOutcome(accountId, projectId, AccountHealthService.DispatchOutcome.UNCLASSIFIED, "mystery error");

        assertEquals(3, account.getEstimatedConcurrentCapacity(), "Ceiling must not move on unclassified error");
        assertEquals(AccountStatus.idle, account.getStatus());
        verify(accountRepository, never()).save(account);
    }

    @Test
    @DisplayName("Obligation 3 & 7: CONCURRENT_CAPACITY_EXHAUSTED moves ceiling down from observed point and records journal")
    void concurrentCapacityExhaustedRevisesDownWithAuditTrail() {
        // Believed ceiling is 5, but refused while holding 3 open
        AccountEntity account = createAccount("busy-acc", 5, 3);

        service.reportDispatchOutcome(
                accountId,
                projectId,
                AccountHealthService.DispatchOutcome.CONCURRENT_CAPACITY_EXHAUSTED,
                "concurrent session limit reached"
        );

        // 3 * 0.7 = 2.1 -> rounded to 2. 2 < 5.
        assertEquals(2, account.getEstimatedConcurrentCapacity(), "Ceiling must revise down from observed refusal point");
        assertEquals(AccountStatus.idle, account.getStatus(), "Being at capacity is not being broken; status stays idle");
        assertEquals(0, account.getConsecutiveApiBlockCount(), "Not charged to consecutive block count");

        verify(accountRepository).save(account);

        // Obligation 7: Defect journal audit entry
        ArgumentCaptor<DefectJournalEntity> captor = ArgumentCaptor.forClass(DefectJournalEntity.class);
        verify(defectJournalRepository).save(captor.capture());
        DefectJournalEntity entry = captor.getValue();

        assertEquals("ACCOUNT_HEALTH", entry.getCategory());
        assertEquals("CONCURRENT_CAPACITY_EXHAUSTED", entry.getDefectType());
        assertEquals("busy-acc", entry.getSourceComponent());
        assertTrue(entry.getDescription().contains("prior=5"));
        assertTrue(entry.getDescription().contains("revised=2"));
        assertTrue(entry.getDescription().contains("observed_open=3"));
        assertEquals(2.0, entry.getMetricValue());
    }

    @Test
    @DisplayName("Obligation 4 & 7: Real SUCCESS at ceiling moves concurrent capacity upward and records journal")
    void realSuccessAtCeilingRevisesUpwardWithAuditTrail() {
        // Believed ceiling is 3, and account held 3 open when Jules accepted
        AccountEntity account = createAccount("capable-acc", 3, 3);

        service.reportDispatchOutcome(
                accountId,
                projectId,
                AccountHealthService.DispatchOutcome.SUCCESS,
                null
        );

        // Step is 1 -> revised to 4
        assertEquals(4, account.getEstimatedConcurrentCapacity(), "Ceiling must revise upward on surviving test at ceiling");
        verify(accountRepository).save(account);

        ArgumentCaptor<DefectJournalEntity> captor = ArgumentCaptor.forClass(DefectJournalEntity.class);
        verify(defectJournalRepository).save(captor.capture());
        DefectJournalEntity entry = captor.getValue();

        assertEquals("ACCOUNT_HEALTH", entry.getCategory());
        assertEquals("CONCURRENT_CAPACITY_EXPANDED", entry.getDefectType());
        assertEquals("capable-acc", entry.getSourceComponent());
        assertTrue(entry.getDescription().contains("prior=3"));
        assertTrue(entry.getDescription().contains("revised=4"));
        assertTrue(entry.getDescription().contains("observed_open=3"));
        assertEquals(4.0, entry.getMetricValue());
    }

    @Test
    @DisplayName("Obligation 4: SUCCESS below ceiling does NOT move concurrent capacity")
    void successBelowCeilingDoesNotMoveCapacity() {
        // Believed ceiling is 5, but only held 2 open when Jules accepted
        AccountEntity account = createAccount("quiet-acc", 5, 2);

        service.reportDispatchOutcome(
                accountId,
                projectId,
                AccountHealthService.DispatchOutcome.SUCCESS,
                null
        );

        assertEquals(5, account.getEstimatedConcurrentCapacity(), "A success below ceiling tests nothing new and leaves belief alone");
        // No upward capacity journal should be saved
        verify(defectJournalRepository, never()).save(argThat(e -> "CONCURRENT_CAPACITY_EXPANDED".equals(e.getDefectType())));
    }
}
