package com.eneik.production.repositories;

import com.eneik.production.models.persistence.RoleThreadEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleThreadRepository extends JpaRepository<RoleThreadEntity, UUID> {
    Optional<RoleThreadEntity> findByProjectIdAndFeatureIdAndRoleTag(UUID projectId, UUID featureId, String roleTag);
}
