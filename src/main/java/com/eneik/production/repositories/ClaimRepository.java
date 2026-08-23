package com.eneik.production.repositories;

import com.eneik.production.dto.dashboard.ExpiredStatDto;
import com.eneik.production.models.persistence.ClaimEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClaimRepository extends JpaRepository<ClaimEntity, UUID> {
    @Query("SELECT new com.eneik.production.dto.dashboard.ExpiredStatDto(c.account.id, COUNT(c)) " +
           "FROM ClaimEntity c WHERE c.resultStatus = 'expired' AND c.claimedAt > :since " +
           "GROUP BY c.account.id")
    List<ExpiredStatDto> expiredCountByAccountSince(@Param("since") Instant since);

    List<ClaimEntity> findByAccountIdAndReleasedAtIsNullOrderByClaimedAtDesc(UUID accountId);
    boolean existsByAccountIdAndReleasedAtIsNull(UUID accountId);
    Optional<ClaimEntity> findByAccountIdAndTaskProjectIdAndReleasedAtIsNull(UUID accountId, UUID projectId);
    List<ClaimEntity> findByAccountIdAndTaskProjectIdAndReleasedAtIsNullOrderByClaimedAtDesc(UUID accountId, UUID projectId);
    /**
     * The most recent unreleased claim on this task, or none.
     *
     * 2026-08-23: this was `findByTaskIdAndReleasedAtIsNull`, whose derived query demands that at most one
     * row match and throws IncorrectResultSizeDataAccessException when two do. Nothing in the schema
     * enforces that. Measured live on test-fiftieth: a task held two unreleased claims and the exception
     * reached GlobalExceptionHandler six times in ten minutes, taking down the operator dashboard and
     * JulesDispatchService.reconcileTaskStatusAgainstGitHubTruth with it - the reconciler that converts
     * stranded pr_opened workflows.
     *
     * Every one of the ten callers asks whether an active claim exists, or wants one to act on. None of
     * them asks whether exactly one exists. A query whose cardinality is stricter than the question it
     * answers turns ordinary data into an outage. `findFirst` with an explicit order is total and
     * deterministic: it answers the question that is actually being asked, and answers it the same way
     * every time.
     */
    Optional<ClaimEntity> findFirstByTaskIdAndReleasedAtIsNullOrderByClaimedAtDesc(UUID taskId);

    List<ClaimEntity> findByReleasedAtIsNullAndLeaseExpiresAtBefore(java.time.Instant now);
    List<ClaimEntity> findByReleasedAtIsNull();
    List<ClaimEntity> findByTaskIdIn(List<UUID> taskIds);
}
