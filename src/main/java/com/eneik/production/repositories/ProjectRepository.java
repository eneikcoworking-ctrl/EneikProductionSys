package com.eneik.production.repositories;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<ProjectEntity, UUID> {
    boolean existsBySlug(String slug);
    Optional<ProjectEntity> findFirstByStatusOrderByCreatedAtDesc(ProjectStatus status);
    List<ProjectEntity> findByStatusOrderByCreatedAtDesc(ProjectStatus status);

    // Admission mutex for "at most one active singleton-task-of-type-X per project" decisions
    // (dispatchFalsificationAudit and its siblings) - a different defect class than a terminal-state
    // revival race: those are UPDATEs on an existing row, guardable by a WHERE-clause CAS
    // (TaskRepository.writeStatusUnlessTerminal); this is a check-then-INSERT race, which no per-row CAS
    // can fix since the row being raced over doesn't exist yet at check time. The correct primitive is a
    // real critical section: hold a row lock on the project for the full check-then-create span, so a
    // second concurrent caller blocks until the first commits and then correctly observes "already
    // active" instead of racing ahead and creating a duplicate singleton task. No SKIP LOCKED (unlike
    // TaskRepository.lockTaskByIdForUpdate) - these are rare admission decisions, not a high-throughput
    // queue, so the second caller should wait briefly for the correct answer rather than skip past it.
    @Query(value = "SELECT * FROM projects WHERE id = :id FOR UPDATE", nativeQuery = true)
    Optional<ProjectEntity> lockProjectForUpdate(@Param("id") UUID id);
}
