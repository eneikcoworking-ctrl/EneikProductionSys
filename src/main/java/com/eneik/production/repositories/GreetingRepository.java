package com.eneik.production.repositories;

import com.eneik.production.models.persistence.GreetingEntity;
import com.eneik.production.models.persistence.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for GreetingEntity, providing methods for Lean/TOC monitoring.
 */
@Repository
public interface GreetingRepository extends JpaRepository<GreetingEntity, UUID> {

    /**
     * Counts greetings by status. Essential for monitoring WIP (Work In Progress) limits.
     * Constraint: MAX_WIP = 5 for IN_PROGRESS status.
     *
     * @param status The status to count.
     * @return Number of greetings with the given status.
     */
    long countByCurrentStatus(Status status);

    /**
     * Calculates the average Cycle Time in seconds for all COMPLETED greetings.
     * Cycle Time = completedAt - processingStartedAt.
     *
     * @return Average Cycle Time in seconds, or 0.0 if no tasks completed.
     */
    @Query("SELECT COALESCE(AVG(CAST(g.completedAt AS double) - CAST(g.processingStartedAt AS double)), 0.0) " +
           "FROM GreetingEntity g WHERE g.currentStatus = com.eneik.production.models.persistence.Status.COMPLETED " +
           "AND g.completedAt IS NOT NULL AND g.processingStartedAt IS NOT NULL")
    Double getAverageCycleTimeSeconds();

    /**
     * Finds the latest greeting added to the system.
     *
     * @return The most recent greeting entity.
     */
    Optional<GreetingEntity> findFirstByOrderByCreatedAtDesc();
}
