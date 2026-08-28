# Спецификация структуры фронтенда и Статус интеграций

**Система:** Eneik Production System  
**Статус:** Реализовано и актуализировано (Production 2026.08)  
**Роли:** BARCAN-TAG-03 (BELIEF-INTENSION), BARCAN-TAG-11 (CLIENT-PERCEPTION), BARCAN-TAG-12 (SOCIAL-CONTRACT)

---

## 1. Архитектура клиентского интерфейса

Фронтенд организован в виде чистой, структурированной системы без визуального мусора:

1. **Active Project Dashboard:**
   * Фокус на **одном активном проекте** (название, репозиторий, статус развертывания).
   * Блок клиентского вишлиста: добавление требований, статус компиляции.
   * Граф декомпозированных задач с приоритетами Кано (`Must-Be`, `Performance`, `Attractive`, `Indifferent`, `Reverse`).
   * Сворачиваемый архив неактивных проектов.

2. **Structured Metrics & Quality View (13 BARCAN Roles):**
   * **Системный конвейер:** Задачи в очереди (`queued`), в работе (`claimed`), узкие места TOC (Dynamic Bottlenecks).
   * **Six Sigma Качество & DPMO:** Метрики прохождения ворот качества, частота конфликтов слияния, First Time Yield.
   * **13 Ролевых метрик:** Охват всех активных хартий (`TAG-00` по `TAG-12`), включая контракты OpenAPI, стабильность базы данных и соответствие нормам конфиденциальности.

3. **Автономные тестовые витрины компонентов (Standalone Component Harnesses, Порт 13000):**
   * Обратный прокси `frontend_proxy.py` маршрутизирует вызовы на порт `13000`, проксируя `/api/*` в рантайм-бэкенд (`:18080`):
     - `http://localhost:13000/registration-harness.html` (`RegistrationForm.svelte`)
     - `http://localhost:13000/dossier-harness.html` (`DossierSearch.svelte`)
     - `http://localhost:13000/privacy-harness.html` (`PrivacySettings.svelte`)
     - `http://localhost:13000/test-harness.html` (`CatalogSearch.svelte`)

---

## 2. Статус устранения заглушек и перехода на реальные данные

| Компонент | Ранее (Заглушки / Симуляции) | Текущее состояние (Real Production) |
|-----------|------------------------------|-------------------------------------|
| **Рантайм-наблюдения** | Статический мок `/actuator/health` | **JIT Reactive Probes:** Реальный запуск контейнеров в `runtime-launcher` (`:8091`), опрос живого эндпоинта `/health` с мостовой маршрутизацией `host.docker.internal` и записью в `client_runtime_observations`. |
| **Слияние Pull Requests** | Симулированные diff (`"mock_diff"`) | **GitHub API & JGit:** Автоматический анализ реального Git diff, проверка mergeability (`mergeable`, `mergeable_state`) и автоматическое слияние через `GitHubPullRequestService` и `AutoMergeService`. |
| **Арбитраж и Судейство** | Заглушки ответов ИИ | **Двухуровневый судейский контур:** `judgment-sidecar` (Claude Code OAuth) + `judgment-proxy` (Gemini Flash/Pro fallback). |
| **API-контракты** | Неформальные договоренности | **BARCAN-TAG-12 (Social Contract):** Машиночитаемые OpenAPI/JSON Schema спецификации в `docs/contracts/*.openapi.yaml`. |
| **ML-сервис & Эмбеддинги** | Статические строки | **FastAPI + Google Gemini API:** Живые эмбеддинги (`gemini-embedding-001`) и генерация контекста на моделях `gemini-3.5-flash` / `gemini-3.1-pro-preview`. |
