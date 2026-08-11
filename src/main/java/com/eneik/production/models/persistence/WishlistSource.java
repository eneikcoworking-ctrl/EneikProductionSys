package com.eneik.production.models.persistence;

public enum WishlistSource {
    // idle_generation removed (2026-07-25, operator directive: "система сама придумывает улучшения... это
    // убрать. опасно" - a system that invents its own scope when idle was judged a real risk, not a
    // useful source). It was already dead code - declared but never actually produced by anything in this
    // codebase (confirmed by grep across backend, ml service, and frontend before removal) - so this is a
    // permanent guard against ever wiring it up later, not a behavior change.
    client, role, role_mismatch_followup, chaotic_debt, self_falsification, onboarding_finding, coverage_gap, closeout_abandoned, philosophical_falsification, gemini_observer,
    // 2026-08-09 (Phase 0, client runtime observability plan): the ONLY structural, one-shot finding this
    // source ever produces - "this delivered project has no documented way to run itself locally"
    // (ProductLaunchabilityService). Deliberately distinct from the removed idle_generation: bounded to
    // exactly one dedup-guarded item per project, triggered only by a concrete verifiable fact (missing
    // docker-compose.yml), never an invented "improvement."
    runtime_observability_gap,
    // 2026-08-04 (Phase B, design/QA acceptance redesign): audit-trail record for
    // DesignSystemFalsificationService - NOT a compiler input, records that a Stitch design-system pass
    // was applied to an epic's already-shipped real UI. See WishlistRepository.existsByFeatureIdAndSource
    // for the idempotency check that reads this.
    design_system_falsification,
    // 2026-08-11 (design shop Stage 2.5, concern-triage self-falsification loop): a real, already-shipped
    // design's review concerns, re-surfacing post-merge (e.g. from ClientRuntimeObservabilityService's
    // Stage 4 live-drift window) where a still-open Stitch mockup edit (edit_screens) is no longer
    // enough - real code already exists and needs a real Jules judgment call to update. Same bounded-
    // fact discipline as runtime_observability_gap: created only from a structured triage record a Jules
    // session already produced (JulesDispatchService.completeDesignConcernTriage), never invented scope.
    design_review_concern_pattern
}
