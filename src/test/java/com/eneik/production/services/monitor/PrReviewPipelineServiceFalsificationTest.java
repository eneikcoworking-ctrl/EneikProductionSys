package com.eneik.production.services.monitor;

import com.eneik.production.dto.monitor.PrDataDto;
import com.eneik.production.models.persistence.PrReviewEntity;
import com.eneik.production.repositories.PrReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Popperian Falsification Suite for PrReviewPipelineService and RiskLevelCalculator.
 * Validates epistemic and architectural invariants:
 * - D010: Goldman Reliable Process Audit (preservation of merged reviews, full causal audit trail).
 * - D012: Popper Truth Status Table (deterministic 3-valued risk assessment: low, medium, high).
 * - D006: Raz Prohibition As Code (critical path and failing CI act as exclusionary reasons forcing high risk).
 */
class PrReviewPipelineServiceFalsificationTest {

    private PrReviewRepository prReviewRepository;
    private RiskLevelCalculator riskLevelCalculator;
    private PrReviewPipelineService pipelineService;

    @BeforeEach
    void setUp() {
        prReviewRepository = mock(PrReviewRepository.class);
        riskLevelCalculator = new RiskLevelCalculator();
        pipelineService = new PrReviewPipelineService(prReviewRepository, riskLevelCalculator);
    }

