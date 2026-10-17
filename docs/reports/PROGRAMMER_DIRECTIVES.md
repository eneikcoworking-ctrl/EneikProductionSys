# Директивы и наряд для L2 (2026-10-17 14:43 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый макро-цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XXX–XXXVI («Сайдкары и пограничные сервисы: ML, репозитории, пульт, наблюдатель, аккаунты, задачи») полностью подтверждён и закрыт инженером (коммит `7a1d813d`, 174/174 green в 22 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS 02:15 min, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает следующий шаг непрерывного макро-цикла фабрики — **Кластер Секций XXXVII, XXXVIII, XXXIX, XL, XLI, XLII, XLIII и XLIV («Сторож ТОС, авторизационная граница AI-ресурсов, метрики Six Sigma и операционная правда, выметающий обход зависших финализаций, заслоны экранов и верстки, кэш постоянного корпуса RAG, верёвка DBR и очистка журнала проектов»)** целиком (**143 теста в едином непрерывном пакетном прогоне в Docker Maven [15 тест-сьютов]**):
     * **Группа 1 — Секция XXXVII: Сторож ТОС: чтение, которое пересчитывает (2 сьюта, 20 тестов):**
       - `TocSentinelServiceTest` (18 тестов), `TocSentinelControllerTest` (2 теста): чистое чтение DBR-снимка `getDbrStatus()` без паразитной мутации графа, адаптивный триггер частоты (`[250ms, 10000ms]`), HTTP 429 при троттлинге (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `ALONZO_CHERCH_21_DERIVED_CUTOFF` / D008, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
     * **Группа 2 — Секция XXXVIII: Вход к ресурсам модели: изменяющие запросы под заслоном (2 сьюта, 18 тестов):**
       - `GoogleAiResourceControllerTest` (6 тестов), `GoogleAiResourceSecurityIntegrationTest` (12 тестов): деонтический барьер на мутирующие и расходные эндпоинты по API-ключу (401/403), институциональный аудит актора (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006);
     * **Группа 3 — Секция XXXIX: Метрики качества: два счёта одного слова, 388 против нуля (3 сьюта, 31 тест):**
       - `SixSigmaAuditServiceTest` (26 тестов), `QualityMetricsControllerTest` (3 теста), `QualityGateControllerTest` (2 теста): 4-значная решётка истинности проверок (`passed`, `failed`, `undetermined`, `not-applied`), разведение уровней проверки и задачи, защита от NPE при пустых предикатах (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
     * **Группа 4 — Секция XL: Выметающий обход зависших финализаций и ветки-сироты (2 сьюта, 21 тест):**
       - `StrandedFinalizingSweepServiceTest` (8 тестов), `BranchGarbageCollectorServiceTest` (13 тестов): атомарный CAS-релиз финализаций по временной метке `finalizingSince`, расчет динамического порога зависания, безопасная очистка неактивных веток (`ALVA_NOE_17_CAUSAL_PROCESS_TRACE` / D013, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
     * **Группа 5 — Секция XLI: Заслон качества экрана и геометрии верстки (3 сьюта, 33 теста):**
       - `DesignExcellenceGateTest` (12 тестов), `VerificationEvidenceGateTest` (8 тестов), `LayoutGeometryAuditServiceTest` (13 тестов): телеосемантическая сверка физических экранов и DOM-геометрии, запрет самоотчетов UI, выявление перекрытий и микро-шрифтов (`ENDI_KLARK_02_GROUPING_PROXIMITY_GATE` / D011, `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
     * **Группа 6 — Секция XLII: Кэш постоянного корпуса: два механизма на одно дело, работает один (1 сьют, 3 теста):**
       - `SystemStatusControllerTest` (3 теста): подтверждение снятия дублирующего бэкенд-кэша `GeminiContextCacheManager` (0 байт), изоляция сайдкар-кэша (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
     * **Группа 7 — Секция XLIII: Верёвка: ограничитель выпуска, который не может сработать (1 сьют, 7 тестов):**
       - `TocOptimizerTest` (7 тестов): ограничение выпуска DBR-верёвки, субординация рычагов ТОС, допуск задач по емкости аккаунтов (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
     * **Группа 8 — Секция XLIV: Очистка журнала проекта: предел настоящий, частота выведена из роста (1 сьют, 10 тестов):**
       - `ProjectEventLogRetentionServiceTest` (10 тестов): потолковое удержание записей журнала проекта, однострочная выборка границы `PageRequest.of(excess - 1, 1)` для защиты памяти кучи, сохранение журналов непринятых проектов (`ALONZO_CHERCH_21_DERIVED_CUTOFF` / D010, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и моделей (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D013, D011, D004)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификационная оснастка: эмпирическая проверка заслонов качества экранов (`DesignExcellenceGateTest`), свидетельств верификации (`VerificationEvidenceGateTest`), оптимизатора ТОС (`TocOptimizerTest`), гейта Six Sigma DPMO (`SixSigmaAuditServiceTest`), выметающего обхода финализаций (`StrandedFinalizingSweepServiceTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` & `ALONZO_CHERCH_21_DERIVED_CUTOFF` [D010 Data lineage loss / Goldman & Church]:**
  * Лок уровней абстракции и деривативный срез: строгое разделение уровней проверок (check-level) и задач (task-level) в метриках Six Sigma, отсечение памяти кучи при ротации журналов через `PageRequest.of(excess - 1, 1)`, динамический расчет лизинга финализаций по выборкам `FINALIZING_DURATION`.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтический заслон на эндпоинты ресурсов Google AI (401/403), институциональная защита от несанкционированного сброса финализаций.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth table / Belnap]:**
  * 4-значная решётка истинности (`passed`, `failed`, `undetermined`, `not-applied`) для метрик качества, исключающая фантомные 388 сбоев при пустых выборках.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` & `ALVA_NOE_17_CAUSAL_PROCESS_TRACE` [D013 Runtime drift / Wittgenstein & Noë]:**
  * Анти-зеркальная телеметрия: `getDbrStatus()` как чистая функция без паразитных мутаций графа TOC; каузальный след переходов статусов финализации в БД.
- **`ENDI_KLARK_02_GROUPING_PROXIMITY_GATE` & `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Sensor-motor disconnection / Clark & Dretske]:**
  * Заслон визуальной группировки и телеосемантическая сверка физических экранов против самоотчетов; подтверждение отсутствия бэкенд-кэша RAG при наличии сайдкара.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D004 Part-whole confusion & D006 Boundary topology / Varzi]:**
  * Топология границ и владение частью-целым: изоляция графа ТОС, четкая граница между бэкендом и сайдкаром.

---

## 3. Регламент исполнения такта инженером L2

1. Проверить наряд в настоящем файле `docs/reports/PROGRAMMER_DIRECTIVES.md`.
2. Запустить непрерывный пакетный прогон в Docker-контейнере Maven с ограничением памяти `-m 2g`:
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=TocSentinelServiceTest,TocSentinelControllerTest,GoogleAiResourceControllerTest,GoogleAiResourceSecurityIntegrationTest,SixSigmaAuditServiceTest,QualityMetricsControllerTest,QualityGateControllerTest,StrandedFinalizingSweepServiceTest,BranchGarbageCollectorServiceTest,DesignExcellenceGateTest,VerificationEvidenceGateTest,LayoutGeometryAuditServiceTest,SystemStatusControllerTest,TocOptimizerTest,ProjectEventLogRetentionServiceTest
   ```
3. Убедиться в соблюдении инварианта Ступени 4: **СТРОГО 0 СТРОК ИЗМЕНЕНИЙ РАБОЧЕГО КОДА** (`git status --porcelain`).
4. Зафиксировать результаты в `docs/reports/PROGRAMMER_STATE.md` (до 15–20 строк).
5. Сделать коммит: `feat(sections-xxxvii-xliv): complete Stage 4 batch verification (D008, D010, D006)`.
