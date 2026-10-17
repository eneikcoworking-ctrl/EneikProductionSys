# Директивы и наряд для L2 (2026-10-17 02:18 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XXX–XXXVI («Внешние клиенты, Разбор чужого репозитория, Завод репозиториев, Пульт управления, Внутренний вход наблюдателя, Выбор аккаунта и Хранилище задач») успешно заверен инженером (коммит `76ccbd17`, 174/174 green в 22 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующей стратегической секции фабрики — **Кластера Секций XXXVII, XXXVIII, XXXIX, XL, XLI, XLII, XLIII, XLIV и XVIII («Финальный контур TOC, авторизация AI-ресурсов, метрики Six Sigma, выметающие обходы, заслоны экранов, кэш корпуса, верёвка TOC, очистка журналов и завершающий баланс фабрики»)** целиком (**143 теста в едином непрерывном пакетном прогоне в Docker Maven [15 тест-сьютов]**):
     * **Группа 1: Секция XXXVII — Сторож TOC и адаптивный тайминг (20 тестов, 2 сьюта):**
       - `TocSentinelServiceTest` (18/18), `TocSentinelControllerTest` (2/2): адаптивный триггер частоты (`min(meanDurationMs) / 2`), расслабление до 10 с при простое, чистое чтение DBR-снимка сторожем без мутаций узлов (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `ALONZO_CHERCH_21_DERIVED_CUTOFF` / D008, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004).
     * **Группа 2: Секция XXXVIII — Авторизация и безопасность AI-ресурсов (18 тестов, 2 сьюта):**
       - `GoogleAiResourceControllerTest` (6/6), `GoogleAiResourceSecurityIntegrationTest` (12/12): деонтический запрет неавторизованного доступа к AI-ресурсам фабрики, строгая проверка полномочий и институциональный аудит акторов (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006).
     * **Группа 3: Секция XXXIX — Аудит Six Sigma и метрики качества (31 тест, 3 сьюта):**
       - `SixSigmaAuditServiceTest` (26/26), `QualityMetricsControllerTest` (3/3), `QualityGateControllerTest` (2/2): расчет DPMO и связности свидетельств, контрольные карты Шухарта, устранение расхождения счётов связности (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012).
     * **Группа 4: Секция XL — Выметающие обходы и сборка мусора (21 тест, 2 сьюта):**
       - `StrandedFinalizingSweepServiceTest` (8/8), `BranchGarbageCollectorServiceTest` (13/13): гарантированное завершение зависших финализаций задач и безопасная санитария временных git-веток без потери данных (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004).
     * **Группа 5: Секция XLI — Гейты совершенства дизайна и свидетельств верификации (33 теста, 3 сьюта):**
       - `DesignExcellenceGateTest` (12/12), `VerificationEvidenceGateTest` (8/8), `LayoutGeometryAuditServiceTest` (13/13): объективная проверка геометрии вьюпортов, заслоны совершенства дизайна и полноты свидетельств верификации (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `ENDI_KLARK_01_TELEOSEMANTIC_FEEDBACK` / D011).
     * **Группа 6: Секция XLII — Кэш корпуса Gemini (декоммиссионирован / inert):**
       - `GeminiContextCacheManager`: подтверждение снятия с эксплуатации устаревшего кэша Gemini (0 тестов, декоммиссионирован в пользу векторных проекций H2/Flyway).
     * **Группа 7: Секция XLIII — Оптимизатор TOC и DBR-верёвка (7 тестов, 1 сьют):**
       - `TocOptimizerTest` (7/7): расчет пропускной способности ограничений барабан-буфер-верёвка (DBR), балансировка потока задач и предотвращение локальных заторов (`ALONZO_CHERCH_03_LAMBDA_CORE_REDUCTION` / D008, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 8: Секция XLIV — Удержание журнала событий проекта (13 тестов, 2 сьюта):**
       - `ProjectEventLogRetentionServiceTest` (10/10), `SystemStatusControllerTest` (3/3): непрерывная очистка журнала событий проекта по минутному расписанию без подъема излишка в память JVM (`PageRequest.of(excess - 1, 1)` строго размер 1) (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006).
     * **Группа 9: Секция XVIII — Завершающий баланс фабрики:**
       - Институциональное равновесие и гармония всех 205 механизмов фабрики (идеальный баланс без дополнительных тестов).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D013, D011, D012, D004)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификационная оснастка: заслоны гейтов совершенства дизайна (`DesignExcellenceGateTest`), свидетельств верификации (`VerificationEvidenceGateTest`), аудита Six Sigma (`SixSigmaAuditServiceTest`) и оптимизатора TOC (`TocOptimizerTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и цепь надежности свидетельств: изоляция расчетов DPMO от побочных проекций, удержание журнала проекта без подъема записей в память JVM (`ProjectEventLogRetentionServiceTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_21_PENALTY_AS_ORDERING` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтический заслон неавторизованного доступа к ресурсам AI (`GoogleAiResourceSecurityIntegrationTest`), гарантированная зачистка зависших задач (`StrandedFinalizingSweepServiceTest`) и веток (`BranchGarbageCollectorServiceTest`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Чистое чтение DBR-снимка сторожем ТОС (`TocSentinelServiceTest`) без побочных эффектов мутации в БД.
- **`ENDI_KLARK_01_TELEOSEMANTIC_FEEDBACK` [D011 Teleosemantic drift / Clark]:**
  * Телеосемантическая обратная связь геометрии макетов (`LayoutGeometryAuditServiceTest`) и оценка точности компоновки.
- **`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` & `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D006 / D004 Boundary topology & Part-whole / Varzi]:**
  * Партитивное владение графом TOC и узлами ограничений, топологическая изоляция границ авторизации.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status / Belnap]:**
  * Многозначная решётка истинности статусов гейтов качества и контроллеров системы.

---

## 3. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон тестов Кластера Секций XXXVII–XLIV и XVIII в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -m 2g -v /root/.m2:/root/.m2 -v /opt/EneikProductionSys:/workspace -w /workspace maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=TocSentinelServiceTest,TocSentinelControllerTest,GoogleAiResourceControllerTest,GoogleAiResourceSecurityIntegrationTest,SixSigmaAuditServiceTest,QualityMetricsControllerTest,QualityGateControllerTest,StrandedFinalizingSweepServiceTest,BranchGarbageCollectorServiceTest,DesignExcellenceGateTest,VerificationEvidenceGateTest,LayoutGeometryAuditServiceTest,TocOptimizerTest,ProjectEventLogRetentionServiceTest,SystemStatusControllerTest
   ```
2. Подтвердить 100% green (**143/143 green, 15 тест-сьютов, 0 failures, 0 errors, BUILD SUCCESS**).
3. Обеспечить строго 0 строк изменений рабочего кода подсистем (`src/main/java`, `src/main/resources`, `scripts/`).
4. Актуализировать строки `*Живое:*` и итоговые блоки Кластера Секций XXXVII–XLIV и XVIII в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Зафиксировать краткий отчёт (15–20 строк) в `docs/reports/PROGRAMMER_STATE.md`.
6. Выполнить коммит: `feat(sections-xxxvii-xliv): complete Stage 4 batch verification (D008, D010, D006)`.
