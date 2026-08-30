"""
Sidecar launcher - Phase 1 of docs/reports/PLAN_client_runtime_observability_2026-08-09.md.

The ONLY component in the whole factory that ever holds the host docker socket. Deliberately tiny,
deliberately not part of the main Spring backend's dependency graph or codebase - a compromise or bug
in the backend's much larger surface can never reach this socket, because it never has it.

Stateless between calls except for one in-memory "what's currently running" pointer - the operator's
own architecture decision (2026-08-09) is that the factory ever observes exactly ONE active project at
a time, so there is deliberately no multi-tenant tracking here.

Four endpoints, matching the plan exactly (plus /fetch, added 2026-08-10 for the design shop's Stage 4
live-drift check - same "GET a caller-given URL, no assumptions about the target's shape" contract as
/healthcheck, just returning the body too):
  POST /launch      - clone/pull the given repo+ref, `docker compose up --build -d` it under a fixed
                       project name so a stale run is always addressable for teardown. Every published
                       host port gets remapped (see EXTERNAL_PORT_BASE) so the client project's own port
                       choice never collides with this factory's own services.
  POST /healthcheck  - GET a caller-given URL, report status code + latency. No assumptions about the
                       target's shape - the backend decides what URL/convention to check.
  POST /fetch        - GET a caller-given URL, report status code + latency + body (truncated). Reuses
                       the same reachability path already proven live by /healthcheck.
  POST /teardown     - `docker compose down -v --remove-orphans` the current run, always safe to call
                       even if nothing is running.
"""
import json
import os
import shutil
import subprocess
import time
from pathlib import Path
from typing import Optional

import requests
import yaml
from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI()

WORKSPACE_ROOT = Path("/workspace")
COMPOSE_PROJECT_NAME = "runtime-observe"

# 2026-08-11 (client runtime observability plan, port collision found live): a client project's own
# docker-compose.yml very commonly publishes 8080 (test-forty-third does) - the SAME host port this
# factory's own backend already publishes permanently. Every launch remaps every published host port to
# a fixed range starting here instead - never requires the client project's own compose file to change
# in its repo, and stays deterministic (exactly one project is ever launched at a time, per the
# operator's own architecture decision - see module docstring).
EXTERNAL_PORT_BASE = 18080

_current_project_slug: Optional[str] = None


class LaunchRequest(BaseModel):
    repo_url: str
    ref: str = "main"
    project_slug: str


class LaunchResponse(BaseModel):
    success: bool
    duration_ms: int
    error: Optional[str] = None
    # The host port the backend/dashboard should actually reach the launched product on, after the
    # port-collision remap below. None only when the compose file declared no published ports at all.
    external_port: Optional[int] = None
    # 2026-08-21: WHICH artifact this observation is about. The backend's posterior is a belief about a
    # specific object, and between merges that object does not change - so N readings of one unchanged
    # commit are one draw, not N (de Finetti requires an exchangeable sequence; a constant one is not).
    # This is the real bearer, read off the clone itself, not a proxy inferred from the factory's own
    # task records - see ACP-102 on criteria standing in for concepts.
    # Present whenever the clone succeeded, including when `docker compose up` then failed: that is still
    # a real observation of a known artifact.
    commit_sha: Optional[str] = None


class HealthCheckRequest(BaseModel):
    url: str
    timeout_seconds: float = 10.0


class HealthCheckResponse(BaseModel):
    status_code: Optional[int]
    latency_ms: int
    error: Optional[str] = None


class FetchRequest(BaseModel):
    url: str
    timeout_seconds: float = 10.0


class FetchResponse(BaseModel):
    status_code: Optional[int]
    body: Optional[str] = None
    latency_ms: int
    error: Optional[str] = None


class TeardownResponse(BaseModel):
    success: bool
    error: Optional[str] = None


MAX_FETCH_BODY_CHARS = 200_000


def _run(args: list[str], cwd: Optional[Path] = None, timeout_seconds: int = 600) -> subprocess.CompletedProcess:
    return subprocess.run(
        args, cwd=cwd, capture_output=True, text=True, timeout=timeout_seconds
    )


# Ports a product publishes in order to be spoken to over HTTP, and ports that by their nature cannot
# answer one. Neither list is a guess about this product: both are properties of the protocols themselves.
HTTP_TARGET_PORTS = {80, 443, 3000, 4200, 5000, 8000, 8080, 8081, 8443, 8888, 9000}
NON_HTTP_TARGET_PORTS = {1433, 1521, 2181, 3306, 5432, 5672, 6379, 9042, 9092, 9200, 11211, 27017}


