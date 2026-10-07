package com.eneik.production.services.dashboard;

import com.eneik.production.dto.dashboard.BottleneckDto;
import com.eneik.production.dto.dashboard.ExpiredStatDto;
import com.eneik.production.dto.dashboard.QueueDashboardDto;
import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.AccountStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.ClaimRepository;
import com.eneik.production.repositories.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Popperian Falsification Test Harness for BottleneckDetectionService (Stage 4).
 *
 * <p>Philosopher Anchors:
 * 1. ELVIN_GOLDMAN_01_RELIABILITY_CHAIN [D010, Goldman]:
 *    Causal reliabilism in evidence gathering: pool capacity summary acquired strictly ONCE per
 *    detect cycle, preventing state skew and redundant N-query churn across rows.
 *    Fact vs dwell window (Law 8): structurally depleted pool (working accounts = 0) establishes
 *    zero capacity as a physical fact and reports immediately without waiting for dwell threshold.
 *
 * 2. NUEL_BELNAP_03_TRUTH_STATUS_TABLE [D012, Belnap]:
 *    Multi-valued truth lattice of degradation causes: daily_limited, api_blocked, and disabled
 *    are distinct causal conditions and must never be conflated. Decommissioned accounts are
 *    retired entities, not disabled operational capacity.
 *
 * 3. FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK [D011, Dretske]:
 *    Teleosemantic information flow: bottleneck signals faithfully carry causal origin (tag,
 *    queued count, waiting time, account ID, expired count) to the dashboard and priority services.
 *    Project-scoped detection restricts evidence strictly to the designated project source.
 */
class BottleneckDetectionServiceFalsificationTest {

    private TaskRepository taskRepository;
    private AccountRepository accountRepository;
    private ClaimRepository claimRepository;
    private BottleneckDetectionService service;

    @BeforeEach
    void setUp() {
        taskRepository = mock(TaskRepository.class);
        accountRepository = mock(AccountRepository.class);
        claimRepository = mock(ClaimRepository.class);

        service = new BottleneckDetectionService(taskRepository, accountRepository, claimRepository);
        ReflectionTestUtils.setField(service, "maxConcurrentJulesSessionsPerAccount", 3);

        when(claimRepository.expiredCountByAccountSince(any(Instant.class)))
                .thenReturn(Collections.emptyList());
    }

    private AccountEntity createAccount(String name, AccountStatus status, boolean enabled) {
        AccountEntity account = new AccountEntity();
        account.setId(UUID.randomUUID());
        account.setName(name);
        account.setStatus(status);
        account.setEnabled(enabled);
        return account;
    }

    // =========================================================================
    // 1. ELVIN_GOLDMAN_01_RELIABILITY_CHAIN [D010, Goldman]
    // =========================================================================

    @Test
    @DisplayName("Goldman D010: Single-pass evidence collection acquires pool state once regardless of queued tags")
    void falsifyGoldmanReliabilityChain_singlePassEvidencePreventsSkewAndRedundantReads() {
        // Given 5 queued tags
        List<QueueDashboardDto.TagCountDto> rows = List.of(
                new QueueDashboardDto.TagCountDto("TAG_1", 2L, 5L),
                new QueueDashboardDto.TagCountDto("TAG_2", 3L, 8L),
                new QueueDashboardDto.TagCountDto("TAG_3", 1L, 2L),
                new QueueDashboardDto.TagCountDto("TAG_4", 4L, 12L),
                new QueueDashboardDto.TagCountDto("TAG_5", 5L, 15L)
        );
        when(taskRepository.queuedGroupedByTag()).thenReturn(rows);
        when(accountRepository.existsJulesAccountWithCapacity(any(), anyInt())).thenReturn(true);
        when(accountRepository.findAllByOrderByNameAsc()).thenReturn(List.of());

        service.detect();

        // Must query account pool exactly 1 time across the entire detect cycle
        verify(accountRepository, times(1)).findAllByOrderByNameAsc();
        verify(accountRepository, never()).findAll();
    }

