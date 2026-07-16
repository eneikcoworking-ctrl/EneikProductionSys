# Satisfaction Scale Diagnosis and Architectural Proposal

## Context

The current `satisfactionScore` metric within the BARCAN role doctrine (`EmsMetricsService.java`) provides an assessment of a role's satisfaction level based on executed/completed tasks (`ownerDone`, `ownerTotal`), source requests (`sourceTotal`), and quality gate passes. The UI (`CommandDashboardV2.svelte`, etc.) currently presents these numbers as horizontal bars with standard color classes (e.g., orange, purple, etc.).

This document proposes structural, architectural, and visual updates to strictly align the scale's behavior with the new requirements:
1. **Visual Gradient Transition**: The scale should transition from purple (low values) to emerald green (high values) as satisfaction increases.
2. **Growth Restriction based on Completion**: The scale must *not* grow from the mere generation of tasks or unfulfilled requirements.
3. **Attack Integration and Impact Coefficients**: The scale must be dynamically linked to executed tasks (resolving attacks) with assigned "Impact Factors/Coefficients" per role.

---

## 1. Visual Gradient Transition (Purple to Emerald Green)

Currently, the frontend assigns discrete CSS classes based on the `stance` (e.g., `UNKNOWN` gets purple, `OBJECTS` gets orange).
To move to a dynamic gradient scale, the calculation of the color must transition from being purely discrete/stance-based to a computed continuous value on the UI side, heavily dependent on the *numeric* `satisfactionScore` (0 to 100).

### Proposal: Computed UI Token
Following the MVC architecture standard documented, the backend should pre-compute the UI token. `EmsDashboardMetricsDto.RoleDoctrineVerdict` could be expanded.
* **Backend Addition**: Introduce a new field like `satisfactionColorGradient` in the DTO or calculate the interpolation purely in Svelte utilizing CSS custom properties.
* **Svelte Implementation**: Using CSS `linear-gradient` or computing an explicit `hsl()` or `rgb()` value based on the `satisfactionScore` percentage. For instance:
    * `0%` -> Purple (e.g., `#800080` or `hsl(300, 100%, 25%)`)
    * `100%` -> Emerald Green (e.g., `#50C878` or `hsl(140, 52%, 55%)`)
    * The frontend component can apply `style="--progress-color: mix(in oklch, var(--emerald-green) {score}%, var(--purple));"` (or an equivalent manual interpolation in JS).

---

## 2. Preventing Score Inflation from Mere Generation

Currently, in `EmsMetricsService.java` lines ~195-201, the `satisfactionScore` is calculated using `ownerTotal`. The formula has elements like:
`satisfactionScore = ownerTotal == 0 ? 78.0 : 92.0 + Math.min(8.0, gatePassed * 1.5);`

If the raw creation of wishlist items or the generation of *pending* tasks inflates the denominator (`ownerTotal` or `sourceTotal`) in a way that artificially increases confidence or pushes the stance out of `UNKNOWN`/`OBJECTS` too early, it violates the rule "cannot grow with uncompleted tasks".

### Proposal: Hard-Capping by Done Status
* The primary driver for the `satisfactionScore` *must* be `ownerDone` and `gatePassed`.
* `sourceTotal` and `ownerTotal` should strictly be used to *calculate the maximum potential*, but the actual score must remain static or drop until those tasks transition to `TaskStatus.done`.
* **Formula adjustment**: Instead of a base satisfaction, the score should evaluate: `(Weighted Sum of Completed Task Impacts) / (Total Potential Impact of All Tasks)`.

---

## 3. Impact Coefficients and Attack Integration

Currently, tasks have an equal or loosely classified weight when evaluated for role satisfaction. To tie the scale directly to "attacks" (wishlist items challenging the system) and the efficiency of resolving them, we need a coefficient system.

### Proposal: Task-Level Role Impact Matrix
Each task must be assigned a "coefficient of influence" for the roles it affects.

1. **Schema Addition**: The `TaskEntity` (and potentially `WishlistEntity` representing the attack) should include a JSON matrix or a new related table mapping `RoleTag` -> `ImpactCoefficient` (e.g., 0.0 to 1.0).
2. **Attack Efficiency**: An attack (Wishlist Item) creates a deficit in satisfaction. When tasks generated from this attack are executed, their `ImpactCoefficient` determines how much satisfaction is restored.
3. **Calculation Engine Updates**:
   In `EmsMetricsService.java`, instead of simply counting `ownerDone`, the method should:
   ```java
   double totalPotentialImpact = sum(task.getImpact(roleTag) for all tasks);
   double actualRealizedImpact = sum(task.getImpact(roleTag) for completed tasks);
   satisfactionScore = (actualRealizedImpact / totalPotentialImpact) * 100;
   ```
4. **Diagnostic Meaning**: A task might have a `0.9` impact on `BARCAN-TAG-00` (Code Guardian) if it's a massive refactor, but only `0.1` on `BARCAN-TAG-03` (Belief Intension). Thus, the completion of this single task causes a massive jump toward Emerald Green for TAG-00, but barely moves the purple bar for TAG-03.

## Conclusion & Next Steps
These changes require:
1. Schema/Payload update to `TaskEntity` to support impact weights.
2. Refactoring of the math inside `EmsMetricsService.java` to use impact sums instead of raw counts, strictly ensuring `ownerTotal` alone does not inflate the score.
3. Updating Svelte frontend components (e.g., `CommandDashboardV2.svelte`, `MetricsView.svelte`) to consume a backend-provided gradient token or compute the Purple-to-Emerald interpolation dynamically based on the strict score.
