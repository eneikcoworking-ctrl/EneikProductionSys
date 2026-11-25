#!/usr/bin/env python3
"""
Unified Budget Circuit Breaker & DeepSeek Balance Guard for Eneik Ecosystem.

Tracks daily spend/call counters in /var/lib/eneik_budget.json (resets at 00:00 UTC):
- DeepSeek API: daily USD ceiling + real-time balance check (cached 5m) + instant 402 circuit breaker
- AGY (Gemini/Claude): daily call cap + dedicated reserve for sovereign-ops console
- Factory pause flag (`factory_paused`) + daily L1/L2 relay tick cap

When DeepSeek runs out of funds (HTTP 402, insufficient_balance, or balance < floor),
callers automatically know to fail over to the bounded AGY reserve.
"""

from __future__ import annotations

import fcntl
import json
import os
import re
import tempfile
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

BUDGET_FILE = Path(os.environ.get("ENEIK_BUDGET_FILE", "/var/lib/eneik_budget.json"))
LOCK_FILE = Path(os.environ.get("ENEIK_BUDGET_LOCK", "/var/lib/eneik_budget.lock"))

DEFAULT_LIMITS = {
    "agy_daily_calls": int(os.environ.get("ENEIK_AGY_DAILY_LIMIT", "25")),
    "agy_worker_fallback_daily_calls": int(os.environ.get("ENEIK_AGY_WORKER_FALLBACK_LIMIT", "10")),
    "relay_daily_ticks": int(os.environ.get("ENEIK_RELAY_DAILY_TICKS", "6")),
    "relay_cooldown_sec": int(os.environ.get("ENEIK_RELAY_COOLDOWN_SEC", "1800")),
    "deepseek_daily_usd": float(os.environ.get("ENEIK_DEEPSEEK_DAILY_USD", "1.50")),
    "deepseek_balance_floor_usd": float(os.environ.get("ENEIK_DEEPSEEK_BALANCE_FLOOR_USD", "0.20")),
    "deepseek_balance_cache_sec": 300,
    "deepseek_exhausted_cooldown_sec": 1800,
}

# Approximate blended $/1M tokens (conservative ceiling for local accounting)
MODEL_USD_PER_1M = {
    "deepseek-flash": 0.45,
    "deepseek-v4-pro": 1.80,
}


def utc_day(now: float | None = None) -> str:
    return datetime.fromtimestamp(now if now is not None else time.time(), tz=timezone.utc).strftime("%Y-%m-%d")


def read_deepseek_api_key() -> str:
    if os.environ.get("DEEPSEEK_API_KEY"):
        return os.environ["DEEPSEEK_API_KEY"].strip()
    for candidate in (
        Path("/opt/EneikProductionSys/.env"),
        Path("/etc/systemd/system/sovereign-ops.service"),
        Path("/opt/agent-support/credentials/deepseek.key"),
    ):
        if not candidate.exists():
            continue
        try:
            text = candidate.read_text(encoding="utf-8", errors="ignore").strip()
            if candidate.name == "deepseek.key" and text and " " not in text:
                return text
            m = re.search(r"(?:^|Environment=[\"']?)DEEPSEEK_API_KEY=([^\s\"']+)", text, re.MULTILINE)
            if m:
                return m.group(1).strip()
        except OSError:
            pass
    return ""