    @Test
    @DisplayName("D010 Goldman Audit: Already-merged review is monotonic and never overwritten by subsequent webhook")
    void falsifyGoldmanReliableProcessAudit_mergedPrReviewIsPreservedAndNeverOverwritten() {
        UUID sessionId = UUID.randomUUID();
        String prUrl = "https://github.com/eneik/repo/pull/10";

        PrReviewEntity mergedReview = new PrReviewEntity();
        mergedReview.setJulesSessionId(sessionId);
        mergedReview.setPrUrl(prUrl);
        mergedReview.setMerged(true);
        mergedReview.setCiStatus("passing");

        when(prReviewRepository.findFirstByJulesSessionIdAndPrUrlOrderByCreatedAtDesc(sessionId, prUrl))
                .thenReturn(Optional.of(mergedReview));

        PrDataDto incomingData = new PrDataDto();
        incomingData.setCiStatus("failing");
        incomingData.setLinesChanged(999);

        PrReviewEntity result = pipelineService.onPrOpened(prUrl, sessionId, incomingData, false);

        assertThat(result).isSameAs(mergedReview);
        assertThat(result.getMerged()).isTrue();
        assertThat(result.getCiStatus()).isEqualTo("passing");
        verify(prReviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("D010 Goldman Audit: Unmerged review updates all audit trail fields including PR number")
    void falsifyGoldmanReliableProcessAudit_unmergedPrReviewIsUpdatedWithLatestPrData() {
        UUID sessionId = UUID.randomUUID();
        String prUrl = "https://github.com/eneik/repo/pull/25";

        PrReviewEntity existingReview = new PrReviewEntity();
        existingReview.setJulesSessionId(sessionId);
        existingReview.setPrUrl(prUrl);
        existingReview.setMerged(false);
        existingReview.setCiStatus("pending");

        when(prReviewRepository.findFirstByJulesSessionIdAndPrUrlOrderByCreatedAtDesc(sessionId, prUrl))
                .thenReturn(Optional.of(existingReview));
        when(prReviewRepository.save(any(PrReviewEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PrDataDto incomingData = new PrDataDto();
        incomingData.setLinesChanged(30);
        incomingData.setFilesChanged(2);
        incomingData.setHasTestChanges(true);
        incomingData.setCiStatus("passing");
        incomingData.setDiffSummary("Refactored auth filter");
        incomingData.setChangedFiles(List.of("src/main/java/AuthFilter.java"));

        PrReviewEntity result = pipelineService.onPrOpened(prUrl, sessionId, incomingData, false);

        assertThat(result).isNotNull();
        assertThat(result.getPrNumber()).isEqualTo(25);
        assertThat(result.getCiStatus()).isEqualTo("passing");
        assertThat(result.getRiskLevel()).isEqualTo("low");
        assertThat(result.getDiffSummary()).isEqualTo("Refactored auth filter");
        assertThat(result.getTouchesCriticalPath()).isFalse();
        verify(prReviewRepository).save(existingReview);
    }

    @Test
    @DisplayName("D012 Popper Truth Status Table: RiskLevelCalculator computes pure deterministic risk statuses")
    void falsifyPopperTruthStatusTable_riskMatrixComputesStrictLowMediumHighStatuses() {
        // Low: lines < 50, hasTestChanges=true, ciStatus='passing', touchesCriticalPath=false
        assertThat(riskLevelCalculator.calculate(40, 2, true, "passing", false)).isEqualTo("low");

        // Medium: lines < 50 but missing tests
        assertThat(riskLevelCalculator.calculate(40, 2, false, "passing", false)).isEqualTo("medium");

        // Medium: lines in range [50, 300], passing CI, non-critical
        assertThat(riskLevelCalculator.calculate(150, 5, true, "passing", false)).isEqualTo("medium");
        assertThat(riskLevelCalculator.calculate(50, 2, true, "passing", false)).isEqualTo("medium");
        assertThat(riskLevelCalculator.calculate(300, 10, true, "passing", false)).isEqualTo("medium");

        // High: lines > 300
        assertThat(riskLevelCalculator.calculate(301, 10, true, "passing", false)).isEqualTo("high");
    }

    @Test
    @DisplayName("D006 Raz Prohibition: Critical path and failing CI act as exclusionary reasons forcing high risk")
    void falsifyRazProhibitionAsCode_criticalPathOrFailingCiUnconditionallyForcesHighRisk() {
        // Exclusionary Reason 1: touchesCriticalPath forces high even for tiny diffs with passing CI and tests
        assertThat(riskLevelCalculator.calculate(5, 1, true, "passing", true)).isEqualTo("high");

        // Exclusionary Reason 2: failing CI forces high even for tiny diffs with tests and non-critical
        assertThat(riskLevelCalculator.calculate(5, 1, true, "failing", false)).isEqualTo("high");

        // Exclusionary Reason 3: both failing CI and critical path
        assertThat(riskLevelCalculator.calculate(5, 1, true, "failing", true)).isEqualTo("high");
    }

    @Test
    @DisplayName("D006 Raz Prohibition: checkCriticalPath detects ClaimService, LeaseWatchdogService, GateOrchestrator")
    void falsifyRazProhibitionAsCode_criticalPathsIdentifiedDeterministically() {
        UUID sessionId = UUID.randomUUID();
        when(prReviewRepository.save(any(PrReviewEntity.class))).thenAnswer(i -> i.getArgument(0));

        PrDataDto dtoClaim = new PrDataDto();
        dtoClaim.setChangedFiles(List.of("src/main/java/com/eneik/production/services/ClaimService.java"));
        dtoClaim.setLinesChanged(10);
        dtoClaim.setCiStatus("passing");
        dtoClaim.setHasTestChanges(true);

        PrReviewEntity r1 = pipelineService.onPrOpened("https://github.com/org/repo/pull/1", sessionId, dtoClaim);
        assertThat(r1.getTouchesCriticalPath()).isTrue();
        assertThat(r1.getRiskLevel()).isEqualTo("high");

        PrDataDto dtoLease = new PrDataDto();
        dtoLease.setChangedFiles(List.of("src/main/java/com/eneik/production/services/LeaseWatchdogService.java"));
        dtoLease.setLinesChanged(10);
        dtoLease.setCiStatus("passing");
        dtoLease.setHasTestChanges(true);

        PrReviewEntity r2 = pipelineService.onPrOpened("https://github.com/org/repo/pull/2", sessionId, dtoLease);
        assertThat(r2.getTouchesCriticalPath()).isTrue();
        assertThat(r2.getRiskLevel()).isEqualTo("high");

        PrDataDto dtoGate = new PrDataDto();
        dtoGate.setChangedFiles(List.of("src/main/java/com/eneik/production/services/gate/GateOrchestrator.java"));
        dtoGate.setLinesChanged(10);
        dtoGate.setCiStatus("passing");
        dtoGate.setHasTestChanges(true);

        PrReviewEntity r3 = pipelineService.onPrOpened("https://github.com/org/repo/pull/3", sessionId, dtoGate);
        assertThat(r3.getTouchesCriticalPath()).isTrue();
        assertThat(r3.getRiskLevel()).isEqualTo("high");

        PrDataDto dtoNormal = new PrDataDto();
        dtoNormal.setChangedFiles(List.of("src/main/java/com/eneik/production/services/UserService.java"));
        dtoNormal.setLinesChanged(10);
        dtoNormal.setCiStatus("passing");
        dtoNormal.setHasTestChanges(true);

        PrReviewEntity r4 = pipelineService.onPrOpened("https://github.com/org/repo/pull/4", sessionId, dtoNormal);
        assertThat(r4.getTouchesCriticalPath()).isFalse();
        assertThat(r4.getRiskLevel()).isEqualTo("low");
    }

    @Test
    @DisplayName("D010 Goldman Audit: extractPrNumber parses standard GitHub URLs and fails open to null on invalid URLs")
    void falsifyGoldmanReliableProcessAudit_extractPrNumberParsesStandardGitHubUrlsAndRejectsMalformed() {
        UUID sessionId = UUID.randomUUID();
        when(prReviewRepository.save(any(PrReviewEntity.class))).thenAnswer(i -> i.getArgument(0));

        PrDataDto dto = new PrDataDto();
        dto.setLinesChanged(10);
        dto.setCiStatus("passing");

        PrReviewEntity rValid = pipelineService.onPrOpened("https://github.com/acme/widgets/pull/999", sessionId, dto);
        assertThat(rValid.getPrNumber()).isEqualTo(999);

        PrReviewEntity rTrailingSlash = pipelineService.onPrOpened("https://github.com/acme/widgets/pull/42/", sessionId, dto);
        // "pull/42/" split gives ["acme", "widgets", "pull", "42"] -> 42
        assertThat(rTrailingSlash.getPrNumber()).isEqualTo(42);

        PrReviewEntity rMalformed = pipelineService.onPrOpened("https://github.com/acme/widgets/pull/abc", sessionId, dto);
        assertThat(rMalformed.getPrNumber()).isNull();

        PrReviewEntity rEmpty = pipelineService.onPrOpened("", sessionId, dto);
        assertThat(rEmpty.getPrNumber()).isNull();

        PrReviewEntity rNull = pipelineService.onPrOpened(null, sessionId, dto);
        assertThat(rNull.getPrNumber()).isNull();
    }
}
