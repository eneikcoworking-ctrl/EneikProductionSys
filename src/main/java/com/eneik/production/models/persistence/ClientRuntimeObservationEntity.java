package com.eneik.production.models.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Phase 1 of docs/reports/PLAN_client_runtime_observability_2026-08-09.md: one row per ephemeral
 * launch+health-check attempt of the active project's own delivered product. This is the raw evidence
 * BetaPosterior's cadence math and (later, Phase 2) ProcessControlService's u-chart both read from -
 * never mutated after insert, append-only.
 */
@Entity
@Table(name = "client_runtime_observations")
public class ClientRuntimeObservationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt = Instant.now();

    @Column(name = "launch_success", nullable = false)
    private boolean launchSuccess;

    @Column(name = "launch_duration_ms")
    private Long launchDurationMs;

    @Column(name = "health_status_code")
    private Integer healthStatusCode;

    @Column(name = "health_latency_ms")
    private Long healthLatencyMs;

    @Column(name = "error_text", length = 4000)
    private String errorText;

    /**
     * What this row is a fact ABOUT. False - the normal case - means the launcher answered, so the row is
     * a real observation of the product, whatever the answer was. True means no answer came back at all:
     * the product was never tried, nothing was learned, and BetaPosterior must not count it. See V104 and
     * ACP-102 (Criterion Is Not The Concept).
     */
    @Column(name = "instrument_failure", nullable = false)
    private boolean instrumentFailure;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }

    public Instant getObservedAt() { return observedAt; }
    public void setObservedAt(Instant observedAt) { this.observedAt = observedAt; }

    public boolean isLaunchSuccess() { return launchSuccess; }
    public void setLaunchSuccess(boolean launchSuccess) { this.launchSuccess = launchSuccess; }

    public boolean isInstrumentFailure() { return instrumentFailure; }
    public void setInstrumentFailure(boolean instrumentFailure) { this.instrumentFailure = instrumentFailure; }

    public Long getLaunchDurationMs() { return launchDurationMs; }
    public void setLaunchDurationMs(Long launchDurationMs) { this.launchDurationMs = launchDurationMs; }

    public Integer getHealthStatusCode() { return healthStatusCode; }
    public void setHealthStatusCode(Integer healthStatusCode) { this.healthStatusCode = healthStatusCode; }

    public Long getHealthLatencyMs() { return healthLatencyMs; }
    public void setHealthLatencyMs(Long healthLatencyMs) { this.healthLatencyMs = healthLatencyMs; }

    public String getErrorText() { return errorText; }
    public void setErrorText(String errorText) { this.errorText = errorText; }
}
