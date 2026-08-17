# Watch Protocol — declared propositions and their authoritative sources

**Stage 1 of `SYSTEMIC_REPAIR_PLAN_2026-08-17.md` (defect D5, operator half; defect D4, reader
register).** This document is not code and changes no runtime behaviour. Its purpose is to make the
observation procedure itself falsifiable.

## Why this exists

Over one watch cycle, eight claims were published whose truth conditions had not been established.
Seven shared one form. The most instructive was this: the watch brief asks for
`Timeout trying to lock`; the check was run against Docker logs; none were found; "zero lock
timeouts" was reported in pass after pass. Twenty-one had occurred, seventeen of them on the
`PROJECTS` table — all in the H2 trace file, a source that had never been opened.

The measurement was not sloppy. It was **undeclared**: no statement existed anywhere saying which
source is authoritative for that proposition, so a wrong source could not be recognised as wrong.

This is the Barcan condition applied to observation. A proposition must be declared before it can be
ruled on, so that "declared, undecided" is distinguishable from "never considered". A watch that does
not declare its propositions cannot abstain — it can only appear to have concluded.

## Rule

Before ruling on a proposition, the pass states it, names its authoritative source, and names what
would falsify the reading. A proposition not in this register has not been ruled on, and silence
about it is ABSTAIN, never a negative finding.

Adding a proposition to this register is part of the work that discovers it.

---

## Proposition register

Every source below was verified against the running system on 2026-08-17 before being entered here.

### Flow state

| Proposition | Authoritative source | Falsifier |
| --- | --- | --- |
| Task counts by status | `GET /api/projects/{id}/dashboard` → `pipeline` | The dashboard task list is a bounded window; `pipeline` is the count. Reading `len(tasks)` instead of `pipeline` gives a different, wrong number. |
| Readiness, denominator, feature completion | same → `productReadiness` | `featureReadinessRatio` and `mergedRatio` are different axes. Quoting one as "readiness" without saying which is a category error. |
| Which tasks are blocked and why | same → `productReadiness.blockedItems` | `stale_in_progress` marks a staleness window elapsing, **not** a dead task — one so flagged completed 14 minutes later. It is not evidence of a stall. |
| Wishlists by source and status | same → `wishlist[]` | Counts are per-window; a source count changing may reflect the window, not new material. |
| Project state as enforced | `docker logs …-backend-1` → `policy denied … in state X` | `/dashboard` reported `decomposing` while Flow Core enforced `SYSTEM_STALLED` in the same minute (F58). The dashboard is **not** authoritative for enforced state. |

### Loops and stalls

| Proposition | Authoritative source | Falsifier |
| --- | --- | --- |
| Forced-unblock nudges | `docker logs …-backend-1 \| grep "Forced stale-revising"` | Counts per session, not per project. Two samples inside a backoff interval are not a termination proof — 2-in-6h became 39-in-1h. |
| System stall | `docker logs` → `SYSTEM STALLED: no forward progress … for N minutes` (ERROR) | Two variants exist; only the `no forward progress` one carries the 60-minute measure. The Branch-GC variant is a different, milder claim. |
| Policy denials | `docker logs` → `policy denied ACTION … in state X` | A denial with an empty work set is noise, not a block (F56): `DISPATCH_QUEUED_TASKS` was denied 35 times while the queue was empty. |
| Board movement | two `dashboard` readings ≥ 30 min apart | **Three identical half-hourly readings are not evidence of a stopped system at this flow's cadence.** Established by counter-example three times. Only the system's own 60-minute detector has been reliable. |
| **Lock timeouts** | **`data/eneik_db.trace.db`** — *not* Docker logs | The signal does not appear in Docker logs at all. Every "zero lock timeouts" reported from Docker logs was void. |
| **Database exceptions, scheduled-job failures** | **`data/eneik_db.trace.db`** | Survives container restarts because it is on the host mount; Docker logs do not. |
| Database volume | `ls -l data/eneik_db.mv.db` | Compaction is symptomatic; size must be read as a series, not a point. |

### Container and infrastructure

| Proposition | Authoritative source | Falsifier |
| --- | --- | --- |
| Backend is alive | `curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/api/projects` | Retry once before concluding; a single `000` has been transient. |
| Docker engine is alive | `docker ps` — check for **output**, not exit code | A dead engine and a dead WSL bridge both return exit 0 with empty output. `grep -c` over that empty output returns `0`, which reads as "nothing happening". This produced a false "quiet system" reading on 2026-08-17. |
| WSL interop is alive | `/mnt/c/Windows/System32/whoami.exe` | Distinguishes a stopped Docker engine (interop fine) from a dead vsock bridge (every `.exe` fails with `accept4 failed 110`). Different failures, different recovery. |
| The container name | `eneikproductionsys-backend-1` | **Not** `eneik-backend`. Queries against the wrong name return empty and read as "no activity". |

