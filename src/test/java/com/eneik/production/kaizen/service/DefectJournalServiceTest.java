package com.eneik.production.kaizen.service;

import com.eneik.production.kaizen.model.DefectJournalEntity;
import com.eneik.production.kaizen.repository.DefectJournalRepository;
import com.eneik.production.repositories.EvidenceNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * GILBERT_RAYL_03_CATEGORY_ERROR_SCAN (D002):
 * Verifies that institutional audit records (INSTITUTIONAL_AUDIT) are strictly excluded from defect queries,
 * so they cannot pollute Kaizen proposals, Muda scans, or Pareto defect charts.
 */
class DefectJournalServiceTest {

    private DefectJournalRepository defectJournalRepository;
    private EvidenceNodeRepository evidenceNodeRepository;
    private DefectJournalService service;

    @BeforeEach
    void setUp() {
        defectJournalRepository = mock(DefectJournalRepository.class);
        evidenceNodeRepository = mock(EvidenceNodeRepository.class);
        service = new DefectJournalService(defectJournalRepository, evidenceNodeRepository);
    }

    @Test
    void institutionalAuditRecordsAreExcludedFromDefectWindow() {
        DefectJournalEntity genuineDefect = new DefectJournalEntity(
                null, null, null, "HIGH", "WASTE_REDUCTION", "TaskQueue",
                "STALE_QUEUE", "Task queue is stale", 5.0
        );
        DefectJournalEntity auditRecord = new DefectJournalEntity(
                null, null, null, "INFO", "INSTITUTIONAL_AUDIT", "eneikdru",
                "ACCOUNT_LIFECYCLE_ENABLEMENT_RULE", "Account enabled toggled", 1.0
        );

        when(defectJournalRepository.findByCreatedAtAfter(any(Instant.class)))
                .thenReturn(List.of(genuineDefect, auditRecord));

        List<DefectJournalEntity> defects = service.getDefectsInWindow(null, 2);

        assertThat(defects).containsExactly(genuineDefect);
        assertThat(defects).doesNotContain(auditRecord);
    }

    @Test
    void scopedProjectDefectsExcludeInstitutionalAudit() {
        UUID projectId = UUID.randomUUID();
        DefectJournalEntity genuineDefect = new DefectJournalEntity(
                projectId, null, null, "HIGH", "DEFECT_ELIMINATION", "QualityGate",
                "DPMO_SPIKE", "Quality gate check failed", 1200.0
        );
        DefectJournalEntity auditRecord = new DefectJournalEntity(
                projectId, null, null, "INFO", "INSTITUTIONAL_AUDIT", "acc-1",
                "ACCOUNT_DECOMMISSION_RULE", "Account decommissioned", 0.0
        );

        when(defectJournalRepository.findByProjectIdAndCreatedAtAfter(eq(projectId), any(Instant.class)))
                .thenReturn(List.of(genuineDefect, auditRecord));

        List<DefectJournalEntity> defects = service.getDefectsInWindow(projectId, 2);

        assertThat(defects).containsExactly(genuineDefect);
        assertThat(defects).doesNotContain(auditRecord);
    }

    @Test
    void recordInstitutionalAuditSavesWithInfoSeverityAndInstitutionalAuditCategory() {
        when(defectJournalRepository.save(any(DefectJournalEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        DefectJournalEntity saved = service.recordInstitutionalAudit("test-acc", "ACCOUNT_STATE_TRANSITION", "State changed", 1.0);

        assertThat(saved.getSeverity()).isEqualTo("INFO");
        assertThat(saved.getCategory()).isEqualTo("INSTITUTIONAL_AUDIT");
        assertThat(saved.getSourceComponent()).isEqualTo("test-acc");
        assertThat(saved.getDefectType()).isEqualTo("ACCOUNT_STATE_TRANSITION");
        verify(defectJournalRepository).save(any(DefectJournalEntity.class));
    }
}
