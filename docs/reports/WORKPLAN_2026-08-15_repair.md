# Work plan: repairing the factory, 2026-08-15

Supersedes the three separate repair orders inside `FINDINGS_2026-08-15_declared_vs_actual.md`, which were
written at different moments and overlap. Findings are unchanged and still referenced by number; only the
sequencing is reconsidered.

## What a fresh reading changes

**Three findings are one defect.** F1 (a task reports `done` without delivering), F11 (`.gitignore` never
landed and nobody noticed) and half of F28 (a rejected generation is logged and retried) are all the same
mechanical fault: **an operation's return value is written to a log instead of being acted on.** They are
one fix, not three, and it is the cheapest fix in the document.

**The verdict lattice is not a third plan, it is the container for the other two.** Both repair orders end
"and this lands in the lattice". Building them separately and retrofitting means wiring twice. But stages
A-C of the lattice are read-only, so it can be built as an **observer** first: it computes and surfaces,
changes nothing, and immediately supplies the one thing the flow lacks - a place where layers reconcile.

**Ordering must be by loss, not by depth.** Three defects are destroying work or burning quota right now;
four are causing decisions to be made on false data; the architecture is last because it is worthless
until its inputs are honest.

## Sequence

Steps are ordered so that each is verifiable on its own and none depends on a later one. Risk rises
monotonically; the factory can be stopped after any step with a consistent system.

---

### Step 1 · An operation's effect must be verified, not logged
**Closes:** F1, F11, and the "silently retried" half of F28. **Risk: low.** **Factory may stay running.**

`commitFile` is documented create-only ("no `sha`, so this is a create, not an update"), written for
timestamped design assets. Bootstrap reused it for fixed paths, one of which (`.gitignore`) already exists,
so the write fails with 422 and the failure is only logged - while `task.setStatus(done)` has already run,
on the strength of a different file.

1. Add `upsertFile(project, path, bytes, message)` as a **new** method: read the current `sha`, include it,
   fall back to create when absent. Do **not** change `commitFile` - fourteen call sites rely on
   create-only semantics, and design-draft promotion depends on a second write failing rather than
   silently overwriting an approved mockup.
2. `commitDeterministicJavaScaffoldIfAbsent` / `...FrontendScaffold...` switch to `upsertFile`.
3. `completeBootstrapDeterministically`: move `task.setStatus(done)` to **after** all scaffold commits, and
   set it only if every one returned true. A partial bootstrap leaves the task queued, which is the
   existing, working fallback to Jules dispatch.

**Verification:** create a throwaway project; assert `.gitignore` on `main` contains `target/` and `data/`,
and that `bootstrap.md`, `pom.xml`, `application.properties` all exist. Today `.gitignore` has exactly one
commit - the factory's - on every project checked.

**Why first:** it is upstream of the conflicts (F12: no shared skeleton → each task invents one → build
artifacts committed → every pair of tasks conflicts) and it costs a day, not a week.

---

### Step 2 · The design shop stops burning quota
**Closes:** F28. **Risk: low**, one isolated service. **Factory may stay running.**

Replace the identity test with a property test, and split failures by modality:

```
usable(r)      ⟺ r.available ∧ r.repoDraftPath ≠ ∅ ∧ implementableHtmlExists(repoDraftPath)
unavailable(r) ⟺ ¬r.available                    → retry, declared budget
wrongKind(r)   ⟺ r.available ∧ ¬usable(r)        → record, escalate once, never retry
```

The model's name leaves the gate and becomes evidence in the reason. The loop closes because **no quantity
of retries changes the kind of a thing** - not because a counter caps it.

**Verification:** the same cause must not produce a second identical retry. Today: six identical failures
across four hours, each consuming a generation call.

---

### Step 3 · Conflict resolution: substitutivity, and stop destroying work
**Closes:** F22 (all parts), F20. **Risk: medium** - `AutoMergeService` has a documented incident history.
**Factory should be idle.**

Execute the approved order, but with **stage 4 pulled to the front**, because it is the only part that
stops loss rather than improving decisions about work already gone:

1. **Never perform an irreversible action to resolve uncertainty.** Escalation currently triggers Branch GC,
   which retires the branch and destroys the work. Preserve and mark instead. (PR#12 was consumed this way:
   plan 21 → 19.)
2. One ownership definition replacing three, grounded in declarations the system already makes:
   `substitutable(f,t) ⟺ f ∉ fileScope(t) ∧ ¬∃ live claim on f`.
3. Delete `ConflictEntropyCalculator`, or reduce it to an observation with no documented threshold.
4. The gate becomes `n = 0`.
5. Mechanical: `setResolutionAttempts` increments rather than assigns; Tier-1 sync uses the PR's real
   `baseRef` instead of a hardcoded `"main"`; the comment claiming a bound of `== 0` is reconciled with the
   code's `< 10`; the "unrecoverable" claim becomes "budget exhausted".

**Verification:** the 17 existing `AutoMergeServiceTest` cases must stay green - they encode the two prior
deadlock fixes. New cases for `substitutable` over a synthetic `fileScope`.

---

### Step 4 · Metrics stop lying
**Closes:** F23, F3, F13. **Risk: low-medium** - DPMO will change, because it is currently wrong.

