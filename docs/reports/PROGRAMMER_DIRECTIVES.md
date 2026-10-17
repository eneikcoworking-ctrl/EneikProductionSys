# Директивы и наряд для L2 (2026-10-17 10:03 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Предыдущий стратегический Кластер Секций XXX–XXXVI полностью заверен и закрыт инженером (коммит `efadef93`, 174/174 green в 22 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующей стратегической секции по оси потока фабрики — **Кластер Секций XXXVII–XLIV («Сторож ТОС», «Вход к ресурсам модели», «Метрики качества: два счёта одного слова», «Выметающий обход: восстановление», «Заслон качества экрана: проверка предмета», «Кэш постоянного корпуса», «Верёвка: ограничитель выпуска», «Очистка журнала проекта» и завершающий баланс фабрики)** целиком (**143 теста в едином непрерывном пакетном прогоне в Docker Maven [15 тест-сьютов]**):
     * **Секция XXXVII — Сторож ТОС: чтение, которое пересчитывает (20 тестов, 2 сьюта):**
       - `TocSentinelServiceTest` (18), `TocSentinelControllerTest` (2): чистые чтения `getDbrStatus()` без паразитной мутации графа, динамический триггер сторожа (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `ALONZO_CHERCH_21_DERIVED_CUTOFF` / D010);
     * **Секция XXXVIII — Вход к ресурсам модели: изменяющие запросы под заслоном (18 тестов, 2 сьюта):**
       - `GoogleAiResourceControllerTest` (6), `GoogleAiResourceSecurityIntegrationTest` (12): деонтический барьер (401/403) на мутирующие эндпоинты AI-ресурсов, институциональный аудит актора (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
     * **Секция XXXIX — Метрики качества: два счёта одного слова, 388 против нуля (31 тест, 3 сьюта):**
       - `SixSigmaAuditServiceTest` (26), `QualityMetricsControllerTest` (3), `QualityGateControllerTest` (2): 4-значная логика истинности Тарского и Белнапа (passed, failed, undetermined, not-applied), ликвидация дефекта «388 ложных сбоев» разделением уровней проверки и задачи, расчет Six Sigma DPMO (`ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
     * **Секция XL — Выметающий обход: восстановление, которому не нужен держатель (21 тест, 2 сьюта):**
       - `StrandedFinalizingSweepServiceTest` (8), `BranchGarbageCollectorServiceTest` (13): атомарный CAS-релиз брошенных финализаций `finalizing -> pending` по метке `finalizingSince`, динамический лизинг по выборкам `FINALIZING_DURATION` (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `ALONZO_CHERCH_21_DERIVED_CUTOFF` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
     * **Секция XLI — Заслон качества экрана: проверка предмета, а не отчёта о нём (33 теста, 3 сьюта):**
       - `DesignExcellenceGateTest` (12), `VerificationEvidenceGateTest` (8), `LayoutGeometryAuditServiceTest` (13): проверка физических скриншотов PR (`desktop-1440.png`, `mobile-375.png`), Gestalt-близость (< 1.0), отсутствие коллизий bounding box (`ENDI_KLARK_02_GROUPING_PROXIMITY_GATE` / D011, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
     * **Секция XLII — Кэш постоянного корпуса: два механизма на одно дело, работает один (3 теста, 1 сьют):**
       - `SystemStatusControllerTest` (3): подтверждение снятия дублирующего бэкенд-кэша `GeminiContextCacheManager` (0 байт), изоляция сайдкар-кэша (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
     * **Секция XLIII — Верёвка: ограничитель выпуска, который не может сработать (7 тестов, 1 сьют):**
       - `TocOptimizerTest` (7): ограничение выпуска DBR-верёвки, субординация рычагов ТОС, допуск задач по емкости аккаунтов (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
     * **Секция XLIV и XVIII — Очистка журнала проекта и завершающий баланс фабрики (10 тестов, 1 сьют):**
       - `ProjectEventLogRetentionServiceTest` (10): потолковое удержание записей журнала проекта, однострочная выборка границы `PageRequest.of(excess - 1, 1)` для защиты памяти кучи, сохранение журналов непринятых проектов (`ALONZO_CHERCH_21_DERIVED_CUTOFF` / D010, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и моделей (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D013, D011, D004)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификационная оснастка: эмпирическая проверка заслонов качества экранов (`DesignExcellenceGateTest`), свидетельств верификации (`VerificationEvidenceGateTest`), оптимизатора ТОС (`TocOptimizerTest`) и DPMO Six Sigma (`SixSigmaAuditServiceTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` & `ALONZO_CHERCH_21_DERIVED_CUTOFF` [D010 Data lineage loss / Goldman & Church]:**
  * Лок уровней абстракции и деривативный срез: разделение проверок и задач в метриках Six Sigma, отсечение памяти кучи при ротации журналов через `PageRequest.of(excess - 1, 1)`, динамический расчет лизинга финализаций.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтический барьер (401/403) на все мутирующие пути AI-ресурсов (`GoogleAiResourceSecurityIntegrationTest`), топологическая защита границ и институциональный аудит актора.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` & `ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` [D012 Policy contradiction / Belnap & Tarski]:**
  * 4-значная решётка истинности исходов качества (passed, failed, undetermined, not-applied), исключающая категориальные ошибки подсчета метрик.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` & `ALVA_NOE_17_CAUSAL_PROCESS_TRACE` [D013 Runtime drift / Wittgenstein & Noë]:**
  * Чистое чтение DBR-статуса верёвки без паразитных мутаций при опросе, чистые GET-контракты контроллеров.
- **`ENDI_KLARK_02_GROUPING_PROXIMITY_GATE` & `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Perception failure / Clark & Dretske]:**
  * Верификация реальных экранных артефактов и геометрии PR (Gestalt-близость < 1.0, отсутствие коллизий bounding box), подтверждение вывода из эксплуатации дублирующего бэкенд-кэша.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Concurrency conflict / Varzi]:**
  * Атомарный CAS-релиз брошенных финализаций, инкапсуляция графа TOC.

---

## 3. Протокол исполнения для L2

1. Провести единый непрерывный пакетный прогон 15 тест-сьютов Кластера Секций XXXVII–XLIV в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -m 2g -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=TocSentinelServiceTest,TocSentinelControllerTest,GoogleAiResourceControllerTest,GoogleAiResourceSecurityIntegrationTest,SixSigmaAuditServiceTest,QualityMetricsControllerTest,QualityGateControllerTest,StrandedFinalizingSweepServiceTest,BranchGarbageCollectorServiceTest,DesignExcellenceGateTest,VerificationEvidenceGateTest,LayoutGeometryAuditServiceTest,TocOptimizerTest,ProjectEventLogRetentionServiceTest,SystemStatusControllerTest
   ```
2. Убедиться в достижении целевого результата: **143/143 green** (0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
3. Проверить инвариант: **строго 0 строк изменений рабочего кода фабрики**.
4. Актуализировать строки `*Живое:*` и итоговые блоки Секций XXXVII–XLIV в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Сделать семантический коммит:
   ```bash
   git commit -m "feat(sections-xxxvii-xliv): complete Stage 4 batch verification (D008, D010, D006)"
   ```
6. Обновить `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
