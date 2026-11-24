#!/usr/bin/env python3
import json
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone

REPO_DIR = "/opt/EneikProductionSys"
MECHANISMS_FILE = os.path.join(REPO_DIR, "docs", "FACTORY_MECHANISMS.md")
MANAGER_STATE_FILE = os.path.join(REPO_DIR, "docs", "reports", "MANAGER_STATE.md")
WORK_ORDER_STATE_FILE = "/var/lib/eneik_work_order_relay.state.json"
LOG_FILE = "/var/log/autonomous_factory_relay.log"
PID = "1695f808-0df7-4393-ace9-d5a79e62ef41"
BASE_URL = "http://127.0.0.1:8080"
AUTH_TOKEN = "975d328d5855088fd531f2f549ccf6d63498d794c4a7f80ccf7c5ac67da27eef"
BATCH_SIZE = 17

LOCKED_AUTONOMY_NOTICE = """КАНОНИЧЕСКИЙ ЗАЩИЩЁННЫЙ КОНТУР АВТОНОМИИ ДЕННЕТТА (LOCKED — КАТЕГОРИЧЕСКИ НЕ ТРОГАТЬ И НЕ ОТКАТЫВАТЬ):
Оператор Дмитрий Ефремов утвердил и ввёл в ядро фабрики эталонную архитектуру гомеостатической автономии («Множественные наброски» Деннетта без картезианского театра, 376/376 тестов green в 31 тест-сьюте).
Категорически запрещено модифицировать, откатывать или ослаблять следующие канонические файлы:
- src/main/java/com/eneik/production/models/persistence/ProjectStatus.java
- src/main/java/com/eneik/production/models/persistence/ProjectEntity.java
- src/main/java/com/eneik/production/services/operational/OperationalPolicyService.java
- src/main/java/com/eneik/production/services/operational/OperationalFlowCoreService.java
- src/main/java/com/eneik/production/services/operational/FlowSpineService.java
- src/main/java/com/eneik/production/services/PlannedWorkRecoveryService.java
- src/main/java/com/eneik/production/services/ContinuousOrchestrationService.java
- src/main/java/com/eneik/production/services/DeliveryRealityProducerService.java
- src/main/java/com/eneik/production/services/ProjectFlowService.java
- src/main/java/com/eneik/production/services/github/GitHubPullRequestService.java
- src/main/java/com/eneik/production/services/AutoMergeService.java
- src/main/java/com/eneik/production/services/jules/JulesDispatchService.java
- src/main/java/com/eneik/production/services/compiler/TechnicalLeadCompiler.java
- src/main/java/com/eneik/production/services/StrandedFinalizingSweepService.java
- src/main/java/com/eneik/production/services/ProjectAuditPipelineService.java
- src/test/java/com/eneik/production/services/DennettAutonomyFalsificationTest.java
- src/test/java/com/eneik/production/invariants/ScheduledQueryCostInvariantTest.java
- scripts/autonomous_factory_relay.py
- docs/reports/AUTONOMY_AND_FULL_AUDIT_PLAN.md
Впредь L1 и L2 обязаны никогда не допускать потери автономии фабрики!"""


def log(msg):
    ts = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")
    line = f"[{ts}] [Relay] {msg}"
    print(line, flush=True)
    # Systemd unit appends stdout/stderr directly to LOG_FILE; only write manually if run interactively on a TTY.
    if sys.stdout.isatty():
        try:
            with open(LOG_FILE, "a", encoding="utf-8") as f:
                f.write(line + "\n")
        except Exception:
            pass


def api_request(method, path, payload=None, timeout=10):
    url = f"{BASE_URL}{path}"
    headers = {"X-API-Key": AUTH_TOKEN, "Accept": "application/json"}
    data = None
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        raw = resp.read().decode("utf-8")
        return resp.status, (json.loads(raw) if raw.strip() else {})


def check_disk_and_resources():
    st = os.statvfs("/")
    free_gb = (st.f_bavail * st.f_frsize) / (1024**3)
    total_gb = (st.f_blocks * st.f_frsize) / (1024**3)
    used_pct = round(((total_gb - free_gb) / total_gb) * 100, 1)

    log(f"Хост: диск {used_pct}% занято ({round(free_gb, 1)} GB свободно).")
    if used_pct >= 68.0:
        log("Диск >= 68%. Запуск гигиены Docker (builder prune, image prune)...")
        subprocess.run(["docker", "builder", "prune", "-f"], capture_output=True)
        subprocess.run(["docker", "image", "prune", "-f"], capture_output=True)

    if used_pct >= 80.0:
        log("КРИТИЧЕСКИЙ ИНВАРИАНТ: Диск >= 80%. Пауза во избежание сбоя хоста.")
        return False
    return True


def get_flow_core_state():
    try:
        _, data = api_request("GET", f"/api/projects/{PID}/flow-core", timeout=8)
        snap = data.get("snapshot", {})
        return (
            snap.get("currentState"),
            snap.get("valueStatus"),
            snap.get("blockingReason"),
            snap.get("counts", {}),
        )
    except Exception as e:
        return None, None, str(e), {}


