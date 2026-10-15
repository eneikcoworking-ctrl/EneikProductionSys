# Пакетная директива L1/L3 -> L2: Секция IX (Самопроверка, оркестрация и операционная истина)

**Дата:** 2026-10-15 09:14 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, согласований и пауз.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секция IX (Самопроверка, оркестрация и операционная истина / Раздел VIII `docs/FACTORY_MECHANISMS.md`) охватывает полный контур самодиагностики, фальсификации и непрерывной оркестрации:
   - `SystemProgressTracker` (ядро)
   - `AiHealthTracker`
   - `RiskLevelCalculator`
   - `FalsificationCycleService` (ядро)
   - `EvidenceCoherenceService`
   - `OpsAuditorService` (ядро)
   - `PlatformSelfReferenceDetector` (ядро)
   - `FactorySelfHealthService` (ядро)
   - `KaizenService`
   - `PlannedWorkRecoveryService` (ядро)
   - `ContinuousOrchestrationService` (ядро)
   - `OperationalPolicyService` / `OperationalAction` (ядро)
   - `TrustSnapshotService`
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ:** Запрещено модифицировать рабочий код сервисов при проходящих тестах!
   - Любая правка рабочего кода фабрики допустима исключительно при наличии воспроизводимого падающего теста, фальсифицирующего инвариант. Если тесты зеленые — код признается нерушимым и фиксируется замером.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green, Popper]:** Принцип фальсификации. Сердцебиение фабрики (`SystemProgressTracker`), здоровье моделей (`AiHealthTracker`), суточный цикл (`FalsificationCycleService`) и предложения улучшений (`KaizenService`) обязаны опираться на эмпирически опровержимые свидетельства, а не на самоподтверждающиеся холостые тики («фабрика занята ≠ фабрика движется»).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` [D010 Data lineage loss, Goldman]:** Лок уровней абстракции и надежность эпистемической цепочки. Строгое разделение уровней «продукт — фабрика — внешняя модель» (`PlatformSelfReferenceDetector`, `FactorySelfHealthService`, `TrustSnapshotService`), исключающее категориальные ошибки попадания заводского шума в клиентский скоуп или потерю происхождения снимков доверия.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity, Raz]:** Деонтический запрет как исключающая причина (`exclusionary reason`) и исполняемый код. Императивный запрет инженеру трогать работающий код сервисов на Ступени 4 при проходящих тестах; запрет `PlannedWorkRecoveryService` и `ContinuousOrchestrationService` порождать неконтролируемые сторонние кроны или дублирующие задачи.

---

## 3. Пакетный наряд по механизмам Секции IX (Ступени 3–4: «Замер в рантайме, не правка кода»)

### А. `SystemProgressTracker`, `AiHealthTracker`, `RiskLevelCalculator`
- **Действие:** Замерить сердцебиение реального прогресса фабрики, регистрацию вызовов моделей и расчет уровня риска.
- **Инварианты:** Запись прогресса фиксируется исключительно при наличии реального продвижения (слияние PR, успешная отправка); здоровье моделей отслеживает реальные отказы без подмены заглушками; предсказания риска не действуют в обход лестницы доверия.
- **Заслон:** `SystemProgressTrackerTest`, `RiskLevelCalculatorTest`, `MLPredictionServiceClientTest`, `Stage5ProtectedInvariantsFalsificationTest`. Исходный код сервисов НЕ ТРОГАТЬ.

### Б. `FalsificationCycleService`, `EvidenceCoherenceService`, `OpsAuditorService`
- **Действие:** Замерить суточный цикл фальсификации, расчет когерентности свидетельств ECHO и аудитор операций.
- **Инварианты:** Владение агрегатами `falsification_runs` и `code_integrity_findings` строго объявлено; когерентность свидетельств не фальсифицирует уверенность на пустых данных; аудитор воздерживается при отсутствии независимых доказательств и повторно валидирует предусловия перед действием.
- **Заслон:** `FalsificationCycleServiceTest`, `EvidenceCoherenceServiceTest`, `EvidenceCoherenceV80TerminationFalsificationTest`, `OpsAuditorServiceTest`. Исходный код сервисов НЕ ТРОГАТЬ.

### В. `PlatformSelfReferenceDetector`, `FactorySelfHealthService`, `KaizenService`
- **Действие:** Замерить детектор самореференции фабрики, монитор собственного здоровья и жизненный цикл Кайдзен.
- **Инварианты:** Отсечение внутренних заводских находок от продуктового бэклога клиента (`D002 Category error`); мониторинг размера БД и пулов фабрики; дедупликация предложений Кайдзен строго в рамках `(projectId, category, targetComponent)`.
- **Заслон:** `PlatformSelfReferenceDetectorTest`, `FactorySelfHealthServiceTest`, `KaizenServiceTest`. Исходный код сервисов НЕ ТРОГАТЬ.

### Г. `PlannedWorkRecoveryService`, `ContinuousOrchestrationService`, `OperationalPolicyService`, `TrustSnapshotService`
- **Действие:** Замерить восстановление задач без дублирования, единый цикл оркестрации, детерминированные переходы политики и фиксацию снимков доверия.
- **Инварианты:** `PlannedWorkRecoveryService` гарантирует сохранение идентичности задачи и исключает каскадное размножение задач; `ContinuousOrchestrationService` выступает единственным диспетчерским тиком фабрики; `OperationalPolicyService` применяет исполняемые правила переходов; `TrustSnapshotService` связывает входы доверия с отложенными результатами.
- **Заслон:** `PlannedWorkRecoveryServiceTest`, `ContinuousOrchestrationServiceTest`, `OperationalPolicyServiceTest`, `OperationalPolicyReasonTest`, `OperationalPolicyDeniedExceptionFalsificationTest`, `TrustSnapshotServiceTest`. Исходный код сервисов НЕ ТРОГАТЬ.

---

## 4. Регламент верификации и фиксации для инженера L2

1. Пакетный запуск заслонов в изолированном Docker-контейнере Maven:  
   `docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=SystemProgressTrackerTest,RiskLevelCalculatorTest,FalsificationCycleServiceTest,EvidenceCoherenceServiceTest,OpsAuditorServiceTest,PlatformSelfReferenceDetectorTest,FactorySelfHealthServiceTest,KaizenServiceTest,PlannedWorkRecoveryServiceTest,ContinuousOrchestrationServiceTest,OperationalPolicyServiceTest,TrustSnapshotServiceTest -DfailIfNoTests=false`
2. Обновление записей Ступени 4 в `docs/FACTORY_MECHANISMS.md` (секции `*Живое:*` и статус `ideal`) и закрытие пунктов Секции IX в `docs/ANTIGRAVITY_QUEUE.md`.
3. Фиксация семантическим коммитом L2: `feat(orchestration): complete Stage 4 batch verification for Section IX (D008, D010, D006)`.
4. Краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (до 15–20 строк).
