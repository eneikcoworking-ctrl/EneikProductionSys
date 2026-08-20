package com.eneik.production.models.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * One observation of one declared capability against the running product.
 *
 * This is the product layer's missing unit. `SixSigmaAuditService`'s "Product" number counts quality-gate
 * checks, PR conflicts and code-integrity findings - all facts about how the work was made, none of them a
 * defect a user could experience. An observation here is an opportunity in the Six Sigma sense and a
 * failure is a defect in the sense that matters: the product asserted it could do something, and it could
 * not.
 *
 * The capability set is declared by the product's own OpenAPI contract, so the denominator comes from what
 * the product asserts rather than from the factory's decomposition (Charter invariant 8), and the witness
 * is the launcher, external to whoever wrote the code (invariant 12).
 */
@Entity
@Table(name = "capability_observations")
public class CapabilityObservationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    /** "<METHOD> <path>" exactly as the contract declares it - a rigid designator across observations. */
    @Column(name = "capability_key", nullable = false, length = 300)
    private String capabilityKey;

    @Column(name = "source_contract", length = 400)
    private String sourceContract;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt = Instant.now();

    @Column(nullable = false)
    private boolean satisfied;

    @Column(name = "status_code")
    private Integer statusCode;

    @Column(length = 2000)
    private String detail;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }

    public String getCapabilityKey() { return capabilityKey; }
    public void setCapabilityKey(String capabilityKey) { this.capabilityKey = capabilityKey; }

    public String getSourceContract() { return sourceContract; }
    public void setSourceContract(String sourceContract) { this.sourceContract = sourceContract; }

    public Instant getObservedAt() { return observedAt; }
    public void setObservedAt(Instant observedAt) { this.observedAt = observedAt; }

    public boolean isSatisfied() { return satisfied; }
    public void setSatisfied(boolean satisfied) { this.satisfied = satisfied; }

    public Integer getStatusCode() { return statusCode; }
    public void setStatusCode(Integer statusCode) { this.statusCode = statusCode; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
}
