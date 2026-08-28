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

    /**
     * Observations still awaiting ground truth, oldest first, for one lever and one subject prefix.
     *
     * <p>Bounded by an explicit {@link Limit} on purpose: a lever that has been observing for hours can
     * have thousands of pending rows (measured 3386 for T1_TOC_SUBORDINATION on 2026-08-28), and an
     * unbounded read on a resolution path is the exact shape that contributed to a real H2
     * out-of-memory here once already. Oldest-first so repeated bounded calls drain the backlog.
     */
    List<LeverObservation> findByLeverKeyAndSubjectIdStartingWithAndGroundTruthOutcomeIsNullAndObservedAtBeforeOrderByObservedAtAsc(
            String leverKey, String subjectIdPrefix, Instant before, org.springframework.data.domain.Limit limit);

    long countByLeverKey(String leverKey);
}