**F23.** `isDefectWork` substring-matches free text against two different word lists, so a *feature* named
"Self-Service Account **Recovery**" is defect work by name. But the system **already declares** what kind
of work each item is: `WishlistSource`. `self_falsification`, `product_not_launchable`,
`dockerfile_missing_build_stage`, `coverage_gap`, `design_review_concern_pattern`, `gemini_observer` are
defect-class by construction; `client` and `role` are not. Same substitutivity move as Step 3 - use the
declaration, not a guess at the text.

```
isDefectWork(t) ⟺ defectClass(originWishlistSource(t)) ∨ t.retryCount > 0
```

One definition, one place, both call sites. Then DPMO measures the process instead of the vocabulary.

**F3.** Four of 38 settings resolve with source `none`. A boolean flag with no value anywhere reads `false`
and the feature is silently off - this already cost a full falsification pass. Startup must log each such
key at WARN and the settings endpoint must mark it; a registered-but-valueless flag is a defect, not a
default.

**F13.** Generated work items are stored with `source=client`. Twenty-one of twenty-two rows on this
project misattribute their own origin, and that attribution is exactly what the market corpus reasons
about. Compiler-generated items take a compiler source.

---

### Step 5 · The verdict lattice, as a read-only observer
**Closes:** the structural problem behind F22-F27. **Risk: near zero - changes no behaviour.**

Stages A-C only. Nothing gates on it yet.

1. `Verdict ∈ {permit, withhold, abstain}` and Kleene strong conjunction. Pure, fully testable in isolation.
2. Each layer declares up front the finite set of propositions it rules on (the Barcan condition - without
   it `abstain` cannot distinguish "declared, undecided" from "never considered", and the second is
   invisible, which is how every silent gap in the findings arose), and maps its own measure to a verdict
   by its own stated rule. **Six Sigma declares `abstain` until Step 4 lands** - honestly.
3. Compute and surface `D` (abstentions) and `W` (refusals). One endpoint. This is the place where the
   flow's layers reconcile, which does not exist today.

**Immediate value without gating anything:** the TOC constraint becomes derivable as `argmax(W + D)`
instead of asserted by hand - measured now, that is the doctrine layer (2 refuse, 7 object, 4 unknown) and
UX/UI within the task layer (F25), neither of which the queue view shows.

---

### Step 6 · Close the runtime loop
**Closes:** F27, and the part of F4 that costs most. **Risk: low.**

The product was repaired twice and never re-observed; the launch verdict is still the one taken before
either repair, and philosophy is subordinated to it.

1. Landing a fix for a launch failure publishes an event; the launchability observation subscribes. Cron
   survives only as a rare safety net for a lost event, never as the primary trigger.
2. A verdict carries the observation it rests on and **reverts to `abstain` when that observation's
   referent changes** - the same expiry rule the market corpus already applies to `observed` entries.

**Verification:** land a Dockerfile fix on a live project and see a new observation without waiting for a
scheduled tick.

---

### Step 7 · The acceptance chain
**Closes:** F30, F21. **Risk: low for the corpus, medium for the compiler prompt.**

1. One `acceptanceRule` in `profiles.json`, status `derived`, instantiated against whatever `valuePaths`
   each profile declares. Not sixteen new chains - the acceptance chain is the existing path under a change
   of quantifier: *there **exists** one complete traversal, performed **by the client**, on the **deployed**
   instance, against **real content**.*
2. The seeding obligation (F21) follows from requirement two: a knowledge base with no materials cannot
   exercise "find the material", so it is unacceptable by rule rather than by opinion.
3. Traversal evidence recorded; `witnessed(P) = Σ|v|` gates acceptability. No threshold - value multiplies
   along a chain, so a partial traversal witnesses nothing.

**Depends on Step 6:** without a fresh observation there is no deployed instance to traverse.

---

### Step 8 · The lattice gates
**Risk: high - this changes what the factory permits.** Behind a flag, one project first.

Advance gates read `advance(P) = ⋀ verdict_ℓ(P)` instead of their private conditions. Existing thresholds
move inside their owning layer, unchanged in value, now singular and inspectable.

Only after Steps 1-7, because a gate is only as honest as its inputs, and today two of the five layers
report numbers derived from substring matching.

---

## What is deliberately not in this plan

| Finding | Why deferred |
|---|---|
| F2 settings audit trail | Real, but no loss is occurring; a day's work with no urgency |
| F8 Linear provisioning fails | Reproduced on both projects, unrelated to the flow; fix when Linear is actually used |
| F14 wrong item titles, F15 `epics` returns `tasks:0` | Cosmetic and display-only |
| F17 all epics `Must-Be`, F18 GDPR absent | Need more than one project's evidence before concluding anything |
| F19 corpus covers DE/US, brief was Russian | Correct behaviour for the declared markets; not a defect |
| F24 doctrine refuses and nobody reads it | Becomes free once Step 5 exists - the doctrine layer is simply another verdict source |
| F26 fragmented dependency graph | Real, but its own investigation; no evidence yet that it causes loss |
| ~187 files containing Cyrillic | A dedicated pass, not to be mixed into repair work |

## Order in one line

**Stop the losses (1-3) → make the inputs honest (4) → build the place where they reconcile (5) → close the
loop (6) → let the client see it (7) → and only then let it decide (8).**
