package com.eneik.production.repositories;

import com.eneik.production.models.persistence.CoherenceRunNodeResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CoherenceRunNodeResultRepository extends JpaRepository<CoherenceRunNodeResultEntity, UUID> {
    List<CoherenceRunNodeResultEntity> findByCoherenceRunId(UUID coherenceRunId);
    List<CoherenceRunNodeResultEntity> findByEvidenceNodeId(UUID evidenceNodeId);
    boolean existsByEvidenceNodeIdAndAcceptedTrue(UUID evidenceNodeId);

    @Query("SELECT COUNT(DISTINCT r.evidenceNodeId) FROM CoherenceRunNodeResultEntity r, EvidenceNodeEntity e " +
            "WHERE r.evidenceNodeId = e.id AND (" +
            "(:sourceType = 'DEFECT_JOURNAL' AND e.defectJournalId IS NOT NULL) OR " +
            "(:sourceType = 'CODE_INTEGRITY_FINDING' AND e.codeIntegrityFindingId IS NOT NULL) OR " +
            "(:sourceType = 'KAIZEN_PROPOSAL' AND e.kaizenProposalId IS NOT NULL) OR " +
            "(:sourceType = 'GEMINI_FINDING' AND e.geminiFindingId IS NOT NULL) OR " +
            "(:sourceType = 'OPERATIONAL_REALITY_FINDING' AND e.operationalRealityFindingId IS NOT NULL))")
    long countDistinctEvaluatedNodesBySourceType(@Param("sourceType") String sourceType);

    @Query("SELECT COUNT(DISTINCT r.evidenceNodeId) FROM CoherenceRunNodeResultEntity r, EvidenceNodeEntity e " +
            "WHERE r.evidenceNodeId = e.id AND r.accepted = true AND (" +
            "(:sourceType = 'DEFECT_JOURNAL' AND e.defectJournalId IS NOT NULL) OR " +
            "(:sourceType = 'CODE_INTEGRITY_FINDING' AND e.codeIntegrityFindingId IS NOT NULL) OR " +
            "(:sourceType = 'KAIZEN_PROPOSAL' AND e.kaizenProposalId IS NOT NULL) OR " +
            "(:sourceType = 'GEMINI_FINDING' AND e.geminiFindingId IS NOT NULL) OR " +
            "(:sourceType = 'OPERATIONAL_REALITY_FINDING' AND e.operationalRealityFindingId IS NOT NULL))")
    long countDistinctAcceptedNodesBySourceType(@Param("sourceType") String sourceType);
}
