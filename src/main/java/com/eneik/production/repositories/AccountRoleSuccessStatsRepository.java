package com.eneik.production.repositories;

import com.eneik.production.models.persistence.AccountRoleSuccessStatsEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRoleSuccessStatsRepository extends JpaRepository<AccountRoleSuccessStatsEntity, UUID> {
    Optional<AccountRoleSuccessStatsEntity> findByAccountIdAndRoleTag(UUID accountId, String roleTag);
}
