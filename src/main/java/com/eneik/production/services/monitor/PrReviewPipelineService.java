package com.eneik.production.services.monitor;

import com.eneik.production.dto.monitor.PrDataDto;
import com.eneik.production.models.persistence.PrReviewEntity;
import com.eneik.production.repositories.PrReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PrReviewPipelineService {

    private final PrReviewRepository prReviewRepository;
    private final RiskLevelCalculator riskLevelCalculator;

    public PrReviewPipelineService(PrReviewRepository prReviewRepository, RiskLevelCalculator riskLevelCalculator) {
        this.prReviewRepository = prReviewRepository;
        this.riskLevelCalculator = riskLevelCalculator;
    }

    @Transactional
    public PrReviewEntity onPrOpened(String prUrl, UUID sessionId, PrDataDto prData) {
        return onPrOpened(prUrl, sessionId, prData, false);
    }

    @Transactional
    public PrReviewEntity onPrOpened(String prUrl, UUID sessionId, PrDataDto prData, boolean merged) {
        boolean touchesCriticalPath = checkCriticalPath(prData.getChangedFiles());

        String riskLevel = riskLevelCalculator.calculate(
                prData.getLinesChanged(),
                prData.getFilesChanged(),
                prData.isHasTestChanges(),
                prData.getCiStatus(),
                touchesCriticalPath
        );

        Optional<PrReviewEntity> existing = prReviewRepository.findFirstByJulesSessionIdAndPrUrlOrderByCreatedAtDesc(sessionId, prUrl);
        if (existing != null && existing.isPresent() && Boolean.TRUE.equals(existing.get().getMerged())) {
            return existing.get();
        }

        PrReviewEntity review = existing == null ? new PrReviewEntity() : existing.orElseGet(PrReviewEntity::new);
        review.setJulesSessionId(sessionId);
        review.setPrUrl(prUrl);
        review.setCiStatus(prData.getCiStatus());
        review.setDiffSummary(prData.getDiffSummary());
        review.setLinesChanged(prData.getLinesChanged());
        review.setFilesChanged(prData.getFilesChanged());
        review.setHasTestChanges(prData.isHasTestChanges());
        review.setTouchesCriticalPath(touchesCriticalPath);
        review.setRiskLevel(riskLevel);
        review.setMerged(merged);

        return prReviewRepository.save(review);
    }

    private boolean checkCriticalPath(List<String> changedFiles) {
        if (changedFiles == null) return false;
        return changedFiles.stream().anyMatch(path ->
            path.contains("ClaimService") ||
            path.contains("LeaseWatchdogService") ||
            path.contains("GateOrchestrator")
        );
    }
}
