#!/usr/bin/env python3
import os
import sys
import time
import subprocess
import urllib.request
import json
from datetime import datetime, timezone

REPO_DIR = "/opt/EneikProductionSys"
LOG_FILE = "/var/log/autonomous_factory_relay.log"
PID = "1695f808-0df7-4393-ace9-d5a79e62ef41"
AUTH_TOKEN = "975d328d5855088fd531f2f549ccf6d63498d794c4a7f80ccf7c5ac67da27eef"

def log(msg):
    ts = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")
    line = f"[{ts}] [Relay] {msg}"
    print(line, flush=True)
    try:
        with open(LOG_FILE, "a", encoding="utf-8") as f:
            f.write(line + "\n")
    except Exception:
        pass

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
        url = f"http://127.0.0.1:8080/api/projects/{PID}/flow-core"
        req = urllib.request.Request(url, headers={"Authorization": f"Bearer {AUTH_TOKEN}"})
        with urllib.request.urlopen(req, timeout=5) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            snap = data.get("snapshot", {})
            return snap.get("currentState"), snap.get("valueStatus"), snap.get("blockingReason"), snap.get("counts", {})
    except Exception as e:
        return None, None, str(e), {}

def resolve_dead_parents_and_stalls():
    c_state, v_status, reason, counts = get_flow_core_state()
    log(f"Flow Core: currentState={c_state}, valueStatus={v_status}")

    if c_state == "SYSTEM_STALLED" or v_status == "value_blocked":
        log(f"Обнаружен затор: {reason}. Запуск процедуры снятия затора...")
        try:
            rec_url = f"http://127.0.0.1:8080/api/projects/{PID}/orchestration/tick"
            req = urllib.request.Request(rec_url, data=b"{}", headers={"Authorization": f"Bearer {AUTH_TOKEN}", "Content-Type": "application/json"})
            urllib.request.urlopen(req, timeout=5)
            log("Recovery-тик оркестратора отправлен.")
        except Exception as e:
            log(f"Тик оркестратора: {e}")

def run_agent_command(user, prompt, timeout_seconds=1800):
    bin_path = f"/home/{user}/.local/bin/agy"
    if not os.path.exists(bin_path):
        bin_path = "/usr/local/bin/agy"
    
    head_before = ""
    try:
        head_before = subprocess.check_output(["git", "-C", REPO_DIR, "rev-parse", "HEAD"]).decode().strip()
    except Exception:
        pass

    cmd = [
        "sudo", "-u", user, bin_path,
        "--model", "gemini-3.8-flash-medium",
        "--dangerously-skip-permissions",
        "-p", prompt
    ]
    log(f"Запуск такта агента [{user}] (таймаут сессии {timeout_seconds}s)...")
    try:
        res = subprocess.run(cmd, cwd=REPO_DIR, capture_output=True, text=True, timeout=timeout_seconds)
        if res.returncode == 0:
            log(f"Такт [{user}] завершен успешно.")
            return True, res.stdout.strip()
        else:
            log(f"Такт [{user}] код {res.returncode}. Stderr: {res.stderr[:200]}")
            head_after = subprocess.check_output(["git", "-C", REPO_DIR, "rev-parse", "HEAD"]).decode().strip()
            if head_after != head_before:
                log(f"Такт [{user}] зафиксировал коммит {head_after[:7]} (успех).")
                return True, "Commit made"
            return False, res.stderr.strip()
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

