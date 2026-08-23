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
    design_review_concern_pattern,
    // 2026-08-11 (client runtime observability plan, extending Phase 0's launchability check): a
    // delivered project's own Dockerfile does a bare `COPY target/*.jar` (or equivalent) with no
    // preceding build stage - confirmed live on test-forty-third, `docker compose up --build` on a
    // fresh clone fails because target/ is gitignored and nothing ever builds the artifact. Same
    // bounded, one-shot, dedup-guarded discipline as runtime_observability_gap.
    dockerfile_missing_build_stage,
    // 2026-08-11 (same plan): a delivered project has a frontend/ directory but its Dockerfile never
    // references it - the deployable image is backend-only, so a real user has nothing to look at even
    // once the product launches successfully. Same bounded, one-shot, dedup-guarded discipline.
    frontend_not_deployed,
    // 2026-08-11 (reliability-strengthening plan, TOC subordination): the product does not currently
    // launch at all (last ClientRuntimeObservationEntity has launchSuccess=false) - a philosophical
    // audit reasoning about a product that can't even start would be reasoning about nothing real. TOC:
    // launchability is the constraint; everything else (including philosophical review) subordinates to
    // it until it's cleared. Kano Must-Be by construction - not a taste judgment, a precondition.
    /**
     * The engine the application defaults to is not the engine its compose stack provides, or the build
     * manifest carries no driver for the engine that ships. Both are checkable from the repository alone,
     * without launching anything - which is the point: O-1 cost days precisely because nothing asserted
     * that these artifacts agree, and a silent system is unrefutable (plan §11.5).
     */
    datastore_artifacts_disagree,

    /**
     * The frontend renders records it carries itself instead of records the product produced. Measured
     * 2026-08-22: the page stated "Showing 1-10 of 12 materials" and listed twelve documents with titles,
     * authors and dates while the API returned zero rows. Twelve names of perfect form and no bearer -
     * a Fregean reference failure, decidable from the repository alone, and the reason a product can look
     * finished while `V_p` stays at its degenerate |C| = 1.
     */
    frontend_unbacked_records,
    product_not_launchable,

    /**
     * Work was accepted that does not satisfy the acceptance criteria the task itself carried.
     *
     * Filed by DeliveredWorkJudgmentService, which reads a done task's own criteria against the diff that
     * was merged for it. This is a DELIVERY fact and belongs on the scope axis: it says a requirement is
     * still owed, never that the product is unhealthy or that the factory is defective.
     */
    delivery_refuted
}
