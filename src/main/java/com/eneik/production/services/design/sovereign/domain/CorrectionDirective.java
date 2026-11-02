package com.eneik.production.services.design.sovereign.domain;

import java.util.Objects;

/**
 * Targeted DOM or CSS token adjustment directive issued by VisionAuditLoop.
 */
public record CorrectionDirective(
        String targetCssSelector,
        String suggestedRule,
        String reason,
        int roundIndex
) {
    public CorrectionDirective {
        Objects.requireNonNull(targetCssSelector, "targetCssSelector cannot be null");
        Objects.requireNonNull(suggestedRule, "suggestedRule cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
    }
}