def execute_relay_round():
    log("=== НАЧАЛО ТАКТА АВТОНОМНОЙ ЭСТАФЕТЫ (L1/L3 <-> L2) ===")
    
    if not check_disk_and_resources():
        time.sleep(300)
        return

    resolve_dead_parents_and_stalls()

    # 1. ТАКТ МЕНЕДЖЕРА-ФИЛОСОФА (L1/L3)
    philosopher_prompt = """Ты — Менеджер-философ (L1/L3) фабрики EneikProductionSys.
ИНВАРИАНТЫ ОПЕРАТОРА ДМИТРИЯ ЕФРЕМОВА:
1. Полная автономия без ожидания внешних подтверждений.
2. ПАКЕТНЫЙ РЕЖИМ (Batching): выдавай Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секция VII (TOC) уже полностью замерена и закрыта инженером.
   - Проверь docs/ANTIGRAVITY_QUEUE.md и docs/FACTORY_MECHANISMS.md, выбери СЛЕДУЮЩУЮ открытую секцию механизмов (например, Секция VIII Flow & Metrics, или Секция XVI/XXIX, или группу сервисов Ступени 4).
3. ПРИНЦИП ANTIGRAVITY_QUEUE: Ступени 3 и 4 — это СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА. Запрети инженеру трогать рабочий код при проходящих тестах!
4. Никаких искусственных спешек и дедлайнов — замеры должны быть основательными и строгими.

ТВОИ ДЕЙСТВИЯ:
- Проверь docs/ANTIGRAVITY_QUEUE.md и docs/FACTORY_MECHANISMS.md.
- Выбери следующую секцию механизмов и сформулируй пакетный Work Order в docs/reports/PROGRAMMER_DIRECTIVES.md.
- Зафиксируй философские инварианты (Поппер D008 фальсификация, Голдман D010 уровни абстракции, Раз D006 запреты).
- Обнови docs/reports/MANAGER_STATE.md (не более 20 строк).
- Сделай git commit с сообщением 'docs(manager): issue batch Work Order for next section'.
- ВНИМАНИЕ: КАТЕГОРИЧЕСКИ НЕ ВЫЗЫВАЙ инструмент schedule или таймеры! Заверши выполнение штатно выводом текста.
Действуй немедленно."""

    p_ok, p_out = run_agent_command("remotecli2", philosopher_prompt)
    if not p_ok:
        log("Философ L1/L3 завершился без подтверждения. Ожидание 60 сек перед повтором.")
        time.sleep(60)
        return

    # 2. ТАКТ ВЕДУЩЕГО ИНЖЕНЕРА (L2)
    engineer_prompt = """Ты — Ведущий инженер (L2) фабрики EneikProductionSys.
ДИРЕКТИВА ОПЕРАТОРА ДМИТРИЯ ЕФРЕМОВА И МЕНЕДЖЕРА-ФИЛОСОФА:
1. ПАКЕТНЫЙ ЗАМЕР: Прочитай свежий пакетный Work Order из docs/reports/PROGRAMMER_DIRECTIVES.md.
2. СТРОГОЕ ПРАВИЛО СТУПЕНИ 4: РАБОТА ЗАМЕРОМ, НЕ ПРАВКОЙ! Если тесты в Maven зелёные — НЕ МЕНЯЙ НИ ОДНОЙ СТРОКИ КОДА СЕРВИСОВ.
3. ЗАМЕР В DOCKER MAVEN:
   Запусти проверку тестов указанной секции в один проход:
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=<НазванияТестовСекции> -DfailIfNoTests=false
4. ФИКСАЦИЯ И КОРПУС:
   - Добавь строки *Живое:* для каждого проверенного механизма в docs/FACTORY_MECHANISMS.md с числом зелёных тестов.
   - Закрой соответствующие пункты в docs/ANTIGRAVITY_QUEUE.md.
   - Сделай git commit с сообщением 'feat(<секция>): complete Stage 4 batch verification (D008, D010, D006)'.
   - Обнови docs/reports/PROGRAMMER_STATE.md.
- ВНИМАНИЕ: КАТЕГОРИЧЕСКИ НЕ ВЫЗЫВАЙ инструмент schedule или таймеры! Заверши выполнение штатно.
Действуй немедленно."""

    e_ok, e_out = run_agent_command("remotecli", engineer_prompt)
    if not e_ok:
        log("Инженер L2 завершился без подтверждения. Ожидание 60 сек.")
        time.sleep(60)
        return

    log("=== ТАКТ ЭСТАФЕТЫ УСПЕШНО ЗАВЕРШЕН. ПЕРЕХОД К СЛЕДУЮЩЕМУ ПАКЕТУ ===")
    time.sleep(30)

def main():
    log("Запуск автономного демона фабрики Eneik (24/7 Relay)...")
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