def _resolve_topology(workdir: Path, base: Path) -> Path:
    """The product's whole declared topology as one file, override included.

    Docker loads docker-compose.override.yml automatically ONLY when no `-f` is given, and this launcher
    always gives one. Every product whose application service lives in its override was therefore launched
    without an application at all. Compose itself performs the merge here, so this adds no opinion about how
    the two files combine; the result is written to a new file in the ephemeral clone and the client's own
    files are left untouched. Best-effort: if the merge cannot be produced, the base file is used exactly as
    before, because a launch on the base topology is still better than no launch."""
    override = next((workdir / n for n in ("docker-compose.override.yml", "docker-compose.override.yaml")
                     if (workdir / n).exists()), None)
    if override is None:
        return base
    cfg = _run(["docker", "compose", "-f", str(base), "-f", str(override), "config"],
               cwd=workdir, timeout_seconds=60)
    if cfg.returncode != 0 or not cfg.stdout.strip():
        return base
    resolved = workdir / "docker-compose.resolved.yml"
    resolved.write_text(cfg.stdout)
    return resolved


def _http_port(port_map: dict) -> Optional[int]:
    """The external port of the service that can actually answer an HTTP health check, or None.

    None is a real answer and not a failure of this function: a product that publishes only a database has
    nothing for a health check to address, and saying so is worth more than probing the database and
    reporting its refusal as the product's health."""
    candidates = []
    for _service, value in port_map.items():
        external, target = value
        try:
            target_port = int(str(target).split("/")[0])
        except ValueError:
            continue
        if target_port in NON_HTTP_TARGET_PORTS:
            continue
        if target_port in HTTP_TARGET_PORTS:
            return external
        candidates.append(external)
    # Nothing on a well-known HTTP port. One remaining candidate that is not a datastore is unambiguous;
    # several are not, and guessing between them is how the wrong port got probed in the first place.
    return candidates[0] if len(candidates) == 1 else None


CLIENT_STACK_MEM_LIMIT = os.environ.get("CLIENT_STACK_MEM_LIMIT", "").strip()


def _bound_memory(compose_file: Path) -> int:
    """Give every service of the product a memory limit, in the ephemeral clone, unless it already has one.

    Why this exists (2026-08-30, plan §4.44). This launcher runs `docker compose up --build -d` against the
    client repository through the host docker socket, so the product's containers and BuildKit are siblings
    of the factory on the same daemon - outside every cgroup declared in the factory's own compose file. The
    factory's compose says so in its own words: the launcher's 256m "binds the launcher process and NOTHING
    IT STARTS". That is why the launcher was kept down, and while it is down nothing serves the product:
    measured that day, https://test-fiftieth.dmitryefremov.com/ answered HTTP 530 / error 1033 - Cloudflare
    reporting that no origin is connected - because no container of the product existed at all.

    The bound is not chosen here. It is declared where every other limit in this factory is declared, in the
    factory's docker-compose.yml, and arrives as CLIENT_STACK_MEM_LIMIT. With nothing declared this function
    changes nothing, so an operator who has not set it gets exactly the previous behaviour.

    A service that already declares its own limit is left untouched: that is the client's own decision about
    its own product, and overwriting it would be this factory imposing an opinion on the product it builds.

    Rewrites the same ephemeral file _remap_ports rewrites, never the client's real repository.
    """
    if not CLIENT_STACK_MEM_LIMIT:
        return 0
    try:
        spec = yaml.safe_load(compose_file.read_text()) or {}
    except (OSError, yaml.YAMLError):
        return 0
    services = spec.get("services") or {}
    bounded = 0
    for _name, service in services.items():
        if not isinstance(service, dict):
            continue
        if service.get("mem_limit") or (service.get("deploy") or {}).get("resources"):
            continue
        service["mem_limit"] = CLIENT_STACK_MEM_LIMIT
        bounded += 1
    if bounded:
        compose_file.write_text(yaml.safe_dump(spec))
    return bounded


