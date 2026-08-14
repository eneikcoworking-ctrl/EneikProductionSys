package com.eneik.production.models.persistence;

public enum PersistentWorkerPurpose {
    WISHLIST_COMPILER, REVIEW_FALLBACK,
    // 2026-08-03: one continuous multi-turn session per project, one role-batch (of 6 philosophers)
    // per follow-up, so later voices can genuinely see and respond to earlier ones - see
    // FalsificationCycleService.executePhilosophicalCycleForProject. Unlike the other two purposes
    // this one has a real finite end (all role-batches covered) and is explicitly closed out at that
    // point rather than left open-ended - see JulesDispatchService.completePersistentPhilosophicalAuditCycle.
    PHILOSOPHICAL_AUDIT
}
