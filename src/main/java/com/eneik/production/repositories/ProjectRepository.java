package com.eneik.production.repositories;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<ProjectEntity, UUID> {
    boolean existsBySlug(String slug);
    Optional<ProjectEntity> findFirstByStatusOrderByCreatedAtDesc(ProjectStatus status);
    List<ProjectEntity> findByStatusOrderByCreatedAtDesc(ProjectStatus status);
}
