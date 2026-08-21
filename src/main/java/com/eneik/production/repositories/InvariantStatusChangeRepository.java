package com.eneik.production.repositories;

import com.eneik.production.models.persistence.InvariantStatusChangeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvariantStatusChangeRepository extends JpaRepository<InvariantStatusChangeEntity, UUID> {

    /** The last status this (project, invariant) was recorded at - the only thing a transition needs. */
    Optional<InvariantStatusChangeEntity> findFirstByProjectIdAndInvariantKeyOrderByObservedAtDesc(
            UUID projectId, String invariantKey);

    /** Transitions since a point in time, newest first - the wake signal for factory-level judgment. */
    List<InvariantStatusChangeEntity> findByObservedAtAfterOrderByObservedAtDesc(Instant since);

    List<InvariantStatusChangeEntity> findByProjectIdOrderByObservedAtDesc(UUID projectId);

    /**
     * Transitions no factory-level judgment has ruled on yet, oldest first.
     *
     * Oldest first on purpose: a refutation is only interpretable against the ones that preceded it, and
     * a bounded cycle that took the newest rows would leave the oldest permanently at the back of a queue
     * it never reaches. FIFO makes the backlog drain; LIFO makes it a floor.
     *
     * **A null previous_status is excluded, and that is the point.** Such a row is the first time an
     * invariant was ever recorded - a baseline registration, not a transition away from an asserted
     * property. V105's own reason for existing is Popper's asymmetry: a confirmation carries no
     * information and there are unboundedly many of them. A baseline is a confirmation with no
     * predecessor, so it is the least informative row this table can hold.
     *
     * Found by the judgment layer itself on its first live cycle, 2026-08-21: all five rows it was given
     * were `null -> pass|warn`, and it answered ABSTAIN five times with the same reason - "a baseline
     * entry into pass is not a transition away from an asserted property". Five rulings at roughly $0.30
     * each, on rows that were knowably uninformative before they were sent. The queue was wrong, not the
     * agent.
     */
    List<InvariantStatusChangeEntity> findByJudgedAtIsNullAndPreviousStatusIsNotNullOrderByObservedAtAsc();

    /**
     * Every recorded transition of one invariant, newest first, across every scope.
     *
     * Keyed on the invariant alone rather than on (project, invariant) because most invariants here are
     * factory-wide and carry a null project_id, and a derived query on a null parameter compiles to
     * {@code project_id = null}, which matches no row in SQL - it would return an empty history for
     * exactly the invariants that have one. Callers narrow the scope in Java with Objects.equals.
     */
    List<InvariantStatusChangeEntity> findByInvariantKeyOrderByObservedAtDesc(String invariantKey);
}
