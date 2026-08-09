package com.eneik.production.kaizen.repository;

import com.eneik.production.kaizen.model.KaizenProposalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KaizenProposalRepository extends JpaRepository<KaizenProposalEntity, String> {
    List<KaizenProposalEntity> findByProjectId(UUID projectId);
    List<KaizenProposalEntity> findByCategoryAndStatusIn(String category, List<String> statuses);
    List<KaizenProposalEntity> findByStatusIn(List<String> statuses);
}
