package com.eneik.production.services.monitor;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks whether Gemini/ML-service calls are actually succeeding, per call site - process-activity
 * monitoring (is a dispatch happening, is a PR opening) gave zero signal that task-slice generation had
 * been silently falling back to fabricated placeholder content for hours, across two separate runs, until
 * a human manually read the actual PR titles. This is the missing content-truthfulness signal: a call
 * site failing repeatedly is exactly the mechanism that produced that failure, and now it's visible
 * without anyone having to notice weird titles by eye.
 */
@Service
public class AiHealthTracker {
    public record CallSiteHealth(long successCount, long failureCount, Instant lastSuccessAt, Instant lastFailureAt, String lastFailureReason) {}

    private final Map<String, AtomicLong> successCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> failureCounts = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastSuccessAt = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastFailureAt = new ConcurrentHashMap<>();
    private final Map<String, String> lastFailureReason = new ConcurrentHashMap<>();

    public void recordSuccess(String callSite) {
        successCounts.computeIfAbsent(callSite, k -> new AtomicLong()).incrementAndGet();
        lastSuccessAt.put(callSite, Instant.now());
    }

    public void recordFailure(String callSite, String reason) {
        failureCounts.computeIfAbsent(callSite, k -> new AtomicLong()).incrementAndGet();
        lastFailureAt.put(callSite, Instant.now());
        lastFailureReason.put(callSite, reason == null ? "" : reason);
    }

    public Map<String, CallSiteHealth> snapshot() {
        Map<String, CallSiteHealth> result = new ConcurrentHashMap<>();
        java.util.Set<String> callSites = new java.util.HashSet<>();
        callSites.addAll(successCounts.keySet());
        callSites.addAll(failureCounts.keySet());
        for (String callSite : callSites) {
            result.put(callSite, new CallSiteHealth(
                    successCounts.getOrDefault(callSite, new AtomicLong()).get(),
                    failureCounts.getOrDefault(callSite, new AtomicLong()).get(),
                    lastSuccessAt.get(callSite),
                    lastFailureAt.get(callSite),
                    lastFailureReason.get(callSite)
            ));
        }
        return result;
    }
}
