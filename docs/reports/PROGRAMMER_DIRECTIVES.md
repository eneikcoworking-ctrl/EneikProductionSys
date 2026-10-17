# Директивы и наряд для L2 (2026-10-17 08:55 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XXX–XXXVI («Внешние клиенты, наблюдатель, разбор чужого репозитория, пульт готовности, выбор аккаунта, хранилище задач») полностью заверен и закрыт инженером (коммит `cbdc45b7`, 174/174 green в 22 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующей стратегической секции по оси потока фабрики — **Кластер Секций XXXVII, XXXVIII, XXXIX, XL, XLI, XLII, XLIII и XLIV («Сторож ТОС, авторизационная граница AI-ресурсов, метрики качества и операционная правда, выметающий обход зависших финализаций, заслон качества экрана и геометрии верстки, кэш постоянного корпуса RAG, верёвка DBR и очистка журнала проектов»)** целиком (**251 тест в едином непрерывном пакетном прогоне в Docker Maven [27 тест-сьютов]**):
     * **Группа 1: Секция XXXVII — Сторож ТОС: чтение, которое пересчитывает (44 теста, 7 сьютов):**
       - `TocSentinelServiceTest` (18 тестов), `TocOptimizerTest` (7 тестов), `TocWelfordOnlineVarianceFalsificationTest` (5 тестов), `TocNodeTimeoutTest` (4 теста), `TocPipelinePhasesInstrumentationTest` (4 теста), `TocSentinelControllerTest` (2 теста), `TocPersistenceSnapshotTest` (4 теста): анти-зеркальное чтение статуса верёвки ТОС `getDbrStatus()` без паразитной мутации наблюдаемого графа при опросе, численный расчет Welford-дисперсии и фальсификационная защита фаз пайплайна (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
     * **Группа 2: Секция XXXVIII — Вход к ресурсам модели: изменяющие запросы под заслоном (18 тестов, 2 сьюта):**
       - `GoogleAiResourceControllerTest` (6 тестов), `GoogleAiResourceSecurityIntegrationTest` (12 тестов): деонтический барьер на мутирующие методы (POST/PUT/DELETE) с проверкой институционального актора, 401/403 заслон и аудит авторизации (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006);
     * **Группа 3: Секция XXXIX — Метрики качества: два счёта одного слова (46 тестов, 3 сьюта):**
       - `QualityMetricsControllerTest` (3 теста), `SixSigmaAuditServiceTest` (26 тестов), `OperationalTruthServiceTest` (17 тестов): строгое различение уровней абстракции проверки и задачи, честное отражение метрик рантайма и операционная правда без подмены понятий (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` / D012);
     * **Группа 4: Секция XL — Выметающий обход зависших финализаций (19 тестов, 3 сьюта):**
       - `StrandedFinalizingSweepServiceTest` (8 тестов), `StrandedFinalizingSweepServiceFalsificationTest` (6 тестов), `StrandedFinalizingDynamicTimeoutFalsificationTest` (5 тестов): попперовская фальсификация зависших финализаций, динамический таймаут и атомарный CAS-релиз без держателя (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `ALVA_NOE_17_CAUSAL_PROCESS_TRACE` / D013);
     * **Группа 5: Секция XLI — Заслон качества экрана и геометрии (31 тест, 3 сьюта):**
       - `DesignExcellenceGateTest` (12 тестов), `DesignExcellenceGateFalsificationTest` (6 тестов), `LayoutGeometryAuditServiceTest` (13 тестов): проверка физических артефактов верстки из PR, фальсификационный заслон и аудит физической геометрии вьюпорта (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ENDI_KLARK_02_GROUPING_PROXIMITY_GATE` / D011, `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011);
     * **Группа 6: Секция XLII — Кэш постоянного корпуса RAG (58 тестов, 4 сьюта):**
       - `SystemStatusControllerTest` (3 теста), `SystemStatusControllerIntegrationTest` (5 тестов), `SystemStatusServiceTest` (22 теста), `GeminiContextServiceTest` (28 тестов): надежность кэша контекста, изоляция проекций статуса и демаркация чтения (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` / D014);
     * **Группа 7: Секция XLIII — Верёвка TOC (25 тестов, 4 сьюта):**
       - `TocSubordinationTruthTest` (5 тестов), `TocSubordinationLeverTest` (12 тестов), `JulesDispatchServiceTocDbrAdmissionTest` (4 теста), `ProjectFlowServiceDbrRopeTest` (4 теста): DBR-верёвка с ограничением выпуска при переполнении буфера, попперовская фальсификация и телеосемантическая обратная связь (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011);
     * **Группа 8: Секция XLIV — Очистка журнала проекта (10 тестов, 1 сьют):**
       - `ProjectEventLogRetentionServiceTest` (10 тестов): защита истории проекта до приемки, обрезка избытка граничным оффсетом без выгрузки строк в кучу JVM (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и моделей (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D013, D011, D012, D004, D014)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификационная оснастка: фальсификация зависших финализаций (`StrandedFinalizingSweepServiceFalsificationTest`, `StrandedFinalizingDynamicTimeoutFalsificationTest`), фальсификация заслона экрана (`DesignExcellenceGateFalsificationTest`), фальсификация верёвки DBR (`ProjectFlowServiceDbrRopeTest`), фальсификация онлайн-дисперсии ТОС (`TocWelfordOnlineVarianceFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и цепочка надежности: четкое разделение метрик уровня проверок, уровня задач и уровня проекта в Six Sigma и Operational Truth; защита журнала проекта от потери данных до приемки; выборка границы одной строкой без переполнения кучи JVM; размер лизинга по выученным сэмплам длительности.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтический заслон 401/403 на мутирующие операции AI-ресурсов, институциональный аудит актора, топология безопасности эндпоинтов.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` & `ALVA_NOE_17_CAUSAL_PROCESS_TRACE` [D013 Runtime drift / Wittgenstein & Noë]:**
  * Чистое чтение DBR-статуса верёвки без паразитных мутаций при опросе (`TocSentinelServiceTest`), каузальный след восстановления зависших финализаций по часам (`StrandedFinalizingSweepServiceTest`).
- **`ENDI_KLARK_02_GROUPING_PROXIMITY_GATE` & `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Physical geometry & feedback / Clark & Dretske]:**
  * Проверка физических артефактов верстки из PR (картинки, коллизии, Gestalt-близость), телеосемантическая обратная связь верёвки TOC и кэша ML-сайдкара.
- **`ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` [D012 Truth value collapse / Tarski]:**
  * Решётка истинности статусов качества и операционной правды: дефект никогда не подменяется непримененной проверкой.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` [D004 / D014]:**
  * Атомарный CAS-релиз финализаций, инкапсуляция графа TOC и детерминированная капсула RAG-контекста.

---

## 3. Команда исполнения для L2 (единый непрерывный пакетный прогон)

```bash
docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=TocSentinelServiceTest,TocOptimizerTest,TocWelfordOnlineVarianceFalsificationTest,TocNodeTimeoutTest,TocPipelinePhasesInstrumentationTest,TocSentinelControllerTest,TocPersistenceSnapshotTest,GoogleAiResourceControllerTest,GoogleAiResourceSecurityIntegrationTest,QualityMetricsControllerTest,SixSigmaAuditServiceTest,OperationalTruthServiceTest,StrandedFinalizingSweepServiceTest,StrandedFinalizingSweepServiceFalsificationTest,StrandedFinalizingDynamicTimeoutFalsificationTest,DesignExcellenceGateTest,DesignExcellenceGateFalsificationTest,LayoutGeometryAuditServiceTest,SystemStatusControllerTest,SystemStatusControllerIntegrationTest,SystemStatusServiceTest,GeminiContextServiceTest,TocSubordinationTruthTest,TocSubordinationLeverTest,JulesDispatchServiceTocDbrAdmissionTest,ProjectFlowServiceDbrRopeTest,ProjectEventLogRetentionServiceTest
```

## 4. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон 27 тест-сьютов Кластера Секций XXXVII–XLIV (251 тест) в Docker Maven 3.9.9 Temurin-21 (`-m 2g`).
2. Зафиксировать 100% green (**251/251 green**, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
3. Обеспечить строго **0 строк изменений** рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`).
4. Актуализировать строки `*Живое:*` и итоговый блок Кластера Секций XXXVII–XLIV в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Зафиксировать результат коммитом: `feat(sections-xxxvii-xliv): complete Stage 4 batch verification (D008, D010, D006)` и кратко отразить в `docs/reports/PROGRAMMER_STATE.md`.
