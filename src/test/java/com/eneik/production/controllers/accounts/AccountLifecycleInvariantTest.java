package com.eneik.production.controllers.accounts;

import com.eneik.production.dto.AccountStatusRequestDto;
import com.eneik.production.kaizen.model.DefectJournalEntity;
import com.eneik.production.kaizen.repository.DefectJournalRepository;
import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.AccountStatus;
import com.eneik.production.repositories.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Invariant tests for Prescription 23:
 * 1. INSTITUTIONAL_FACT_REGISTER (D007): Status/enablement mutation produces an audit trail with rule and reason.
 * 2. ACTUAL_OBJECT_REGISTER (D002): Unified lifecycle ensures decommissioned accounts cannot be enabled (no contradictory state).
 * 3. TRUTH_STATUS_TABLE (D012): Contradictory states are eliminated and resolved.
 */
class AccountLifecycleInvariantTest {

    private AccountRepository accountRepository;
    private DefectJournalRepository defectJournalRepository;
    private AccountController controller;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        defectJournalRepository = mock(DefectJournalRepository.class);
        controller = new AccountController(accountRepository, defectJournalRepository);
    }

    @Test
    void disablingAccountRecordsInstitutionalFactAuditWithRuleAndReason() {
        UUID id = UUID.randomUUID();
        AccountEntity account = new AccountEntity();
        account.setId(id);
        account.setName("test-acc");
        account.setStatus(AccountStatus.idle);
        account.setEnabled(true);

        when(accountRepository.findById(id)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(AccountEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = controller.update(id, Map.of(
                "enabled", false,
                "reason", "Operator manual rotation pause"
        ));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(account.isEnabled()).isFalse();

        ArgumentCaptor<DefectJournalEntity> captor = ArgumentCaptor.forClass(DefectJournalEntity.class);
        verify(defectJournalRepository).save(captor.capture());
        DefectJournalEntity audit = captor.getValue();
        assertThat(audit.getSourceComponent()).isEqualTo("test-acc");
        assertThat(audit.getCategory()).isEqualTo("INSTITUTIONAL_AUDIT");
        assertThat(audit.getSeverity()).isEqualTo("INFO");
        assertThat(audit.getDefectType()).isEqualTo("ACCOUNT_LIFECYCLE_ENABLEMENT_RULE");
        assertThat(audit.getDescription()).contains("ACCOUNT_LIFECYCLE_ENABLEMENT_RULE");
        assertThat(audit.getDescription()).contains("Operator manual rotation pause");
        assertThat(audit.getDescription()).contains("enabled: true -> false");
    }

    @Test
    void decommissioningAccountEnforcesEnabledFalseAndRecordsAudit() {
        UUID id = UUID.randomUUID();
        AccountEntity account = new AccountEntity();
        account.setId(id);
        account.setName("old-acc");
        account.setStatus(AccountStatus.idle);
        account.setEnabled(true);

        when(accountRepository.findById(id)).thenReturn(Optional.of(account));
        when(accountRepository.save(any(AccountEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = controller.updateStatus(id, new AccountStatusRequestDto(AccountStatus.decommissioned));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.decommissioned);
        assertThat(account.isEnabled()).isFalse(); // Invariant: decommissioned implies enabled == false

        ArgumentCaptor<DefectJournalEntity> captor = ArgumentCaptor.forClass(DefectJournalEntity.class);
        verify(defectJournalRepository).save(captor.capture());
        DefectJournalEntity audit = captor.getValue();
        assertThat(audit.getCategory()).isEqualTo("INSTITUTIONAL_AUDIT");
        assertThat(audit.getSeverity()).isEqualTo("INFO");
        assertThat(audit.getDefectType()).isEqualTo("ACCOUNT_DECOMMISSION_RULE");
        assertThat(audit.getDescription()).contains("ACCOUNT_DECOMMISSION_RULE");
        assertThat(audit.getDescription()).contains("status: idle -> decommissioned");
    }

    @Test
    void enablingDecommissionedAccountIsRejectedWithBadRequest() {
        UUID id = UUID.randomUUID();
        AccountEntity account = new AccountEntity();
        account.setId(id);
        account.setName("decom-acc");
        account.setStatus(AccountStatus.decommissioned);
        account.setEnabled(false);

        when(accountRepository.findById(id)).thenReturn(Optional.of(account));

        ResponseEntity<?> response = controller.update(id, Map.of("enabled", true));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(account.isEnabled()).isFalse();
        verify(accountRepository, never()).save(any());
        verify(defectJournalRepository, never()).save(any());
    }

    @Test
    void entityLevelInvariantForbidsEnablingDecommissionedAccount() {
        AccountEntity account = new AccountEntity();
        account.setStatus(AccountStatus.decommissioned);
        assertThat(account.isEnabled()).isFalse();

        assertThrows(IllegalStateException.class, () -> account.setEnabled(true));
    }
}
