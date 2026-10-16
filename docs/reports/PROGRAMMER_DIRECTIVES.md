# Директивы и наряд для L2 (2026-10-16 18:38 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XII, XIIа и XIII («Журнал, память, санитария, управляющая поверхность API и карта владения состоянием фабрики») успешно заверен инженером (коммит `9b47dd5f`, 189/189 green в Docker Maven, 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического кластера — **Кластер Секций XIV, XV и XVI («Философский корпус образцов, класс дефекта "тихое размножение задач" и реестр 59 предписаний L2»)** целиком (**224 теста в 26 тест-сьютах в едином пакетном прогоне в Docker Maven**):
     * **Группа 1: Секция XIV — Сверка с корпусом философских образцов (17 тестов, 3 сьюта):**
       - `PhilosopherPatternCorpusConsistencyTest` (6/6 green): 1:1 сверка 53 семейств, 104 общих образцов и 1720 публикационных паттернов корпуса философии (`DZHUDIA_PERL_01_RAG_GROUNDING_CAPSULE` / D014, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008);
       - `RequirementGroundingServiceTest` (6/6 green), `RequirementGroundingServiceFalsificationTest` (5/5 green): строгое эпистемическое заземление требований, отсечение галлюцинаций и демаркация дефектов (`KIT_DEROUZ_01_RELIABILITY_CHAIN` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 2: Секция XV — Класс дефекта «тихое размножение задач»: подпись, замер, случаи (48 тестов, 7 сьютов):**
       - `TaskDuplicateDetectorTest` (5/5 green), `SemanticDuplicateVetoTest` (4/4 green): превентивное обнаружение и семантическое вето дубликатов задач до их сохранения в базу (`SOL_KRIPKE_01_RIGID_DESIGNATOR` / D001, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `CompilerTaskIdentityTest` (3/3 green), `TechnicalLeadCompilerTest` (4/4 green), `TechnicalLeadCompilerIntegrationTest` (11/11 green), `TechnicalLeadCompilerFalsificationTest` (6/6 green): компилятор техлида, декомпозиция эпиков, тождество задач по несводимому содержанию (`GILBERT_RAYL_02_CATEGORY_ERROR_SCAN` / D002, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `PlannedWorkRecoveryServiceTest` (15/15 green): автономное восстановление запланированной работы без мутации терминальных состояний (`DZHON_SERL_01_INSTITUTIONAL_FACT_REGISTER` / D007).
     * **Группа 3: Секция XVI — Реестр 59 предписаний L2: что и как чинить (159 тестов, 16 сьютов):**
       - `InternalGeminiObserverSecurityIntegrationTest` (9/9 green), `InternalGeminiObserverControllerTest` (16/16 green), `GeminiObserverActionServiceTest` (17/17 green): защита внутренних входов наблюдателя, деонтический аудит действий (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `TocSentinelServiceTest` (18/18 green), `TocSentinelControllerTest` (2/2 green): сторож ТОС, адаптивная частота проверки без холостых пробуждений (`ALONZO_CHERCH_03_LAMBDA_CORE_REDUCTION` / D008, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `LeanValueTest` (9/9 green), `TargetContextTest` (4/4 green): 4-значная таблица истинности LeanValue и строгая изоляция контекста цели (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012);
       - `RepositoryStackAnalyzerTest` (6/6 green): анализ чужих репозиториев без ложных находок при недоступности (`GILBERT_RAYL_02_CATEGORY_ERROR_SCAN` / D002);
       - `OperationalTruthServiceTest` (17/17 green): асимметричная динамика доверия (`ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` / D010);
       - `WishlistServiceFalsificationTest` (6/6 green), `WishlistContentSimilarityMatcherTest` (8/8 green), `WishlistSimilarityTest` (6/6 green): сличитель сходства вишлистов и дедупликация (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `SettingsControllerIntegrationTest` (8/8 green), `SystemSettingsServiceTest` (13/13 green): защита настроек системы (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `FlywayMigrationValidationTest` (4/4 green): запрет на отключение валидации миграций схемы (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `ProductNamespaceLaw26Test` (16/16 green): закон 26 — категорическая изоляция пространств имен фабрики и продукта (`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D013, D014, D001, D002)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация заслонов RAG-требований (`RequirementGroundingServiceFalsificationTest`), компилятора техлида (`TechnicalLeadCompilerFalsificationTest`), дедупликации вишлистов (`WishlistServiceFalsificationTest`) и миграций базы (`FlywayMigrationValidationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и надежность цепочки происхождения данных: строгое разграничение рыночных требований, скомпилированных задач, философских паттернов и системных настроек.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтические запреты неавторизованных действий в эндпоинтах наблюдателя, семантическое вето дубликатов задач и топологическая граница закона 26.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Multi-valued Truth Status / Belnap]:**
  * 4-значные таблицы истинности для статусов LeanValue (`essential`, `valuable`, `waste`, `undetermined`) и определенности контекста цели (`TargetContext`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало телеметрии сторожа ТОС и наблюдателя: чтение состояния не мутирует граф и базу данных.
- **`DZHUDIA_PERL_01_RAG_GROUNDING_CAPSULE` [D014 Ungrounded RAG / Pearl]:**
  * 100% заземление RAG-корпуса: сверка 53 семейств и 1720 образцов без свободных пересказов и галлюцинаций.
- **`SOL_KRIPKE_01_RIGID_DESIGNATOR` [D001 Semantic Drift / Kripke] & `GILBERT_RAYL_02_CATEGORY_ERROR_SCAN` [D002 Category Error / Ryle]:**
  * Жесткие десигнаторы несводимого тождества задач и сканирование категориальных ошибок при анализе стека внешних репозиториев.

---

## 3. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон тестов Кластера Секций XIV, XV и XVI в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/app -w /app -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=PhilosopherPatternCorpusConsistencyTest,RequirementGroundingServiceTest,RequirementGroundingServiceFalsificationTest,TaskDuplicateDetectorTest,PlannedWorkRecoveryServiceTest,CompilerTaskIdentityTest,SemanticDuplicateVetoTest,TechnicalLeadCompilerIntegrationTest,TechnicalLeadCompilerFalsificationTest,TechnicalLeadCompilerTest,InternalGeminiObserverSecurityIntegrationTest,InternalGeminiObserverControllerTest,GeminiObserverActionServiceTest,TocSentinelServiceTest,TocSentinelControllerTest,LeanValueTest,TargetContextTest,RepositoryStackAnalyzerTest,OperationalTruthServiceTest,WishlistServiceFalsificationTest,WishlistContentSimilarityMatcherTest,WishlistSimilarityTest,SettingsControllerIntegrationTest,SystemSettingsServiceTest,FlywayMigrationValidationTest,ProductNamespaceLaw26Test
   ```
2. Подтвердить 100% green (224/224 green, 0 failures, 0 errors).
3. Обеспечить строго 0 строк изменений рабочего кода сервисов, моделей, контроллеров и конфигураций фабрики (`src/main/java`, `src/main/resources`, `scripts/`).
4. Актуализировать строки *Живое:* в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Зафиксировать краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (15–20 строк).
6. Закоммитить результат с семантическим сообщением: `feat(sections-xiv-xvi): complete Stage 4 batch verification (D008, D010, D006)`.
