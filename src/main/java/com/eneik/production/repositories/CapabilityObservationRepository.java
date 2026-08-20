package com.eneik.production.repositories;

import com.eneik.production.models.persistence.CapabilityObservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CapabilityObservationRepository extends JpaRepository<CapabilityObservationEntity, UUID> {

    List<CapabilityObservationEntity> findByProjectIdOrderByObservedAtDesc(UUID projectId);
}
