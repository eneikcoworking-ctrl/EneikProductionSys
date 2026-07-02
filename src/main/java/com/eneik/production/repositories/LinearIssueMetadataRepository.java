package com.eneik.production.repositories;

import com.eneik.production.models.persistence.LinearIssueMetadataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface LinearIssueMetadataRepository extends JpaRepository<LinearIssueMetadataEntity, UUID> {
}
