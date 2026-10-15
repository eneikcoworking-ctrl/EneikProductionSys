#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Autonomous Factory Relay: Continuous L1/L3 (Philosopher) <-> L2 (Engineer) Loop
Implements full 24/7 autonomous advancement of EneikProductionSys:
- Enforces strict batching of mechanism sections (Section VII TOC, etc.).
- Enforces "measurement, not editing" on Stages 3 & 4 (zero code modifications for passing tests).
- Auto-resolves Dead Parent task deadlocks and flow stalls.
- Preserves all epistemological invariants without artificial deadlines.
"""

import os
import sys
import time
import json
import subprocess
import datetime
import urllib.request
import shutil

REPO_DIR = "/opt/EneikProductionSys"
SCRIPTS_DIR = os.path.join(REPO_DIR, "scripts")
REPORTS_DIR = os.path.join(REPO_DIR, "docs/reports")
LOG_FILE = "/var/log/autonomous_factory_relay.log"

MANAGER_STATE = os.path.join(REPORTS_DIR, "MANAGER_STATE.md")
PROGRAMMER_STATE = os.path.join(REPORTS_DIR, "PROGRAMMER_STATE.md")
MANAGER_DIRECTIVES = os.path.join(REPORTS_DIR, "MANAGER_DIRECTIVES.md")
PROGRAMMER_DIRECTIVES = os.path.join(REPORTS_DIR, "PROGRAMMER_DIRECTIVES.md")
ANTIGRAVITY_QUEUE = os.path.join(REPO_DIR, "docs/ANTIGRAVITY_QUEUE.md")
FACTORY_MECHANISMS = os.path.join(REPO_DIR, "docs/FACTORY_MECHANISMS.md")

PID = "1695f808-0df7-4393-ace9-d5a79e62ef41"
AUTH_TOKEN = "975d328d5855088fd531f2f549ccf6d63498d794c4a7f80ccf7c5ac67da27eef"

def log(msg):
    now = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")
    line = f"[{now}] [Relay] {msg}"
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

    # Если проект в SYSTEM_STALLED, проверим задачи и выполним recovery-тик
    if c_state == "SYSTEM_STALLED" or v_status == "value_blocked":
        log(f"Обнаружен затор: {reason}. Запуск процедуры отцепки Dead Parent и снятия затора...")
        try:
            # Вызов проверки и восстановления через API проекта
            rec_url = f"http://127.0.0.1:8080/api/projects/{PID}/orchestration/tick"
            req = urllib.request.Request(rec_url, data=b"{}", headers={"Authorization": f"Bearer {AUTH_TOKEN}", "Content-Type": "application/json"})
            urllib.request.urlopen(req, timeout=5)
            log("Recovery-тик оркестратора отправлен.")
        except Exception as e:
            log(f"Тик оркестратора: {e}")

def run_agent_command(user, prompt, timeout_seconds=900):
    bin_path = f"/home/{user}/.local/bin/agy"
    if not os.path.exists(bin_path):
        bin_path = "/usr/local/bin/agy"
    
    cmd = [
        "sudo", "-u", user, bin_path,
        "--model", "gemini-3.8-flash-medium",
        "--dangerously-skip-permissions",
        "-p", prompt
    ]
    log(f"Запуск такта агента [{user}] (без дедлайна, таймаут сессии {timeout_seconds}s)...")
    try:
        res = subprocess.run(cmd, cwd=REPO_DIR, capture_output=True, text=True, timeout=timeout_seconds)
        if res.returncode == 0:
            log(f"Такт [{user}] завершен успешно.")
            return True, res.stdout.strip()
        else:
            log(f"Такт [{user}] завершен с кодом {res.returncode}. Stderr: {res.stderr[:300]}")
            return False, res.stderr.strip()
    except subprocess.TimeoutExpired:
        log(f"Такт [{user}] превысил лимит сессии ({timeout_seconds}s).")
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
2. ПАКЕТНЫЙ РЕЖИМ (Batching): выдавай Work Order сразу на всю незакрытую секцию механизмов целиком (начиная с Секции VII: TOC / ConstraintIdentificationService, BottleneckDetectionService, LaunchabilityConstraintService, TocSubordinationLever).
3. ПРИНЦИП ANTIGRAVITY_QUEUE: Ступени 3 и 4 — это СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА. Запрети инженеру трогать рабочий код при проходящих тестах!
4. Никаких искусственных спешек и дедлайнов — замеры должны быть основательными и строгими.

ТВОИ ДЕЙСТВИЯ:
- Проверь docs/ANTIGRAVITY_QUEUE.md и docs/FACTORY_MECHANISMS.md.
- Сформулируй пакетный Work Order для Секции VII (TOC) в docs/reports/PROGRAMMER_DIRECTIVES.md.
- Зафиксируй философские инварианты (Поппер D008 фальсификация, Голдман D010 уровни абстракции, Раз D006 запреты).
- Обнови docs/reports/MANAGER_STATE.md.
Действуй немедленно."""

    p_ok, p_out = run_agent_command("remotecli2", philosopher_prompt)
    if not p_ok:
        log("Философ L1/L3 завершился с ошибкой. Повтор через 60 сек.")
        time.sleep(60)
        return

    # 2. ТАКТ ВЕДУЩЕГО ИНЖЕНЕРА (L2)
    engineer_prompt = """Ты — Ведущий инженер (L2) фабрики EneikProductionSys.
ДИРЕКТИВА ОПЕРАТОРА ДМИТРИЯ ЕФРЕМОВА И МЕНЕДЖЕРА-ФИЛОСОФА:
1. ПАКЕТНЫЙ ЗАМЕР: Возьми из docs/reports/PROGRAMMER_DIRECTIVES.md все механизмы Секции VII (TOC) пакетом.
2. СТРОГОЕ ПРАВИЛО СТУПЕНИ 4: РАБОТА ЗАМЕРОМ, НЕ ПРАВКОЙ! Если тесты в Maven зелёные — НЕ МЕНЯЙ НИ ОДНОЙ СТРОКИ КОДА СЕРВИСОВ.
3. ЗАМЕР В DOCKER MAVEN:
   Запусти проверку тестов TOC в один проход:
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=ConstraintIdentificationServiceTest,BottleneckDetectionServiceTest,BottleneckDetectionServiceFalsificationTest,TocSubordinationLeverTest,TocSubordinationTruthTest,BottleneckAwarePriorityServiceTest,BottleneckAwarePriorityServiceFalsificationTest -DfailIfNoTests=false
4. ФИКСАЦИЯ И КОРПУС:
   - Добавь строки *Живое:* для каждого механизма Секции VII в docs/FACTORY_MECHANISMS.md с числом зелёных тестов.
   - Закрой пункты в docs/ANTIGRAVITY_QUEUE.md.
   - Сделай git commit с сообщением 'feat(toc): complete Stage 4 batch verification for Section VII (D008, D010, D006)'.
   - Обнови docs/reports/PROGRAMMER_STATE.md.
Действуй немедленно."""

    e_ok, e_out = run_agent_command("remotecli", engineer_prompt)
    if not e_ok:
        log("Инженер L2 завершился с ошибкой. Повтор через 60 сек.")
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