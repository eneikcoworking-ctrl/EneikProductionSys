# Audit Report: System Startup and Task Generation Failure

## Issue Description
The user reported that upon triggering a new flow (e.g. project creation), the system did not fully start up as expected. The only observable artifact created in the database was the initial wishlist item (`WishlistEntity`), but no corresponding tasks (`TaskEntity`) were generated.

## Root Cause Analysis

Based on an audit of the codebase, the issue originates from a resource starvation problem related to the reserved AI compiler account (`eneikdru`).

The orchestration pipeline stalls at the exact point where the client’s unstructured wishlist should be decomposed into strict tasks by the Jules AI compiler.

### 1. Successful Project Creation and Bootstrap
When a new project is created, the system correctly creates the initial client `WishlistEntity` in a `pending` status.
When the `ContinuousOrchestrationService` loop runs, it successfully generates the `EMS bootstrap` task. The initial assumption that this bootstrap task lacked Definition of Ready (DoR) fields was incorrect; the system actually correctly sets all required fields (`tocConstraintRef`, `leanValue`, `jtbd`, etc.) programmatically, and the bootstrap task successfully drops into the `queued` state.

### 2. Dispatching the Client Wishlist
The stall occurs in the subsequent step when the system attempts to compile the user's unstructured `pending` wishlist item.
1. The `orchestrate` loop correctly identifies the client wishlist as a compiler candidate and calls `dispatchBatchedWishlistCompiler`.
2. The client wishlist status is updated from `pending` to `compiling`.
3. A system task of type `wishlist_compiler` (with role `BARCAN-TAG-09`) is created in the `queued` status.
4. The system immediately attempts to dispatch this compiler task via `dispatchCompilerTask(compilerTask)`.

### 3. Resource Starvation on the Reserved Account
The compiler task requires the reserved AI account named `eneikdru` (configured via `taskCompilerAccountName()`).
In `dispatchCompilerTask`, the system attempts to lock this account and verify its capacity:
```java
Optional<AccountEntity> accountOpt = accountRepository.lockAccountByNameWithCapacity(
        taskCompilerAccountName(), maxConcurrentJulesSessionsPerAccount);
```
However, the `eneikdru` account currently has **no free capacity**. This results in the following log entry:
```
WARN ... ProjectFlowService: Wishlist compiler account 'eneikdru' has no free capacity right now; task <id> stays queued for the next cycle
```

Because the account has no available capacity:
* The `wishlist_compiler` task is left in the `queued` status indefinitely.
* The client `WishlistEntity` is stuck in the `compiling` status, waiting for the compiler task to finish.
* The orchestration loop evaluates `hasActiveCompilerTask`, observes that there is an existing compiler task in the `queued` state, and purposefully refuses to admit any new wishlist items into the compiler batch.

## Conclusion
The system successfully bootstraps but fails to start generating feature tasks because the reserved Jules AI account (`eneikdru`) responsible for decomposing client wishlists into tasks has exhausted its concurrent session capacity. The compiler task gets stuck in the `queued` state waiting for resources, halting the entire project orchestration flow and leaving the user's request as just a wishlist item.