def _remap_ports(compose_file: Path) -> dict[str, tuple[int, str]]:
    """Rewrite every published host port in the compose file IN PLACE to a fixed range starting at
    EXTERNAL_PORT_BASE. A separate override file passed via a second `-f` does NOT work for this: Docker
    Compose concatenates `ports:` lists across merged files rather than replacing by target port (confirmed
    live - `-f base.yml -f override.yml` left BOTH 8080 and 18080 in the resolved config, and the compose
    file's own 8080 entry still failed to bind against this factory's own backend). Rewriting the compose
    file itself, once, in the ephemeral clone (never the client's real repo) is the only way that actually
    works.

    Returns {service_name: (external_port, container_port)}. The container port is carried because an
    external port number alone cannot tell you what it serves, and choosing what to probe without that was
    F15 - the factory addressed PostgreSQL with an HTTP health check for the life of a project."""
    try:
        spec = yaml.safe_load(compose_file.read_text()) or {}
    except Exception:
        return {}

    services = spec.get("services") or {}
    port_map: dict[str, tuple[int, str]] = {}
    next_port = EXTERNAL_PORT_BASE
    changed = False

    for name, service in services.items():
        if not isinstance(service, dict):
            continue
        ports = service.get("ports")
        if not ports:
            continue
        new_ports = []
        for entry in ports:
            if isinstance(entry, dict):
                target = str(entry.get("target") or "")
                protocol = str(entry.get("protocol") or "tcp")
                entry["published"] = str(next_port)
                new_ports.append(entry)
                port_map[name] = (next_port, f"{target}/{protocol}" if target else str(next_port))
            else:
                raw = str(entry).strip()
                container_port = raw.split(":")[-1]
                new_ports.append(f"{next_port}:{container_port}")
                port_map[name] = (next_port, container_port)
            next_port += 1
        service["ports"] = new_ports
        changed = True

    if changed:
        compose_file.write_text(yaml.safe_dump(spec))
    return port_map


@app.post("/launch", response_model=LaunchResponse)
def launch(req: LaunchRequest) -> LaunchResponse:
    global _current_project_slug
    started = time.monotonic()
    workdir = WORKSPACE_ROOT / req.project_slug

    try:
        if workdir.exists():
            shutil.rmtree(workdir)
        workdir.parent.mkdir(parents=True, exist_ok=True)

        clone = _run(["git", "clone", "--depth", "1", "--branch", req.ref, req.repo_url, str(workdir)], timeout_seconds=120)
        if clone.returncode != 0:
            return LaunchResponse(success=False, duration_ms=_elapsed_ms(started), error=f"git clone failed: {clone.stderr[-2000:]}")

        # Never fatal: an unreadable SHA costs the caller the ability to collapse repeat readings, which is
        # strictly better than failing an otherwise good launch over bookkeeping.
        rev = _run(["git", "-C", str(workdir), "rev-parse", "HEAD"], timeout_seconds=30)
        commit_sha = rev.stdout.strip() if rev.returncode == 0 else None

        compose_file = workdir / "docker-compose.yml"
        if not compose_file.exists():
            return LaunchResponse(success=False, duration_ms=_elapsed_ms(started),
                                   error="docker-compose.yml not found at repo root after clone",
                                   commit_sha=commit_sha)

        # 2026-08-23 (F14). Passing `-f docker-compose.yml` suppresses Docker's automatic loading of
        # docker-compose.override.yml, so the launcher was starting a topology the product does not have.
        # Measured on test-fiftieth: the base compose declares only `db` and `backup`, and the ONLY service
        # that serves HTTP is declared in the override - which was never read. Nothing that answers was ever
        # started, on any run, for the life of the project.
        #
        # The reason the override was dropped is real and is preserved: Compose concatenates `ports:` across
        # merged files instead of replacing them, so `-f base -f override` left both the original and the
        # remapped port in the resolved config. Resolving first and remapping the RESULT removes that
        # entirely, because _remap_ports replaces each service's whole `ports` list and a concatenated
        # duplicate collapses with it.
        compose_file = _resolve_topology(workdir, compose_file)
        port_map = _remap_ports(compose_file)
        bounded = _bound_memory(compose_file)
        if bounded:
            print(f"[launcher] bounded {bounded} service(s) of the product at {CLIENT_STACK_MEM_LIMIT}", flush=True)

        up = _run(
            ["docker", "compose", "-p", COMPOSE_PROJECT_NAME, "-f", str(compose_file), "up", "-d", "--build"],
            cwd=workdir, timeout_seconds=600,
        )
        if up.returncode != 0:
            return LaunchResponse(success=False, duration_ms=_elapsed_ms(started),
                                   error=f"docker compose up failed: {up.stderr[-2000:]}", commit_sha=commit_sha)

        _current_project_slug = req.project_slug

        # 2026-08-23 (F15). This was `next(iter(port_map.values()))` - the first published port of whatever
        # service happened to come first. Measured on test-fiftieth, that was the DATABASE: the base compose
        # publishes ${POSTGRES_PORT:-5432}:5432 and nothing else, so the factory has been sending an HTTP
        # health request to PostgreSQL and recording the refusal as the product's health. A port is not
        # interchangeable with another port merely because both are published; what it serves is the whole
        # of what makes it the right one to probe.
        primary_port = _http_port(port_map)
        if primary_port is None:
            return LaunchResponse(
                success=False, duration_ms=_elapsed_ms(started), commit_sha=commit_sha,
                error="no service in this product publishes a port that serves HTTP - published targets are "
                      + ", ".join(f"{svc}:{tgt}" for svc, (_, tgt) in sorted(port_map.items()))
                      + ". A health check has nothing to address here. This is an assembly defect: the "
                        "product declares data services and no application surface.")
        return LaunchResponse(success=True, duration_ms=_elapsed_ms(started), external_port=primary_port,
                               commit_sha=commit_sha)
    except subprocess.TimeoutExpired as e:
        return LaunchResponse(success=False, duration_ms=_elapsed_ms(started), error=f"timed out: {e}")
    except Exception as e:  # noqa: BLE001 - report every failure back as real observation evidence, never crash the sidecar
        return LaunchResponse(success=False, duration_ms=_elapsed_ms(started), error=str(e))


