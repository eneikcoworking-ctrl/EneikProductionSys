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

    Optional<ClaimEntity> findByAccountIdAndReleasedAtIsNull(UUID accountId);
    Optional<ClaimEntity> findByAccountIdAndTaskProjectIdAndReleasedAtIsNull(UUID accountId, UUID projectId);
    Optional<ClaimEntity> findByTaskIdAndReleasedAtIsNull(UUID taskId);

    List<ClaimEntity> findByReleasedAtIsNullAndLeaseExpiresAtBefore(java.time.Instant now);
    List<ClaimEntity> findByReleasedAtIsNull();
}
