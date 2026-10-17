# Директивы и наряд для L2 (2026-10-17 09:28 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XXX–XXXVI («Клиент к предсказателю», «Разбор чужого репозитория», «Завод репозиториев», «Пульт управления», «Внутренний вход наблюдателя», «Выбор аккаунта», «Хранилище задач») полностью заверен и закрыт инженером (коммит `937aa06e`, 174/174 green в 22 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующей стратегической секции по оси потока фабрики — **Кластер Секций XXXVII, XXXVIII, XXXIX, XL, XLI, XLII, XLIII и XLIV** («Сторож ТОС, авторизационная граница AI-ресурсов, метрики качества Six Sigma и операционная правда, выметающий обход зависших финализаций, заслон качества экрана и геометрии верстки, кэш постоянного корпуса RAG, верёвка DBR и очистка журнала проектов») целиком (**251 тест в едином непрерывном пакетном прогоне в Docker Maven [27 тест-сьютов]**):
     * **Секция XXXVII — Сторож ТОС: чтение, которое пересчитывает (44 теста, 7 сьютов):**
       - `TocSentinelServiceTest` (18), `TocSentinelControllerTest` (2), `TocOptimizerTest` (7), `TocPersistenceSnapshotTest` (4), `TocWelfordOnlineVarianceFalsificationTest` (5), `TocPipelinePhasesInstrumentationTest` (4), `TocNodeTimeoutTest` (4): чистые чтения `getDbrStatus()`, отсутствие мутации при чтении, адаптивный шедулинг частоты, снимки персистентности и онлайн-дисперсия Велфорда (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `ALONZO_CHERCH_21_DERIVED_CUTOFF` / D010);
     * **Секция XXXVIII — Вход к ресурсам модели: изменяющие запросы под заслоном (18 тестов, 2 сьюта):**
       - `GoogleAiResourceControllerTest` (6), `GoogleAiResourceSecurityIntegrationTest` (12): деонтический барьер на все 5 мутирующих путей AI-ресурсов (401/403), нормализация путей и институциональный аудит актора (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
     * **Секция XXXIX — Метрики качества: два счёта одного слова, 388 против нуля (46 тестов, 3 сьюта):**
       - `QualityMetricsControllerTest` (3), `SixSigmaAuditServiceTest` (26), `OperationalTruthServiceTest` (17): 4-значная логика истинности Тарского и Белнапа (`passed`, `failed`, `undetermined`, `not-applied`), полная ликвидация дефекта «388 ложных сбоев» разделением уровней абстракции проверки и задачи, операционная правда фабрики (`ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
     * **Секция XL — Выметающий обход: восстановление, которому не нужен держатель (19 тестов, 3 сьюта):**
       - `StrandedFinalizingSweepServiceTest` (8), `StrandedFinalizingSweepServiceFalsificationTest` (6), `StrandedFinalizingDynamicTimeoutFalsificationTest` (5): атомарный CAS-релиз брошенных финализаций `finalizing -> pending` по временной метке `finalizingSince`, динамический расчет лизинга по фактическим выборкам `FINALIZING_DURATION` (`ALVA_NOE_17_CAUSAL_PROCESS_TRACE` / D013, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
     * **Секция XLI — Заслон качества экрана: проверка предмета, а не отчёта о нём (31 тест, 3 сьюта):**
       - `DesignExcellenceGateTest` (12), `DesignExcellenceGateFalsificationTest` (6), `LayoutGeometryAuditServiceTest` (13): проверка реальных физических артефактов и геометрии PR (`desktop-1440.png`, `mobile-375.png`, `layout-check.json`), Gestalt-близость (< 1.0), отсутствие коллизий bounding box (`ENDI_KLARK_02_GROUPING_PROXIMITY_GATE` / D011, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
     * **Секция XLII — Кэш постоянного корпуса: два механизма на одно дело, работает один (58 тестов, 4 сьюта):**
       - `SystemStatusControllerTest` (3), `SystemStatusControllerIntegrationTest` (5), `SystemStatusServiceTest` (22), `GeminiContextServiceTest` (28): ликвидация дублирующего бэкенд-кэша `GeminiContextCacheManager` (снят/decommissioned / 0 байт), изоляция сайдкар-кэша и standing-knowledge RAG (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` / D014);
     * **Секция XLIII — Верёвка: ограничитель выпуска, который не может сработать (25 тестов, 4 сьюта):**
       - `TocSubordinationTruthTest` (5), `TocSubordinationLeverTest` (12), `JulesDispatchServiceTocDbrAdmissionTest` (4), `ProjectFlowServiceDbrRopeTest` (4): DBR-верёвка, ограничение выпуска работы при переполнении буфера, честный статус `Flow unmeasured`, приоритетный обход (`priority >= 80`) (`ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008, `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011, `ALONZO_CHERCH_21_DERIVED_CUTOFF` / D010);
     * **Секция XLIV — Очистка журнала проекта: предел настоящий, частота выведена из роста (10 тестов, 1 сьют):**
       - `ProjectEventLogRetentionServiceTest` (10): частая задержка `fixedDelay`, однострочная выборка границы `PageRequest.of(excess - 1, 1)` для защиты памяти кучи, сохранение журналов непринятых проектов и потолковое удержание записей (`ALONZO_CHERCH_21_DERIVED_CUTOFF` / D010, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `AYZEK_LEVI_10_DEFEASIBLE_EXCEPTION_LEDGER` / D008).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и моделей (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D013, D012, D011, D004, D014)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификационная оснастка: эмпирическая проверка заслонов зависших финализаций (`StrandedFinalizingSweepServiceFalsificationTest`), качества экрана (`DesignExcellenceGateFalsificationTest`), дисперсии ТОС (`TocWelfordOnlineVarianceFalsificationTest`) и отсечение ложно-зеленого "optimal" при пустом графе (`TocOptimizerTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Разделение уровней абстракции проверки и задачи (ликвидация "388 ложных сбоев" в `QualityMetricsControllerTest` и `SixSigmaAuditServiceTest`), однострочная выборка границы `PageRequest.of(excess - 1, 1)` для защиты памяти кучи (`ProjectEventLogRetentionServiceTest`), динамический расчет лизинга финализаций (`StrandedFinalizingDynamicTimeoutFalsificationTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` & `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D006 Authorization ambiguity & D004 Concurrency conflict / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтический заслон на все 5 мутирующих путей AI-ресурсов (401/403, `GoogleAiResourceSecurityIntegrationTest`), монопольное владение `inFlightCount` и графом ТОС (`TocSentinelService`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` & `ALVA_NOE_17_CAUSAL_PROCESS_TRACE` [D013 Runtime drift / Wittgenstein & Noë]:**
  * Чтение `getDbrStatus()` как чистый кэшированный снимок без скрытых мутаций графа при опросе; каузальный след восстановления брошенных финализаций `finalizing -> pending` по `finalizingSince`.
- **`ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` & `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Policy contradiction / Tarski & Belnap]:**
  * 4-значная решётка истинности качества (`passed`, `failed`, `undetermined`, `not-applied`) и операционная правда проекта.
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` & `ENDI_KLARK_02_GROUPING_PROXIMITY_GATE` [D011 Perception failure / Dretske & Clark]:**
  * Заслон качества экрана по реальным физическим артефактам (`desktop-1440.png`, `mobile-375.png`, `layout-check.json`), Gestalt-близость (< 1.0) и ликвидация мертвого дублирующего бэкенд-кэша (`GeminiContextCacheManager`).

---

## 3. Команда исполнения для L2 (единый непрерывный пакетный прогон)

```bash
docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test \
  -Dtest=TocSentinelServiceTest,\
TocSentinelControllerTest,\
TocOptimizerTest,\
TocPersistenceSnapshotTest,\
TocWelfordOnlineVarianceFalsificationTest,\
TocPipelinePhasesInstrumentationTest,\
TocNodeTimeoutTest,\
GoogleAiResourceControllerTest,\
GoogleAiResourceSecurityIntegrationTest,\
QualityMetricsControllerTest,\
SixSigmaAuditServiceTest,\
OperationalTruthServiceTest,\
StrandedFinalizingSweepServiceTest,\
StrandedFinalizingSweepServiceFalsificationTest,\
StrandedFinalizingDynamicTimeoutFalsificationTest,\
DesignExcellenceGateTest,\
DesignExcellenceGateFalsificationTest,\
LayoutGeometryAuditServiceTest,\
SystemStatusControllerTest,\
SystemStatusControllerIntegrationTest,\
SystemStatusServiceTest,\
GeminiContextServiceTest,\
TocSubordinationTruthTest,\
TocSubordinationLeverTest,\
JulesDispatchServiceTocDbrAdmissionTest,\
ProjectFlowServiceDbrRopeTest,\
ProjectEventLogRetentionServiceTest
```

## 4. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон 27 тест-сьютов Кластера Секций XXXVII–XLIV (251 тест) в Docker Maven 3.9.9 Temurin-21 (`-m 2g`).
2. Зафиксировать 100% green (**251/251 green**, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
3. Обеспечить строго **0 строк изменений** рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`).
4. Актуализировать строки `*Живое:*` и итоговый блок Кластера Секций XXXVII–XLIV в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Зафиксировать результат коммитом: `feat(sections-xxxvii-xliv): complete Stage 4 batch verification (D008, D010, D006)` и кратко отразить в `docs/reports/PROGRAMMER_STATE.md`.
