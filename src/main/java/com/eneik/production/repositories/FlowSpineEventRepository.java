package com.eneik.production.repositories;

import com.eneik.production.models.persistence.FlowSpineEventEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FlowSpineEventRepository extends JpaRepository<FlowSpineEventEntity, UUID> {
    Optional<FlowSpineEventEntity> findTop1ByProjectIdOrderByObservedAtDesc(UUID projectId);
    List<FlowSpineEventEntity> findByProjectIdOrderByObservedAtDesc(UUID projectId, Pageable pageable);
    long countByProjectId(UUID projectId);
}