    @Test
    @DisplayName("Goldman D010 / Law 8: Structurally depleted pool triggers immediate bottleneck without dwell delay")
    void falsifyGoldmanReliabilityChain_structurallyDepletedPoolTriggersImmediateBottleneckWithoutDwellTime() {
        String tag = "BARCAN-TAG-10";
        // Task has only been waiting 2 minutes (below standard 10-minute threshold)
        QueueDashboardDto.TagCountDto row = new QueueDashboardDto.TagCountDto(tag, 3L, 2L);
        when(taskRepository.queuedGroupedByTag()).thenReturn(List.of(row));
        when(accountRepository.existsJulesAccountWithCapacity(eq(tag), anyInt())).thenReturn(false);

        // Account pool exists but is structurally depleted: 1 daily_limited, 1 api_blocked (working accounts = 0)
        AccountEntity dailyAcc = createAccount("acc-daily", AccountStatus.daily_limited, true);
        AccountEntity blockedAcc = createAccount("acc-blocked", AccountStatus.api_blocked, true);
        when(accountRepository.findAllByOrderByNameAsc()).thenReturn(List.of(dailyAcc, blockedAcc));

        List<BottleneckDto> bottlenecks = service.detect();

        // Must report bottleneck immediately because zero capacity is an established physical fact
        assertEquals(1, bottlenecks.size(),
                "Falsification: structurally depleted pool did not establish immediate bottleneck fact!");
        BottleneckDto bottleneck = bottlenecks.get(0);
        assertEquals("no_free_jules_slot", bottleneck.type());
        assertEquals(tag, bottleneck.tag());
        assertTrue(bottleneck.reason().contains("daily_limited=1"));
        assertTrue(bottleneck.reason().contains("api_blocked=1"));
    }

    // =========================================================================
    // 2. NUEL_BELNAP_03_TRUTH_STATUS_TABLE [D012, Belnap]
    // =========================================================================

    @Test
    @DisplayName("Belnap D012: Distinct capacity reduction causes are explicitly differentiated in diagnostic reason")
    void falsifyBelnapTruthStatusTable_distinctCapacityReductionReasonsNeverConflated() {
        String tag = "CORE_FEATURE";
        QueueDashboardDto.TagCountDto row = new QueueDashboardDto.TagCountDto(tag, 5L, 20L);
        when(taskRepository.queuedGroupedByTag()).thenReturn(List.of(row));
        when(accountRepository.existsJulesAccountWithCapacity(eq(tag), anyInt())).thenReturn(false);

        // Heterogeneous degraded pool: 1 daily_limited, 2 api_blocked, 1 disabled operational
        AccountEntity a1 = createAccount("acc-1", AccountStatus.daily_limited, true);
        AccountEntity a2 = createAccount("acc-2", AccountStatus.api_blocked, true);
        AccountEntity a3 = createAccount("acc-3", AccountStatus.api_blocked, true);
        AccountEntity a4 = createAccount("acc-4", AccountStatus.idle, false); // disabled operational

        when(accountRepository.findAllByOrderByNameAsc()).thenReturn(List.of(a1, a2, a3, a4));

        List<BottleneckDto> bottlenecks = service.detect();

        assertEquals(1, bottlenecks.size());
        String reason = bottlenecks.get(0).reason();

        // Verify that Belnap truth status lattice preserves each condition
        assertTrue(reason.contains("daily_limited=1"), "Falsification: daily_limited count lost or conflated!");
        assertTrue(reason.contains("api_blocked=2"), "Falsification: api_blocked count lost or conflated!");
        assertTrue(reason.contains("disabled=1"), "Falsification: disabled count lost or conflated!");
        assertTrue(reason.contains("api_blocked is not a daily limit"), "Falsification: api_blocked diagnostic advice missing!");
        assertTrue(reason.contains("operational account(s) are disabled"), "Falsification: disabled diagnostic advice missing!");
    }

