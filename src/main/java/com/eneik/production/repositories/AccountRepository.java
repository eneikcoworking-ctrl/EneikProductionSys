package com.eneik.production.repositories;

import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {
    List<AccountEntity> findByProjectIdOrderByNameAsc(UUID projectId);

    List<AccountEntity> findByEnabledTrueAndProjectIsNullAndGithubUsernameIsNotNullOrderByNameAsc();

    @Query("SELECT COUNT(a) > 0 FROM AccountEntity a WHERE " +
           "a.lastHeartbeat > :threshold AND " +
           "a.capabilities LIKE %:tag%")
    boolean existsOnlineWithCapability(@Param("tag") String tag, @Param("threshold") Instant threshold);

    @Modifying
    @Query("UPDATE AccountEntity a SET a.status = :status WHERE a.id = :id")
    void updateStatus(@Param("id") UUID id, @Param("status") AccountStatus status);

    @Modifying
    @Query("UPDATE AccountEntity a SET a.currentProjectId = :newProjectId " +
           "WHERE a.currentProjectId IS NULL " +
           "OR a.currentProjectId IN (SELECT p.id FROM ProjectEntity p WHERE p.status = com.eneik.production.models.persistence.ProjectStatus.accepted)")
    void assignFreeAccountsToProject(@Param("newProjectId") UUID newProjectId);

    @Query(value = "SELECT * FROM accounts WHERE status = 'idle' AND enabled = true " +
            "AND (current_project_id IS NULL OR current_project_id = :projectId) " +
            "ORDER BY last_heartbeat DESC LIMIT 1 FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<AccountEntity> lockNextIdleAccountForProject(@Param("projectId") UUID projectId);

    long countByCurrentProjectId(UUID currentProjectId);
}
