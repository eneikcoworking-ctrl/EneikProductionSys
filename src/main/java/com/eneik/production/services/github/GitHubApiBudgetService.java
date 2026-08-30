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

    /** Per-operation spend for the current rate-limit window - see countSpend (model rule 8.17). */
    private final java.util.concurrent.ConcurrentHashMap<String, Long> spend = new java.util.concurrent.ConcurrentHashMap<>();

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
                current.updatedAt(),
                spendByOperation()
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
        countSpend(previous, resetAt, operation);
        if ("exhausted".equals(status)
                && (previous.cooldownUntil() == null || !previous.cooldownUntil().equals(cooldownUntil))) {
            log.warn("[GITHUB-BUDGET] Rate limit exhausted; cooldownUntil={}. Spent this window by operation: {}",
                    cooldownUntil, spendByOperation());
        }
    }

    /**
     * What spent this window, per operation (model rule 8.17).
     *
     * <p>An admission order says who yields to whom, and it cannot be applied to spend that is not
     * attributable. The hourly GitHub limit is shared capacity of exactly that kind, and exhausting it stops
     * the whole flow - GITHUB_RATE_LIMITED is globally blocking. Measured 2026-08-30: 5000 of 5000 spent,
     * the entire project frozen, and the only thing recorded was the name of the LAST call. Measured again
     * two minutes after the reset: 234 calls gone, which is the whole window inside forty-three minutes -
     * so this recurs every hour until someone can see who is spending it.
     *
     * <p>The key is the operation with its query string removed. Without that the key set is unbounded -
     * page numbers alone would grow it without limit - and an unbounded tally is a leak, not an account.
     */
    private void countSpend(BudgetState previous, Instant resetAt, String operation) {
        Instant previousReset = previous.resetAt();
        if (resetAt != null && previousReset != null && !resetAt.equals(previousReset)) {
            spend.clear();
        }
        spend.merge(normalizeOperation(operation), 1L, Long::sum);
    }

    static String normalizeOperation(String operation) {
        if (operation == null || operation.isBlank()) {
            return "(unnamed)";
        }
        int query = operation.indexOf('?');
        return query < 0 ? operation : operation.substring(0, query);
    }

    /** The window's tally, heaviest first, so a reader sees the cause before the noise. */
    public java.util.Map<String, Long> spendByOperation() {
        return spend.entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(java.util.LinkedHashMap::new,
                        (m, e) -> m.put(e.getKey(), e.getValue()),
                        java.util.LinkedHashMap::putAll);
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
            Instant updatedAt,
            /** What spent this window, heaviest first - model rule 8.17: spend must be attributable. */
            Map<String, Long> spendByOperation
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
                    Map.entry("updatedAt", updatedAt == null ? "" : updatedAt),
                    Map.entry("spendByOperation", spendByOperation == null ? Map.of() : spendByOperation)
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
