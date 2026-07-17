package com.eneik.production.repositories;

import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.models.persistence.WishlistStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface WishlistRepository extends JpaRepository<WishlistEntity, UUID> {
    List<WishlistEntity> findByProjectId(UUID projectId);
    List<WishlistEntity> findByProjectIdAndStatus(UUID projectId, WishlistStatus status);
    boolean existsByProjectIdAndSourceRoleTagAndSourceAndCreatedAtAfter(
            UUID projectId, String sourceRoleTag, WishlistSource source, Instant after);
}
