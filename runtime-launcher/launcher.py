"""
Sidecar launcher - Phase 1 of docs/reports/PLAN_client_runtime_observability_2026-08-09.md.

The ONLY component in the whole factory that ever holds the host docker socket. Deliberately tiny,
deliberately not part of the main Spring backend's dependency graph or codebase - a compromise or bug
in the backend's much larger surface can never reach this socket, because it never has it.

Stateless between calls except for one in-memory "what's currently running" pointer - the operator's
own architecture decision (2026-08-09) is that the factory ever observes exactly ONE active project at
a time, so there is deliberately no multi-tenant tracking here.

Three endpoints, matching the plan exactly:
  POST /launch      - clone/pull the given repo+ref, `docker compose up --build -d` it under a fixed
                       project name so a stale run is always addressable for teardown.
  POST /healthcheck  - GET a caller-given URL, report status code + latency. No assumptions about the
                       target's shape - the backend decides what URL/convention to check.
  POST /teardown     - `docker compose down -v --remove-orphans` the current run, always safe to call
                       even if nothing is running.
"""
import shutil
import subprocess
import time
from pathlib import Path
from typing import Optional

import requests
from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI()

WORKSPACE_ROOT = Path("/workspace")
COMPOSE_PROJECT_NAME = "runtime-observe"

_current_project_slug: Optional[str] = None


class LaunchRequest(BaseModel):
    repo_url: str
    ref: str = "main"
    project_slug: str


class LaunchResponse(BaseModel):
    success: bool
    duration_ms: int
    error: Optional[str] = None


class HealthCheckRequest(BaseModel):
    url: str
    timeout_seconds: float = 10.0


class HealthCheckResponse(BaseModel):
    status_code: Optional[int]
    latency_ms: int
    error: Optional[str] = None


class TeardownResponse(BaseModel):
    success: bool
    error: Optional[str] = None


def _run(args: list[str], cwd: Optional[Path] = None, timeout_seconds: int = 600) -> subprocess.CompletedProcess:
    return subprocess.run(
        args, cwd=cwd, capture_output=True, text=True, timeout=timeout_seconds
    )


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

        up = _run(
            ["docker", "compose", "-p", COMPOSE_PROJECT_NAME, "-f", str(compose_file), "up", "-d", "--build"],
            cwd=workdir, timeout_seconds=600,
        )
        if up.returncode != 0:
            return LaunchResponse(success=False, duration_ms=_elapsed_ms(started), error=f"docker compose up failed: {up.stderr[-2000:]}")

        _current_project_slug = req.project_slug
        return LaunchResponse(success=True, duration_ms=_elapsed_ms(started))
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
