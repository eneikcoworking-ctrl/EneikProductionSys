package com.eneik.production.repositories;

import com.eneik.production.models.persistence.ProjectEventLogEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectEventLogRepository extends JpaRepository<ProjectEventLogEntity, UUID> {
    List<ProjectEventLogEntity> findByProjectIdOrderByCreatedAtDesc(UUID projectId, Pageable pageable);
    List<ProjectEventLogEntity> findByProjectIdAndCreatedAtAfterOrderByCreatedAtAsc(UUID projectId, Instant since);

    // 2026-08-14: this table had no delete path at all. It is written every 5 seconds in batches of 500
    // and nothing ever removed a row, so it grew without bound by construction - 162k rows and still
    // climbing with every project frozen. That is not what took the database down (94% of the 1.7 GB file
    // was unreclaimed MVStore pages from never closing cleanly), but an append-only log with no policy is
    // a second, slower version of the same failure waiting to happen.

    long countByProjectId(UUID projectId);

    /** Oldest-first, used to find the cutoff timestamp when trimming a project back to its cap. */
    List<ProjectEventLogEntity> findByProjectIdOrderByCreatedAtAsc(UUID projectId, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ProjectEventLogEntity e WHERE e.projectId = :projectId AND e.createdAt < :cutoff")
    int deleteByProjectIdAndCreatedAtBefore(@Param("projectId") UUID projectId, @Param("cutoff") Instant cutoff);
}
