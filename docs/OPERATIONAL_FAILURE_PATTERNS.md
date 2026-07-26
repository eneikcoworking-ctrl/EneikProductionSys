# Operational failure patterns (structural signatures, not code review)

Indexed into the Gemini observer's standing knowledge base. The observer never sees source code, so these
are written as **symptom -> structural pattern** entries she can match against numbers in her evidence
snapshot - not code smells, which require reading code she doesn't have access to.

## Self-triggering / recursive loop

**Symptom:** a metric that should stabilize (readiness ratio, a count of "pending" items of one kind) keeps
resetting back to a worse value shortly after every attempt to resolve it, even though real work keeps
completing elsewhere.

**Structural cause:** a corrective mechanism's own output satisfies the same condition that re-triggers it -
e.g. a re-audit whose own report-only PR counts as "new work since last audit" and immediately re-qualifies
for another audit. Confirmed live twice in this system: 2026-07-24 (an audit's own record PR re-triggered
itself, unbounded), 2026-07-26 (a coverage-audit watermark scoped to the whole project, not the specific
wishlist, kept resetting an unrelated wishlist's own readiness every time ANY other wishlist's work merged).

**What it looks like in a snapshot:** STAGNATION WARNING alongside steady "Resolved since your last visit"
activity - progress is genuinely happening, but the metric that's supposed to reflect it never moves.

## Duplicate/runaway generation

**Symptom:** DUPLICATE TASK WARNING - 3+ non-terminal tasks with byte-identical descriptions.

**Structural cause:** a generator (compiler, decomposition step) re-ran against the same input without
checking whether it had already produced this output, or a hardcoded/demo code path fired instead of the
real one. Confirmed live 2026-07-25/26: a hardcoded fallback generator produced 9 near-identical tasks over
~2 hours before being traced to its root cause.

## Watermark/scope mismatch

**Symptom:** a per-item mechanism (an audit, a re-check) never stabilizes for one specific wishlist/feature
even though that item's own work is genuinely done, while OTHER items in the same project progress fine.

**Structural cause:** the "has anything changed" check is scoped too broadly (whole project) instead of to
the specific item, so unrelated activity elsewhere keeps re-arming it. The fix is always narrowing the scope
of the "what changed" query to the item's own dependency graph, never widening the readiness bar.

## Silent capability gap (looks calm, isn't)

**Symptom:** nothing anomalous in counts or stagnation, but a category of expected activity (e.g. one whole
work type) never appears in "Resolved since your last visit" across many cycles for a project that should
have that kind of work.

**Structural cause:** a dispatch/compile path silently never fires for that category (missing wiring, a
condition that's always false) rather than firing and failing - the fix is not more retries, it's finding
why the category never enters the pipeline in the first place. Not detectable from status counts alone; a
concrete example the observer can flag as a finding worth investigating, not something to act on herself.

## Reference confusion across contexts (rigid-designator failure)

**Symptom:** when choosing an id to act on, a plausible-looking, recently-seen identifier is used instead of
one actually present in the current evidence snapshot - the id is real and resolves to a genuine record, just
the wrong one for this context.

**Structural cause:** your own retrieved standing knowledge (OBSERVER_LOG.md entries from past cycles, past
incidents) is full of real, valid ids - but they belong to whatever project/situation was being described
*then*, not necessarily this one, now. An id is only a rigid designator for the object it names WITHIN one
fixed context (project); reusing it across a different context silently changes what it refers to, without
looking any different on the page. Confirmed live 2026-07-26: the observer proposed `nudgeStuckSession` with
a task id that was a genuine task - but one belonging to an entirely different project (test-thirty-third),
almost certainly picked up from retrieved historical log text rather than the current snapshot. The
project-ownership guard in `GeminiObserverActionService` caught it (outcome: skipped, not silently acted on)
- that guard is a required correctness check for this exact failure mode, not an optional safety margin.

**What to do:** when proposing a `targetId` for any action, use ONLY an id that appears in THIS cycle's
evidence snapshot section, never one recalled from retrieved context/prior journal text - even a completely
real, valid id from a different context is the wrong answer here.
