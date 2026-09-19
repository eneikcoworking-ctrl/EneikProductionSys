package com.eneik.production.kaizen.repository;

import com.eneik.production.kaizen.model.KaizenProposalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KaizenProposalRepository extends JpaRepository<KaizenProposalEntity, String> {
    List<KaizenProposalEntity> findByProjectId(UUID projectId);
    List<KaizenProposalEntity> findByProjectIdIsNull();
    List<KaizenProposalEntity> findAllByOrderByCreatedAtDesc();
    List<KaizenProposalEntity> findByCategoryAndStatusIn(String category, List<String> statuses);
    List<KaizenProposalEntity> findByStatusIn(List<String> statuses);
    long countByStatus(String status);
    java.util.Optional<KaizenProposalEntity> findFirstByStatusAndCategoryAndTargetComponentAndProjectId(String status, String category, String targetComponent, UUID projectId);
    java.util.Optional<KaizenProposalEntity> findFirstByStatusAndCategoryAndTargetComponentAndProjectIdIsNull(String status, String category, String targetComponent);
}
