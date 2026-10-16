# Директивы и наряд для L2 (2026-10-16 23:38 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секции XII, XIIа и XIII («Журнал, память, санитария, управляющая поверхность API и карта владения состоянием фабрики») полностью заверены инженером (коммит `4e6ae0b4`, 193/193 green в 32 тест-сьютах Docker Maven, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического контура фабрики — **Кластер Секций XIV, XV и XVI («Сверка с корпусом философских образцов, класс дефекта "тихое размножение задач" и реестр 59 архитектурных предписаний L2»)** целиком (**224 теста в 26 тест-сьютах в едином пакетном прогоне в Docker Maven**):
     * **Группа 1: Секция XIV — Сверка с корпусом философских образцов (17 тестов, 3 сьюта):**
       - `PhilosopherPatternCorpusConsistencyTest` (6/6 green): целостность и согласованность RAG-корпуса из 86 файлов, 1720 строк паттернов и 15 кодов дефектов (`KIT_DEROUZ_13_LEVEL_OF_ABSTRACTION_LOCK` / D010, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `RequirementGroundingServiceTest` (6/6 green), `RequirementGroundingServiceFalsificationTest` (5/5 green): защита от галлюцинаций в требованиях, строгое заземление RAG-капсулы (`DZHUDA_PERL_15_RAG_GROUNDING_CAPSULE` / D014, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 2: Секция XV — Класс дефекта «тихое размножение задач»: подпись, замер, случаи (48 тестов, 7 сьютов):**
       - `TaskDuplicateDetectorTest` (5/5 green): детекция тихого размножения задач во времени, семантическое вето дубликатов (`GILBERT_RAYL_02_CATEGORY_ERROR_SCAN` / D002, `ALFRED_TARSKIY_10_INSTITUTIONAL_FACT_REGISTER` / D007);
       - `PlannedWorkRecoveryServiceTest` (15/15 green): безопасное восстановление зависших задач без дублирующей генерации (`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` / D004);
       - `CompilerTaskIdentityTest` (3/3 green), `SemanticDuplicateVetoTest` (4/4 green), `TechnicalLeadCompilerIntegrationTest` (11/11 green), `TechnicalLeadCompilerFalsificationTest` (6/6 green), `TechnicalLeadCompilerTest` (4/4 green): тождество задач компилятора по контентному ключу V137, CAS-финализация компиляции вишлистов и заслон против скрытых повторов (`DZHONATAN_SHAFFER_18_PERSISTENCE_SNAPSHOT` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 3: Секция XVI — Предписания для L2: реестр 59 архитектурных предписаний (159 тестов, 16 сьютов):**
       - `InternalGeminiObserverSecurityIntegrationTest` (9/9 green), `InternalGeminiObserverControllerTest` (16/16 green), `GeminiObserverActionServiceTest` (17/17 green): защита сервисных интерфейсов наблюдателя, аудит действий и деонтический запрет обхода (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `TocSentinelServiceTest` (18/18 green), `TocSentinelControllerTest` (2/2 green): анти-зеркальное чтение сторожа TOC без паразитной мутации при опросе (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `LeanValueTest` (9/9 green), `TargetContextTest` (4/4 green): 4-значная решётка истинности Lean, строгая типизация ценности без ложного одобрения потерь (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012);
       - `RepositoryStackAnalyzerTest` (6/6 green): сканирование категориальных ошибок стека без заражения платформы (`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
       - `OperationalTruthServiceTest` (17/17 green): операционная правда фабрики с асимметричным ростом доверия (`ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` / D010);
       - `WishlistServiceFalsificationTest` (6/6 green), `WishlistContentSimilarityMatcherTest` (8/8 green), `WishlistSimilarityTest` (6/6 green): попперовская фальсификация сходства вишлистов и пресечение дубликатов (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `SettingsControllerIntegrationTest` (8/8 green), `SystemSettingsServiceTest` (13/13 green): неизменяемость системных настроек и деонтический барьер (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
       - `FlywayMigrationValidationTest` (4/4 green): верификация строгой миграционной схемы базы данных (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `ProductNamespaceLaw26Test` (16/16 green): демаркация продуктовых пространств имён по Закону 26 (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D013, D007, D004, D002, D014)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация схожести вишлистов (`WishlistServiceFalsificationTest`), миграционной схемы Flyway (`FlywayMigrationValidationTest`), требований компилятора (`TechnicalLeadCompilerFalsificationTest`) и заземления требований (`RequirementGroundingServiceFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции корпуса паттернов (`PhilosopherPatternCorpusConsistencyTest`), снимок персистентности тождества задач компилятора (`CompilerTaskIdentityTest`, V137) и надежная каузальная цепочка операционной правды (`OperationalTruthServiceTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при зелёных тестах (exclusionary reasons);**
  * Деонтические заслоны системных настроек (`SystemSettingsServiceTest`), безопасность наблюдателя (`InternalGeminiObserverSecurityIntegrationTest`) и демаркация пространств имён по Закону 26 (`ProductNamespaceLaw26Test`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркальное чтение сторожа TOC без скрытых побочных мутаций базы данных (`TocSentinelServiceTest`, `TocSentinelControllerTest`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status / Belnap]:**
  * 4-значная решётка истинности ценности Lean и целевого контекста задач (`LeanValueTest`, `TargetContextTest`).
- **`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Mereology / Shaffer & Varzi]:**
  * Монопольное владение восстановлением состояния упавших задач (`PlannedWorkRecoveryServiceTest`) и топология границ (`ProductNamespaceLaw26Test`).
- **`GILBERT_RAYL_02_CATEGORY_ERROR_SCAN` & `GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error / Ryle]:**
  * Сканирование категориальных ошибок генерации дубликатов во времени (`TaskDuplicateDetectorTest`) и разделение уровней абстракции стека репозиториев (`RepositoryStackAnalyzerTest`).
- **`DZHUDA_PERL_15_RAG_GROUNDING_CAPSULE` [D014 RAG hallucination / Pearl]:**
  * Строгое заземление сущностей и требований без галлюцинированных записей (`RequirementGroundingServiceTest`).

---

## 3. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон тестов Кластера Секций XIV, XV и XVI в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=PhilosopherPatternCorpusConsistencyTest,RequirementGroundingServiceTest,RequirementGroundingServiceFalsificationTest,TaskDuplicateDetectorTest,PlannedWorkRecoveryServiceTest,CompilerTaskIdentityTest,SemanticDuplicateVetoTest,TechnicalLeadCompilerIntegrationTest,TechnicalLeadCompilerFalsificationTest,TechnicalLeadCompilerTest,InternalGeminiObserverSecurityIntegrationTest,InternalGeminiObserverControllerTest,GeminiObserverActionServiceTest,TocSentinelServiceTest,TocSentinelControllerTest,LeanValueTest,TargetContextTest,RepositoryStackAnalyzerTest,OperationalTruthServiceTest,WishlistServiceFalsificationTest,WishlistContentSimilarityMatcherTest,WishlistSimilarityTest,SettingsControllerIntegrationTest,SystemSettingsServiceTest,FlywayMigrationValidationTest,ProductNamespaceLaw26Test
   ```
2. Подтвердить 100% green результат (**224/224 green в 26 тест-сьютах**).
3. Зафиксировать строго 0 строк изменений рабочего кода (`git status` чист по `src/main/java`, `src/main/resources`, `scripts/`).
4. Актуализировать отметки Ступени 4 по Кластеру Секций XIV, XV и XVI в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Сформировать лаконичный отчёт в `docs/reports/PROGRAMMER_STATE.md` (до 15–20 строк) и закоммитить: `feat(sections-xiv-xvi): complete Stage 4 batch verification (D008, D010, D006)`.
