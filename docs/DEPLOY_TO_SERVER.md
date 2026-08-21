# Moving the factory to a Linux server

Written 2026-08-22 from the running system, not from memory: every path, port and size below was read off
the live host. Hand this file and the repository URL to whoever does the deployment.

The repository is public — `https://github.com/eneikcoworking-ctrl/EneikProductionSys` — so nothing needs
to be sent except the one state file described in step 3, and that file needs care (see the warning).

---

## What the system is

Five containers, started by one `docker compose up`:

| Service | Port | What it does |
| --- | --- | --- |
| `backend` | 8080 | the factory itself — Spring Boot, owns all state |
| `frontend` | 3000 | the operator's web UI |
| `ml` | 8000 | a thin HTTP proxy used for scoring; **internal only** |
| `runtime-launcher` | 8091 | launches the *client's* product to observe it; **holds the Docker socket** |
| `judgment-sidecar` | 8092 | runs a Claude Code CLI as an independent reviewer; **internal only** |

Persistent state lives in exactly three places, all bind-mounted from the repository directory:

- `./data` — the factory's database. **3.1 GB**, of which `eneik_db.mv.db` is **2786 MB**.
- `./project-workspaces` — 836 KB.
- `./runtime-launcher-workspace` — 84 MB of throwaway clones. **Do not copy it**; it is rebuilt on demand.

---

## Prerequisites on the server

- Docker Engine + the Compose plugin. Nothing else — no Java, Node or Maven; every build happens inside
  containers.
- **8 GB RAM minimum.** The current host has 3.9 GB and that single fact is a recorded defect: the factory
  and its own test suite cannot run at the same time, and one database read takes ~10 seconds.
- ~15 GB free disk for the database, images and build cache.
- A reverse proxy with authentication in front of the machine.

---

## Step 1 — read the security warnings before anything else

**Ports 8091, 8000 and 8092 must never be reachable from the internet.**

`runtime-launcher` on 8091 has `/var/run/docker.sock` mounted, because launching a client's product means
starting containers on this host. Anyone who can reach 8091 can start a container on the server and is
therefore root on the box. This is not a hardening nicety; it is the whole security boundary.

Expose **only 3000 and 8080**, and only behind a proxy that requires a login. If the firewall is the only
protection, bind the other three to loopback explicitly.

**The database file carries every credential the factory holds.** GitHub, Stitch and Linear access is
stored inside `eneik_db.mv.db`, not in environment variables — verified: the only credential in the compose
environment is `GEMINI_API_KEY`, which is being retired. Copying that file copies the keys. Move it over
SSH/`scp` or an encrypted volume — never email, chat or a public link.

---

## Step 2 — stop the factory cleanly on the old host

```bash
docker compose stop -t 60
```

Wait for it to finish. H2 compacts and closes on shutdown; **copying the file while the backend runs
produces a corrupt database** — this has already happened once and cost a restore.

---

## Step 3 — copy the state

From the old host, after step 2:

```bash
scp EneikProductionSys/data/eneik_db.mv.db          user@server:/opt/eneik/data/
scp -r EneikProductionSys/project-workspaces        user@server:/opt/eneik/
```

2.8 GB over the wire. `runtime-launcher-workspace/` is deliberately not copied.

---

## Step 4 — clone and place the state

```bash
git clone https://github.com/eneikcoworking-ctrl/EneikProductionSys.git /opt/eneik
# then move the copied data/ and project-workspaces/ into /opt/eneik/
```

`data/` and `project-workspaces/` are gitignored, so the clone will not overwrite them.

---

## Step 5 — the one step only the account owner can do

The `judgment-sidecar` runs the Claude Code CLI as an independent reviewer, and mounts a credentials
directory into the container. On the current host that is the Windows path
`C:\Users\<user>\.claude`, which does not exist on Linux.

On the server:

1. Log in to Claude Code **as the account owner** — a deployment engineer cannot do this step for you.
2. Note the directory it creates, normally `~/.claude`.
3. Put that path in `.env` (next step).

If this is skipped the factory still starts, but the judgment layer has no reviewer.

---

## Step 6 — write `.env`

`.env` is gitignored and must be created on the server. Every variable has a default except the ones
below; only these normally need setting:

```ini
CLAUDE_CREDENTIAL_DIR=/home/<user>/.claude
GITHUB_ORG=eneikcoworking-ctrl
COMPOSE_PROJECT_NAME=eneikproductionsys
```

Credentials themselves are already inside the database from step 3. `GEMINI_API_KEY` can be left unset —
the factory is being moved off Gemini and the key's quota is exhausted.

---

## Step 7 — start

```bash
cd /opt/eneik
docker compose up -d --build
```

The first build takes 10–20 minutes. Flyway applies any pending migrations on boot; nothing needs to be run
by hand.

---

## Step 8 — verify, by evidence rather than by exit code

```bash
# every container up, ml/launcher/sidecar healthy
docker ps

# the backend answers (allow ~2 min on first boot; longer while the store is 2.8 GB)
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/actuator/health

# the state arrived intact - this should show the projects, not an empty list
curl -s http://localhost:8080/api/projects | head -c 300

# no startup failure
docker logs eneikproductionsys-backend-1 2>&1 | grep -c 'APPLICATION FAILED'   # must print 0
```

If `/api/projects` is empty while the file is 2.8 GB, the copy was taken while the backend was running.
Stop, re-copy from a cleanly stopped source, and start again.

---

## After the move

**Compact the database.** It is 2786 MB holding roughly 88 MB of live data. Do it on the server, where
there is room to take a copy first: stop the factory, copy the file, compact, start, verify `/api/projects`
still answers, and only then delete the copy. There is currently **no rollback snapshot in existence** —
they were deleted to reclaim disk on the old host — so the copy is not optional.

**Decommission the old host** only after step 8 passes. Nothing on it is needed afterwards.

---

## What this move does and does not buy

It removes a physical limit. Builds stop competing with the running factory, database reads stop taking
seconds, and the full test suite stops requiring the backend to be shut down.

**It fixes nothing else.** Every defect in `LIVE_PRODUCT_PLAN_2026-08-19.md` travels with the system
unchanged, including the one that currently stops the delivered product from starting at all. The server
removes a physical constraint, not a logical one.
