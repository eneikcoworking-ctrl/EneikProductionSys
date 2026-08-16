package com.eneik.production.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Background process for reaping expired task leases.
 */
@Service
public class LeaseWatchdogService {
    private static final Logger log = LoggerFactory.getLogger(LeaseWatchdogService.class);

    private final ClaimService claimService;

    public LeaseWatchdogService(ClaimService claimService) {
        this.claimService = claimService;
    }

    /**
     * Trigger for periodic maintenance of expired task leases.
     */
    @Scheduled(fixedRate = 60000)
    public void reapExpiredLeases() {
        claimService.reapExpiredLeases();
    }
}