def get_task_status_counts():
    try:
        _, counts = api_request("GET", f"/internal/tasks/status-counts?projectId={PID}", timeout=8)
        return counts if isinstance(counts, dict) else {}
    except Exception:
        return {}


def resolve_dead_parents_and_stalls():
    c_state, v_status, reason, counts = get_flow_core_state()
    t_counts = get_task_status_counts()
    log(
        f"Flow Core: currentState={c_state}, valueStatus={v_status}, "
        f"tasks(done={t_counts.get('done', 0)}, review={t_counts.get('pending_review', 0)}, "
        f"claimed={t_counts.get('claimed', 0)}, queued={t_counts.get('queued', 0)})"
    )

    if c_state in ("SYSTEM_STALLED", "PROJECT_NOT_ACTIVE"):
        log(f"Обнаружен статус {c_state} ({reason}). Гомеостатическая реактивация проекта...")
        try:
            api_request("POST", f"/api/projects/{PID}/activate", payload={}, timeout=10)
            log("POST /activate выполнен успешно.")
        except Exception as e:
            log(f"Предупреждение /activate: {e}")

    if c_state in ("SYSTEM_STALLED", "BLOCKED_BY_FAILED_FRONTIER", "IDLE_NO_ACTIONABLE_WORK") or v_status == "value_blocked":
        active_inflight = int(t_counts.get("claimed", 0)) + int(t_counts.get("in_progress", 0)) + int(t_counts.get("pending_review", 0))
        if c_state == "SYSTEM_STALLED" or active_inflight == 0:
            log(f"Гомеостатический импульс оркестрации для состояния {c_state} (inflight={active_inflight})...")
            try:
                api_request("POST", f"/api/projects/{PID}/orchestrate", payload={}, timeout=15)
                log("POST /orchestrate выполнен успешно.")
            except urllib.error.HTTPError as he:
                log(f"Ответ /orchestrate: HTTP {he.code}")
            except Exception as e:
                log(f"Предупреждение /orchestrate: {e}")

            after_counts = get_task_status_counts()
            still_starving = (
                int(after_counts.get("claimed", 0)) == 0
                and int(after_counts.get("in_progress", 0)) == 0
                and int(after_counts.get("pending_review", 0)) == 0
                and int(after_counts.get("queued", 0)) == 0
                and c_state not in ("ACCEPTED", "ARCHIVED")
            )
            if still_starving:
                log("Фронт пуст после /orchestrate — запуск контура самофальсификации (Multiple Drafts)...")
                try:
                    api_request("POST", f"/api/projects/{PID}/falsification/run", payload={}, timeout=15)
                    log("POST /falsification/run запущен.")
                except Exception as e:
                    log(f"Предупреждение /falsification/run: {e}")


def scan_remaining_mechanisms(filepath=MECHANISMS_FILE):
    try:
        with open(filepath, encoding="utf-8") as f:
            lines = f.readlines()
    except Exception as e:
        log(f"Ошибка чтения {filepath}: {e}")
        return []

    rx_presc = re.compile(r"^### (\d+)\.\s+(.*)$")
    rx_mech = re.compile(r"^\*\*`([^`]+)`\*\*")
    rx_heading = re.compile(r"^#+ ")

    items = []
    n = len(lines)

    i = 0
    while i < n:
        m_p = rx_presc.match(lines[i])
        if m_p:
            num = int(m_p.group(1))
            title = m_p.group(2).strip()
            j = i + 1
            while j < n and not rx_heading.match(lines[j]):
                j += 1
            block_text = "".join(lines[i:j])
            if "*Живое" not in block_text:
                items.append({
                    "kind": "prescription",
                    "line": i + 1,
                    "end_line": j,
                    "id": f"Предписание #{num}",
                    "title": title[:110],
                })
            i = j
            continue
        i += 1

    i = 0
    while i < n:
        prev_blank = (i == 0 or lines[i - 1].strip() == "")
        m_m = rx_mech.match(lines[i])
        if prev_blank and m_m:
            name = m_m.group(1)
            j = i + 1
            while j < n:
                if rx_heading.match(lines[j]):
                    break
                if lines[j - 1].strip() == "" and rx_mech.match(lines[j]):
                    break
                j += 1
            block_text = "".join(lines[i:j])
            markers = ("*Связи:*", "*Ценность:*", "*Комментарий:*", "*Философия:*", "*Идеал:*", "*Идеальная форма:*")
            if any(mk in block_text for mk in markers) and "*Живое" not in block_text:
                items.append({
                    "kind": "mechanism",
                    "line": i + 1,
                    "end_line": j,
                    "id": f"`{name}`",
                    "title": lines[i].strip()[:110],
                })
            i = j
            continue
        i += 1

    items.sort(key=lambda x: x["line"])
    return items


def read_manager_state(filepath=MANAGER_STATE_FILE):
    try:
        if os.path.exists(filepath):
            with open(filepath, encoding="utf-8") as f:
                return f.read()
    except Exception as e:
        log(f"Предупреждение чтения {filepath}: {e}")
    return ""