# 2026-08-19: a health check that cannot connect reports the SYMPTOM - "connection refused". The CAUSE
# sat in the app container's own log two steps away and nothing ever read it. Measured on
# test-forty-ninth: `launch_success=true`, health refused on 18080, and the real reason was
# `Driver org.h2.Driver claims to not accept jdbcUrl, jdbc:postgresql://db:5432/epidemiology_db` - the
# product's compose declares PostgreSQL while its application config and build manifest declare H2.
# Without the cause the factory can only route a guess: that blocker became an OPERATIONS task fixing a
# symbol, when it is an ASSEMBLY defect. Naming the failing service and quoting its own log is what turns
# the next observation into a verdict instead of a guess.
def _silent_container_facts(container: str, row: dict) -> str:
    """What can be SEEN about a container that wrote nothing, as checkable claims.

    2026-08-21, measured on test-forty-ninth: the app container came up, logged not one line, and nothing
    answered on the health port. The evidence handed to the next worker was
    "<no output on this container's stdout/stderr>" - true, and worth almost no information. A worker
    receiving it can only form a hypothesis, and by Popper a cycle that produces a hypothesis rather than
    a refutation has not moved. Each iteration costs an hour of cadence, so the bits it carries are the
    limiting factor, not the speed.

    This sidecar is the only component in the factory holding the docker socket, so it is the only place
    these facts are visible at all. They are the ones that explain silence: which ports the container
    actually publishes (compare against what the application listens on), what command it was given (a
    container that runs the wrong command runs quietly and forever), and whether it has been restarting.
    Best-effort throughout - a diagnostic must never break the observation it explains."""
    facts = []
    publishers = row.get("Publishers")
    if isinstance(publishers, list) and publishers:
        mapped = []
        for pub in publishers[:6]:
            if not isinstance(pub, dict):
                continue
            published = pub.get("PublishedPort")
            target = pub.get("TargetPort")
            if published:
                mapped.append(f"{published}->{target}/{pub.get('Protocol', 'tcp')}")
            elif target:
                mapped.append(f"(unpublished){target}/{pub.get('Protocol', 'tcp')}")
        facts.append("published=[" + ", ".join(mapped) + "]" if mapped
                     else "published=[] (the service declares no published port)")
    else:
        facts.append("published=[] (the service declares no published port)")

    inspected = _run(
        ["docker", "inspect", "-f",
         "{{json .Config.Entrypoint}}|{{json .Config.Cmd}}|{{.RestartCount}}|{{.State.Status}}|{{.State.ExitCode}}",
         str(container)],
        timeout_seconds=20)
    if inspected.returncode == 0 and inspected.stdout.strip():
        pieces = inspected.stdout.strip().split("|")
        if len(pieces) == 5:
            facts.append(f"entrypoint={pieces[0]}")
            facts.append(f"cmd={pieces[1]}")
            facts.append(f"restarts={pieces[2]}")
            facts.append(f"status={pieces[3]} exit={pieces[4]}")
    return "<no output on this container's stdout/stderr; " + "; ".join(facts) + ">"


