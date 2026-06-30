package com.eneik.production.services.task;

import com.eneik.production.models.persistence.ClaimEntity;
import com.eneik.production.repositories.ClaimRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service for managing task claims and enforcing business rules.
 */
@Service
public class TaskClaimService {

    private final ClaimRepository claimRepository;

    public TaskClaimService(ClaimRepository claimRepository) {
        this.claimRepository = claimRepository;
    }

    /**
     * Enforces the business rule that a task cannot have more than one active claim.
     * This compensates for the lack of a partial unique index in the H2 test environment.
     */
    public void validateTaskAvailability(UUID taskId) {
        boolean hasActiveClaim = claimRepository.findAll().stream()
                .anyMatch(c -> c.getTask().getId().equals(taskId) && c.getReleasedAt() == null);

        if (hasActiveClaim) {
            throw new IllegalStateException("Task " + taskId + " already has an active claim.");
        }
    }
}
