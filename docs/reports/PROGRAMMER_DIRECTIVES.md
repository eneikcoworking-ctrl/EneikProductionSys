# Директивы и наряд для L2 (2026-10-17 11:16 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XXX–XXXVI («Сайдкары и пограничные сервисы фабрики: клиент ML-предсказателя, разбор чужого репозитория, завод репозиториев, пульт управления готовностью, внутренний вход наблюдателя, выбор аккаунта, хранилище задач») полностью заверен и закрыт инженером (коммит `e36f8243`, 174/174 green в 22 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS 02:02 min, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующей стратегической секции по оси потока фабрики — **Кластер Секций XXXVII–XLIV («Сторож ТОС, вход к ресурсам модели, метрики качества Six Sigma, выметающий обход брошенных финализаций, заслон качества экранов и геометрии, изоляция кэша корпуса, ограничитель выпуска DBR-верёвки, очистка журнала проекта и завершающий архитектурный баланс»)** целиком (**143 теста в едином непрерывном пакетном прогоне в Docker Maven [15 тест-сьютов]**):
     * **Секция XXXVII — Сторож ТОС: чтение, которое пересчитывает (2 сьюта, 20 тестов):**
       - `TocSentinelServiceTest` (18 тестов), `TocSentinelControllerTest` (2 теста): чистые чтения `getDbrStatus()` без паразитной мутации графа, динамический триггер сторожа (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `ALONZO_CHERCH_21_DERIVED_CUTOFF` / D010);
     * **Секция XXXVIII — Вход к ресурсам модели: изменяющие запросы под заслоном (2 сьюта, 18 тестов):**
       - `GoogleAiResourceControllerTest` (6 тестов), `GoogleAiResourceSecurityIntegrationTest` (12 тестов): деонтический барьер (401/403) на мутирующие эндпоинты AI-ресурсов, институциональный аудит актора (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
     * **Секция XXXIX — Метрики качества: два счёта одного слова, 388 против нуля (3 сьюта, 31 тест):**
       - `SixSigmaAuditServiceTest` (26 тестов), `QualityMetricsControllerTest` (3 теста), `QualityGateControllerTest` (2 теста): 4-значная логика истинности Тарского и Белнапа (`passed`, `failed`, `undetermined`, `not-applied`), ликвидация дефекта «388 ложных сбоев» разделением уровней проверки и задачи, расчет Six Sigma DPMO (`ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
     * **Секция XL — Выметающий обход: восстановление, которому не нужен держатель (2 сьюта, 21 тест):**
       - `StrandedFinalizingSweepServiceTest` (8 тестов), `BranchGarbageCollectorServiceTest` (13 тестов): атомарный CAS-релиз брошенных финализаций `finalizing -> pending` по метке `finalizingSince`, динамический лизинг по выборкам `FINALIZING_DURATION` (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `ALONZO_CHERCH_21_DERIVED_CUTOFF` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
     * **Секция XLI — Заслон качества экрана: проверка предмета, а не отчёта о нём (3 сьюта, 33 теста):**
       - `DesignExcellenceGateTest` (12 тестов), `VerificationEvidenceGateTest` (8 тестов), `LayoutGeometryAuditServiceTest` (13 тестов): проверка физических скриншотов PR (`desktop-1440.png`, `mobile-375.png`), Gestalt-близость (< 1.0), отсутствие коллизий bounding box (`ENDI_KLARK_02_GROUPING_PROXIMITY_GATE` / D011, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
     * **Секция XLII — Кэш постоянного корпуса: два механизма на одно дело, работает один (1 сьют, 3 теста):**
       - `SystemStatusControllerTest` (3 теста): подтверждение снятия дублирующего бэкенд-кэша `GeminiContextCacheManager` (0 байт), изоляция сайдкар-кэша (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
     * **Секция XLIII — Верёвка: ограничитель выпуска, который не может сработать (1 сьют, 7 тестов):**
       - `TocOptimizerTest` (7 тестов): ограничение выпуска DBR-верёвки, субординация рычагов ТОС, допуск задач по емкости аккаунтов (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
     * **Секция XLIV и XVIII — Очистка журнала проекта и завершающий баланс фабрики (1 сьют, 10 тестов):**
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
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Институциональная защита эндпоинтов GoogleAiResourceController от неавторизованных мутаций (401/403) и строгий аудит актора.
- **`ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` [D012 Truth status confusion / Tarski & Belnap]:**
  * Четырёхзначная логика истинности гейтов качества (`passed`, `failed`, `undetermined`, `not-applied`) без подмены понятий.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Чистые чтения состояния DBR сторожа TOC без паразитной мутации графа и искажения телеметрии рантайма.
- **`ENDI_KLARK_02_GROUPING_PROXIMITY_GATE` & `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Verification bypass / Clark & Dretske]:**
  * Заслон визуальной группировки элементов UI и ликвидация дублирующего неиспользуемого бэкенд-кэша.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Coupling creep / Varzi]:**
  * Изоляция владения ресурсами, инкапсуляция графа TOC и разделение ответственности выметающих сервисов.

---

## 3. Команда исполнения для L2 (единый непрерывный пакетный прогон)

```bash
docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test \
  -Dtest=TocSentinelServiceTest,\
TocSentinelControllerTest,\
GoogleAiResourceControllerTest,\
GoogleAiResourceSecurityIntegrationTest,\
SixSigmaAuditServiceTest,\
QualityMetricsControllerTest,\
QualityGateControllerTest,\
StrandedFinalizingSweepServiceTest,\
BranchGarbageCollectorServiceTest,\
DesignExcellenceGateTest,\
VerificationEvidenceGateTest,\
LayoutGeometryAuditServiceTest,\
SystemStatusControllerTest,\
TocOptimizerTest,\
ProjectEventLogRetentionServiceTest
```

---

## 4. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон 15 тест-сьютов Кластера Секций XXXVII–XLIV (143 теста) в Docker Maven 3.9.9 Temurin-21 (`-m 2g`).
2. Зафиксировать 100% green (**143/143 green**, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
3. Обеспечить строго **0 строк изменений** рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`).
4. Актуализировать строки `*Живое:*` и итоговый блок Кластера Секций XXXVII–XLIV в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Зафиксировать результат коммитом:
   ```bash
   git commit -m "feat(sections-xxxvii-xliv): complete Stage 4 batch verification (D008, D010, D006)"
   ```
6. Обновить `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
