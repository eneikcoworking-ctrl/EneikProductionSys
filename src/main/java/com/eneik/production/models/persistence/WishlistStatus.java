package com.eneik.production.models.persistence;

public enum WishlistStatus {
    pending, compiling,
    // 2026-08-07 (live incident, test-forty-third: the same wishlist got fully decomposed into task graphs
    // 3 times within a minute, producing duplicate tasks that tripped BLOCKED_BY_DUPLICATE_CONTENT and
    // halted the whole project). An atomic claim state between `compiling` and the terminal
    // converted_to_task/dismissed - a compiler completion must win a compare-and-swap from `compiling` to
    // `finalizing` before doing the slow GitHub/parse work, so a concurrent replay of the same completion
    // (poller retry, duplicate webhook) sees `finalizing` (not `compiling`) and backs off instead of
    // independently rebuilding the same task graph. See JulesDispatchService.admitWishlistCompilationCompletion.
    finalizing,
    converted_to_task, dismissed
}