def _declared_services() -> set:
    """Service names the product's own compose declares, as opposed to the ones that materialised.

    Best-effort like everything else in this diagnostic: on any failure it returns an empty set, which makes
    the caller fall through to its previous behaviour rather than claim something it could not check."""
    try:
        cfg = _run(["docker", "compose", "-p", COMPOSE_PROJECT_NAME, "config", "--services"],
                   timeout_seconds=30)
        if cfg.returncode != 0:
            return set()
        return {line.strip() for line in cfg.stdout.splitlines() if line.strip()}
    except Exception:
        return set()


def _assembly_report(max_chars: int = 3000) -> str:
    """Which services of the current run are not up, and what their own logs say. Best-effort: any failure
    here must never replace the health result itself - an empty report is honest, a crash is not.

    The cap is 3000 and not larger on purpose: this string is concatenated onto the health-check error and
    stored in client_runtime_observations.error_text, which is VARCHAR(4000). An overflow would not
    truncate the diagnosis - it would fail the INSERT and lose the observation itself, turning a better
    explanation into no observation at all."""
    if _current_project_slug is None:
        return ""
    try:
        # 2026-08-23 (F12): `--all`. Without it this listing shows only RUNNING containers, which hides
        # exactly the ones that explain a refused health port - a service that exits on startup is absent
        # from the output entirely, so `unhealthy` comes back empty and the report concludes that every
        # service is running. Measured on test-fiftieth: the compose override declares a `backend` that is
        # a bare JRE image with no command, its container exits at once, and the diagnosis handed to the
        # factory named only `db` and `backup` and said the fault was inside one of them.
        ps = _run(["docker", "compose", "-p", COMPOSE_PROJECT_NAME, "ps", "--all", "--format", "json"],
                  timeout_seconds=30)
        if ps.returncode != 0:
            return ""
        rows = []
        for line in ps.stdout.splitlines():
            line = line.strip()
            if not line:
                continue
            try:
                parsed = json.loads(line)
            except json.JSONDecodeError:
                continue
            rows.extend(parsed if isinstance(parsed, list) else [parsed])

        unhealthy = [r for r in rows
                     if str(r.get("State", "")).lower() not in ("running",)
                     or "unhealthy" in str(r.get("Health", "")).lower()]
        # 2026-08-20, measured on the very first run of this report: at health-check time the app container
        # was still State=running - a Spring Boot process that dies twelve seconds later on a bad datasource
        # is "running" while it boots. Returning "every service reports running" was honest and useless: the
        # health port refused, so SOMETHING is wrong, and the only place the reason can be is a running
        # service's own log. "Up but not serving" is precisely the case the log is needed for.
        # 2026-08-23 (F12): a service the compose declares and for which no container exists at all is a
        # different fact from a service that is up and misbehaving, and it is decided before the disjunction
        # below. The old branch quantified over the services it could see and concluded the fault was inside
        # one of them - a claim that presupposes the failing service is among them. When the product declares
        # a service that never materialised, the defect is ASSEMBLY, and the launcher's own 2026-08-19 note
        # says that distinction is what decides whether the next task is routed to operations or to assembly.
        declared = _declared_services()
        present = {str(r.get("Service") or "") for r in rows}
        missing = sorted(name for name in declared if name and name not in present)
        if missing:
            # Capped like the normal path below, and for the same reason: this string is concatenated onto
            # the health error and stored in client_runtime_observations.error_text, a VARCHAR(4000). An
            # overflow does not truncate the diagnosis, it fails the INSERT and loses the observation - a
            # better explanation traded for no observation at all.
            return (" | assembly: the compose declares service(s) " + ", ".join(missing)
                    + " for which no container exists at all - not stopped, not unhealthy, absent. Nothing "
                    + "in this topology can answer on the health port. This is an assembly defect: the "
                    + "service is declared but never built or started, so no operations change can fix it."
                    )[:max_chars]

        if not unhealthy:
            unhealthy = rows[:3]
            prefix = " | assembly: every service reports running, so the failure is inside one of them"
        else:
            prefix = " | assembly: "

        parts = []
        for row in unhealthy[:3]:
            service = row.get("Service") or row.get("Name") or "<unnamed>"
            state = row.get("State", "<unknown>")
            # 2026-08-20, measured: `docker compose -p NAME logs SERVICE` returned an empty body on every
            # real run - without `-f <compose file>` the service name does not resolve, and the command
            # fails quietly rather than erroring. The container's own name is already in this same `ps` row
            # and `docker logs` needs no compose context at all, so ask the container directly.
            container = row.get("Name") or service
            logs = _run(["docker", "logs", "--tail", "40", str(container)], timeout_seconds=30)
            tail = ((logs.stdout or "") + (logs.stderr or "")).strip()[-1200:]
            if not tail:
                # Silence is not information. Say what can be seen instead - see _silent_container_facts.
                tail = _silent_container_facts(str(container), row)
            parts.append(f"service '{service}' (container '{container}') state={state}: {tail}")
        return prefix + " || ".join(parts)[:max_chars]
    except Exception:  # noqa: BLE001 - diagnostics must never break the observation they explain
        return ""


