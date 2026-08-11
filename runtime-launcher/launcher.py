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
PORT_OVERRIDE_FILENAME = "runtime-launcher-port-override.yml"

# 2026-08-11 (client runtime observability plan, port collision found live): a client project's own
# docker-compose.yml very commonly publishes 8080 (test-forty-third does) - the SAME host port this
# factory's own backend already publishes permanently. Every launch remaps every published host port to
# a fixed range starting here instead, via a generated compose override - never requires the client
# project's own compose file to change, and stays deterministic (exactly one project is ever launched
# at a time, per the operator's own architecture decision - see module docstring).
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


def _generate_port_override(compose_file: Path) -> tuple[Optional[Path], dict[str, int]]:
    """Rewrite every published host port in the compose file to a fixed range starting at
    EXTERNAL_PORT_BASE, so a client project's own port choice never has to change or collide with this
    factory's own services. Returns (override file path, {service_name: external_port}) - the path is
    None and the map empty when the compose file declares no published ports (nothing to remap)."""
    try:
        spec = yaml.safe_load(compose_file.read_text()) or {}
    except Exception:
        return None, {}

    services = spec.get("services") or {}
    override_services: dict[str, dict] = {}
    port_map: dict[str, int] = {}
    next_port = EXTERNAL_PORT_BASE

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
        override_services[name] = {"ports": new_ports}

    if not override_services:
        return None, {}

    override_path = compose_file.parent / PORT_OVERRIDE_FILENAME
    override_path.write_text(yaml.safe_dump({"services": override_services}))
    return override_path, port_map


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

        override_path, port_map = _generate_port_override(compose_file)
        compose_args = ["-f", str(compose_file)]
        if override_path is not None:
            compose_args += ["-f", str(override_path)]

        up = _run(
            ["docker", "compose", "-p", COMPOSE_PROJECT_NAME, *compose_args, "up", "-d", "--build"],
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


@app.post("/healthcheck", response_model=HealthCheckResponse)
def healthcheck(req: HealthCheckRequest) -> HealthCheckResponse:
    started = time.monotonic()
    try:
        response = requests.get(req.url, timeout=req.timeout_seconds)
        return HealthCheckResponse(status_code=response.status_code, latency_ms=_elapsed_ms(started))
    except requests.RequestException as e:
        return HealthCheckResponse(status_code=None, latency_ms=_elapsed_ms(started), error=str(e))


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
            compose_args = ["-f", str(compose_file)]
            override_path = workdir / PORT_OVERRIDE_FILENAME
            if override_path.exists():
                compose_args += ["-f", str(override_path)]
            down = _run(
                ["docker", "compose", "-p", COMPOSE_PROJECT_NAME, *compose_args, "down", "-v", "--remove-orphans"],
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
