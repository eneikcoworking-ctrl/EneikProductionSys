package com.eneik.production.repositories;

import com.eneik.production.models.persistence.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<AccountEntity, UUID> {
    @Query("SELECT COUNT(a) > 0 FROM AccountEntity a WHERE " +
           "a.lastHeartbeat > :threshold AND " +
           "a.capabilities LIKE %:tag%")
    boolean existsOnlineWithCapability(@Param("tag") String tag, @Param("threshold") Instant threshold);
}
