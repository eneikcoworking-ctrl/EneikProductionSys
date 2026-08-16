package com.eneik.production.repositories;

import com.eneik.production.models.persistence.GreetingEntity;
import com.eneik.production.models.persistence.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GreetingRepository extends JpaRepository<GreetingEntity, UUID> {
    long countByCurrentStatus(Status status);

    @Query(value = """
            SELECT COALESCE(AVG(DATEDIFF('SECOND', processing_started_at, completed_at)), 0)
            FROM greetings
            WHERE current_status = 'COMPLETED'
              AND completed_at IS NOT NULL
              AND processing_started_at IS NOT NULL
            """, nativeQuery = true)
    Double getAverageCycleTimeSeconds();

    Optional<GreetingEntity> findFirstByOrderByCreatedAtDesc();
}
