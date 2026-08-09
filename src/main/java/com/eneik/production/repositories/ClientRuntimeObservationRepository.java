package com.eneik.production.repositories;

import com.eneik.production.models.persistence.ClientRuntimeObservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClientRuntimeObservationRepository extends JpaRepository<ClientRuntimeObservationEntity, UUID> {
    List<ClientRuntimeObservationEntity> findByProjectIdOrderByObservedAtDesc(UUID projectId);
}
