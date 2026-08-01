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

    // 2026-08-01: subgroup query for ProcessControlService's u-chart - all defects attributed to one эпик,
    // full history (no time window - the u-chart's own Phase 1/Phase 2 subgroup sequencing IS the ordering,
    // not wall-clock recency).
    List<DefectJournalEntity> findByFeatureId(UUID featureId);

    // 2026-08-01: data-driven account-recovery backoff (AccountHealthService) - sourceComponent=account
    // name, defectType="ACCOUNT_RECOVERY_DURATION". Per-account history first; findByDefectType (pooled
    // across all accounts) is the fallback when one account has too few observations of its own yet.
    List<DefectJournalEntity> findBySourceComponentAndDefectTypeOrderByCreatedAtDesc(String sourceComponent, String defectType);

    List<DefectJournalEntity> findByDefectTypeOrderByCreatedAtDesc(String defectType);
}
