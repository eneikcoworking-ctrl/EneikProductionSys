# Директивы и наряд для L2 (2026-10-23 11:53 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый макро-цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XII, XIIа и XIII («Журнал, память, санитария, управляющая поверхность API и карта владения состоянием фабрики») полностью заверен и закрыт инженером (коммит `a77ddd1a`, 193/193 green в 32 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS 01:59 min, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает следующий такт непрерывного макро-цикла фабрики — **Кластер Секций XIV, XV и XVI («Философский корпус образцов, класс дефекта "тихое размножение задач" и реестр 59 архитектурных предписаний L2»)** целиком (**224 теста в едином непрерывном пакетном прогоне в Docker Maven [26 тест-сьютов]**):
     * **Группа 1: Секция XIV — Сверка с корпусом философских образцов (3 сьюта, 17 тестов):**
       - `PhilosopherPatternCorpusConsistencyTest` (6 тестов): сверка 53 семейств паттернов, нулевые коллизии фабрично-выведенных образцов, полнота общих паттернов и фальсификационные заслоны целостности философского словаря фабрики (`KIT_DEROUZ_01_RELIABILITY_CHAIN` / D010, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008);
       - `RequirementGroundingServiceTest` (6 тестов), `RequirementGroundingServiceFalsificationTest` (5 тестов): сверка текста требований с математическим и архитектурным корпусом через RAG без мутации исходного текста клиента, фальсификационный заслон устойчивости к сбоям RAG-контекста (`DZHUDA_PERL_15_RAG_GROUNDING_CAPSULE` / D014, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 2: Секция XV — Класс дефекта «тихое размножение задач»: подпись, замер, случаи (7 сьютов, 48 тестов):**
       - `TaskDuplicateDetectorTest` (5 тестов): демаркация залипших дубликатов задач от скорости их генерации, фиксация скорости дублирования в журнале дефектов без блокировки потока (`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002, `ALFRED_TARSKIY_10_INSTITUTIONAL_FACT_REGISTER` / D007);
       - `PlannedWorkRecoveryServiceTest` (15 тестов): монопольное владение восстановлением запланированной работы, безопасное восстановление зависших вишлистов по критерию ClientDeliverableReadiness (`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` / D004, `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011);
       - `CompilerTaskIdentityTest` (3 теста), `SemanticDuplicateVetoTest` (4 теста), `TechnicalLeadCompilerIntegrationTest` (11 тестов), `TechnicalLeadCompilerFalsificationTest` (6 тестов), `TechnicalLeadCompilerTest` (4 теста): тождество задач компилятора по контентному ключу V137, вето семантических дубликатов, CAS-финализация и инварианты пространства имен Закона 26 (`DZHONATAN_SHAFFER_18_PERSISTENCE_SNAPSHOT` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 3: Секция XVI — Реестр 59 архитектурных предписаний L2: что и как чинить (16 сьютов, 159 тестов):**
       - `InternalGeminiObserverSecurityIntegrationTest` (9 тестов), `InternalGeminiObserverControllerTest` (16 тестов), `GeminiObserverActionServiceTest` (17 тестов): деонтическая защита внутреннего входа наблюдателя, авторизация и аудит действий без побочных эффектов мутации (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `TocSentinelServiceTest` (18 тестов), `TocSentinelControllerTest` (2 теста): чистое чтение DBR-снимка сторожем ТОС, адаптивная частота пробуждения без холостых записей в граф (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `LeanValueTest` (9 тестов), `TargetContextTest` (4 теста): 4-значная решётка истинности Белнапа для ценности работы и контекста задач (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012);
       - `RepositoryStackAnalyzerTest` (6 тестов): демаркация внутренних неудач фабрики от продуктового бэклога заказчика (`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
       - `OperationalTruthServiceTest` (17 тестов): асимметричный рост оперативного доверия фабрики строго по накопленным свидетельствам (`ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` / D010, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012);
       - `WishlistServiceFalsificationTest` (6 тестов), `WishlistContentSimilarityMatcherTest` (8 тестов), `WishlistSimilarityTest` (6 тестов): дедупликация заявок вишлиста по косинусному и лексическому сходству, атомарные переходы состояний (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` / D004);
       - `SettingsControllerIntegrationTest` (8 тестов), `SystemSettingsServiceTest` (13 тестов): защита системных настроек и факт-регистр с маскированием секретов (`DZHON_SERL_01_INSTITUTIONAL_FACT_REGISTER` / D007, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `FlywayMigrationValidationTest` (4 теста): целостность и валидация миграций схемы Flyway (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `ProductNamespaceLaw26Test` (16 тестов): Закон 26 — топологическая изоляция пространств имён продукта и фабрики (`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и моделей (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Никаких искусственных спешек:** Замеры должны быть проведены основательно, надежно и доказательно.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D002, D004, D007, D012, D013, D014)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Фальсификационные заслоны целостности философского словаря (`PhilosopherPatternCorpusConsistencyTest`), RAG-заземления (`RequirementGroundingServiceFalsificationTest`), компилятора техлида (`TechnicalLeadCompilerFalsificationTest`), дедупликации вишлистов (`WishlistServiceFalsificationTest`) и адаптивного сторожа ТОС (`TocSentinelServiceTest`).
- **`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` & `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` [D010 Level of abstraction lock & reliability chain / Goldman]:**
  * Четкое разделение уровней абстракции задач и логов, контентные ключи V137, асимметричный рост оперативного доверия фабрики строго по подтвержденным фактам.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity & Exclusionary reasons / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на мутацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтическая защита путей внутреннего наблюдателя и изоляция пространств имён продукта и фабрики по Закону 26.
- **`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error scan / Ryle]:**
  * Демаркация внутренних фабричных сбоев стека от продуктового бэклога заказчика (`RepositoryStackAnalyzer`), разделение залипших дубликатов задач и скорости их генерации (`TaskDuplicateDetector`).
- **`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Part-whole ownership / Shaffer & Varzi]:**
  * Монопольное владение восстановлением запланированных работ (`PlannedWorkRecoveryService`) и атомарные переходы вишлистов.
- **`ALFRED_TARSKIY_10_INSTITUTIONAL_FACT_REGISTER` & `DZHON_SERL_01_INSTITUTIONAL_FACT_REGISTER` [D007 Institutional fact register / Tarski & Searle]:**
  * Институциональный факт-регистр скорости дубликатов задач и системных настроек с маскированием секретов.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status table / Belnap]:**
  * 4-значная решётка истинности для Lean-ценностей, контекста задач и оперативной правды.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Чистое чтение снимка сторожа ТОС и внутренних DTO наблюдателя без мутирующих побочных эффектов при GET-запросах.
- **`DZHUDA_PERL_15_RAG_GROUNDING_CAPSULE` [D014 RAG grounding capsule / Pearl]:**
  * 100% заземление RAG-контекста требований без галлюцинаций с безопасной деградацией.

---

## 3. Регламент исполнения наряда L2

1. **Команда верификации пакета:** Единый непрерывный запуск в Docker Maven 3.9.9 Temurin-21 (-m 2g):
   ```bash
   docker run --rm -m 2g -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=PhilosopherPatternCorpusConsistencyTest,RequirementGroundingServiceTest,RequirementGroundingServiceFalsificationTest,TaskDuplicateDetectorTest,PlannedWorkRecoveryServiceTest,CompilerTaskIdentityTest,SemanticDuplicateVetoTest,TechnicalLeadCompilerIntegrationTest,TechnicalLeadCompilerFalsificationTest,TechnicalLeadCompilerTest,InternalGeminiObserverSecurityIntegrationTest,InternalGeminiObserverControllerTest,GeminiObserverActionServiceTest,TocSentinelServiceTest,TocSentinelControllerTest,LeanValueTest,TargetContextTest,RepositoryStackAnalyzerTest,OperationalTruthServiceTest,WishlistServiceFalsificationTest,WishlistContentSimilarityMatcherTest,WishlistSimilarityTest,SettingsControllerIntegrationTest,SystemSettingsServiceTest,FlywayMigrationValidationTest,ProductNamespaceLaw26Test
   ```
2. **Критерий приёмки:** Ровно 224/224 green в 26 тест-сьютах, BUILD SUCCESS, 0 failures, 0 errors, строго 0 строк изменений рабочего кода.
3. **Фиксация результатов:**
   - Обновить строки `*Живое:*` в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`;
   - Зафиксировать отчет в `docs/reports/PROGRAMMER_STATE.md`;
   - Выполнить семантический коммит `feat(sections-xiv-xv-xvi): complete Stage 4 batch verification (D008, D010, D006)`.
   - Внимание: по директиве оператора инструмент `schedule` и таймеры не взводить!
