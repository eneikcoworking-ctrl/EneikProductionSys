package com.eneik.production.repositories;

import com.eneik.production.models.persistence.TrustSignalSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TrustSignalSnapshotRepository extends JpaRepository<TrustSignalSnapshotEntity, UUID> {
    List<TrustSignalSnapshotEntity> findByEventualOutcomeIsNull();
}
