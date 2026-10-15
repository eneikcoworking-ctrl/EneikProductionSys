# Пакетная директива L1/L3 -> L2: Секция VIII (Flow & Metrics, Самопроверка, Операционная Правда, Кайдзен, Фальсификация и Оркестрация)

**Дата:** 2026-10-15 18:30 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секция III («Отправка: задача уходит в работу») замерена и закрыта инженером (299/299 green в коммите `abe23e8`), Секция VII замерена. Настоящая директива охватывает целиком следующий фундаментальный массив конвейера фабрики — **Секцию VIII («Flow & Metrics, Самопроверка, Операционная Правда, Кайдзен, Фальсификация и Оркестрация»)**:
   - **Контур VIII.1. `FalsificationCycleService` (Varzi D004, Popper D008):**
     * Мереологическое владение Варци (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004): суточный цикл фальсификации кода и философский цикл; исключительное владение агрегатами `falsification_runs` и `code_integrity_findings`;
     * Попперовская фальсификация (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008): генерация задач-замен взамен терминально провалившихся без порождения неконтролируемых дубликатов.
   - **Контур VIII.2. `EvidenceCoherenceService` (Gilbert D007 / Thagard INUS, Goldman D010):**
     * Релаксация графа свидетельств ECHO Тагарда: независимая калибровка источников свидетельств без завышения уверенности на пустых данных;
     * Каузальная цепочка Голдмана (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010): строгая изоляция контекста источников и сходимость весов доверия.
   - **Контур VIII.3. `OpsAuditorService` (Goldman D006, Popper D008):**
     * Доказательное действие Голдмана (`ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` / D006): аудит операций действует только на независимо подтверждённых фактах рантайма, а не на непроверенных утверждениях LLM;
     * Трёхкратная валидация предусловий перед выполнением корректирующих действий; отказ при нарушении любого предусловия.
   - **Контур VIII.4. `PlatformSelfReferenceDetector` (Ryle D002):**
     * Категориальная демаркация Райла (`DZHON_OSTIN_02_CATEGORY_ERROR_SCAN` / D002): немедленное отсечение внутренних заводских находок и фабричного шума от клиентского продуктового бэклога.
   - **Контур VIII.5. `FactorySelfHealthService` (Wittgenstein D013):**
     * Анти-зеркало телеметрии Витгенштейна (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013): непрерывный мониторинг собственного здоровья фабрики (размер базы данных, пулы соединений, очереди, память) с прямой эскалацией в Кайдзен.
   - **Контур VIII.6. `KaizenService` & `DefectJournalService` (Gilbert D007, Goldman D010):**
     * Институциональный регистр дефектов: непрерывная фиксация отказов в журнале дефектов (`DefectJournalEntity`);
     * Жизненный цикл улучшений PDCA со строгой дедупликацией по `(projectId, category, targetComponent)` в окне 2 часов, запрет межпроектных мутаций при стандартизации.
   - **Контур VIII.7. `FlowMetricsService` & `TaskWaitTimeService` (Popper D008, Goldman D010):**
     * Фальсификационный баланс закона Литтла (`WIP ≈ Throughput × CycleTime`): выявление расхождений замеров без искажения сырых данных;
     * Лок уровня абстракции Голдмана (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010): метрики рассчитываются строго в границах `projectId`; время в сессии воркера отделено от времени простоя в очереди.
   - **Контур VIII.8. `LeverPromotionService` & `MarketResearchService` (Goldman D010, Raz D006):**
     * Лестница продвижения ТОС: переход из `observe_only` в `soft_gate` только по подтвержденным эмпирическим наблюдениям в `LeverObservationRepository`; немедленное понижение при расхождении;
     * Исследования рынка: детерминированный выбор несущего проекта без произвольного захвата случайных записей БД.
   - **Контур VIII.9. `PlannedWorkRecoveryService` (Ryle D002):**
     * Сохранение идентичности задачи: восстановление упавших задач возвращает ту же самую личность задачи и никогда не создаёт вторичных дубликатов, заявок или веток.
   - **Контур VIII.10. `ContinuousOrchestrationService` & `OperationalFlowCoreService` & `FlowSpineService` & `OperationalPolicyService` (Wittgenstein D013, Raz D006):**
     * Деонтический позвоночник потока (`FlowSpineService`): непрерывный контроль инварианта `done_is_not_delivery`;
     * Деонтические запреты Раза (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006): детерминированный отказ политик `OperationalPolicyService` с регистрацией точной причины отказа (`OperationalPolicyDeniedException`).
   - **Контур VIII.11. `TrustSnapshotService` (Goldman D010):**
     * Персистентные снимки доверия: сохранение входных сигналов до момента получения физического подтверждения сдачи/отклонения.
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено модифицировать рабочий код сервисов (`src/main/java`) при проходящих тестах!
   - Механизмы Секции VIII уже реализованы в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D013, D004, D002, D007)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green, Popper]:**
  * Попперовская фальсификация: арифметическая сверка закона Литтла (`FlowMetricsService`), проверка сходимости базовых контрольных пределов, тесты-заслоны обязаны падать при нарушении инвариантов потока;
