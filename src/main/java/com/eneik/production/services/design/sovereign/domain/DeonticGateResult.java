package com.eneik.production.services.design.sovereign.domain;

/**
 * Audit result of deontic quality gates.
 */
public record DeonticGateResult(
        boolean zeroLeicaGpsNoise,
        boolean noMobileHorizontalScroll,
        boolean focusVisibleAccessible,
        boolean contrastRatioValid
) {
    public boolean allGatesPassed() {
        return zeroLeicaGpsNoise && noMobileHorizontalScroll && focusVisibleAccessible && contrastRatioValid;
    }
}
