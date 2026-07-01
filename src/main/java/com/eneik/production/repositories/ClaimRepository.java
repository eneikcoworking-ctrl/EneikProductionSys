package com.eneik.production.repositories;

import com.eneik.production.models.persistence.ClaimEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface ClaimRepository extends JpaRepository<ClaimEntity, UUID> {
    List<ClaimEntity> findByReleasedAtIsNullAndLeaseExpiresAtBefore(Instant now);
}
