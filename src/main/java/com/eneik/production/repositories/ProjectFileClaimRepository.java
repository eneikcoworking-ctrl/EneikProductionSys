package com.eneik.production.repositories;

import com.eneik.production.models.persistence.ProjectFileClaimEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectFileClaimRepository extends JpaRepository<ProjectFileClaimEntity, UUID> {
    List<ProjectFileClaimEntity> findByProjectIdAndFilePathIn(UUID projectId, Collection<String> filePaths);
}