### Evidence and reasoning

| Proposition | Authoritative source | Falsifier |
| --- | --- | --- |
| Evidence nodes and coherence | `GET /api/projects/{id}/coherence-graph` | Verified 2026-08-17: 73 nodes, `coherenceScore` 49.74, `acceptedNodes` 78 of 78. |
| Which signal sources actually exist | same → `nodes[].sourceType` | See §"Measured state of the evidence graph" below — the observer's prompt says five; three are present. |
| Observer findings | `GET /api/projects/{id}/observer-journal` and `wishlist[source=gemini_observer]` | Her claims must be checked against the board before being repeated. Documented failure mode is quantifier inflation, not fabrication — but a named id may not exist (`c034c2fb`). |
| Auditor activity | `docker logs \| grep OpsAuditor` | **Logs only when it acts.** Silence conflates "swept, no evidence", "did not sweep", and "stopped". This is defect D5's machine half; until it is fixed, auditor silence is ABSTAIN. |
| Effective flag values | `GET /api/settings` → `{key, enabled, source}` | Never quote a source default. Flags live in the database and `source` says so. |

### Client repository

| Proposition | Authoritative source | Falsifier |
| --- | --- | --- |
| Is real code landing | `git ls-remote` / shallow clone of the client repo | `pull-requests` returned 0 while `mergedPlannedTasks` was 22 — the endpoint is not authoritative for merge history. |
| Merged PRs | `docker logs` → `merged PR #N`, corroborated by `[BRANCH-GC] Found N open PR` | A single BRANCH-GC reading is a snapshot; PR counts move within a window. |

---

## Signal → reader register (defect D4)

`signal(s) ∧ ¬∃reader(s) → ¬monitoring(s)`. A signal whose only declared reader is a log file is not
monitoring. Current state, measured:

| Signal | Declared reader today | Status |
| --- | --- | --- |
| `SYSTEM STALLED: no forward progress` | Docker log only | **No reader.** ERROR-level, fires once a minute, appears in no operator surface. |
| `FLAGGED FOR HUMAN REVIEW` (OpsAuditor) | Docker log only | **No reader.** The system asked for a human and told nobody. |
| Hourly `DISK_SPACE_USED` failure | H2 trace only | **No reader.** 60 consecutive failures since 2026-08-14, never surfaced. |
| Lock timeouts | H2 trace only | **No reader.** |
| Valueless-flag reporter (step 6, authored by me) | Startup log only | **No reader.** Same defect, self-inflicted. |
| Evidence nodes | `/coherence-graph`, observer prompt | Has a reader. |
| Task/wishlist state | `/dashboard` | Has a reader. |

Six signals, five with no consumer. Adopted rule: **a signal declares its reader at the point it is
emitted; "a log file" is not a reader.**

---

## Measured state of the evidence graph (2026-08-17)

Recorded here because it converts defect D1 from an argument into a measurement.

```
sourceType distribution over 73 nodes:
  OPERATIONAL_REALITY_FINDING   51   (70%)
  KAIZEN_PROPOSAL               21   (29%)
  CODE_INTEGRITY_FINDING         1   ( 1%)

coherenceScore  49.74
acceptedNodes   78 / 78 total
```

Three consequences, each checkable:

1. **The observer's prompt describes "5 independent signal sources"; three are present.** Two are
   declared and empty — an instance of the Barcan condition being honoured in the prompt and violated
   in the data.
2. **Distinct-sourceType corroboration is nearly inert.** With 70% of nodes from one source, the
   maximum achievable corroboration count is 3 and the typical is 1. This is the mechanical reason
   quantifier inflation survives: a claim resting on `OPERATIONAL_REALITY_FINDING` alone is
   uncorroborated by construction, and nothing in the graph can contradict it.
3. **`acceptedNodes` equals `totalNodes`.** Nothing has ever been rejected. A coherence filter that
   accepts everything is not discriminating; whether that is correct or a defect is **not
   established** and is a candidate for Stage 2 measurement.

This is the quantitative case for D1: adding infrastructure as a distinct `sourceType` raises the
achievable corroboration count and gives the graph its first source capable of contradicting the
dominant one.

---

## What Stage 1 does not do

No code changed. No flow touched. No flag altered. The project remains active throughout.

Stage 1 is complete when the next watch pass rules only on propositions in this register and cites
the source named here for each.
