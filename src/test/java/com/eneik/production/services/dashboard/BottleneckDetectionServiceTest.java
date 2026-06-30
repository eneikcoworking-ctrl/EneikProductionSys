package com.eneik.production.services.dashboard;

import com.eneik.production.dto.dashboard.BottleneckDto;
import com.eneik.production.dto.dashboard.ExpiredStatDto;
import com.eneik.production.dto.dashboard.QueueDashboardDto;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.ClaimRepository;
import com.eneik.production.repositories.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class BottleneckDetectionServiceTest {

    @Mock
    private TaskRepository taskRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private ClaimRepository claimRepository;

    private BottleneckDetectionService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new BottleneckDetectionService(taskRepository, accountRepository, claimRepository);
    }

    @Test
    void detectNoCapableAgent() {
        String tag = "BARCAN-TAG-08";
        QueueDashboardDto.TagCountDto row = new QueueDashboardDto.TagCountDto(tag, 4L, 23L);
        when(taskRepository.queuedGroupedByTag()).thenReturn(List.of(row));
        when(accountRepository.existsOnlineWithCapability(eq(tag), any(Instant.class))).thenReturn(false);

        List<BottleneckDto> bottlenecks = service.detect();

        assertEquals(1, bottlenecks.size());
        assertEquals("no_capable_agent", bottlenecks.get(0).type());
        assertEquals(tag, bottlenecks.get(0).tag());
        assertEquals(4L, bottlenecks.get(0).queuedCount());
    }

    @Test
    void detectExpiredLeaseSpike() {
        UUID accountId = UUID.randomUUID();
        ExpiredStatDto stat = new ExpiredStatDto(accountId, 6L);
        when(taskRepository.queuedGroupedByTag()).thenReturn(List.of());
        when(claimRepository.expiredCountByAccountSince(any(Instant.class))).thenReturn(List.of(stat));

        List<BottleneckDto> bottlenecks = service.detect();

        assertEquals(1, bottlenecks.size());
        assertEquals("expired_lease_spike", bottlenecks.get(0).type());
        assertEquals(accountId, bottlenecks.get(0).accountId());
        assertEquals(6L, bottlenecks.get(0).expiredCount24h());
    }
}
