# Директивы и наряд для L2 (2026-10-17 19:35 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый макро-цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секция VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Непрерывная Оркестрация фабрики») полностью заверена и закрыта инженером (коммит `87851a36`, 290/290 green в 25 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS 01:20 min, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает следующий шаг непрерывного макро-цикла фабрики — **Кластер Секций XVI и XXIX («Реестр 59 архитектурных предписаний L2: что и как чинить» и «Цех дизайна: порождение экранов, марка, геометрия и дизайн-система»)** целиком (**272 теста в едином непрерывном пакетном прогоне в Docker Maven [29 тест-сьютов]**):
     * **Группа 1 — Архитектурные предписания L2: авторизация наблюдателя, сторож ТОС, истинность LeanValue и TargetContext (7 сьютов, 75 тестов):**
       - `InternalGeminiObserverSecurityIntegrationTest` (9 тестов), `InternalGeminiObserverControllerTest` (16 тестов), `GeminiObserverActionServiceTest` (17 тестов): деонтическая защита сервисных эндпоинтов наблюдателя, строгий запрет неавторизованных мутаций (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `TocSentinelServiceTest` (18 тестов), `TocSentinelControllerTest` (2 теста): анти-зеркальное чистое чтение DBR-статуса и динамический триггер сторожа TOC без скрытых побочных эффектов (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `ALONZO_CHERCH_03_LAMBDA_CORE_REDUCTION` / D008);
       - `LeanValueTest` (9 тестов), `TargetContextTest` (4 теста): 4-значная решётка истинности Белнэпа для Lean-ценностей и целевого контекста задачи (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012).
     * **Группа 2 — Разбор репозитория, правда операций, семантика вишлистов, настройки, миграции и границы пространств имён (9 сьютов, 84 теста):**
       - `RepositoryStackAnalyzerTest` (6 тестов): отсечение категориальных ошибок стека без ложных обвинений заказчика (`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
       - `OperationalTruthServiceTest` (17 тестов): асимметричная динамика доверия строго на основе накопленных свидетельств (`ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` / D010);
       - `WishlistServiceFalsificationTest` (6 тестов), `WishlistContentSimilarityMatcherTest` (8 тестов), `WishlistSimilarityTest` (6 тестов): семантическая дедупликация требований и фальсификационный заслон (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `NUEL_BELNAP_05_TOKEN_TRACE_UNITY` / D015);
       - `SettingsControllerIntegrationTest` (8 тестов), `SystemSettingsServiceTest` (13 тестов): деонтический запрет мутации неизменяемых системных параметров (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `FlywayMigrationValidationTest` (4 теста): целостность структуры и нерушимость схемы БД фабрики (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `ProductNamespaceLaw26Test` (16 тестов): топологическая изоляция продуктовых пространств по Закону 26 (`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006).
     * **Группа 3 — Цех дизайна, геометрия, консистентность и фальсификационные заслоны (7 сьютов, 71 тест):**
       - `DesignAssetServiceTest` (12 тестов): модальный актуализм физических ассетов экранов (`RUT_BARKAN_MARKUS_01_MODAL_ACTUALISM` / D015);
       - `DesignConsistencyAuditServiceTest` (15 тестов): единство следа дизайн-токенов (`NUEL_BELNAP_05_TOKEN_TRACE_UNITY` / D015);
       - `LayoutGeometryAuditServiceTest` (13 тестов): фиксация уровней абстракции геометрии вьюпорта (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `DesignSystemFalsificationServiceTest` (6 тестов): попперовская фальсификация согласованности дизайн-системы (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `DesignDriftMonitorServiceTest` (7 тестов): анти-зеркальная телеметрия дрейфа интерфейса (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `DesignExcellenceGateTest` (12 тестов), `DesignExcellenceGateFalsificationTest` (6 тестов): многозначная оценка совершенства дизайна и заслон против фиктивных вердиктов (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 4 — Оркестрация дизайн-цеха, Законы 15 и 3, выметающий обход и клиент Stitch (6 сьютов, 42 теста):**
       - `DesignShopCycleRepositoryIntegrationTest` (6 тестов), `DesignShopOrchestrationServiceTest` (13 тестов), `DesignShopOrchestrationServiceLaw15Test` (7 тестов): владение переходами и деонтическая фазовая дисциплина дизайна по Закону 15 (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `StrandedDesignCycleSweepServiceTest` (4 теста): атомарный CAS-свип зависших циклов дизайна (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `DesignImplementationLoopClosureLaw3Test` (2 теста): телеосемантическое замыкание цикла имплементации по Закону 3 (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011);
       - `StitchClientTest` (10 тестов): каузальная цепь взаимодействия со Stitch UI и запрет подмены кода изображениями (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `NUEL_BELNAP_06_SUBSTITUTION_ORACLE` / D009).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и моделей (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D002, D012, D013, D015, D011, D009)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green / Popper]:**
  * Фальсификационный заслон дизайн-системы (`DesignSystemFalsificationServiceTest`), семантического вишлиста (`WishlistServiceFalsificationTest`), и заслона дизайна (`DesignExcellenceGateFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Фиксация уровней абстракции вьюпорта (`LayoutGeometryAuditService`), надежная цепочка вызовов Stitch API и целостность миграций схемы Flyway.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity & Exclusionary reasons / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на мутацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтическая граница доступа к наблюдателю (`InternalGeminiObserverSecurityIntegrationTest`) и системным настройкам (`SettingsControllerIntegrationTest`).
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D004 / D006 Varzi]:**
  * Монопольное владение переходами циклов дизайна по Закону 15 (`DesignShopOrchestrationService`), атомарный CAS-свип и топологическая изоляция продуктового пространства имён по Закону 26.
- **`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error / Ryle]:**
  * Демаркация внутренних сбоев анализатора стека от фактов о клиентском репозитории (`RepositoryStackAnalyzerTest`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` & `NUEL_BELNAP_05_TOKEN_TRACE_UNITY` [D012 / D015 Belnap]:**
  * 4-значная логика истинности Lean-ценностей и целевого контекста задач; неразрывность следа дизайн-токенов марки.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Чистое чтение состояния сторожа ТОС и непрерывный мониторинг дрейфа дизайн-макетов без побочных эффектов.
- **`ENDI_KLARK_02_GROUPING_PROXIMITY_GATE` & `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Clark & Dretske]:**
  * Gestalt-близость геометрии экранов (< 1.0) и телеосемантическая обратная связь при замыкании цикла имплементации по Закону 3.

---

## 3. Регламент отчёта инженера L2

1. Провести единый непрерывный пакетный прогон всех 29 тест-сьютов Кластера Секций XVI и XXIX в Docker Maven 3.9.9 Temurin-21 (-m 2g).
2. Зафиксировать результат 272/272 green, 0 строк правок рабочего кода.
3. Актуализировать строки *Живое:* в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
4. Сделать семантический коммит в фабрику: `feat(sections-xvi-xxix): complete Stage 4 batch verification (D008, D010, D006)`.
5. Обновить `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
6. **КАТЕГОРИЧЕСКИ НЕ ВЫЗЫВАТЬ таймеры или инструмент schedule.** Завершить выполнение штатным текстовым отчётом.
