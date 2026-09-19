package com.eneik.production.controllers.dashboard;

import com.eneik.production.dto.dashboard.AgentDashboardDto;
import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.AccountStatus;
import com.eneik.production.models.persistence.ClaimEntity;
import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.ClaimRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.dashboard.BottleneckDetectionService;
import com.eneik.production.services.dashboard.TaskWaitTimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DashboardControllerTest {

    private AccountRepository accountRepository;
    private ClaimRepository claimRepository;
    private TaskRepository taskRepository;
    private BottleneckDetectionService bottleneckDetectionService;
    private TaskWaitTimeService taskWaitTimeService;
    private DashboardController controller;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        claimRepository = mock(ClaimRepository.class);
        taskRepository = mock(TaskRepository.class);
        bottleneckDetectionService = mock(BottleneckDetectionService.class);
        taskWaitTimeService = mock(TaskWaitTimeService.class);

        controller = new DashboardController(
                accountRepository,
                claimRepository,
                taskRepository,
                bottleneckDetectionService,
                taskWaitTimeService
        );
    }

    @Test
    @DisplayName("getAgents uses findAllByOrderByNameAsc and batch resolves active claims")
    void getAgentsUsesOrderedAccountsAndBatchActiveClaims() {
        UUID accountId1 = UUID.randomUUID();
        AccountEntity acc1 = new AccountEntity();
        acc1.setId(accountId1);
        acc1.setName("account-alpha");
        acc1.setStatus(AccountStatus.busy);
        acc1.setLastHeartbeat(Instant.now());

        UUID accountId2 = UUID.randomUUID();
        AccountEntity acc2 = new AccountEntity();
        acc2.setId(accountId2);
        acc2.setName("account-beta");
        acc2.setStatus(AccountStatus.idle);
        acc2.setLastHeartbeat(Instant.now());

        when(accountRepository.findAllByOrderByNameAsc()).thenReturn(List.of(acc1, acc2));

        RoleEntity role = new RoleEntity();
        role.setTag("BACKEND-01");

        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setTitle("Fix pipeline barrier");

        ClaimEntity claim = new ClaimEntity();
        claim.setId(UUID.randomUUID());
        claim.setAccount(acc1);
        claim.setRole(role);
        claim.setTask(task);
        claim.setClaimedAt(Instant.now().minusSeconds(120));
        claim.setLeaseExpiresAt(Instant.now().plusSeconds(600));

        when(claimRepository.findByReleasedAtIsNull()).thenReturn(List.of(claim));

        List<AgentDashboardDto> result = controller.getAgents();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("account-alpha");
        assertThat(result.get(0).currentRoleTag()).isEqualTo("BACKEND-01");
        assertThat(result.get(0).currentTaskDescription()).isEqualTo("Fix Pipeline Barrier");
        assertThat(result.get(1).name()).isEqualTo("account-beta");
        assertThat(result.get(1).currentRoleTag()).isNull();

        verify(accountRepository).findAllByOrderByNameAsc();
        verify(accountRepository, never()).findAll();
        verify(claimRepository).findByReleasedAtIsNull();
        verify(claimRepository, never()).findByAccountIdAndReleasedAtIsNullOrderByClaimedAtDesc(any());
    }

    @Test
    @DisplayName("getAgents falls back to findAll and individual queries when batch queries fail")
    void getAgentsFallbackWhenBatchQueriesFail() {
        UUID accountId = UUID.randomUUID();
        AccountEntity acc = new AccountEntity();
        acc.setId(accountId);
        acc.setName("account-solo");
        acc.setStatus(AccountStatus.busy);

        when(accountRepository.findAllByOrderByNameAsc()).thenThrow(new RuntimeException("DB order error"));
        when(accountRepository.findAll()).thenReturn(List.of(acc));
        when(claimRepository.findByReleasedAtIsNull()).thenThrow(new RuntimeException("DB batch error"));

        RoleEntity role = new RoleEntity();
        role.setTag("ARCH-01");

        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.setTitle("Architecture task");

        ClaimEntity claim = new ClaimEntity();
        claim.setId(UUID.randomUUID());
        claim.setAccount(acc);
        claim.setRole(role);
        claim.setTask(task);

        when(claimRepository.findByAccountIdAndReleasedAtIsNullOrderByClaimedAtDesc(accountId))
                .thenReturn(List.of(claim));

        List<AgentDashboardDto> result = controller.getAgents();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("account-solo");
        assertThat(result.get(0).currentRoleTag()).isEqualTo("ARCH-01");
        assertThat(result.get(0).currentTaskDescription()).isEqualTo("Architecture Task");

        verify(accountRepository).findAll();
        verify(claimRepository).findByAccountIdAndReleasedAtIsNullOrderByClaimedAtDesc(accountId);
    }
}
