#!/usr/bin/env python3
"""Autonomous health & HikariPool stall watchdog for eneikproductionsys-backend-1.
Checks /actuator/health and recent HikariPool exhaustion logs every 5 minutes.
If >=3 HikariPool timeout errors appear in last 2m OR health fails, logs diagnostic
snapshot to docs/reports/HIKARI_WATCHDOG_LOG.md and restarts backend (30m cooldown).
"""
import json, subprocess, time, urllib.request
from datetime import datetime, timezone
from pathlib import Path

STATE = Path("/tmp/eneik_hikari_watchdog.json")
LOG_MD = Path("/opt/EneikProductionSys/docs/reports/HIKARI_WATCHDOG_LOG.md")
COOLDOWN_SEC = 1800

def now_ts(): return time.time()

def load_state():
    if STATE.exists():
        try: return json.loads(STATE.read_text())
        except Exception: pass
    return {"last_restart": 0}

def check_health():
    try:
        with urllib.request.urlopen("http://127.0.0.1:8080/actuator/health", timeout=8) as r:
            return r.status == 200
    except Exception:
        return False

def check_hikari_stalls():
    r = subprocess.run(
        ["docker", "logs", "--since", "2m", "eneikproductionsys-backend-1"],
        capture_output=True, text=True, timeout=15
    )
    txt = r.stdout + r.stderr
    stalls = txt.count("HikariPool-1 - Connection is not available")
    leaks = [l for l in txt.splitlines() if "Connection leak detection" in l][-3:]
    return stalls, leaks

def main():
    healthy = check_health()
    stalls, leaks = check_hikari_stalls()
    if healthy and stalls < 3:
        return
    st = load_state()
    if now_ts() - st.get("last_restart", 0) < COOLDOWN_SEC:
        print(f"SKIP_COOLDOWN healthy={healthy} stalls={stalls}")
        return
    ts = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")
    entry = f"\n## [{ts}] Auto-Recovery Triggered\n- healthy={healthy}, hikari_stalls_2m={stalls}\n- leak_hints={leaks}\n"
    LOG_MD.parent.mkdir(parents=True, exist_ok=True)
    with open(LOG_MD, "a", encoding="utf-8") as f:
        f.write(entry)
    subprocess.run(["docker", "restart", "eneikproductionsys-backend-1"], check=True, timeout=60)
    STATE.write_text(json.dumps({"last_restart": now_ts(), "at": ts}))
    print(f"AUTO_RESTARTED eneikproductionsys-backend-1 (healthy={healthy}, stalls={stalls})")

if __name__ == "__main__":
    main()
