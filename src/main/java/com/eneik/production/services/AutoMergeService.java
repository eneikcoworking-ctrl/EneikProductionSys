package com.eneik.production.services;

import com.eneik.production.models.persistence.PrReviewEntity;
import com.eneik.production.repositories.PrReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AutoMergeService {
    private static final Logger log = LoggerFactory.getLogger(AutoMergeService.class);
    private static final String APPROVAL_TOKEN = "CORE ARCHITECTURE VERIFIED. APPROVED.";

    private final PrReviewRepository prReviewRepository;

    public AutoMergeService(PrReviewRepository prReviewRepository) {
        this.prReviewRepository = prReviewRepository;
    }

    @Scheduled(fixedRateString = "${automerge.rate-ms:60000}")
    public void processAutoMerge() {
        List<PrReviewEntity> pendingReviews = prReviewRepository.findAll().stream()
                .filter(r -> "success".equalsIgnoreCase(r.getCiStatus()))
                .filter(r -> !Boolean.TRUE.equals(r.getMerged()))
                .toList();

        for (PrReviewEntity review : pendingReviews) {
            if (review.getDiffSummary() != null && review.getDiffSummary().contains(APPROVAL_TOKEN)) {
                executeMerge(review);
            }
        }
    }

    @Transactional
    protected void executeMerge(PrReviewEntity review) {
        log.info("AutoMergeService: Merging PR {} due to valid approval token and green CI", review.getPrUrl());
        // In a real system, this would call GitHub API. Here we mark as merged.
        review.setMerged(true);
        prReviewRepository.save(review);
    }
}