    @Test
    @DisplayName("Belnap D012: Decommissioned accounts are excluded from disabled operational capacity count")
    void falsifyBelnapTruthStatusTable_decommissionedAccountsExcludedFromDisabledCount() {
        String tag = "CORE_FEATURE";
        QueueDashboardDto.TagCountDto row = new QueueDashboardDto.TagCountDto(tag, 1L, 15L);
        when(taskRepository.queuedGroupedByTag()).thenReturn(List.of(row));
        when(accountRepository.existsJulesAccountWithCapacity(eq(tag), anyInt())).thenReturn(false);

        // Account is decommissioned (retired), enabled=false
        AccountEntity retired = createAccount("acc-retired", AccountStatus.decommissioned, false);
        // Working account busy
        AccountEntity working = createAccount("acc-working", AccountStatus.busy, true);
        when(accountRepository.findAllByOrderByNameAsc()).thenReturn(List.of(retired, working));

        List<BottleneckDto> bottlenecks = service.detect();

        assertEquals(1, bottlenecks.size());
        String reason = bottlenecks.get(0).reason();

        // Decommissioned account MUST NOT appear as 'disabled=1' or trigger disabled warning
        assertFalse(reason.contains("disabled=1"),
                "Falsification: decommissioned account was falsely counted as disabled operational capacity!");
        assertFalse(reason.contains("operational account(s) are disabled"),
                "Falsification: decommissioned account triggered operational disabled alert!");
    }

    // =========================================================================
    // 3. FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK [D011, Dretske]
    // =========================================================================

    @Test
    @DisplayName("Dretske D011: Expired lease spike conveys account ID and spike magnitude above threshold")
    void falsifyDretskeTeleosemanticFeedback_expiredLeaseSpikeCarriesAccountIdAndCount() {
        UUID accountIdSpike = UUID.randomUUID();
        UUID accountIdNormal = UUID.randomUUID();

        // accountIdSpike has 6 expired leases (> threshold 5); accountIdNormal has 5 (at threshold)
        List<ExpiredStatDto> stats = List.of(
                new ExpiredStatDto(accountIdSpike, 6L),
                new ExpiredStatDto(accountIdNormal, 5L)
        );

        when(taskRepository.queuedGroupedByTag()).thenReturn(Collections.emptyList());
        when(claimRepository.expiredCountByAccountSince(any(Instant.class))).thenReturn(stats);
        when(accountRepository.findAllByOrderByNameAsc()).thenReturn(Collections.emptyList());

        List<BottleneckDto> bottlenecks = service.detect();

        // Exactly 1 spike bottleneck emitted
        assertEquals(1, bottlenecks.size(),
                "Falsification: lease spike threshold check failed to isolate genuine spike!");
        BottleneckDto spikeDto = bottlenecks.get(0);
        assertEquals("expired_lease_spike", spikeDto.type());
        assertEquals(accountIdSpike, spikeDto.accountId());
        assertEquals(6L, spikeDto.expiredCount24h());
        assertTrue(spikeDto.reason().contains("repeated expired leases"));
    }

    @Test
    @DisplayName("Dretske D011: Project-scoped detect restricts queued tag query strictly to project scope")
    void falsifyDretskeTeleosemanticFeedback_projectScopedDetectRestrictsQueuedTagScope() {
        UUID projectId = UUID.randomUUID();
        when(taskRepository.queuedGroupedByProjectAndTag(projectId)).thenReturn(Collections.emptyList());
        when(accountRepository.findAllByOrderByNameAsc()).thenReturn(Collections.emptyList());

        service.detect(projectId);

        // Must invoke project-scoped query and NEVER global query
        verify(taskRepository, times(1)).queuedGroupedByProjectAndTag(projectId);
        verify(taskRepository, never()).queuedGroupedByTag();
    }
}
