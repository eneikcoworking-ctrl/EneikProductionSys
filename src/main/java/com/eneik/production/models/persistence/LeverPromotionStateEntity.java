package com.eneik.production.models.persistence;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "lever_promotion_state")
public class LeverPromotionStateEntity {

    @Id
    @Column(name = "lever_key", length = 64)
    private String leverKey;

    // "observe_only" - kept as a plain literal (not a services.lever.LeverStage reference) so this
    // persistence entity has no dependency on the service-layer enum; LeverPromotionService is the single
    // canonical place that interprets/revises this string (Gärdenfors AGM, BARCAN-TAG-04 philosopher 7).
    @Column(name = "current_stage", nullable = false, length = 24)
    private String currentStage = "observe_only";

    @Column(name = "sample_count", nullable = false)
    private long sampleCount = 0;

    @Column(name = "agreement_count", nullable = false)
    private long agreementCount = 0;

    @Column(name = "promoted_at")
    private Instant promotedAt;

    @Column(name = "demoted_at")
    private Instant demotedAt;

    @Column(name = "last_evaluated_at")
    private Instant lastEvaluatedAt;

    public String getLeverKey() { return leverKey; }
    public void setLeverKey(String leverKey) { this.leverKey = leverKey; }

    public String getCurrentStage() { return currentStage; }
    public void setCurrentStage(String currentStage) { this.currentStage = currentStage; }

    public long getSampleCount() { return sampleCount; }
    public void setSampleCount(long sampleCount) { this.sampleCount = sampleCount; }

    public long getAgreementCount() { return agreementCount; }
    public void setAgreementCount(long agreementCount) { this.agreementCount = agreementCount; }

    public Instant getPromotedAt() { return promotedAt; }
    public void setPromotedAt(Instant promotedAt) { this.promotedAt = promotedAt; }

    public Instant getDemotedAt() { return demotedAt; }
    public void setDemotedAt(Instant demotedAt) { this.demotedAt = demotedAt; }

    public Instant getLastEvaluatedAt() { return lastEvaluatedAt; }
    public void setLastEvaluatedAt(Instant lastEvaluatedAt) { this.lastEvaluatedAt = lastEvaluatedAt; }
}
