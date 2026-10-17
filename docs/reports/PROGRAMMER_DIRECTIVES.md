# Директивы и наряд для L2 (2026-10-17 08:18 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XXX–XXXVI («Внешние клиенты, наблюдатель, разбор чужого репозитория, пульт готовности, выбор аккаунта, хранилище задач и Законы 1, 14, 20») полностью заверен инженером (коммит `7d793dd2`, 174/174 green в 22 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующей стратегической секции по оси потока фабрики — **Кластер Секций XXXVII, XXXVIII, XXXIX, XL, XLI, XLII, XLIII и XLIV («Сторож ТОС, авторизационная граница AI-ресурсов, метрики качества и операционная правда, выметающий обход зависших финализаций, заслон качества экрана и геометрии верстки, кэш постоянного корпуса RAG, верёвка DBR и очистка журнала проектов»)** целиком (**251 тест в едином непрерывном пакетном прогоне в Docker Maven [27 тест-сьютов]**):
     * **Группа 1: Секция XXXVII — Сторож ТОС: чтение, которое пересчитывает (44 теста, 7 сьютов):**
       - `TocSentinelServiceTest` (18 тестов), `TocSentinelControllerTest` (2 теста), `TocOptimizerTest` (7 тестов), `TocPersistenceSnapshotTest` (4 теста), `TocWelfordOnlineVarianceFalsificationTest` (5 тестов), `TocPipelinePhasesInstrumentationTest` (4 теста), `TocNodeTimeoutTest` (4 теста): анти-зеркальное чтение статуса верёвки ТОС `getDbrStatus()` без паразитной мутации наблюдаемого графа при опросе, пересчет только в `periodicWatchdog()` или `refreshDbrStatus()`, фасадные методы вместо утечки мутабельного графа, вывод cadence из наблюдаемой длительности шагов, снимки персистентности графа и онлайн-дисперсия Велфорда (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `ALONZO_CHERCH_21_DERIVED_CUTOFF` / D010);
     * **Группа 2: Секция XXXVIII — Вход к ресурсам модели: изменяющие запросы под заслоном (18 тестов, 2 сьюта):**
       - `GoogleAiResourceControllerTest` (6 тестов), `GoogleAiResourceSecurityIntegrationTest` (12 тестов): деонтический запрет мутирующих AI-операций (`/design-drafts-cleanup`, `/probe-models`, `/design-assets`, `/stitch-design-system`, `/video-assets`) без валидного API-ключа даже с loopback, fail-closed при отсутствии ключа, безопасные GET-эндпоинты остаются чистым чтением, институциональный аудит актора (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
     * **Группа 3: Секция XXXIX — Метрики качества: два счёта одного слова, 388 против нуля (46 тестов, 3 сьюта):**
       - `QualityMetricsControllerTest` (3 теста), `SixSigmaAuditServiceTest` (26 тестов), `OperationalTruthServiceTest` (17 тестов): строгое разделение уровней абстракции метрик (уровень проверок `checks`, уровень задач `TaskEntity` и уровень фабрики/проекта), таблица истинности Тьюринга/Тарского для статусов качества (`passed`, `failed`, `undetermined`, `unapplied`), операционная правда фабрики с асимметричным ростом доверия, защита от подмены дефекта непримененной проверкой (`ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
     * **Группа 4: Секция XL — Выметающий обход: восстановление, которому не нужен держатель (19 тестов, 3 сьюта):**
       - `StrandedFinalizingSweepServiceTest` (8 тестов), `StrandedFinalizingSweepServiceFalsificationTest` (6 тестов), `StrandedFinalizingDynamicTimeoutFalsificationTest` (5 тестов): `finalizing` — временный заслон завершения, а не очередь; каузальный след восстановления по часам `finalizingSince` через атомарный CAS без необходимости знать мертвого воркера; динамический таймаут лизинга по валидным сэмплам `FINALIZING_DURATION` (`ALVA_NOE_17_CAUSAL_PROCESS_TRACE` / D013, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
     * **Группа 5: Секция XLI — Заслон качества экрана: проверка предмета, а не отчёта о нём (31 тест, 3 сьюта):**
       - `DesignExcellenceGateTest` (12 тестов), `DesignExcellenceGateFalsificationTest` (6 тестов), `LayoutGeometryAuditServiceTest` (13 тестов): проверка физического артефакта из PR (`desktop-1440.png`, `mobile-375.png`, `layout-check.json`), а не нарратива исполнителя; Gestalt-близость (< 1.0), отсутствие коллизий bounding box, запрет отключения viewport zoom; `CANNOT_JUDGE` при отсутствии геометрии с нулевым баллом адаптивности (`ENDI_KLARK_02_GROUPING_PROXIMITY_GATE` / D011, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
     * **Группа 6: Секция XLII — Кэш постоянного корпуса: два механизма на одно дело, работает один (58 тестов, 4 сьюта):**
       - `SystemStatusControllerTest` (3 теста), `SystemStatusControllerIntegrationTest` (5 тестов), `SystemStatusServiceTest` (22 теста), `GeminiContextServiceTest` (28 тестов): Java-бэкенд не создает неиспользуемые `cachedContents`; создание кэша провайдера вынесено в ML-сайдкар; кэшируются только статические системные инструкции, контекст звонка передается в prompt; изоляция удаленного `GeminiContextCacheManager` (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` / D014);
     * **Группа 7: Секция XLIII — Верёвка: ограничитель выпуска, который не может сработать (25 тестов, 4 сьюта):**
       - `TocSubordinationTruthTest` (5 тестов), `TocSubordinationLeverTest` (12 тестов), `JulesDispatchServiceTocDbrAdmissionTest` (4 теста), `ProjectFlowServiceDbrRopeTest` (4 теста): DBR-верёвка душит выпуск работы (`dispatchQueuedTasks`) при переполнении буфера ограничения внешней ёмкости Jules-аккаунтов; ноль срабатываний ограничителя эксплицитно фиксируется как `UNVERIFIED`; приоритет >= 80 обходит верёвку с логированием байпаса (`ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008, `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011, `ALONZO_CHERCH_21_DERIVED_CUTOFF` / D010);
     * **Группа 8: Секция XLIV — Очистка журнала проекта: предел настоящий, частота выведена из роста (10 тестов, 1 сьют):**
       - `ProjectEventLogRetentionServiceTest` (10 тестов): защита от потери истории до приемки (`acceptedAt + grace`), незавершенные/замороженные проекты категорически не удаляются по возрасту; потолок записей на проект обрезается выборкой граничной строки `PageRequest.of(excess - 1, 1)` без выгрузки избыточных строк в кучу JVM (`ALONZO_CHERCH_21_DERIVED_CUTOFF` / D010, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `AYZEK_LEVI_10_DEFEASIBLE_EXCEPTION_LEDGER` / D008).
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

## 3. Критерий закрытия такта инженером L2

1. Прогон всех 27 тест-сьютов Кластера Секций XXXVII–XLIV в едином пакетном запуске внутри контейнера Docker Maven 3.9.9 Temurin-21 (`-m 2g`): результат **251/251 green** (0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
2. Строго **0 строк изменений рабочего кода** (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`).
3. Фиксация результата в `docs/reports/PROGRAMMER_STATE.md` (до 20 строк) и семантический коммит в репозиторий фабрики.
