package com.eneik.production.repositories;

import com.eneik.production.models.persistence.ClientRuntimeObservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Limit;

import java.util.List;
import java.util.UUID;

public interface ClientRuntimeObservationRepository extends JpaRepository<ClientRuntimeObservationEntity, UUID> {
    List<ClientRuntimeObservationEntity> findByProjectIdOrderByObservedAtDesc(UUID projectId);

    /**
     * The newest rows only, for questions that are about a RUN rather than about the whole history.
     *
     * The instrument's availability is such a question (ACP-103, plan section 9.3): what matters is how
     * many consecutive attempts reached nothing, and that answer never needs more rows than the run it is
     * bounded by. Loading the full history to look at its head is how a table that grows without bound
     * becomes a per-tick cost.
     */
    List<ClientRuntimeObservationEntity> findByProjectIdOrderByObservedAtDesc(UUID projectId, Limit limit);
}
