package com.eneik.production.repositories;

import com.eneik.production.models.persistence.ProjectFinalReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ProjectFinalReportRepository extends JpaRepository<ProjectFinalReportEntity, UUID> {
}
