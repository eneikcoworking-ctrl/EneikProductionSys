package com.eneik.production.repositories;

import com.eneik.production.models.persistence.OnboardingAuditFindingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OnboardingAuditFindingRepository extends JpaRepository<OnboardingAuditFindingEntity, UUID> {
    List<OnboardingAuditFindingEntity> findByProjectIdOrderByCreatedAtAsc(UUID projectId);
    long countByProjectId(UUID projectId);
}