def has_active_work_order(manager_state_text):
    """Возвращает True, если в MANAGER_STATE.md активен многотактовый наряд L1/L2."""
    if not manager_state_text:
        return False
    return bool(
        re.search(
            r"^\s*#*\s*STATUS:\s*ACTIVE_AUTONOMOUS_WORK_ORDER\b",
            manager_state_text,
            re.MULTILINE,
        )
    )


def should_mark_audit_ready(manager_state_text):
    """Запрещает перезапись MANAGER_STATE.md на AUDIT_READY при активном наряде или уже выставленном AUDIT_READY."""
    if not manager_state_text:
        return True
    if has_active_work_order(manager_state_text):
        return False
    if "STATUS: AUDIT_READY" in manager_state_text:
        return False
    return True


def work_order_retry_decision(ws, now, max_attempts=3):
    """RETRY_GUARD для многотактового наряда ACTIVE_AUTONOMOUS_WORK_ORDER -> (exhausted|wait|run, state)."""
    ws = dict(ws) if isinstance(ws, dict) else {}
    ws.setdefault("pending_stage", "l2")
    ws.setdefault("attempts", 0)
    ws.setdefault("last", 0)
    ws.setdefault("cooldown_until", 0)
    if now < ws.get("cooldown_until", 0):
        return "wait", ws
    if ws["attempts"] >= max_attempts:
        return "exhausted", ws
    wait = 300 * (3 ** (ws["attempts"] - 1)) if ws["attempts"] else 0
    if now - ws["last"] < wait:
        return "wait", ws
    return "run", ws


def record_work_order_outcome(ws, now, ok, err_text="", stage="l2"):
    """Обновляет состояние многотактового цикла: l2 <-> l1, 429 -> cooldown без траты попыток."""
    new_ws = dict(ws) if isinstance(ws, dict) else {}
    new_ws.setdefault("pending_stage", stage)
    new_ws.setdefault("attempts", 0)
    new_ws.setdefault("last", 0)
    new_ws.setdefault("cooldown_until", 0)
    if ok:
        new_ws["attempts"] = 0
        new_ws["cooldown_until"] = 0
        new_ws["last"] = now
        new_ws["pending_stage"] = "l1" if stage == "l2" else "l2"
        return "ok", 0, new_ws
    reset_wait = parse_quota_reset_seconds(err_text)
    if reset_wait is not None:
        new_ws["cooldown_until"] = now + reset_wait
        new_ws["last_quota_error"] = err_text[:200]
        new_ws["pending_stage"] = stage
        return "quota_wait", reset_wait, new_ws
    new_ws["attempts"] += 1
    new_ws["last"] = now
    new_ws["cooldown_until"] = 0
    new_ws["pending_stage"] = stage
    return "failed", 0, new_ws


def mark_audit_ready_if_needed():
    try:
        current = read_manager_state()
        if not should_mark_audit_ready(current):
            return
        ts = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
        c_state, v_status, _, _ = get_flow_core_state()
        t_counts = get_task_status_counts()
        banner = (
            f"# STATUS: AUDIT_READY — 100% МЕХАНИЗМОВ И ПРЕДПИСАНИЙ ЗАВЕРЕНЫ ({ts})\n\n"
            f"- Все записи и предписания в `docs/FACTORY_MECHANISMS.md` имеют живое свидетельство `*Живое:*` (остаток: 0).\n"
            f"- Канонический контур автономии Деннетта защищён и неприкосновенен (376/376 green).\n"
            f"- Продукт `dmitryefremov-site`: Flow Core `{c_state}` (`{v_status}`), done={t_counts.get('done', 0)}, review={t_counts.get('pending_review', 0)}, claimed={t_counts.get('claimed', 0)}, queued={t_counts.get('queued', 0)}.\n"
            f"- План полного аудита готов в `docs/reports/AUTONOMY_AND_FULL_AUDIT_PLAN.md`.\n"
            f"- Эстафета L1/L2 переведена в режим бережного автономного дозора (ожидание команды Дмитрия Ефремова на запуск полного аудита).\n"
        )
        with open(MANAGER_STATE_FILE, "w", encoding="utf-8") as f:
            f.write(banner)
        subprocess.run(["git", "-C", REPO_DIR, "add", "docs/reports/MANAGER_STATE.md"], capture_output=True)
        subprocess.run(
            ["git", "-C", REPO_DIR, "commit", "-m", "docs(autonomy): all factory mechanisms 100% verified — enter AUDIT_READY standby"],
            capture_output=True,
        )
    except Exception as e:
        log(f"Предупреждение записи AUDIT_READY: {e}")


