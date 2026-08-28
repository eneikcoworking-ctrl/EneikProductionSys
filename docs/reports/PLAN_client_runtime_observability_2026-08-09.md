# Спецификация и статус реализации: Наблюдение за реальным рантаймом активного продукта + Продуктовый Kaizen

**Дата утверждения плана:** 2026-08-09 | **Статус:** **ПОЛНОСТЬЮ РЕАЛИЗОВАНО В ПРОДАКШЕНЕ (2026-08)**

---

## 1. Архитектурный контекст и решенные задачи

Система переведена от чисто «бухгалтерской» фиксации сборок (факт мерджа фич в Git) к **непрерывному наблюдению за реальным запущенным артефактом**:

1. **Принцип единого фокуса:** Фабрика строит и наблюдает ровно **один активный проект** в любой момент времени.
2. **Отказ от слепых таймеров:** Запуск проверок управляется накопленной апостериорной уверенностью и событиями в `ContinuousOrchestrationService` (никаких жестких cron-костылей).
3. **JIT-реактивная верификация (2026-08-25):** Фальсификационный и судейский контуры не блокируются устаревшими записями в БД — при оценке TOC-ограничений вызывается JIT-зонд `ensureFreshObservation(project)` для получения мгновенного факта о текущем коммите ($C_{\text{sha}}$).

---

## 2. Статус реализации фаз плана

| Фаза | Название | Статус | Исполняющие классы и компоненты |
|------|----------|--------|----------------------------------|
| **Phase 0** | **Проверка возможности запуска (Launchability Constraint)** | **РЕАЛИЗОВАНО** | `ProductLaunchabilityService`, `LaunchabilityConstraintService`, `ProjectReadinessService` |
| **Phase 1** | **Живучесть и Beta-апостериор (Безопасный Launcher Sidecar)** | **РЕАЛИЗОВАНО** | `ClientRuntimeObservabilityService`, `RuntimeLauncherClient`, таблица `client_runtime_observations`, контейнер `runtime-launcher` (порт `8091`) |
| **Phase 2** | **Детекция сдвига качества и Defect Journal** | **РЕАЛИЗОВАНО** | `ProcessControlService` (`STREAM_PRODUCT_RUNTIME_HEALTH`), `DefectJournalService` (`scope = 'product'`), `KaizenService` |
| **Phase 3** | **Продуктовый Kaizen и Философская Фальсификация** | **РЕАЛИЗОВАНО** | `FalsificationCycleService`, `PersistentWorkerSessionService`, `EvidenceCoherenceService` (Thagard/ECHO + AGM) |

---

## 3. Детали архитектурных компонентов

### 3.1. Изолированный контейнер `runtime-launcher` (Phase 1)
* **Принцип наименьших привилегий:** Хостовый сокет `/var/run/docker.sock` смонтирован **только** в легковесный Python FastAPI sidecar (`runtime-launcher`), изолированный от основного Spring Boot бэкенда.
* **Переназначение портов:** Все публикуемые сервисами клиента порты динамически ремапятся начиная с `18080` (`EXTERNAL_PORT_BASE`), предотвращая конфликты с инфраструктурой фабрики (`8080`, `8000`, `8091`, `8092`, `8093`).
* **Мостовая маршрутизация:** Обращение к запущенным клиентским контейнерам выполняется через `host.docker.internal` с таймаутом ожидания холодного старта JVM (до 60 с).

### 3.2. Схема базы данных (`client_runtime_observations`)
```sql
CREATE TABLE client_runtime_observations (
    id UUID NOT NULL PRIMARY KEY,
    project_id UUID NOT NULL,
    observed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    launch_success BOOLEAN NOT NULL,
    launch_duration_ms BIGINT,
    health_status_code INT,
    health_latency_ms BIGINT,
    error_text TEXT,
    commit_sha VARCHAR(64)
);
```

### 3.3. JIT Reactive Runtime Observability (2026-08-25)
* Метод `clientRuntimeObservabilityService.ensureFreshObservation(project)` опрашивает запущенный контейнер, если:
  1. Наблюдения по проекту отсутствуют;
  2. Последнее наблюдение старше 1 часа;
  3. Последнее наблюдение завершилось ошибкой или зафиксировано на старом Git-коммите.
* Исключает «залипание» теории ограничений (TOC) на исторических сбоях.

### 3.4. Продуктовая фальсификация (Phase 3)
* Запуск `FalsificationCycleService` выполняет пошаговый диалог (13 ролей, 78 мыслителей) по принципам Карла Поппера с принудительной классификацией Кано (`Must-Be`, `Performance`, `Attractive`, `Indifferent`, `Reverse`).
* Каждая находка валидируется через `EvidenceCoherenceService` (байесовское обновление уверенности по Бовенсу-Хартманну и когерентность Тагарда).
