package com.eneik.production.services.monitor;

import com.eneik.production.dto.monitor.PrDataDto;
import com.eneik.production.models.persistence.PrReviewEntity;
import com.eneik.production.repositories.PrReviewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PrReviewPipelineServiceTest {

    @Mock
    private PrReviewRepository prReviewRepository;

    @Mock
    private RiskLevelCalculator riskLevelCalculator;

    @InjectMocks
    private PrReviewPipelineService pipelineService;

    @Test
    public void testOnPrOpened() {
        UUID sessionId = UUID.randomUUID();
        String prUrl = "https://github.com/org/repo/pull/1";

        PrDataDto prData = new PrDataDto();
        prData.setLinesChanged(45);
        prData.setFilesChanged(3);
        prData.setHasTestChanges(true);
        prData.setCiStatus("passing");
        prData.setChangedFiles(Collections.singletonList("src/main/java/SomeService.java"));
        prData.setDiffSummary("summary");

        when(riskLevelCalculator.calculate(eq(45), eq(3), eq(true), eq("passing"), eq(false)))
                .thenReturn("low");

        when(prReviewRepository.save(any(PrReviewEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PrReviewEntity result = pipelineService.onPrOpened(prUrl, sessionId, prData);

        assertNotNull(result);
        assertEquals(sessionId, result.getJulesSessionId());
        assertEquals(prUrl, result.getPrUrl());
        assertEquals(1, result.getPrNumber(), "prNumber must be captured from the real PR URL, same fixed GitHub format the URL itself already carries");
        assertEquals("low", result.getRiskLevel());
        assertEquals(false, result.getTouchesCriticalPath());

        verify(prReviewRepository).save(any(PrReviewEntity.class));
    }

    @Test
    public void testOnPrOpenedWithCriticalPath() {
        UUID sessionId = UUID.randomUUID();
        String prUrl = "https://github.com/org/repo/pull/2";

        PrDataDto prData = new PrDataDto();
        prData.setLinesChanged(10);
        prData.setFilesChanged(1);
        prData.setHasTestChanges(false);
        prData.setCiStatus("passing");
        prData.setChangedFiles(Collections.singletonList("src/main/java/com/eneik/production/services/ClaimService.java"));
        prData.setDiffSummary("critical change");

        when(riskLevelCalculator.calculate(eq(10), eq(1), eq(false), eq("passing"), eq(true)))
                .thenReturn("high");

        when(prReviewRepository.save(any(PrReviewEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PrReviewEntity result = pipelineService.onPrOpened(prUrl, sessionId, prData);

        assertNotNull(result);
        assertTrue(result.getTouchesCriticalPath());
        assertEquals(2, result.getPrNumber());
        assertEquals("high", result.getRiskLevel());

        verify(prReviewRepository).save(any(PrReviewEntity.class));
    }

    @Test
    public void testOnPrOpenedWithMockUrlLeavesPrNumberNullInsteadOfGuessing() {
        UUID sessionId = UUID.randomUUID();
        String mockPrUrl = "https://github.com/mock-org/mock-repo/pull/mock-999";

        PrDataDto prData = new PrDataDto();
        prData.setLinesChanged(5);
        prData.setFilesChanged(1);
        prData.setHasTestChanges(false);
        prData.setCiStatus("passing");
        prData.setChangedFiles(Collections.emptyList());
        prData.setDiffSummary("mock");

        when(riskLevelCalculator.calculate(eq(5), eq(1), eq(false), eq("passing"), eq(false)))
                .thenReturn("low");
        when(prReviewRepository.save(any(PrReviewEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PrReviewEntity result = pipelineService.onPrOpened(mockPrUrl, sessionId, prData);

        assertNotNull(result);
        assertEquals(null, result.getPrNumber(), "a non-numeric trailing segment must leave prNumber unset, never a wrong guess");
    }
}
