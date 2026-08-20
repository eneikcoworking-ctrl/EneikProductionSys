package com.eneik.production.repositories;

import com.eneik.production.models.persistence.InvariantStatusChangeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvariantStatusChangeRepository extends JpaRepository<InvariantStatusChangeEntity, UUID> {

    /** The last status this (project, invariant) was recorded at - the only thing a transition needs. */
    Optional<InvariantStatusChangeEntity> findFirstByProjectIdAndInvariantKeyOrderByObservedAtDesc(
            UUID projectId, String invariantKey);

    /** Transitions since a point in time, newest first - the wake signal for factory-level judgment. */
    List<InvariantStatusChangeEntity> findByObservedAtAfterOrderByObservedAtDesc(Instant since);

    List<InvariantStatusChangeEntity> findByProjectIdOrderByObservedAtDesc(UUID projectId);
}
