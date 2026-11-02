# БОЕВОЙ НАРЯД ДЛЯ L2 (ВЕДУЩИЙ ИНЖЕНЕР): ПОЛНЫЙ СКВОЗНОЙ КОНТУР СУВЕРЕННЫХ ВОРКЕРОВ И СИНТЕЗ НАСТОЯЩЕГО ДИЗАЙНЕРА БЕЗ ЗАГЛУШЕК
**Дата:** 2026-11-02 11:15 UTC  
**От:** Менеджер-философ (L1/L3) & Оператор Дмитрий Ефремов  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** ОБЯЗЫВАЮЩАЯ ДИРЕКТИВА АВТОНОМИИ (PRIORITY #0 — LIVE SOVEREIGN WORKER BRIDGE & ZERO-MOCK DESIGNER)  
**Эпистемический фундамент:** Karl Popper (D008 Falsification / Severe Testing), Joseph Raz (D006 Deontic Separation of Identities), Daniel Dennett (Cranes, not Skyhooks), Ludwig Wittgenstein (Anti-Mirror / Empirical Runtime Reality).

---

## 0. ДИАГНОСТИЧЕСКИЙ ВЕРДИКТ И ПОППЕРОВСКАЯ ВЕРИФИКАЦИЯ (D008)

Инспекция рантайма фабрики и кодовой базы вскрыла две критические проблемы:

1. **Бутафорские заглушки (Mocks) в коде Дизайнера:**
   - В перенесённых в ядро классах `com.eneik.production.services.design.sovereign.*` (и в репозитории `designer-eneik`) обнаружены 3 симулякра:
     - `DefaultSemanticLayoutSynthesizer`: конкатенирует захардкоженный шаблон с шаблонными фиолетовыми кнопками `bg-indigo-600` (нейрослоп) независимо от промпта и брифа.
     - `DefaultPlaywrightVisionHarness`: вместо вызова реального браузера считает `SHA-256` от строки и проверяет `htmlContent.contains("w-[1440px]")`.
     - `DefaultVisionAuditLoop`: вместо реального расчёта контраста и геометрии ищет подстроку `"leica"` и возвращает захардкоженные `9.6` / `7.2`.
2. **Критический разрыв сквозной PR-оркестрации:**
   - Роутер `WorkerExecutionRouter` (`LOCAL_AGY` / `DEEPSEEK_WORKTREE`) покрыт тестами, но **НЕ ВЫЗЫВАЕТСЯ** из `JulesDispatchService.java`! На строке ~868 вызовы задач фабрики по-прежнему идут напрямую в `julesApiClient.createSessionDetailed(...)`.
   - Контейнер `eneikproductionsys-backend-1` изолирован в Docker. На хосте Hetzner есть 8 суверенных пользователей `remotecli3..10` с бинарниками `agy` и установленный `node`/`playwright`, но изнутри контейнера запустить их напрямую через `ProcessBuilder` невозможно. Нужен сетевой мост между контейнером бэкенда и хостом.

---

## ЧАСТЬ 1: ВКЛЮЧЕНИЕ СКВОЗНОГО КОНТУРА СУВЕРЕННЫХ ВОРКЕРОВ (HOST SOVEREIGN WORKER BRIDGE)

### 1.1. Host Sovereign Worker & Vision Bridge (`scripts/sovereign_host_bridge.py`)
Разработать и поднять на хосте легковесный Python HTTP-сервис (`scripts/sovereign_host_bridge.py`), слушающий на `0.0.0.0:8095` (или `127.0.0.1:8095` и `172.17.0.1:8095`), доступный из Docker-контейнера через шлюз `172.17.0.1:8095` или `host.docker.internal:8095`.

#### Спецификация API Бриджа:
1. **`POST /api/worker/execute`** (Выполнение задач локальными суверенными агентами):
   - **Request JSON:**
     ```json
     {
       "workerUser": "remotecli3",
       "workingDirectory": "/tmp/worktrees/task-123",
       "prompt": "...",
       "model": "deepseek-coder",
       "timeoutSeconds": 300
     }
     ```
   - **Заслон Раза (D006 / AgyWorkerSecurityGuard):**
     Бридж обязан намертво отвергать запросы с `workerUser` равным `remotecli` или `remotecli2` (HTTP 403 Forbidden). Разрешены строго `remotecli3` – `remotecli10`.
   - **Исполнение:** Выполняется команда через `sudo -u <workerUser>` с вызовом `/home/<workerUser>/.local/bin/agy` в указанной рабочей директории.
   - **Response JSON:**
     ```json
     {
       "exitCode": 0,
       "stdout": "...",
       "stderr": "...",
       "durationMs": 1420
     }
     ```

2. **`POST /api/vision/render`** (Реальный рендеринг HTML и замер DOM):
   - **Request JSON:**
     ```json
     {
       "htmlContent": "<!DOCTYPE html>...",
       "viewports": [
         { "name": "DESKTOP_1440", "width": 1440, "height": 900 },
         { "name": "MOBILE_375", "width": 375, "height": 812 }
       ]
     }
     ```
   - **Исполнение:** Через Playwright/Chromium на хосте (headless):
     - Открывает HTML (через data URI или временный файл).
     - Ждёт `networkidle` и загрузку шрифтов.
     - Делает реальный замер геометрии DOM:
       `scrollWidth = document.documentElement.scrollWidth;`
       `clientWidth = document.documentElement.clientWidth;`
       `hasHorizontalOverflow = scrollWidth > clientWidth;`
     - Сохраняет реальный скриншот (PNG в base64 или файл).
   - **Response JSON:**
     ```json
     {
       "captures": [
         {
           "viewport": "DESKTOP_1440",
           "measuredScrollWidthPx": 1440,
           "hasHorizontalOverflow": false,
           "screenshotHash": "...",
           "screenshotBase64": "..."
         },
         {
           "viewport": "MOBILE_375",
           "measuredScrollWidthPx": 375,
           "hasHorizontalOverflow": false,
           "screenshotHash": "...",
           "screenshotBase64": "..."
         }
       ]
     }
     ```

3. **Служба systemd (`/etc/systemd/system/eneik-sovereign-bridge.service`):**
   - Обеспечить автозапуск демона при старте хоста и мониторинг через `systemctl status eneik-sovereign-bridge`.

---

### 1.2. Интеграция в кодовую базу Фабрики (`EneikProductionSys`)

1. **Модификация `LocalAgyCliClient.java`:**
   - Вместо локального `ProcessBuilder` (который терпит крах внутри Docker) отправлять HTTP POST запрос на `http://172.17.0.1:8095/api/worker/execute`.
   - Внедрить fallback: если бридж недоступен, логировать внятное предупреждение и пробрасывать исключение для Dennett Cranes Homeostasis.
2. **Модификация `DefaultPlaywrightVisionHarness.java`:**
   - Отправлять HTTP POST запрос на `http://172.17.0.1:8095/api/vision/render`.
   - Ликвидировать фиктивные проверки `htmlContent.contains("w-[1440px]")` и SHA-256 от строк — возвращать реальные данные замера от headless-браузера хоста!
3. **Прямое включение `WorkerExecutionRouter` в `JulesDispatchService.java`:**
   - Внедрить бин `WorkerExecutionRouter` в `JulesDispatchService`.
   - В методе `dispatchInternal(...)`: при `worker_engine_mode != JULES` перенаправлять задачу в `workerExecutionRouter.dispatch(task, account, mode)`.
   - В методе опроса сессий (`pollSessions` / `pollStatus`): для суверенных сессий вызывать `workerExecutionRouter.poll(session)`, обновляя состояние задач до завершения и мержа PR.
4. **Заслон Поппера:**
   - Прогнать unit и integration тесты в Docker Maven, подтвердив, что при включенном `LOCAL_AGY` задачи маршрутизируются через суверенный контур.

---

## ЧАСТЬ 2: САМОСИНТЕЗ НАСТОЯЩЕГО ДИЗАЙНЕРА ФАБРИКОЙ (ПРОЕКТ `designer-eneik`)

После включения сквозного суверенного контура запустить Фабрику на проектирование и реализацию реального Дизайнера в его собственном репозитории:

1. **Активация проекта `designer-eneik` (`ef264aea-dd30-46e2-bfac-e7e40918d5ab`):**
   - Вызвать endpoint перевода проекта из `accepted` в `active`:
     `POST /api/projects/ef264aea-dd30-46e2-bfac-e7e40918d5ab/activate`
2. **Подача боевого вишлиста (Wishlist) в проект `designer-eneik`:**
   - Отправить через `POST /api/projects/ef264aea-dd30-46e2-bfac-e7e40918d5ab/wishlist` подробный клиентский наряд:
     > **Тема:** Полная ликвидация симулякров и заглушек. Создание живого генератора дизайн-системы.  
     > **Требования:**  
     > 1. В `DefaultSemanticLayoutSynthesizer` полностью удалить статичный шаблон и запрещённые кнопки `bg-indigo-600`. Реализовать настоящий синтез структуры страницы на основе токенов архетипа (`ProductArchetype`), переданного брифа, композиционной сетки (Swiss / Bento) и цветовой палитры.  
     > 2. В `DefaultPlaywrightVisionHarness` подключить реальный клиент взаимодействия с headless-браузером, получающий физический `scrollWidth` и скриншоты.  
     > 3. В `DefaultVisionAuditLoop` убрать фиктивные оценки (9.6) и хардкод `leica`. Реализовать реальный аудит контрастности по стандарту WCAG 2.1 (APCA/контраст текста к фону), реальный детектив горизонтального переполнения мобильного вьюпорта и реальную проверку фокус-стилей.
3. **Автономное выполнение Фабрикой:**
   - Запустить оркестрацию (`POST /api/projects/ef264aea-dd30-46e2-bfac-e7e40918d5ab/orchestrate`).
   - Фабрика должна автономно:
     - Скомпилировать вишлист в системные эпики и задачи;
     - Назначить суверенных воркеров `remotecli3..10` через `WorkerExecutionRouter`;
     - Создать изолированные worktree, написать боевой код без заглушек;
     - Прогнать тесты верификации;
     - Открыть Pull Request в репозиторий `eneikdru/designer-eneik`;
     - Автоматически смержить PR при выполнении всех гейтов качества.
4. **Синхронизация в ядро Фабрики:**
   - После мержа в репозиторий продукта синхронизировать проверенный код без заглушек в пакет `com.eneik.production.services.design.sovereign.*` фабрики `EneikProductionSys`.
   - Пересобрать и перезапустить контейнер бэкенда (`docker compose up -d --build backend`).

---

## 3. КРИТЕРИИ ПРИЁМКИ И СВИДЕТЕЛЬСТВА ИСТИННОСТИ (WITTGENSTEIN & POPPER)

1. `curl -s http://172.17.0.1:8095/health` возвращает `{"status": "ok", "playwright": "ready", "workers": ["remotecli3", ..., "remotecli10"]}`.
2. Логи `JulesDispatchService` содержат: `WorkerExecutionRouter: Dispatching via Zero-Cost LOCAL_AGY sovereign engine...` вместо обращений к облачному Jules.
3. В кодовой базе `com.eneik.production.services.design.sovereign` отсутствуют строки `contains("w-[1440px]")`, `contains("leica")` и захардкоженный шаблон с `bg-indigo-600`.
4. Сгенерированный дизайн для тестового проекта проходит реальный аудит Playwright на хосте с физическим замером `scrollWidth` = 375px на мобильном вьюпорте.
5. Фиксация результата в `docs/reports/PROGRAMMER_STATE.md`.
