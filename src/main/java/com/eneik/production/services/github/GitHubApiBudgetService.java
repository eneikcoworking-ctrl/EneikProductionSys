package com.eneik.production.services.github;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class GitHubApiBudgetService {
    private static final Logger log = LoggerFactory.getLogger(GitHubApiBudgetService.class);
    private static final long DEFAULT_COOLDOWN_SECONDS = 300;

    private final AtomicReference<BudgetState> state = new AtomicReference<>(BudgetState.initial());

    public GuardDecision guard(String operation) {
        BudgetState current = state.get();
        Instant now = Instant.now();
        if (current.cooldownUntil() != null && current.cooldownUntil().isAfter(now)) {
            return new GuardDecision(
                    false,
                    current.status(),
                    current.reason(),
                    current.cooldownUntil(),
                    current.remaining(),
                    current.limit(),
                    current.resetAt(),
                    current.lastOperation()
            );
        }
        return new GuardDecision(true, "available", "GitHub API budget is available",
                null, current.remaining(), current.limit(), current.resetAt(), operation);
    }

    public boolean available() {
        return guard("generic").allowed();
    }

    public Snapshot snapshot() {
        BudgetState current = state.get();
        GuardDecision guard = guard("snapshot");
        String visibleStatus = guard.allowed() && "exhausted".equals(current.status())
                ? "available"
                : (guard.allowed() ? current.status() : "exhausted");
        return new Snapshot(
                visibleStatus,
                guard.allowed(),
                current.limit(),
                current.remaining(),
                current.used(),
                current.resetAt(),
                current.cooldownUntil(),
                current.lastStatusCode(),
                current.lastOperation(),
                guard.allowed() ? guard.reason() : current.reason(),
                current.updatedAt()
        );
    }

    public void recordResponse(String operation, HttpResponse<?> response) {
        if (response == null) {
            return;
        }
        recordResponse(operation, response.statusCode(), response.headers(), response.body() == null ? "" : response.body().toString());
    }

    void recordResponse(String operation, int statusCode, HttpHeaders headers, String body) {
        Integer limit = intHeader(headers, "x-ratelimit-limit").orElse(null);
        Integer remaining = intHeader(headers, "x-ratelimit-remaining").orElse(null);
        Integer used = intHeader(headers, "x-ratelimit-used").orElse(null);
        Instant resetAt = epochHeader(headers, "x-ratelimit-reset").orElse(null);
        Instant retryAfter = retryAfter(headers).orElse(null);
        Instant cooldownUntil = null;
        String status = "available";
        String reason = "GitHub API budget is available";

        if (isRateLimited(statusCode, remaining, body)) {
            status = "exhausted";
            cooldownUntil = firstFuture(retryAfter, resetAt, Instant.now().plusSeconds(DEFAULT_COOLDOWN_SECONDS));
            reason = "GitHub API rate limit exhausted; suppressing further GitHub API calls until reset.";
        }

        BudgetState next = new BudgetState(
                status,
                limit,
                remaining,
                used,
                resetAt,
                cooldownUntil,
                statusCode,
                operation,
                reason,
                Instant.now()
        );
        BudgetState previous = state.getAndSet(next);
        if ("exhausted".equals(status)
                && (previous.cooldownUntil() == null || !previous.cooldownUntil().equals(cooldownUntil))) {
            log.warn("[GITHUB-BUDGET] Rate limit exhausted by operation '{}'; cooldownUntil={}", operation, cooldownUntil);
        }
    }

    private boolean isRateLimited(int statusCode, Integer remaining, String body) {
        if (remaining != null && remaining == 0) {
            return true;
        }
        String normalized = body == null ? "" : body.toLowerCase(Locale.ROOT);
        return (statusCode == 403 || statusCode == 429)
                && (normalized.contains("rate limit") || normalized.contains("secondary rate limit"));
    }

    private Optional<Integer> intHeader(HttpHeaders headers, String name) {
        return headers.firstValue(name)
                .flatMap(value -> {
                    try {
                        return Optional.of(Integer.parseInt(value));
                    } catch (NumberFormatException e) {
                        return Optional.empty();
                    }
                });
    }

    private Optional<Instant> epochHeader(HttpHeaders headers, String name) {
        return headers.firstValue(name)
                .flatMap(value -> {
                    try {
                        return Optional.of(Instant.ofEpochSecond(Long.parseLong(value)));
                    } catch (NumberFormatException e) {
                        return Optional.empty();
                    }
                });
    }

    private Optional<Instant> retryAfter(HttpHeaders headers) {
        return headers.firstValue("retry-after")
                .flatMap(value -> {
                    try {
                        return Optional.of(Instant.now().plusSeconds(Long.parseLong(value)));
                    } catch (NumberFormatException e) {
                        return Optional.empty();
                    }
                });
    }

    private Instant firstFuture(Instant... candidates) {
        Instant now = Instant.now();
        for (Instant candidate : candidates) {
            if (candidate != null && candidate.isAfter(now)) {
                return candidate;
            }
        }
        return now.plusSeconds(DEFAULT_COOLDOWN_SECONDS);
    }

    public record GuardDecision(
            boolean allowed,
            String status,
            String reason,
            Instant cooldownUntil,
            Integer remaining,
            Integer limit,
            Instant resetAt,
            String lastOperation
    ) {
    }

    public record Snapshot(
            String status,
            boolean available,
            Integer limit,
            Integer remaining,
            Integer used,
            Instant resetAt,
            Instant cooldownUntil,
            Integer lastStatusCode,
            String lastOperation,
            String reason,
            Instant updatedAt
    ) {
        public Map<String, Object> asMap() {
            return Map.ofEntries(
                    Map.entry("status", status),
                    Map.entry("available", available),
                    Map.entry("limit", limit == null ? "" : limit),
                    Map.entry("remaining", remaining == null ? "" : remaining),
                    Map.entry("used", used == null ? "" : used),
                    Map.entry("resetAt", resetAt == null ? "" : resetAt),
                    Map.entry("cooldownUntil", cooldownUntil == null ? "" : cooldownUntil),
                    Map.entry("lastStatusCode", lastStatusCode == null ? "" : lastStatusCode),
                    Map.entry("lastOperation", lastOperation == null ? "" : lastOperation),
                    Map.entry("reason", reason == null ? "" : reason),
                    Map.entry("updatedAt", updatedAt == null ? "" : updatedAt)
            );
        }
    }

    private record BudgetState(
            String status,
            Integer limit,
            Integer remaining,
            Integer used,
            Instant resetAt,
            Instant cooldownUntil,
            Integer lastStatusCode,
            String lastOperation,
            String reason,
            Instant updatedAt
    ) {
        private static BudgetState initial() {
            return new BudgetState("unknown", null, null, null, null, null, null,
                    "", "GitHub API budget has not been observed in this runtime yet.", Instant.now());
        }
    }
}
