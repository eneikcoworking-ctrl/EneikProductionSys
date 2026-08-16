# Handover — night of 2026-08-16

## BLOCKER — tooling only. The factory is RUNNING.

**The backend is alive.** `curl localhost:8080/api/projects` → HTTP `200`. The project is not idle
and no recovery action is required for the factory itself.

What *is* broken is the WSL→Windows interop bridge, i.e. **my ability to run `docker` commands**:

- Every Windows binary invoked from WSL (`docker.exe`, `cmd.exe`, `whoami.exe`) fails with
  `WSL ERROR: UtilAcceptVsock:273: accept4 failed 110` (110 = ETIMEDOUT) and produces no output.
- `/var/run/docker.sock` does not exist in this distro.
- `/proc/sys/fs/binfmt_misc/WSLInterop` is `enabled` with `interpreter /init` — interop is
  *registered*; the vsock channel to the host is what is broken.

Consequence: **no `docker logs`, no `docker ps`, no image build, no container restart** until the
bridge is restored. Monitoring can continue over HTTP against the live API, which works normally.

Recovery, on Windows, whenever convenient: `wsl --shutdown` in PowerShell, then reopen the WSL
terminal.

### Correction to my own first reading

I initially recorded here that Docker Desktop was down and the factory with it. That was wrong.
`tasklist.exe` reporting zero Docker processes and `curl` returning `000` were both artefacts of the
same broken bridge and one transient blip — not evidence of an outage. A retry showed HTTP `200`.
The lesson is the standing one: a Windows-binary call that fails through this bridge produces **no
output but exit code 0**, so its silence must never be read as a finding about the system.

## Diagnosis completed before the outage: why no replacement was created

The operator's rule — *"if a task is needed it must be done; if it is not needed it must not be
counted"* — is exactly what this codebase already intends. There is no third state by design.

Two distinct metrics exist, and conflating them was the source of the confusion:

| metric | rule | purpose |
|---|---|---|
| `ratio()` | strictly **actually merged** | drives client-facing DELIVERED status; a failed task with no replacement legitimately holds it below 1.0 |
| `selfFalsificationReadyRatio()` | fulfilled **OR** the task chain bottoms out at a real `failed` task with no replacement | lets self-falsification reach the dead end and produce replacement work |

`ClientDeliverableReadinessService` (block dated 2026-08-06, live incident test-forty-second, task
`5ac0b91b`) documents the failure mode in its own words: a failed dependency with no replacement
leaves a dependent *"stuck in queued forever"*. It also names the one authorised cure:

> `self_falsification` — the **only** authorised mechanism for producing replacement work for a
> permanently-failed task

`FalsificationCycleService:360` correctly gates on `selfFalsificationReadyRatio()`, **not**
`ratio()`, precisely so a permanent dead end cannot hold the gate shut forever.

So the machinery is designed correctly and the gate was passable. **The open question — not yet
answered — is why self-falsification, which did run and produced two findings this cycle, did not
emit replacement work for these two specific failed tasks.** The likely shape (unverified, must be
confirmed against logs once the engine is back): self-falsification analyses the *product* and its
own findings, and nothing explicitly feeds it *"these two tasks are terminally failed, produce
replacements"*. That would make the dead-end credit in the ratio a licence to pass the gate without
any corresponding obligation to act on the dead end.

**Do not "fix" this by adjusting the readiness counter or dropping failed tasks from the
denominator.** Both would be exactly the patch-instead-of-mathematics mistake. The repair belongs on
the missing link: *terminal failure → obligation to produce replacement work*.

## Verification still owed once Docker is back

1. `docker logs eneik-backend | grep FalsificationCycleService` — confirm the gate outcome and
   whether replacement wishlists were considered at all for the two failed tasks.
2. Confirm the two failed task ids and whether any `self_falsification` wishlist references them.
3. Only then design the fix for the missing link.

## Corrections made to earlier reporting

- **"The project will stall"** — wrong. There is no stalled state. `ProjectStatus` is
  `active · analyzing · waiting · frozen · accepted · archived`; there is no `delivered`, and
  `accepted` is a human act. `active` is the normal continuing state; the factory is designed to
  keep generating work indefinitely and is stopped only by client acceptance.
- **"Design is blocked"** — conflated two independent services.
  `DesignSystemFalsificationService` runs on a 30-minute cron gated on *epics with merged UI code*
  and knows nothing about readiness — **this is what was calling Stitch**.
  `DesignShopOrchestrationService` fires on the readiness false→true edge — this is the one that
  never started (F44). They must always be named separately: *falsification* and *shop*, never
  "design".
