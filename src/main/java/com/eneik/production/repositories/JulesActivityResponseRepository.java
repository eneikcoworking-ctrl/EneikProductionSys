package com.eneik.production.repositories;

import com.eneik.production.models.persistence.JulesActivityResponseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface JulesActivityResponseRepository extends JpaRepository<JulesActivityResponseEntity, UUID> {
    Optional<JulesActivityResponseEntity> findByJulesSessionIdAndActivityHash(UUID julesSessionId, String activityHash);
}
