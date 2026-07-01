package com.eneik.production.controllers;

import com.eneik.production.config.LeaseConfig;
import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.AccountStatus;
import com.eneik.production.models.persistence.ClaimEntity;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.ClaimRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final ClaimRepository claimRepository;
    private final AccountRepository accountRepository;
    private final LeaseConfig leaseConfig;

    public TaskController(ClaimRepository claimRepository, AccountRepository accountRepository, LeaseConfig leaseConfig) {
        this.claimRepository = claimRepository;
        this.accountRepository = accountRepository;
        this.leaseConfig = leaseConfig;
    }

    @PostMapping("/{claimId}/heartbeat")
    @Transactional
    public ResponseEntity<Void> heartbeat(@PathVariable UUID claimId) {
        ClaimEntity claim = claimRepository.findById(claimId).orElse(null);

        if (claim == null || claim.getReleasedAt() != null) {
            return ResponseEntity.notFound().build();
        }

        Instant now = Instant.now();
        int ttlSeconds = leaseConfig.getTtlForTag(claim.getRole().getTag());
        claim.setLeaseExpiresAt(now.plusSeconds(ttlSeconds));
        claimRepository.save(claim);

        AccountEntity account = claim.getAccount();
        account.setLastHeartbeat(now);
        if (account.getStatus() == AccountStatus.offline) {
            account.setStatus(AccountStatus.busy);
        }
        accountRepository.save(account);

        return ResponseEntity.ok().build();
    }
}