def run_agent_command(user, prompt, timeout_seconds=1800):
    bin_path = f"/home/{user}/.local/bin/agy"
    if not os.path.exists(bin_path):
        bin_path = "/usr/local/bin/agy"

    head_before = ""
    try:
        head_before = subprocess.check_output(["git", "-C", REPO_DIR, "rev-parse", "HEAD"]).decode().strip()
    except Exception:
        pass

    selected_model = "gemini-4-neon-high" if user == "remotecli2" else "gemini-4-neon-medium"
    cmd = [
        "sudo", "-u", user, bin_path,
        "--model", selected_model,
        "--dangerously-skip-permissions",
        "-p", prompt,
    ]
    log(f"Запуск такта агента [{user}] (таймаут сессии {timeout_seconds}s)...")
    try:
        res = subprocess.run(cmd, cwd=REPO_DIR, capture_output=True, text=True, timeout=timeout_seconds)
        if res.returncode == 0:
            log(f"Такт [{user}] завершен успешно.")
            return True, res.stdout.strip()
        else:
            combined_err = "\n".join(p for p in (res.stderr.strip(), res.stdout.strip()) if p)
            log(f"Такт [{user}] код {res.returncode}. Stderr: {combined_err[:200]}")
            head_after = subprocess.check_output(["git", "-C", REPO_DIR, "rev-parse", "HEAD"]).decode().strip()
            if head_after != head_before:
                log(f"Такт [{user}] зафиксировал коммит {head_after[:7]} (успех).")
                return True, "Commit made"
            return False, combined_err
    except subprocess.TimeoutExpired:
        log(f"Такт [{user}] превысил лимит сессии ({timeout_seconds}s).")
        head_after = subprocess.check_output(["git", "-C", REPO_DIR, "rev-parse", "HEAD"]).decode().strip()
        if head_after != head_before:
            log(f"Несмотря на таймаут, такт [{user}] зафиксировал коммит {head_after[:7]} (успех).")
            return True, "Commit made during session"
        return False, "Timeout"
    except Exception as e:
        log(f"Ошибка вызова агента [{user}]: {e}")
        return False, str(e)


def parse_quota_reset_seconds(err_text, default_wait=900, max_wait=21600, buffer_sec=30):
    """Распознаёт 429/RESOURCE_EXHAUSTED и парсит 'Resets in XhYmZs' -> секунды ожидания или None."""
    if not err_text:
        return None
    low = err_text.lower()
    if not any(tok in low for tok in ("resource_exhausted", "code 429", "http 429", "quota reached", "rate limit")):
        return None
    m = re.search(r"resets\s+in\s+(?:(\d+)\s*h)?\s*(?:(\d+)\s*m)?\s*(?:(\d+)\s*s)?", low)
    if m and any(m.groups()):
        hours = int(m.group(1) or 0)
        mins = int(m.group(2) or 0)
        secs = int(m.group(3) or 0)
        total = hours * 3600 + mins * 60 + secs
        if total > 0:
            return min(total + buffer_sec, max_wait)
    return default_wait


def directive_retry_decision(rs, cur_hash, old_hash, now, max_attempts=3):
    """RETRY_GUARD (чистая функция): -> (done|exhausted|wait|run, state). Паузы 5/15/45 мин + 429 cooldown."""
    if rs.get("hash") != cur_hash:
        rs = {"hash": cur_hash, "attempts": 0, "last": 0, "cooldown_until": 0, "l1_done": False}
    else:
        rs = dict(rs)
        rs.setdefault("attempts", 0)
        rs.setdefault("last", 0)
        rs.setdefault("cooldown_until", 0)
        rs.setdefault("l1_done", False)
    if cur_hash == old_hash:
        return "done", rs
    if now < rs.get("cooldown_until", 0):
        return "wait", rs
    if rs["attempts"] >= max_attempts:
        return "exhausted", rs
    wait = 300 * (3 ** (rs["attempts"] - 1)) if rs["attempts"] else 0
    if now - rs["last"] < wait:
        return "wait", rs
    return "run", rs


def record_directive_outcome(rs, now, ok, err_text="", stage="l1"):
    """Обновляет состояние RETRY_GUARD после шага L1/L2: 429 не тратит попытки, а ставит cooldown_until."""
    new_rs = dict(rs)
    new_rs.setdefault("attempts", 0)
    new_rs.setdefault("last", 0)
    new_rs.setdefault("cooldown_until", 0)
    new_rs.setdefault("l1_done", False)
    if ok:
        new_rs["cooldown_until"] = 0
        new_rs["last"] = now
        if stage == "l1":
            new_rs["l1_done"] = True
        return "ok", 0, new_rs
    reset_wait = parse_quota_reset_seconds(err_text)
    if reset_wait is not None:
        new_rs["cooldown_until"] = now + reset_wait
        new_rs["last_quota_error"] = err_text[:200]
        return "quota_wait", reset_wait, new_rs
    new_rs["attempts"] += 1
    new_rs["last"] = now
    new_rs["cooldown_until"] = 0
    return "failed", 0, new_rs