- **`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` & `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` [D010 Data lineage loss, Goldman]:**
  * Каузальная надежность: расчет метрик потока и времени ожидания строго в границах `projectId`, персистентные снимки доверия `TrustSnapshotService`, лестница продвижения рычагов `LeverPromotionService`;
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity, Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода сервисов Секции VIII при проходящих тестах;**
  * Программные запреты: деонтические отказы `OperationalPolicyService`, блокировка непроверенных действий `OpsAuditorService`;
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Boundary drift, Varzi]:**
  * Мереологическое владение: исключительное право `FalsificationCycleService` на агрегаты прогонов фальсификации, четкие границы оркестрации `ContinuousOrchestrationService`;
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift, Wittgenstein]:**
  * Запрет субъективных нарративов: состояние системы оценивается исключительно по физическим метрикам, логам контейнеров и данным БД; публикация свидетельств против собственного мнимого благополучия (`done_is_not_delivery`);
- **`DZHON_OSTIN_02_CATEGORY_ERROR_SCAN` [D002 Category error, Austin / Ryle]:**
  * Отсечение фабричного шума от клиентских требований (`PlatformSelfReferenceDetector`), сохранение стабильной личности задачи при восстановлении (`PlannedWorkRecoveryService`);
- **`MARGARET_GILBERT_04_INSTITUTIONAL_FACT_REGISTER` [D007 Institutional fact, Gilbert]:**
  * Фиксация институциональных фактов сбоев конвейера в `DefectJournalService` с дедупликацией PDCA в `KaizenService`.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий пакетный замер механизмов Секции VIII в Docker Maven (`docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=...`).
2. Подтвердить 100% green статус по ключевым классам-заслонам Секции VIII (281 тест):
   - Фальсификация и когерентность: `FalsificationCycleServiceTest` (28/28), `EvidenceCoherenceServiceTest` (21/21), `EvidenceCoherenceV80TerminationFalsificationTest` (5/5);
   - Аудит операций и самодиагностика: `OpsAuditorServiceTest` (11/11), `PlatformSelfReferenceDetectorTest` (7/7), `FactorySelfHealthServiceTest` (5/5);
   - Кайдзен и журнал дефектов: `KaizenServiceTest` (10/10), `DefectJournalServiceTest` (3/3), `DefectJournalRootCauseAttributionTest` (6/6), `LogScopeAndDefectJournalInusFalsificationTest` (5/5);
   - Метрики потока и рычаги: `FlowMetricsServiceTest` (4/4), `LeverPromotionServiceTest` (7/7), `LeverPromotionServiceFalsificationTest` (4/4);
   - Восстановление и оркестрация: `PlannedWorkRecoveryServiceTest` (15/15), `ContinuousOrchestrationServiceTest` (16/16);
   - Операционный контур и политика: `OperationalFlowCoreServiceTest` (6/6), `OperationalFlowCoreFalsificationTest` (39/39), `OperationalPolicyServiceTest` (17/17), `OperationalPolicyReasonTest` (5/5), `OperationalPolicyDeniedExceptionFalsificationTest` (9/9);
   - Доверие, правда и позвоночник: `TrustSnapshotServiceTest` (9/9), `OperationalTruthServiceTest` (17/17), `FlowSpineServiceTest` (32/32).
3. Зафиксировать форму образца и актуализировать статус Секции VIII в `docs/FACTORY_MECHANISMS.md` (подтверждающую Ступень 4 closed, ideal, 281/281 green).
4. Синхронизировать `docs/ANTIGRAVITY_QUEUE.md`.
5. Оформить лаконичный семантический коммит фабрики.
