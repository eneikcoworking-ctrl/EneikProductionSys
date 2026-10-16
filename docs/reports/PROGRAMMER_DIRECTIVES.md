# Директивы и наряд для L2 (2026-10-16 20:43 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую секцию механизмов целиком.
   - Секции VII (TOC), VIII (Flow & Metrics, Kaizen, Оркестрация), IX, X и XI («Внешние системы, Цех дизайна и Наблюдение за живым продуктом»), а также Кластер Секций XII, XIIа и XIII («Журнал, память, санитария, API и владение состоянием») полностью заверены инженером (коммиты `37702f45`, `7a275402`, `3114bfc1`, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического кластера — **Кластер Секций XIV, XV и XVI («Философский корпус образцов, класс дефекта "тихое размножение задач" и реестр 59 предписаний L2»)** целиком (**26 тест-сьютов в едином пакетном прогоне в Docker Maven**):
     * **Группа 1: Секция XIV — Сверка с корпусом философских образцов (3 сьюта, 17 тестов):**
       - `PhilosopherPatternCorpusConsistencyTest` (6/6): заземление RAG-корпуса, сверка 53 семейств, 0 коллизий публикационных и производственных паттернов, инварианты каталога (`ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` / D014, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `RequirementGroundingServiceTest` (6/6), `RequirementGroundingServiceFalsificationTest` (5/5): строгая попперовская фальсификация требований заказчика и запрет галлюцинаций сущностей (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010).
     * **Группа 2: Секция XV — Класс дефекта «тихое размножение задач»: подпись, замер, случаи (7 сьютов, 48 тестов):**
       - `TaskDuplicateDetectorTest` (5/5), `SemanticDuplicateVetoTest` (4/4): семантическое вето на клонирование задач, эвристический барьер совпадений (`SOL_KRIPKE_01_RIGID_DESIGNATOR` / D001, `GILBERT_RAYL_02_CATEGORY_ERROR_SCAN` / D002);
       - `CompilerTaskIdentityTest` (3/3), `TechnicalLeadCompilerTest` (4/4), `TechnicalLeadCompilerIntegrationTest` (11/11), `TechnicalLeadCompilerFalsificationTest` (6/6): нерушимое тождество задач по контентному ключу V137, попперовский заслон компилятора техлида от тихого размножения (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `PlannedWorkRecoveryServiceTest` (15/15): монопольное восстановление запланированной работы без мусорного дублирования (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004).
     * **Группа 3: Секция XVI — Реестр 59 предписаний L2: что и как чинить (16 сьютов, 159 тестов):**
       - `InternalGeminiObserverSecurityIntegrationTest` (9/9), `InternalGeminiObserverControllerTest` (16/16), `GeminiObserverActionServiceTest` (17/17): демаркация внутренних эндпоинтов наблюдателя, защита границ полномочий (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006);
       - `TocSentinelServiceTest` (18/18), `TocSentinelControllerTest` (2/2): адаптивный триггер сторожа ТОС и анти-зеркальное чтение снимка барабана-буфера-верёвки (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `ALONZO_CHERCH_21_DERIVED_CUTOFF` / D008);
       - `LeanValueTest` (9/9), `TargetContextTest` (4/4): 4-значная таблица истинности и строгая демаркация неразобранного/неопределённого контекста назначения (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012);
       - `RepositoryStackAnalyzerTest` (6/6): устранение категориальной ошибки трансляции сбоя авторизации фабрики в вину клиента (`GILBERT_RAYL_02_CATEGORY_ERROR_SCAN` / D002);
       - `OperationalTruthServiceTest` (17/17): асимметричная динамика доверия, начинающаяся с нуля и растущая строго по свидетельствам (`ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` / D010);
       - `WishlistServiceFalsificationTest` (6/6), `WishlistContentSimilarityMatcherTest` (8/8), `WishlistSimilarityTest` (6/6): заслон от паразитного пересоздания элементов вишлиста при семантическом совпадении (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `SettingsControllerIntegrationTest` (8/8), `SystemSettingsServiceTest` (13/13): неприкосновенность системных настроек и аудит изменений (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `FlywayMigrationValidationTest` (4/4): строгая валидация неизменности применённых миграций (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `ProductNamespaceLaw26Test` (16/16): Закон 26 — категорическая изоляция пространств имён продукта и фабрики (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D001, D002, D004, D012, D013, D014)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация требований (`RequirementGroundingServiceFalsificationTest`), компилятора (`TechnicalLeadCompilerFalsificationTest`), схожести вишлистов (`WishlistServiceFalsificationTest`) и валидации миграций Flyway (`FlywayMigrationValidationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и происхождение данных: тождество задач по контентному ключу V137 (`CompilerTaskIdentityTest`), асимметричный рост доверия строго по свидетельствам (`OperationalTruthServiceTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Защита внутренних входов наблюдателя (`InternalGeminiObserverSecurityIntegrationTest`), защита системных настроек (`SystemSettingsServiceTest`), Закон 26 изоляции пространств имён продукта и фабрики (`ProductNamespaceLaw26Test`).
- **`SOL_KRIPKE_01_RIGID_DESIGNATOR` [D001 Semantic drift / Kripke]:**
  * Жесткий десигнатор тождества сущностей задач по их неустранимому содержанию, исключающий паразитное дублирование.
- **`GILBERT_RAYL_02_CATEGORY_ERROR_SCAN` [D002 Category mistake / Ryle]:**
  * Разделение категорий: локальные сбои сетевого доступа фабрики не транслируются в утверждения о дефектах клиентского репозитория (`RepositoryStackAnalyzerTest`); разделение залипших дубликатов и скорости их генерации.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Mereological overreach / Varzi]:**
  * Атомарное монопольное владение восстановлением запланированной работы в `PlannedWorkRecoveryServiceTest`.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Multi-valued truth / Belnap]:**
  * 4-значная таблица истинности в `LeanValueTest` и `TargetContextTest`: честное сохранение неопределённого состояния без принудительной булевой редукции.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркальная телеметрия сторожа ТОС (`TocSentinelServiceTest`) без скрытых мутаций состояния графа при чтении.
- **`ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` [D014 Grounding gap / Church & Pearl]:**
  * 100% заземление 1720 публикационных паттернов и 53 философских семейств без коллизий и пропусков (`PhilosopherPatternCorpusConsistencyTest`).

---

## 3. Регламент отчёта и фиксации для L2

1. Провести единый непрерывный пакетный прогон тестов Кластера Секций XIV, XV и XVI в Docker-контейнере Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=PhilosopherPatternCorpusConsistencyTest,RequirementGroundingServiceTest,RequirementGroundingServiceFalsificationTest,TaskDuplicateDetectorTest,SemanticDuplicateVetoTest,CompilerTaskIdentityTest,TechnicalLeadCompilerTest,TechnicalLeadCompilerIntegrationTest,TechnicalLeadCompilerFalsificationTest,PlannedWorkRecoveryServiceTest,InternalGeminiObserverSecurityIntegrationTest,InternalGeminiObserverControllerTest,GeminiObserverActionServiceTest,TocSentinelServiceTest,TocSentinelControllerTest,LeanValueTest,TargetContextTest,RepositoryStackAnalyzerTest,OperationalTruthServiceTest,WishlistServiceFalsificationTest,WishlistContentSimilarityMatcherTest,WishlistSimilarityTest,SettingsControllerIntegrationTest,SystemSettingsServiceTest,FlywayMigrationValidationTest,ProductNamespaceLaw26Test
   ```
2. Подтвердить инвариант Ступени 4: строго 0 строк изменений рабочего кода (`git diff --stat src/main/java src/main/resources scripts/` пуст).
3. Актуализировать строки `*Живое:*` в `docs/FACTORY_MECHANISMS.md` и статус в `docs/ANTIGRAVITY_QUEUE.md`.
4. Сохранить компактный отчет в `docs/reports/PROGRAMMER_STATE.md` (до 15–20 строк).
5. Зафиксировать такт семантическим коммитом: `feat(sections-xiv-xvi): complete Stage 4 batch verification (D008, D010, D006)`.
