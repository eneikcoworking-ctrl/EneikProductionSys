package com.eneik.production.repositories;

import com.eneik.production.models.persistence.ClientAcceptanceTraversalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClientAcceptanceTraversalRepository extends JpaRepository<ClientAcceptanceTraversalEntity, UUID> {
    List<ClientAcceptanceTraversalEntity> findByProjectIdOrderByTraversedAtDesc(UUID projectId);
}
