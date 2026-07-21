package com.eneik.production.repositories;

import com.eneik.production.models.persistence.PersistentWorkerPurpose;
import com.eneik.production.models.persistence.PersistentWorkerSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PersistentWorkerSessionRepository extends JpaRepository<PersistentWorkerSessionEntity, UUID> {
    Optional<PersistentWorkerSessionEntity> findByProjectIdAndPurposeAndRetiredAtIsNull(
            UUID projectId, PersistentWorkerPurpose purpose);
    Optional<PersistentWorkerSessionEntity> findByCarrierTaskId(UUID carrierTaskId);
}