def _empty_state(day: str) -> dict[str, Any]:
    return {
        "day": day,
        "factory_paused": False,
        "pause_reason": "",
        "limits": dict(DEFAULT_LIMITS),
        "agy_calls": 0,
        "agy_calls_by_source": {},
        "agy_worker_fallback_calls": 0,
        "relay_ticks": 0,
        "last_relay_tick_ts": 0.0,
        "deepseek_calls": 0,
        "deepseek_calls_by_model": {},
        "deepseek_usd_est": 0.0,
        "deepseek_last_balance_usd": None,
        "deepseek_balance_checked_at": 0.0,
        "deepseek_exhausted_until": 0.0,
        "deepseek_last_error": "",
        "updated_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    }


def _atomic_write_json(path: Path, data: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(prefix=path.name + ".", dir=str(path.parent))
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
            f.write("\n")
        os.chmod(tmp, 0o644)
        os.replace(tmp, path)
    finally:
        if os.path.exists(tmp):
            os.unlink(tmp)


def _load_unlocked(now: float | None = None) -> dict[str, Any]:
    day = utc_day(now)
    if BUDGET_FILE.exists():
        try:
            data = json.loads(BUDGET_FILE.read_text(encoding="utf-8"))
            if isinstance(data, dict):
                if data.get("day") != day:
                    fresh = _empty_state(day)
                    fresh["factory_paused"] = bool(data.get("factory_paused", False))
                    fresh["pause_reason"] = str(data.get("pause_reason", ""))
                    fresh["deepseek_last_balance_usd"] = data.get("deepseek_last_balance_usd")
                    fresh["deepseek_balance_checked_at"] = data.get("deepseek_balance_checked_at", 0.0)
                    fresh["deepseek_exhausted_until"] = data.get("deepseek_exhausted_until", 0.0)
                    return fresh
                merged = _empty_state(day)
                merged.update(data)
                merged["limits"] = {**DEFAULT_LIMITS, **(data.get("limits") or {})}
                return merged
        except Exception:
            pass
    return _empty_state(day)


def _with_lock(fn):
    LOCK_FILE.parent.mkdir(parents=True, exist_ok=True)
    with open(LOCK_FILE, "a+", encoding="utf-8") as lf:
        fcntl.flock(lf.fileno(), fcntl.LOCK_EX)
        try:
            state = _load_unlocked()
            res, dirty = fn(state)
            if dirty:
                state["updated_at"] = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
                _atomic_write_json(BUDGET_FILE, state)
            return res
        finally:
            fcntl.flock(lf.fileno(), fcntl.LOCK_UN)


def get_status() -> dict[str, Any]:
    return _with_lock(lambda st: (dict(st), False))


def set_factory_paused(paused: bool, reason: str = "") -> dict[str, Any]:
    def _op(st):
        st["factory_paused"] = bool(paused)
        st["pause_reason"] = reason if paused else ""
        return dict(st), True
    return _with_lock(_op)


def is_factory_paused() -> tuple[bool, str]:
    st = get_status()
    return bool(st.get("factory_paused", False)), str(st.get("pause_reason", ""))


def is_deepseek_balance_error(err_text: str, status_code: int | None = None) -> bool:
    if status_code == 402:
        return True
    low = (err_text or "").lower()
    return any(
        tok in low
        for tok in (
            "insufficient balance",
            "insufficient_balance",
            "payment required",
            "http 402",
            "code 402",
            "credit balance is too low",
            "out of balance",
            "no balance",
        )
    )


def fetch_deepseek_balance_usd(api_key: str, timeout: int = 8) -> float | None:
    if not api_key:
        return None
    req = urllib.request.Request(
        "https://api.deepseek.com/user/balance",
        headers={"Authorization": f"Bearer {api_key}", "Accept": "application/json"},
        method="GET",
    )
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            data = json.loads(resp.read().decode("utf-8"))
        if not data.get("is_available", True):
            return 0.0
        infos = data.get("balance_infos") or []
        for info in infos:
            if str(info.get("currency", "")).upper() == "USD":
                return float(info.get("total_balance", 0.0))
        if infos:
            return float(infos[0].get("total_balance", 0.0))
    except Exception:
        return None
    return None


def can_use_deepseek(estimated_usd: float = 0.02, check_remote_balance: bool = True) -> tuple[bool, str]:
    api_key = read_deepseek_api_key()
    if not api_key:
        return False, "deepseek_key_missing"

    now = time.time()
    st = get_status()
    limits = st["limits"]

    if now < float(st.get("deepseek_exhausted_until", 0.0)):
        rem = int(float(st["deepseek_exhausted_until"]) - now)
        return False, f"deepseek_balance_exhausted_cooldown({rem}s)"

    if float(st.get("deepseek_usd_est", 0.0)) + estimated_usd > float(limits["deepseek_daily_usd"]):
        return False, f"deepseek_daily_usd_limit({st['deepseek_usd_est']:.3f}/{limits['deepseek_daily_usd']})"

    if check_remote_balance:
        last_checked = float(st.get("deepseek_balance_checked_at", 0.0))
        cached_bal = st.get("deepseek_last_balance_usd")
        if cached_bal is None or (now - last_checked) >= float(limits["deepseek_balance_cache_sec"]):
            bal = fetch_deepseek_balance_usd(api_key)
            if bal is not None:
                def _save_bal(s):
                    s["deepseek_last_balance_usd"] = round(bal, 4)
                    s["deepseek_balance_checked_at"] = now
                    if bal < float(s["limits"]["deepseek_balance_floor_usd"]):
                        s["deepseek_exhausted_until"] = now + float(s["limits"]["deepseek_exhausted_cooldown_sec"])
                        s["deepseek_last_error"] = f"balance_below_floor({bal:.4f} < {s['limits']['deepseek_balance_floor_usd']})"
                    return bal, True
                cached_bal = _with_lock(_save_bal)

        if cached_bal is not None and float(cached_bal) < float(limits["deepseek_balance_floor_usd"]):
            return False, f"deepseek_balance_below_floor(${float(cached_bal):.2f} < ${float(limits['deepseek_balance_floor_usd']):.2f})"

    return True, "ok"


def mark_deepseek_exhausted(reason: str = "http_402") -> None:
    now = time.time()
    def _op(st):
        st["deepseek_exhausted_until"] = now + float(st["limits"]["deepseek_exhausted_cooldown_sec"])
        st["deepseek_last_error"] = reason[:200]
        return None, True
    _with_lock(_op)


def record_deepseek_usage(model: str, total_tokens: int = 0, source: str = "generic") -> None:
    rate = MODEL_USD_PER_1M.get(model, 0.80)
    usd = (max(total_tokens, 500) / 1_000_000.0) * rate
    def _op(st):
        st["deepseek_calls"] = int(st.get("deepseek_calls", 0)) + 1
        by_m = st.setdefault("deepseek_calls_by_model", {})
        by_m[model] = int(by_m.get(model, 0)) + 1
        st["deepseek_usd_est"] = round(float(st.get("deepseek_usd_est", 0.0)) + usd, 6)
        return None, True
    _with_lock(_op)


def can_use_agy(source: str = "generic", is_worker_fallback: bool = False) -> tuple[bool, str]:
    st = get_status()
    limits = st["limits"]
    if source == "ops_console":
        return True, "ops_console_priority"
    if int(st.get("agy_calls", 0)) >= int(limits["agy_daily_calls"]):
        return False, f"agy_daily_limit({st['agy_calls']}/{limits['agy_daily_calls']})"
    if is_worker_fallback:
        if int(st.get("agy_worker_fallback_calls", 0)) >= int(limits["agy_worker_fallback_daily_calls"]):
            return False, f"agy_worker_fallback_limit({st['agy_worker_fallback_calls']}/{limits['agy_worker_fallback_daily_calls']})"
    return True, "ok"


def record_agy_call(source: str = "generic", is_worker_fallback: bool = False) -> None:
    def _op(st):
        st["agy_calls"] = int(st.get("agy_calls", 0)) + 1
        by_s = st.setdefault("agy_calls_by_source", {})
        by_s[source] = int(by_s.get(source, 0)) + 1
        if is_worker_fallback:
            st["agy_worker_fallback_calls"] = int(st.get("agy_worker_fallback_calls", 0)) + 1
        return None, True
    _with_lock(_op)


def check_relay_budget(now: float | None = None) -> tuple[str, int, str]:
    """Returns (decision: 'run'|'paused'|'daily_cap'|'cooldown', wait_sec, detail)."""
    now = now if now is not None else time.time()
    st = get_status()
    if st.get("factory_paused"):
        return "paused", 300, str(st.get("pause_reason") or "paused_by_operator")
    limits = st["limits"]
    ticks = int(st.get("relay_ticks", 0))
    max_ticks = int(limits["relay_daily_ticks"])
    if ticks >= max_ticks:
        return "daily_cap", 600, f"relay_daily_cap({ticks}/{max_ticks})"
    agy_ok, agy_reason = can_use_agy(source="factory_relay")
    if not agy_ok:
        return "daily_cap", 600, agy_reason
    last_ts = float(st.get("last_relay_tick_ts", 0.0))
    cd = int(limits["relay_cooldown_sec"])
    if last_ts > 0 and (now - last_ts) < cd:
        rem = int(cd - (now - last_ts))
        return "cooldown", rem, f"relay_cooldown({rem}s remaining)"
    return "run", 0, "ok"


def record_relay_tick(source: str = "factory_relay", now: float | None = None) -> None:
    now = now if now is not None else time.time()
    def _op(st):
        st["relay_ticks"] = int(st.get("relay_ticks", 0)) + 1
        st["last_relay_tick_ts"] = now
        st["agy_calls"] = int(st.get("agy_calls", 0)) + 1
        by_s = st.setdefault("agy_calls_by_source", {})
        by_s[source] = int(by_s.get(source, 0)) + 1
        return None, True
    _with_lock(_op)


if __name__ == "__main__":
    import sys
    if len(sys.argv) >= 2 and sys.argv[1] == "pause":
        reason = " ".join(sys.argv[2:]) or "manual_pause"
        print(json.dumps(set_factory_paused(True, reason), ensure_ascii=False, indent=2))
    elif len(sys.argv) >= 2 and sys.argv[1] == "resume":
        print(json.dumps(set_factory_paused(False, ""), ensure_ascii=False, indent=2))
    else:
        print(json.dumps(get_status(), ensure_ascii=False, indent=2))
