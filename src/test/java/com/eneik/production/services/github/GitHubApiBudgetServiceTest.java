package com.eneik.production.services.github;

import org.junit.jupiter.api.Test;

import java.net.http.HttpHeaders;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHubApiBudgetServiceTest {

    @Test
    void startsAvailableButUnknownUntilFirstGithubResponse() {
        GitHubApiBudgetService service = new GitHubApiBudgetService();

        GitHubApiBudgetService.Snapshot snapshot = service.snapshot();

        assertTrue(snapshot.available());
        assertEquals("unknown", snapshot.status());
    }

    @Test
    void rateLimitResponseOpensCooldownAndBlocksNextCall() {
        GitHubApiBudgetService service = new GitHubApiBudgetService();
        HttpHeaders headers = HttpHeaders.of(Map.of(
                "x-ratelimit-limit", List.of("5000"),
                "x-ratelimit-remaining", List.of("0"),
                "x-ratelimit-used", List.of("5000"),
                "x-ratelimit-reset", List.of(String.valueOf(Instant.now().plusSeconds(120).getEpochSecond()))
        ), (name, value) -> true);

        service.recordResponse("GET /repos/org/repo/pulls", 403, headers, "{\"message\":\"API rate limit exceeded\"}");

        GitHubApiBudgetService.Snapshot snapshot = service.snapshot();
        assertFalse(snapshot.available());
        assertEquals("exhausted", snapshot.status());
        assertEquals(0, snapshot.remaining());
        assertFalse(service.guard("GET /repos/org/repo/pulls?state=open").allowed());
    }

    @Test
    void normalResponseLeavesBudgetAvailable() {
        GitHubApiBudgetService service = new GitHubApiBudgetService();
        HttpHeaders headers = HttpHeaders.of(Map.of(
                "x-ratelimit-limit", List.of("5000"),
                "x-ratelimit-remaining", List.of("4999"),
                "x-ratelimit-used", List.of("1")
        ), (name, value) -> true);

        service.recordResponse("GET /repos/org/repo/pulls", 200, headers, "[]");

        GitHubApiBudgetService.Snapshot snapshot = service.snapshot();
        assertTrue(snapshot.available());
        assertEquals("available", snapshot.status());
        assertEquals(4999, snapshot.remaining());
    }
}
