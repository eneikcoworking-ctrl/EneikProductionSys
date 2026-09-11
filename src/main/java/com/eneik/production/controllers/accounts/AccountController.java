package com.eneik.production.controllers.accounts;

import com.eneik.production.dto.AccountDto;
import com.eneik.production.dto.AccountRequestDto;
import com.eneik.production.dto.AccountStatusRequestDto;
import com.eneik.production.models.persistence.AccountEntity;
import com.eneik.production.models.persistence.AccountStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.services.jules.JulesRoleCapabilities;
import com.eneik.production.kaizen.model.DefectJournalEntity;
import com.eneik.production.kaizen.repository.DefectJournalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    private static final Logger log = LoggerFactory.getLogger(AccountController.class);

    private final AccountRepository accountRepository;
    private final DefectJournalRepository defectJournalRepository;

    public AccountController(AccountRepository accountRepository,
                             DefectJournalRepository defectJournalRepository) {
        this.accountRepository = accountRepository;
        this.defectJournalRepository = defectJournalRepository;
    }

    @GetMapping
    public List<AccountDto> list() {
        return accountRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountDto> get(@PathVariable UUID id) {
        return accountRepository.findById(id)
                .map(account -> ResponseEntity.ok(toDto(account)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> create(@jakarta.validation.Valid @RequestBody AccountRequestDto request) {
        String validationError = validate(request);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", validationError, "code", 400));
        }

        AccountEntity account = new AccountEntity();
        account.setName(request.name().trim());
        account.setCapabilities(JulesRoleCapabilities.canonicalCapabilities());
        account.setStatus(AccountStatus.idle);
        account.setLastHeartbeat(Instant.now());
        account.setGithubUsername(request.githubUsername() != null ? request.githubUsername().trim() : null);
        account.setApiKey(request.apiKey());

        if (account.getGithubUsername() == null || account.getGithubUsername().trim().isEmpty()) {
            account.setGithubUsername(account.getName());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(accountRepository.save(account)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable UUID id, @RequestBody Map<String, Object> updates) {
        return accountRepository.findById(id)
                .<ResponseEntity<?>>map(account -> {
                    boolean wasEnabled = account.isEnabled();
                    AccountStatus wasStatus = account.getStatus();
                    String reason = updates.containsKey("reason")
                            ? String.valueOf(updates.get("reason")).trim()
                            : "Administrative update via PATCH /api/accounts/{id}";

                    if (updates.containsKey("name")) {
                        account.setName(((String) updates.get("name")).trim());
                    }
                    if (updates.containsKey("githubUsername")) {
                        account.setGithubUsername((String) updates.get("githubUsername"));
                    }
                    if (updates.containsKey("capabilities")) {
                        account.setCapabilities(JulesRoleCapabilities.canonicalCapabilities());
                    }
                    if (updates.containsKey("apiKey")) {
                        account.setApiKey((String) updates.get("apiKey"));
                    }
                    if (updates.containsKey("status")) {
                        AccountStatus newStatus = AccountStatus.valueOf((String) updates.get("status"));
                        account.setStatus(newStatus);
                    }
                    if (updates.containsKey("enabled")) {
                        boolean newEnabled = (Boolean) updates.get("enabled");
                        if (newEnabled && account.getStatus() == AccountStatus.decommissioned) {
                            return ResponseEntity.badRequest().body(Map.of(
                                    "error", "Account in status 'decommissioned' cannot be enabled; change status to operational first",
                                    "code", 400));
                        }
                        account.setEnabled(newEnabled);
                    }
                    if (updates.containsKey("maxConcurrentSessions")) {
                        Object raw = updates.get("maxConcurrentSessions");
                        account.setMaxConcurrentSessions(raw == null ? null : ((Number) raw).intValue());
                    }

                    if (account.getGithubUsername() == null || account.getGithubUsername().trim().isEmpty()) {
                        account.setGithubUsername(account.getName());
                    }

                    // INSTITUTIONAL_FACT_REGISTER (D007) / ACTUAL_OBJECT_REGISTER (D002):
                    // Record an institutional fact audit record when status or enablement changes.
                    if (account.isEnabled() != wasEnabled || account.getStatus() != wasStatus) {
                        String rule = (account.getStatus() == AccountStatus.decommissioned || wasStatus == AccountStatus.decommissioned)
                                ? "ACCOUNT_DECOMMISSION_RULE"
                                : (!account.isEnabled() || !wasEnabled)
                                ? "ACCOUNT_LIFECYCLE_ENABLEMENT_RULE"
                                : "ACCOUNT_OPERATIONAL_STATUS_RULE";
                        String description = String.format("Account '%s' state transition [status: %s -> %s, enabled: %s -> %s]. Rule: %s. Reason: %s",
                                account.getName(), wasStatus, account.getStatus(), wasEnabled, account.isEnabled(), rule, reason);
                        defectJournalRepository.save(new DefectJournalEntity(
                                null, null, null, "MEDIUM", "ACCOUNT_LIFECYCLE", account.getName(),
                                "ACCOUNT_STATE_TRANSITION",
                                description,
                                account.isEnabled() ? 1.0 : 0.0));
                        log.info("Institutional Fact Audit: {}", description);
                    }

                    return ResponseEntity.ok(toDto(accountRepository.save(account)));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable UUID id, @RequestBody AccountStatusRequestDto request) {
        return applyStatus(id, request);
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<?> postStatus(@PathVariable UUID id, @RequestBody AccountStatusRequestDto request) {
        return applyStatus(id, request);
    }

    private ResponseEntity<?> applyStatus(UUID id, AccountStatusRequestDto request) {
        if (request.status() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "status is required", "code", 400));
        }

        return accountRepository.findById(id)
                .<ResponseEntity<?>>map(account -> {
                    AccountStatus wasStatus = account.getStatus();
                    boolean wasEnabled = account.isEnabled();
                    account.setStatus(request.status());

                    if (account.isEnabled() != wasEnabled || account.getStatus() != wasStatus) {
                        String rule = account.getStatus() == AccountStatus.decommissioned
                                ? "ACCOUNT_DECOMMISSION_RULE"
                                : "ACCOUNT_OPERATIONAL_STATUS_RULE";
                        String description = String.format("Account '%s' state transition [status: %s -> %s, enabled: %s -> %s]. Rule: %s. Reason: %s",
                                account.getName(), wasStatus, account.getStatus(), wasEnabled, account.isEnabled(), rule, "Status updated via /status endpoint");
                        defectJournalRepository.save(new DefectJournalEntity(
                                null, null, null, "MEDIUM", "ACCOUNT_LIFECYCLE", account.getName(),
                                "ACCOUNT_STATE_TRANSITION",
                                description,
                                account.isEnabled() ? 1.0 : 0.0));
                        log.info("Institutional Fact Audit: {}", description);
                    }

                    return ResponseEntity.ok(toDto(accountRepository.save(account)));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/heartbeat")
    public ResponseEntity<?> heartbeat(@PathVariable UUID id) {
        return accountRepository.findById(id)
                .<ResponseEntity<?>>map(account -> {
                    account.setLastHeartbeat(Instant.now());
                    if (account.getStatus() == AccountStatus.offline) {
                        account.setStatus(AccountStatus.idle);
                    }
                    return ResponseEntity.ok(toDto(accountRepository.save(account)));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        if (!accountRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        accountRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private AccountDto toDto(AccountEntity account) {
        String masked = null;
        if (account.getApiKey() != null && !account.getApiKey().isBlank()) {
            String raw = account.getApiKey();
            masked = raw.length() > 8 ? raw.substring(0, 4) + "..." + raw.substring(raw.length() - 4) : "****";
        }
        return new AccountDto(
                account.getId(),
                account.getName(),
                account.getStatus(),
                account.getCapabilities(),
                account.getLastHeartbeat(),
                account.getCurrentProjectId(),
                masked,
                account.getGithubUsername(),
                account.isEnabled(),
                account.getMaxConcurrentSessions()
        );
    }

    private String validate(AccountRequestDto request) {
        if (request == null || request.name() == null || request.name().trim().isEmpty()) {
            return "name is required";
        }
        return null;
    }
}
