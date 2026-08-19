package com.eneik.production.services.runtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

/**
 * Thin HTTP client to the runtime-launcher sidecar (Phase 1, docs/reports/PLAN_client_runtime_observability_2026-08-09.md).
 * Deliberately the ONLY thing in the main backend that knows the sidecar exists - it never touches
 * Docker itself, only this narrow, purpose-built HTTP contract (launch/healthcheck/teardown).
 */
@Service
public class RuntimeLauncherClient {
    private final RestTemplate restTemplate;
    private final String baseUrl;

    public RuntimeLauncherClient(RestTemplateBuilder restTemplateBuilder,
                                  @Value("${runtime.launcher.url}") String baseUrl) {
        // Launch involves a fresh `docker compose up --build`, genuinely slow for a real Spring Boot
        // stack - generous read timeout, matches the sidecar's own 600s subprocess timeout.
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(600))
                .build();
        this.baseUrl = baseUrl;
    }

    /**
     * 2026-08-19: `success=false` used to carry two different facts - "the launcher answered and the
     * launch failed" (about the PRODUCT) and "the launcher never answered" (about the INSTRUMENT). The
     * second was written into the product's own observation history, so an instrument fault made the
     * factory believe the product was broken, and - because each recorded failure narrows the posterior -
     * pushed the next attempt further away. Feedback with the wrong sign. See ACP-102, Criterion Is Not
     * The Concept: "the launch call returned success" is not "the product launched".
     *
     * {@code observed} is false only when no answer came back at all. A launch the launcher itself
     * reports as failed is fully observed and stays a real negative observation.
     */
    public record LaunchResult(boolean success, long durationMs, String error, Integer externalPort,
                               boolean observed) {
        /** An answered call - whatever the answer was. */
        static LaunchResult answered(boolean success, long durationMs, String error, Integer externalPort) {
            return new LaunchResult(success, durationMs, error, externalPort, true);
        }

        /** No answer: nothing was learned about the product. Never a negative observation. */
        static LaunchResult unobserved(String error) {
            return new LaunchResult(false, 0, error, null, false);
        }
    }

    public record HealthCheckResult(Integer statusCode, long latencyMs, String error) {
    }

    public record FetchResult(Integer statusCode, String body, long latencyMs, String error) {
    }

    public LaunchResult launch(String repoUrl, String ref, String projectSlug) {
        try {
            Map<String, Object> body = Map.of("repo_url", repoUrl, "ref", ref, "project_slug", projectSlug);
            var response = restTemplate.postForObject(baseUrl + "/launch", body, Map.class);
            if (response == null) {
                // A 2xx with no body tells us nothing about the product either.
                return LaunchResult.unobserved("empty response from runtime-launcher");
            }
            Object externalPort = response.get("external_port");
            return LaunchResult.answered(
                    Boolean.TRUE.equals(response.get("success")),
                    ((Number) response.getOrDefault("duration_ms", 0)).longValue(),
                    (String) response.get("error"),
                    externalPort == null ? null : ((Number) externalPort).intValue());
        } catch (RestClientException e) {
            return LaunchResult.unobserved("runtime-launcher unreachable: " + e.getMessage());
        }
    }

    public HealthCheckResult healthcheck(String url) {
        try {
            Map<String, Object> body = Map.of("url", url);
            var response = restTemplate.postForObject(baseUrl + "/healthcheck", body, Map.class);
            if (response == null) {
                return new HealthCheckResult(null, 0, "empty response from runtime-launcher");
            }
            Object statusCode = response.get("status_code");
            return new HealthCheckResult(
                    statusCode == null ? null : ((Number) statusCode).intValue(),
                    ((Number) response.getOrDefault("latency_ms", 0)).longValue(),
                    (String) response.get("error"));
        } catch (RestClientException e) {
            return new HealthCheckResult(null, 0, "runtime-launcher unreachable: " + e.getMessage());
        }
    }

    /** Same reachability path as {@link #healthcheck}, but returns the body too - the design shop's
     * Stage 4 live-drift check needs the real served HTML/CSS, not just a status code. */
    public FetchResult fetchHtml(String url) {
        try {
            Map<String, Object> body = Map.of("url", url);
            var response = restTemplate.postForObject(baseUrl + "/fetch", body, Map.class);
            if (response == null) {
                return new FetchResult(null, null, 0, "empty response from runtime-launcher");
            }
            Object statusCode = response.get("status_code");
            return new FetchResult(
                    statusCode == null ? null : ((Number) statusCode).intValue(),
                    (String) response.get("body"),
                    ((Number) response.getOrDefault("latency_ms", 0)).longValue(),
                    (String) response.get("error"));
        } catch (RestClientException e) {
            return new FetchResult(null, null, 0, "runtime-launcher unreachable: " + e.getMessage());
        }
    }

    public void teardown() {
        try {
            restTemplate.postForObject(baseUrl + "/teardown", Map.of(), Map.class);
        } catch (RestClientException e) {
            // Best-effort - a failed teardown leaves an orphaned ephemeral stack on the launcher's own
            // host, never something the main factory's own state depends on being clean.
        }
    }
}
