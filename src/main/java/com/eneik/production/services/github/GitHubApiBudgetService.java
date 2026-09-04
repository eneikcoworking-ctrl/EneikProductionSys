package com.eneik.production.services.github;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class GitHubApiBudgetService {
    private static final Logger log = LoggerFactory.getLogger(GitHubApiBudgetService.class);
    private static final long DEFAULT_COOLDOWN_SECONDS = 300;
    public static final String DEFAULT_TOKEN_FINGERPRINT = "default";

    /**
     * Rigid designator token fingerprint (SOL_KRIPKE_02_INDEXICAL_CONTEXT_LOCK).
     * Prevents cross-token budget leakage and protects secrets by computing an irreversible SHA-256 hash.
     */
    public static String fingerprint(String token) {
        if (token == null || token.isBlank()) {
            return DEFAULT_TOKEN_FINGERPRINT;
        }
        String raw = token.trim();
        if (raw.regionMatches(true, 0, "Bearer ", 0, 7)) {
            raw = raw.substring(7).trim();
        } else if (raw.regionMatches(true, 0, "token ", 0, 6)) {
            raw = raw.substring(6).trim();
        }
        if (raw.isEmpty()) {
            return DEFAULT_TOKEN_FINGERPRINT;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(raw.hashCode());
        }
    }

    /**
     * Isolated budget state and spend tracking for an individual token context (Law 13 / Kripke Context Lock).
     */
    public static class TokenBudget {
        private final String tokenFingerprint;
        private final AtomicReference<BudgetState> state = new AtomicReference<>(BudgetState.initial());
        private final ConcurrentHashMap<String, Long> spend = new ConcurrentHashMap<>();

        public TokenBudget(String tokenFingerprint) {
            this.tokenFingerprint = tokenFingerprint;
        }

        public String getTokenFingerprint() {
            return tokenFingerprint;
        }

        public BudgetState getState() {
            return state.get();
        }

        public BudgetState getAndSetState(BudgetState next) {
            return state.getAndSet(next);
        }

        public ConcurrentHashMap<String, Long> getSpend() {
            return spend;
        }

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

        public void countSpend(BudgetState previous, Instant resetAt, String operation) {
            Instant previousReset = previous.resetAt();
            if (resetAt != null && previousReset != null && !resetAt.equals(previousReset)) {
                spend.clear();
            }
            spend.merge(normalizeOperation(operation), 1L, Long::sum);
        }

        public Map<String, Long> spendByOperation() {
            return spend.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .collect(java.util.LinkedHashMap::new,
                            (m, e) -> m.put(e.getKey(), e.getValue()),
                            java.util.LinkedHashMap::putAll);
        }
    }

    private final ConcurrentHashMap<String, TokenBudget> budgets = new ConcurrentHashMap<>();
    private final AtomicReference<String> lastActiveFingerprint = new AtomicReference<>(DEFAULT_TOKEN_FINGERPRINT);

    public TokenBudget budgetFor(String token) {
        String fp = fingerprint(token);
        lastActiveFingerprint.set(fp);
        return budgets.computeIfAbsent(fp, TokenBudget::new);
    }

    public GuardDecision guard(String token, String operation) {
        return budgetFor(token).guard(operation);
    }

    public GuardDecision guard(String operation) {
        String lastFp = lastActiveFingerprint.get();
        TokenBudget tb = budgets.get(lastFp);
        return tb != null ? tb.guard(operation) : budgetFor(null).guard(operation);
    }

    public boolean available(String token) {
        return budgetFor(token).available();
    }

    public boolean available() {
        if (budgets.isEmpty()) {
            return true;
        }
        return budgets.values().stream().anyMatch(TokenBudget::available);
    }

    public Snapshot snapshot(String token) {
        return budgetFor(token).snapshot();
    }

    public Snapshot snapshot() {
        String lastFp = lastActiveFingerprint.get();
        TokenBudget tb = budgets.get(lastFp);
        if (tb == null) {
            tb = budgetFor(null);
        }
        return tb.snapshot();
    }

    public void recordResponse(String token, String operation, HttpResponse<?> response) {
        if (response == null) {
            return;
        }
        recordResponse(token, operation, response.statusCode(), response.headers(), response.body() == null ? "" : response.body().toString());
    }

    public void recordResponse(String operation, HttpResponse<?> response) {
        if (response == null) {
            return;
        }
        String token = null;
        if (response.request() != null) {
            token = response.request().headers().firstValue("Authorization").orElse(null);
        }
        recordResponse(token, operation, response.statusCode(), response.headers(), response.body() == null ? "" : response.body().toString());
    }

    public void recordResponse(String token, String operation, int statusCode, HttpHeaders headers, String body) {
        TokenBudget budget = budgetFor(token);
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
            reason = "GitHub API rate limit exhausted for token [" + budget.getTokenFingerprint() + "]; suppressing further calls until reset.";
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
        BudgetState previous = budget.getAndSetState(next);
        budget.countSpend(previous, resetAt, operation);
        if ("exhausted".equals(status)
                && (previous.cooldownUntil() == null || !previous.cooldownUntil().equals(cooldownUntil))) {
            log.warn("[GITHUB-BUDGET] Rate limit exhausted for token [{}]; cooldownUntil={}. Spent this window by operation: {}",
                    budget.getTokenFingerprint(), cooldownUntil, budget.spendByOperation());
        }
    }

    void recordResponse(String operation, int statusCode, HttpHeaders headers, String body) {
        String token = headers != null ? headers.firstValue("Authorization").orElse(null) : null;
        recordResponse(token, operation, statusCode, headers, body);
    }

    static String normalizeOperation(String operation) {
        if (operation == null || operation.isBlank()) {
            return "(unnamed)";
        }
        int query = operation.indexOf('?');
        return query < 0 ? operation : operation.substring(0, query);
    }

    public Map<String, Long> spendByOperation(String token) {
        return budgetFor(token).spendByOperation();
    }

    public Map<String, Long> spendByOperation() {
        String lastFp = lastActiveFingerprint.get();
        TokenBudget tb = budgets.get(lastFp);
        return tb != null ? tb.spendByOperation() : budgetFor(null).spendByOperation();
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
