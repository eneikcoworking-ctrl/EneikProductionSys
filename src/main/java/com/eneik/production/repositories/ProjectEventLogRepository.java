package com.eneik.production.repositories;

import com.eneik.production.models.persistence.ProjectEventLogEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectEventLogRepository extends JpaRepository<ProjectEventLogEntity, UUID> {
    List<ProjectEventLogEntity> findByProjectIdOrderByCreatedAtDesc(UUID projectId, Pageable pageable);
    List<ProjectEventLogEntity> findByProjectIdAndCreatedAtAfterOrderByCreatedAtAsc(UUID projectId, Instant since);
}
