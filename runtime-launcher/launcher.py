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


def _remap_ports(compose_file: Path) -> dict[str, int]:
    """Rewrite every published host port in the compose file IN PLACE to a fixed range starting at
    EXTERNAL_PORT_BASE. A separate override file passed via a second `-f` does NOT work for this: Docker
    Compose concatenates `ports:` lists across merged files rather than replacing by target port (confirmed
    live - `-f base.yml -f override.yml` left BOTH 8080 and 18080 in the resolved config, and the compose
    file's own 8080 entry still failed to bind against this factory's own backend). Rewriting the compose
    file itself, once, in the ephemeral clone (never the client's real repo) is the only way that actually
    works. Returns {service_name: external_port}, empty if the compose file declares no published ports."""
    try:
        spec = yaml.safe_load(compose_file.read_text()) or {}
    except Exception:
        return {}

    services = spec.get("services") or {}
    port_map: dict[str, int] = {}
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
            container_port = str(entry).split(":")[-1]
            new_ports.append(f"{next_port}:{container_port}")
            port_map[name] = next_port
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

        compose_file = workdir / "docker-compose.yml"
        if not compose_file.exists():
            return LaunchResponse(success=False, duration_ms=_elapsed_ms(started),
                                   error="docker-compose.yml not found at repo root after clone")

        port_map = _remap_ports(compose_file)

        up = _run(
            ["docker", "compose", "-p", COMPOSE_PROJECT_NAME, "-f", str(compose_file), "up", "-d", "--build"],
            cwd=workdir, timeout_seconds=600,
        )
        if up.returncode != 0:
            return LaunchResponse(success=False, duration_ms=_elapsed_ms(started), error=f"docker compose up failed: {up.stderr[-2000:]}")

        _current_project_slug = req.project_slug
        primary_port = next(iter(port_map.values()), None)
        return LaunchResponse(success=True, duration_ms=_elapsed_ms(started), external_port=primary_port)
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
def _assembly_report(max_chars: int = 2400) -> str:
    """Which services of the current run are not up, and what their own logs say. Best-effort: any failure
    here must never replace the health result itself - an empty report is honest, a crash is not."""
    if _current_project_slug is None:
        return ""
    try:
        ps = _run(["docker", "compose", "-p", COMPOSE_PROJECT_NAME, "ps", "--format", "json"],
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
        if not unhealthy:
            return " | assembly: every service reports running - the failure is inside a running service"

        parts = []
        for row in unhealthy[:3]:
            service = row.get("Service") or row.get("Name") or "<unnamed>"
            state = row.get("State", "<unknown>")
            logs = _run(["docker", "compose", "-p", COMPOSE_PROJECT_NAME, "logs", "--tail", "25", str(service)],
                        timeout_seconds=30)
            tail = (logs.stdout or logs.stderr or "").strip()[-1200:]
            parts.append(f"service '{service}' state={state}: {tail}")
        return " | assembly: " + " || ".join(parts)[:max_chars]
    except Exception:  # noqa: BLE001 - diagnostics must never break the observation they explain
        return ""


@app.post("/healthcheck", response_model=HealthCheckResponse)
def healthcheck(req: HealthCheckRequest) -> HealthCheckResponse:
    started = time.monotonic()
    try:
        response = requests.get(req.url, timeout=req.timeout_seconds)
        return HealthCheckResponse(status_code=response.status_code, latency_ms=_elapsed_ms(started))
    except requests.RequestException as e:
        return HealthCheckResponse(status_code=None, latency_ms=_elapsed_ms(started),
                                   error=str(e) + _assembly_report())


@app.post("/fetch", response_model=FetchResponse)
def fetch(req: FetchRequest) -> FetchResponse:
    started = time.monotonic()
    try:
        response = requests.get(req.url, timeout=req.timeout_seconds)
        return FetchResponse(status_code=response.status_code, body=response.text[:MAX_FETCH_BODY_CHARS],
                              latency_ms=_elapsed_ms(started))
    except requests.RequestException as e:
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
