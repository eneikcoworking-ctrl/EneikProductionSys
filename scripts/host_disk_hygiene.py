#!/usr/bin/env python3
"""Safe, deterministic host disk hygiene for EneikProductionSys.
Philosophy: rely on Memorilla latent memory, not unbounded raw worker transcripts.
Strict invariants:
  - NEVER touches remotecli (L2), remotecli2 (L1), auth/oauth/config, DBs, or git repos.
  - Cleans ONLY ephemeral product worker logs/brains (>3 days) in remotecli3..10.
  - Caps systemd journal at 150M and prunes rotated system logs & stale Docker cache (>72h).
"""
import shutil
import subprocess
import time
from datetime import datetime, timezone
from pathlib import Path

AGE_DAYS = 3
CUTOFF = time.time() - AGE_DAYS * 86400
LOG_FILE = Path("/var/log/eneik_disk_hygiene.log")


def root_usage_pct() -> str:
    r = subprocess.run(["df", "-h", "--output=pcent,avail", "/"], capture_output=True, text=True)
    lines = [l.strip() for l in r.stdout.splitlines() if l.strip()]
    return lines[-1] if lines else "?"


def dir_size(p: Path) -> int:
    return sum(f.stat().st_size for f in p.rglob("*") if f.is_file()) if p.exists() else 0


def clean_old_entries(parent: Path) -> int:
    freed = 0
    if not parent.exists():
        return 0
    for child in list(parent.iterdir()):
        try:
            if child.stat().st_mtime < CUTOFF:
                if child.is_dir():
                    freed += dir_size(child)
                    shutil.rmtree(child, ignore_errors=True)
                elif child.is_file():
                    freed += child.stat().st_size
                    child.unlink(missing_ok=True)
        except Exception:
            continue
    return freed


def main():
    before = root_usage_pct()
    freed = 0

    # 1. Ephemeral worker logs/brains (>3 days) in remotecli3..10 ONLY
    for i in range(3, 11):
        home = Path(f"/home/remotecli{i}")
        if not home.exists():
            continue
        for rel in [
            ".gemini/antigravity-cli/brain",
            ".gemini/antigravity-cli/conversations",
            ".gemini/antigravity-cli/logs",
            ".gemini/antigravity/brain",
            ".gemini/antigravity/conversations",
            ".gemini/antigravity/code_tracker",
            ".gemini/antigravity/implicit",
            ".cache",
            ".npm",
        ]:
            freed += clean_old_entries(home / rel)

    # 2. Systemd journal & rotated /var/log files (>3 days)
    subprocess.run(["journalctl", "--vacuum-size=150M"], capture_output=True)
    var_log = Path("/var/log")
    for pat in ("auth.log.[0-9]*", "syslog.[0-9]*", "kern.log.[0-9]*", "ufw.log.[0-9]*"):
        for f in var_log.glob(pat):
            try:
                if f.is_file() and f.stat().st_mtime < CUTOFF:
                    freed += f.stat().st_size
                    f.unlink(missing_ok=True)
            except Exception:
                pass

    # 3. Safe Docker hygiene (dangling images + build cache >72h)
    subprocess.run(["docker", "image", "prune", "-f"], capture_output=True)
    subprocess.run(["docker", "builder", "prune", "-f", "--filter", "until=72h"], capture_output=True)

    after = root_usage_pct()
    ts = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")
    line = f"[{ts}] hygiene complete | freed_files={freed/1e6:.1f}MB | root_before=({before}) -> root_after=({after})\n"
    with open(LOG_FILE, "a", encoding="utf-8") as lf:
        lf.write(line)
    print(line.strip())


if __name__ == "__main__":
    main()