def _normalize_target_url(url: str) -> str:
    if "localhost" in url:
        return url.replace("localhost", "host.docker.internal")
    if "127.0.0.1" in url:
        return url.replace("127.0.0.1", "host.docker.internal")
    return url


@app.post("/healthcheck", response_model=HealthCheckResponse)
def healthcheck(req: HealthCheckRequest) -> HealthCheckResponse:
    started = time.monotonic()
    target_url = _normalize_target_url(req.url)
    deadline = started + max(req.timeout_seconds, 60.0)
    last_error = None

    while time.monotonic() < deadline:
        try:
            response = requests.get(target_url, timeout=3.0)
            if response.status_code >= 200 and response.status_code < 400:
                return HealthCheckResponse(status_code=response.status_code, latency_ms=_elapsed_ms(started))
        except requests.RequestException as e:
            last_error = e
            if target_url != req.url:
                try:
                    response = requests.get(req.url, timeout=3.0)
                    if response.status_code >= 200 and response.status_code < 400:
                        return HealthCheckResponse(status_code=response.status_code, latency_ms=_elapsed_ms(started))
                except requests.RequestException:
                    pass
        time.sleep(1.5)

    return HealthCheckResponse(status_code=None, latency_ms=_elapsed_ms(started),
                               error=str(last_error or "health check timed out") + _assembly_report())


@app.post("/fetch", response_model=FetchResponse)
def fetch(req: FetchRequest) -> FetchResponse:
    started = time.monotonic()
    target_url = _normalize_target_url(req.url)
    try:
        response = requests.get(target_url, timeout=req.timeout_seconds)
        return FetchResponse(status_code=response.status_code, body=response.text[:MAX_FETCH_BODY_CHARS],
                              latency_ms=_elapsed_ms(started))
    except requests.RequestException as e:
        if target_url != req.url:
            try:
                response = requests.get(req.url, timeout=req.timeout_seconds)
                return FetchResponse(status_code=response.status_code, body=response.text[:MAX_FETCH_BODY_CHARS],
                                      latency_ms=_elapsed_ms(started))
            except requests.RequestException:
                pass
        return FetchResponse(status_code=None, latency_ms=_elapsed_ms(started), error=str(e))


@app.post("/teardown", response_model=TeardownResponse)
def teardown() -> TeardownResponse:
    global _current_project_slug
    if _current_project_slug is None:
        return TeardownResponse(success=True)
    workdir = WORKSPACE_ROOT / _current_project_slug
    compose_file = workdir / "docker-compose.yml"
    try:
        if compose_file.exists():
            down = _run(
                ["docker", "compose", "-p", COMPOSE_PROJECT_NAME, "-f", str(compose_file), "down", "-v", "--remove-orphans"],
                cwd=workdir, timeout_seconds=120,
            )
            if down.returncode != 0:
                return TeardownResponse(success=False, error=f"docker compose down failed: {down.stderr[-2000:]}")
        _current_project_slug = None
        return TeardownResponse(success=True)
    except Exception as e:  # noqa: BLE001
        return TeardownResponse(success=False, error=str(e))


def _elapsed_ms(started: float) -> int:
    return int((time.monotonic() - started) * 1000)
