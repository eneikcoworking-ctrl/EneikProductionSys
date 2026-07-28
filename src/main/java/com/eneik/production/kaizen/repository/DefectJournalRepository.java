package com.eneik.production.kaizen.repository;

import com.eneik.production.kaizen.model.DefectJournalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface DefectJournalRepository extends JpaRepository<DefectJournalEntity, UUID> {

    List<DefectJournalEntity> findByCreatedAtAfter(Instant fromTime);

    List<DefectJournalEntity> findByProjectIdAndCreatedAtAfter(UUID projectId, Instant fromTime);
}
