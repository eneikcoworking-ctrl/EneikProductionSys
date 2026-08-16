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
        review.setPrNumber(extractPrNumber(prUrl));
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

    // GitHub PR URLs are always shaped https://github.com/{owner}/{repo}/pull/{number} - a fixed, documented
    // format, not free text - so extracting the trailing digit segment is deterministic, not a guess. Same
    // guarantee AutoMergeService.parseGithubPullRequestUrl already relies on for the same URL shape; kept as
    // its own tiny helper here rather than reaching into that unrelated service, to avoid touching its
    // already-tested parsing logic for an unrelated caller.
    private Integer extractPrNumber(String prUrl) {
        if (prUrl == null || prUrl.isBlank()) {
            return null;
        }
        try {
            java.net.URI uri = java.net.URI.create(prUrl);
            String[] parts = uri.getPath().replaceAll("^/+", "").split("/");
            if (parts.length >= 4 && "pull".equals(parts[2]) && parts[3].matches("\\d+")) {
                return Integer.parseInt(parts[3]);
            }
        } catch (Exception e) {
            // Malformed/mock URL - leave prNumber unset, same fail-open-to-null shape as prUrl parsing
            // elsewhere in this codebase; never block review creation over a cosmetic parse failure.
        }
        return null;
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
