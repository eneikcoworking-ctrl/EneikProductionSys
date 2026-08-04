package com.eneik.production.models.persistence;

public enum WishlistSource {
    // idle_generation removed (2026-07-25, operator directive: "система сама придумывает улучшения... это
    // убрать. опасно" - a system that invents its own scope when idle was judged a real risk, not a
    // useful source). It was already dead code - declared but never actually produced by anything in this
    // codebase (confirmed by grep across backend, ml service, and frontend before removal) - so this is a
    // permanent guard against ever wiring it up later, not a behavior change.
    client, role, role_mismatch_followup, chaotic_debt, self_falsification, onboarding_finding, coverage_gap, closeout_abandoned, philosophical_falsification, gemini_observer,
    // 2026-08-04 (Phase B, design/QA acceptance redesign): audit-trail record for
    // DesignSystemFalsificationService - NOT a compiler input, records that a Stitch design-system pass
    // was applied to an epic's already-shipped real UI. See WishlistRepository.existsByFeatureIdAndSource
    // for the idempotency check that reads this.
    design_system_falsification
}
