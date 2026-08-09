package com.eneik.production.repositories;

import com.eneik.production.models.persistence.LeverObservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface LeverObservationRepository extends JpaRepository<LeverObservation, UUID> {

    List<LeverObservation> findByLeverKeyAndObservedAtAfterOrderByObservedAtAsc(String leverKey, Instant since);

    long countByLeverKey(String leverKey);
}