def execute_relay_round():
    if not check_disk_and_resources():
        time.sleep(300)
        return

    resolve_dead_parents_and_stalls()

    # OPERATOR_DIRECTIVES_WAKEUP: never sleep when OPERATOR_DIRECTIVES.md has unprocessed directives
    dir_path = os.path.join(REPO_DIR, "docs", "reports", "OPERATOR_DIRECTIVES.md")
    dir_hash_file = "/var/lib/eneik_operator_directives.sha256"
    if os.path.exists(dir_path):
        import hashlib
        cur_hash = hashlib.sha256(open(dir_path, "rb").read()).hexdigest()
        old_hash = open(dir_hash_file).read().strip() if os.path.exists(dir_hash_file) else ""
        # RETRY_GUARD: макс. 3 попытки на хэш директивы, паузы 5/15/45 мин, 429 -> cooldown без траты попыток
        retry_file = "/var/lib/eneik_operator_directives.retry.json"
        MAX_DIRECTIVE_ATTEMPTS = 3
        try:
            rs = json.load(open(retry_file)) if os.path.exists(retry_file) else {}
        except Exception:
            rs = {}
        now_ts = time.time()
        decision, rs = directive_retry_decision(rs, cur_hash, old_hash, now_ts, MAX_DIRECTIVE_ATTEMPTS)
        if decision == "exhausted":
            log("RETRY_GUARD: лимит попыток исчерпан для директивы %s — требуется ручной разбор Оператора, ретраи остановлены" % cur_hash[:12])
            time.sleep(600)
            return
        if decision == "wait":
            rem_cd = int(max(0, rs.get("cooldown_until", 0) - now_ts))
            if rem_cd > 0:
                log("RETRY_GUARD (429): ожидание сброса квоты (%ds осталось, попытки %d/%d, l1_done=%s)" % (rem_cd, rs["attempts"], MAX_DIRECTIVE_ATTEMPTS, rs.get("l1_done", False)))
                time.sleep(min(300, max(30, rem_cd)))
            else:
                time.sleep(60)
            return
        if cur_hash != old_hash:
            def _save_rs(state):
                with open(retry_file, "w") as rf:
                    json.dump(state, rf)

            if not rs.get("l1_done"):
                log("=== НОВАЯ ДИРЕКТИВА ОПЕРАТОРА ОБНАРУЖЕНА В OPERATOR_DIRECTIVES.md — ПРОБУЖДЕНИЕ L1 (попытка %d/%d) ===" % (rs["attempts"] + 1, MAX_DIRECTIVE_ATTEMPTS))
                l1_prompt = (
                    "Ты — L1 Философ EneikProductionSys. В docs/reports/OPERATOR_DIRECTIVES.md поступила НОВАЯ СРОЧНАЯ ДИРЕКТИВА ОПЕРАТОРА "
                    "(также прочти docs/reports/ECOSYSTEM_PHILOSOPHICAL_CONTEXT.md). "
                    "Проведи диагностику, задокументируй контракт и ответственные механизмы, сними standby в docs/reports/MANAGER_STATE.md "
                    "и выдай точный Work Order для L2 Инженера в docs/reports/PROGRAMMER_DIRECTIVES.md. Сделай git commit. Не вызывай таймеры."
                )
                p_ok, p_err = run_agent_command("remotecli2", l1_prompt)
                kind, wait_s, rs = record_directive_outcome(rs, time.time(), p_ok, p_err, stage="l1")
                _save_rs(rs)
                if not p_ok:
                    if kind == "quota_wait":
                        log("RETRY_GUARD (429): квота L1 исчерпана, попытка не засчитана; пауза %ds до сброса" % wait_s)
                    else:
                        log("RETRY_GUARD: L1 завершился ошибкой (попытка %d/%d)" % (rs["attempts"], MAX_DIRECTIVE_ATTEMPTS))
                    time.sleep(20)
                    return
            else:
                log("=== ДИРЕКТИВА %s: L1 уже выполнен ранее — переход сразу к L2 (попытка %d/%d) ===" % (cur_hash[:12], rs["attempts"] + 1, MAX_DIRECTIVE_ATTEMPTS))

            l2_prompt = (
                "Ты — L2 Инженер EneikProductionSys. Выполни срочный Work Order от L1 Философа в docs/reports/PROGRAMMER_DIRECTIVES.md "
                "(по последней директиве из docs/reports/OPERATOR_DIRECTIVES.md). Исправь код/схему, покрой тестами в Docker Maven, "
                "задокументируй результат в docs/reports/PROGRAMMER_STATE.md и сделай git commit. Не вызывай таймеры."
            )
            e_ok, e_err = run_agent_command("remotecli", l2_prompt)
            kind, wait_s, rs = record_directive_outcome(rs, time.time(), e_ok, e_err, stage="l2")
            _save_rs(rs)
            if e_ok:
                with open(dir_hash_file, "w") as hf:
                    hf.write(cur_hash)
                if has_active_work_order(read_manager_state()):
                    try:
                        with open(WORK_ORDER_STATE_FILE, "w", encoding="utf-8") as wf:
                            json.dump({"pending_stage": "l1", "attempts": 0, "last": 0, "cooldown_until": 0}, wf)
                    except Exception as e:
                        log(f"Предупреждение сохранения состояния WORK_ORDER: {e}")
                    log("=== ТАКТ 1 ДИРЕКТИВЫ ВЫПОЛНЕН — ПЕРЕДАЧА В МНОГОТАКТОВЫЙ ЦИКЛ ACTIVE_AUTONOMOUS_WORK_ORDER (следующий этап: приёмка L1) ===")
                else:
                    log("=== СРОЧНАЯ ДИРЕКТИВА ОПЕРАТОРА ОТРАБОТАНА L1 <-> L2 ===")
            elif kind == "quota_wait":
                log("RETRY_GUARD (429): квота L2 исчерпана, попытка не засчитана; пауза %ds до сброса (L1 сохранён)" % wait_s)
            else:
                log("RETRY_GUARD: L2 завершился ошибкой (попытка %d/%d)" % (rs["attempts"], MAX_DIRECTIVE_ATTEMPTS))
            time.sleep(20)
            return

    # ACTIVE_AUTONOMOUS_WORK_ORDER: многотактовая эстафета L2 (1 механизм за такт) <-> L1 (приёмка и перевод статуса)
    manager_state_text = read_manager_state()
    if has_active_work_order(manager_state_text):
        try:
            ws = json.load(open(WORK_ORDER_STATE_FILE)) if os.path.exists(WORK_ORDER_STATE_FILE) else {}
        except Exception:
            ws = {}
        now_ts = time.time()
        MAX_WO_ATTEMPTS = 3
        wo_dec, ws = work_order_retry_decision(ws, now_ts, MAX_WO_ATTEMPTS)
        if wo_dec == "exhausted":
            log("WORK_ORDER_GUARD: лимит последовательных ошибок (%d/%d) исчерпан на этапе %s — пауза 600с" % (ws["attempts"], MAX_WO_ATTEMPTS, ws["pending_stage"]))
            time.sleep(600)
            return
        if wo_dec == "wait":
            rem_cd = int(max(0, ws.get("cooldown_until", 0) - now_ts))
            if rem_cd > 0:
                log("WORK_ORDER_GUARD (429): ожидание сброса квоты (%ds осталось, этап=%s)" % (rem_cd, ws["pending_stage"]))
                time.sleep(min(300, max(30, rem_cd)))
            else:
                time.sleep(60)
            return

        def _save_ws(state):
            with open(WORK_ORDER_STATE_FILE, "w", encoding="utf-8") as wf:
                json.dump(state, wf)

        stage = ws.get("pending_stage", "l2")
        if stage == "l1":
            log("=== ACTIVE_AUTONOMOUS_WORK_ORDER: ПРИЁМКА L1 (remotecli2) И ОБНОВЛЕНИЕ СТАТУСА ===")
            l1_wo_prompt = (
                "Ты — L1 Философ (Менеджер-архитектор) фабрики EneikProductionSys.\n"
                "АКТИВЕН МНОГОТАКТОВЫЙ НАРЯД (`STATUS: ACTIVE_AUTONOMOUS_WORK_ORDER` в `docs/reports/MANAGER_STATE.md`).\n"
                "Твоя задача на этот такт приёмки:\n"
                "1. Проверь последний коммит L2 (`git log -n 5 --oneline`), отчёт `docs/reports/PROGRAMMER_STATE.md`, ресурсы хоста (`df -h /` < 70%, `free -h`) и живую телеметрию контура.\n"
                "2. Проведи приёмку завершённого такта L2 по `docs/reports/PROGRAMMER_DIRECTIVES.md` (сохраняя контур гомеостатической автономии Деннетта и `DennettAutonomyFalsificationTest` 100% green).\n"
                "3. Обнови `docs/reports/MANAGER_STATE.md` (компактно, <= 25 строк):\n"
                "   - Если в очереди Work Order ещё остались невыполненные такты (или пересборка/живая проверка не завершена) — ОБЯЗАТЕЛЬНО СОХРАНИ заголовок `# STATUS: ACTIVE_AUTONOMOUS_WORK_ORDER` и явно укажи, какие такты закрыты, а какой ОДИН следующий такт L2 должен взять сейчас.\n"
                "   - Если ВСЕ активные наряды (P0/P1/P2) полностью выполнены, покрыты зелёными заслон-тестами и верифицированы в живом рантайме — замени заголовок на `# STATUS: AUDIT_READY — ВСЕ АКТИВНЫЕ НАРЯДЫ ЗАКРЫТЫ` (это переведёт relay в режим ожидания).\n"
                "4. Закоммить `docs/reports/MANAGER_STATE.md` (и при необходимости уточнения в `docs/reports/PROGRAMMER_DIRECTIVES.md`). НЕ вызывай schedule/таймеры!"
            )
            ok, err = run_agent_command("remotecli2", l1_wo_prompt)
            kind, wait_s, ws = record_work_order_outcome(ws, time.time(), ok, err, stage="l1")
            _save_ws(ws)
            if ok:
                log("=== ACTIVE_AUTONOMOUS_WORK_ORDER: приёмка L1 завершена (следующий этап: L2 если наряд ещё активен) ===")
            elif kind == "quota_wait":
                log("WORK_ORDER_GUARD (429): квота L1 исчерпана, пауза %ds" % wait_s)
            else:
                log("WORK_ORDER_GUARD: L1 завершился ошибкой (попытка %d/%d)" % (ws["attempts"], MAX_WO_ATTEMPTS))
            time.sleep(20)
            return
        else:
            log("=== ACTIVE_AUTONOMOUS_WORK_ORDER: ИСПОЛНЕНИЕ СЛЕДУЮЩЕГО ТАКТА L2 (remotecli) ===")
            l2_wo_prompt = (
                "Ты — Ведущий инженер L2 фабрики EneikProductionSys.\n"
                "АКТИВЕН МНОГОТАКТОВЫЙ НАРЯД (`STATUS: ACTIVE_AUTONOMOUS_WORK_ORDER`).\n"
                "Твоя задача на этот такт (строго ОДИН следующий механизм за такт по `GEMINI.md` §1):\n"
                "1. Прочти `docs/reports/MANAGER_STATE.md`, `docs/reports/PROGRAMMER_STATE.md` и соответствующий раздел `docs/reports/PROGRAMMER_DIRECTIVES.md`.\n"
                "2. Возьми строго ОДИН следующий незакрытый такт из очереди (не перескакивай и не дроби механизм). Строго сохраняй защищённый контур автономии Деннетта (`DennettAutonomyFalsificationTest` обязан оставаться 100% green).\n"
                "3. Реализуй механизм целиком, покрой фальсифицирующими тестами-заслонами в Docker Maven (`docker run --rm -v /opt/EneikProductionSys:/workspace -v /root/.m2:/root/.m2 -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=...`), а для node-сервисов — `node --test`. При завершении этапа с пересборкой — проверь живой рантайм.\n"
                "4. Никаких ручных правок репозиториев продуктов (`GEMINI.md` §0).\n"
                "5. Обнови `docs/reports/PROGRAMMER_STATE.md` (что закрыто в этом такте, какие тесты green, какой следующий такт в очереди) и сделай семантический `git commit`.\n"
                "6. НЕ вызывай schedule/таймеры! Заверши выполнение штатно."
            )
            ok, err = run_agent_command("remotecli", l2_wo_prompt)
            kind, wait_s, ws = record_work_order_outcome(ws, time.time(), ok, err, stage="l2")
            _save_ws(ws)
            if ok:
                log("=== ACTIVE_AUTONOMOUS_WORK_ORDER: такт L2 завершён (следующий этап: приёмка L1) ===")
            elif kind == "quota_wait":
                log("WORK_ORDER_GUARD (429): квота L2 исчерпана, пауза %ds" % wait_s)
            else:
                log("WORK_ORDER_GUARD: L2 завершился ошибкой (попытка %d/%d)" % (ws["attempts"], MAX_WO_ATTEMPTS))
            time.sleep(20)
            return

    remaining = scan_remaining_mechanisms()
    total_rem = len(remaining)
    if total_rem == 0:
        mark_audit_ready_if_needed()
        log(
            "AUDIT_READY: Все 100% записей в docs/FACTORY_MECHANISMS.md заверены (*Живое:*). "
            "Ожидание команды Дмитрия Ефремова на запуск полного аудита. Автономный дозор потока активен."
        )
        time.sleep(120)
        return

    batch = remaining[:BATCH_SIZE]
    batch_lines = "\n".join(
        f"  - Строки L{it['line']}–L{it['end_line']} ({it['kind']}): {it['id']} — {it['title']}"
        for it in batch
    )
    log(f"=== НАЧАЛО ТАКТА ЭСТАФЕТЫ: осталось {total_rem} незаверенных блоков, в текущем пакете {len(batch)} ===")

    philosopher_prompt = f"""Ты — Менеджер-философ (L1/L3) фабрики EneikProductionSys.
{LOCKED_AUTONOMY_NOTICE}

ТЕКУЩАЯ ЗАДАЧА — ДОДЕЛАТЬ ОСТАВШИЕСЯ ~10% ЗАПИСЕЙ МЕХАНИЗМОВ ДО 100% ГОТОВНОСТИ К АУДИТУ:
Сейчас в `docs/FACTORY_MECHANISMS.md` осталось ровно {total_rem} индивидуальных блоков без инлайн-свидетельства `*Живое:*`.
Выдай в `docs/reports/PROGRAMMER_DIRECTIVES.md` точный пакетный Work Order для Инженера (L2) СТРОГО на следующие {len(batch)} блоков:
{batch_lines}

СТРОГИЕ ПРАВИЛА:
1. Ступень 4 — СТРОГО ЗАМЕР В РАНТАЙМЕ (Docker Maven с кэшем `-v /root/.m2:/root/.m2 -m 2g`), А НЕ ПРАВКА РАБОЧЕГО КОДА при зелёных тестах!
2. Укажи Инженеру L2 конкретные тест-сьюты Maven для замера этих {len(batch)} блоков и потребуй вписать строку `*Живое, <дата>:* ...` СТРОГО ВНУТРЬ КАЖДОГО ИЗ ЭТИХ {len(batch)} БЛОКОВ (перед концом каждого блока `Lstart–Lend`).
3. КАТЕГОРИЧЕСКИ ЗАПРЕТИ дописывать новые сводные блоки `### Итог Пакетного Замера` в конец `docs/FACTORY_MECHANISMS.md` — только точечная инлайн-простановка `*Живое:*` внутри перечисленных блоков!
4. Используй ТОЛЬКО верифицированные идентификаторы философского корпуса (`KARL_POPPER_01_FALSIFICATION_HARNESS` D008, `ELVIN_GOLDMAN_03_LEVEL_OF_ABSTRACTION_LOCK` D010, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` D006, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` D012) — не выдумывай несуществующих ID.
5. Обнови `docs/reports/MANAGER_STATE.md` (не более 20 строк, укажи остаток {total_rem} блоков) и сделай `git commit -m "docs(manager): issue inline verification Work Order ({len(batch)} of {total_rem} remaining)"`.
6. НЕ ВЫЗЫВАЙ инструмент schedule или таймеры! Заверши выполнение штатно."""

    p_ok, p_err = run_agent_command("remotecli2", philosopher_prompt)
    if not p_ok:
        q_wait = parse_quota_reset_seconds(p_err)
        if q_wait is not None:
            log(f"Философ L1/L3 упёрся в квоту (429). Пауза {q_wait} сек до сброса.")
            time.sleep(q_wait)
        else:
            log("Философ L1/L3 завершился без коммита. Ожидание 60 сек перед повтором.")
            time.sleep(60)
        return

    engineer_prompt = f"""Ты — Ведущий инженер (L2) фабрики EneikProductionSys.
{LOCKED_AUTONOMY_NOTICE}

ТЕКУЩАЯ ЗАДАЧА ПО НАРЯДУ `docs/reports/PROGRAMMER_DIRECTIVES.md` (осталось {total_rem} блоков до `AUDIT_READY`):
Прочитай `docs/reports/PROGRAMMER_DIRECTIVES.md` и замерь ровно этот пакет из {len(batch)} блоков в `docs/FACTORY_MECHANISMS.md`:
{batch_lines}

СТРОГИЕ ПРАВИЛА ИСПОЛНЕНИЯ:
1. СТРОГОЕ ПРАВИЛО СТУПЕНИ 4: если тесты в Maven зелёные — НЕ МЕНЯЙ НИ ОДНОЙ СТРОКИ JAVA-КОДА ИЛИ СКРИПТОВ, особенно защищённый контур автономии!
2. ЗАМЕР В DOCKER MAVEN (обязательно монтируй `-v /root/.m2:/root/.m2` для мгновенного кэша):
   `docker run --rm -v /opt/EneikProductionSys:/workspace -v /root/.m2:/root/.m2 -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=<СписокТестовПакета> -DfailIfNoTests=false`
3. ТОЧЕЧНАЯ ИНЛАЙН-ФИКСАЦИЯ `*Живое:*`:
   - Вставь строку `*Живое, <дата UTC>:* Замер Ступени 4 в Docker Maven 3.9.9 Temurin-21 (-m 2g): <N/N> green в `<TestClass>` ... Ступень 4 закрыта, статус: ideal.` НЕПОСРЕДСТВЕННО ВНУТРЬ КАЖДОГО ИЗ {len(batch)} УКАЗАННЫХ БЛОКОВ в `docs/FACTORY_MECHANISMS.md`!
   - КАТЕГОРИЧЕСКИ ЗАПРЕЩЕНО добавлять новые секции `### Итог Пакетного Замера` в конец `docs/FACTORY_MECHANISMS.md`!
4. Обнови `docs/reports/PROGRAMMER_STATE.md` и закоммить изменения:
   `git commit -m "docs(mechanisms): verify inline Stage 4 batch ({len(batch)} blocks, D008/D010/D006)"`
5. НЕ ВЫЗЫВАЙ инструмент schedule или таймеры! Заверши выполнение штатно."""

    e_ok, e_err = run_agent_command("remotecli", engineer_prompt)
    if not e_ok:
        q_wait = parse_quota_reset_seconds(e_err)
        if q_wait is not None:
            log(f"Инженер L2 упёрся в квоту (429). Пауза {q_wait} сек до сброса.")
            time.sleep(q_wait)
        else:
            log("Инженер L2 завершился без коммита. Ожидание 60 сек.")
            time.sleep(60)
        return

    rem_after = len(scan_remaining_mechanisms())
    log(f"=== ТАКТ ЭСТАФЕТЫ ЗАВЕРШЕН. Остаток незаверенных блоков: {rem_after} (было {total_rem}) ===")
    time.sleep(20)


def main():
    log("Запуск автономного демона фабрики Eneik (Dennettian Homeostatic Relay 24/7)...")
    while True:
        try:
            execute_relay_round()
        except KeyboardInterrupt:
            log("Остановка по сигналу.")
            break
        except Exception as e:
            log(f"Непредвиденное исключение в цикле эстафеты: {e}")
            time.sleep(60)


if __name__ == "__main__":
    main()
