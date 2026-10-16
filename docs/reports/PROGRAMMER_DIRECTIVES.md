# Директивы и наряд для L2 (2026-10-16 15:44 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секции XXX–XXXVI («Клиент к предсказателю, Разбор чужого репозитория, Завод репозиториев, Пульт управления, Внутренний вход наблюдателя, Выбор аккаунта и Хранилище задач») успешно заверены инженером (коммит `e7d84c7`, 136/136 green, 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического сектора фабрики — **Секций XXXVII–XLIV и XVIII («Финальный контур TOC, авторизация AI-ресурсов, метрики Six Sigma, выметающие обходы, заслоны экранов, кэш ML-сайдкара, верёвка DBR, очистка журналов и завершающий баланс фабрики»)** целиком (17 тест-сьютов, 143 теста в едином Docker Maven прогоне):
     * **Группа 1: Секция XXXVII — Сторож TOC (`TocSentinelService` & `TocSentinelController`, 20 тестов):**
       - `TocSentinelServiceTest` (18/18), `TocSentinelControllerTest` (2/2): чистые чтения снимков без мутации состояния узлов и флагов ограничений (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013), адаптивный динамический trigger частоты сторожа (`ALONZO_CHERCH_21_DERIVED_CUTOFF` / D008), инкапсуляция монопольного владения графом (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
     * **Группа 2: Секция XXXVIII — Вход к ресурсам модели (`GoogleAiResourceController` & Security, 18 тестов):**
       - `GoogleAiResourceControllerTest` (6/6), `GoogleAiResourceSecurityIntegrationTest` (12/12): деонтический отказ 401 UNAUTHORIZED при отсутствии ключа, 403 FORBIDDEN при неверном ключе/токене, допуск 200 OK только при валидном ключе оператора, институциональный аудит актора и нормализация путей медиа (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006);
     * **Группа 3: Секция XXXIX — Метрики качества и Six Sigma (`SixSigmaAuditService`, `QualityMetricsController`, `QualityGateController`, 31 тест):**
       - `SixSigmaAuditServiceTest` (26/26), `QualityMetricsControllerTest` (3/3), `QualityGateControllerTest` (2/2): устранение дефекта «388 ложных сбоев против нуля», 4-значная таблица истинности (passed, failed, undetermined, unapplied), запрет трактовки непримененных проверок как дефектов (`ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` / D012, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
     * **Группа 4: Секция XL — Выметающий обход и сборка мусора веток (`StrandedFinalizingSweepService` & `BranchGarbageCollectorService`, 21 тест):**
       - `StrandedFinalizingSweepServiceTest` (8/8), `BranchGarbageCollectorServiceTest` (13/13): безопасное освобождение брошенных финализаций через атомарный CAS `compareAndSetStatus(finalizing -> pending)` по таймстемпу лизинга `finalizingSince`, динамический расчет лизинга, сборка мусора веток (`ALVA_NOE_17_CAUSAL_PROCESS_TRACE` / D013, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
     * **Группа 5: Секция XLI — Заслон качества экрана и геометрия (`DesignExcellenceGate`, `VerificationEvidenceGate`, `LayoutGeometryAuditService`, 33 теста):**
       - `DesignExcellenceGateTest` (12/12), `VerificationEvidenceGateTest` (8/8), `LayoutGeometryAuditServiceTest` (13/13): заслон качества на основе реальных физических артефактов из PR (скриншоты, markup, `layout-check.json`), детекция коллизий, ограничений масштабирования вьюпорта и пропорций близости Гештальта (`ENDI_KLARK_02_GROUPING_PROXIMITY_GATE` / D011, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
     * **Группа 6: Секция XLII — Кэш постоянного корпуса (`GeminiContextCacheManager` & Sidecar Cache):**
       - `SystemStatusControllerTest` (3/3), `GeminiContextServiceTest` (28/28): ликвидация дублирующего бэкенд-кэша Java (`GeminiContextCacheManager` decommissioned), 0 упоминаний `cachedContents` в кодовой базе Java, единый кэш в сайдкаре `PredictionService.py` (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `ALONZO_CHERCH_03_LAMBDA_CORE_REDUCTION` / D008);
     * **Группа 7: Секция XLIII — Верёвка TOC (`TocOptimizer`, 7 тестов):**
       - `TocOptimizerTest` (7/7): честный статус `Flow unmeasured: single instrumented stage...` при отсутствии этапов или единственном этапе (защита от ложного зеленого "optimal"), управляемый буфер емкости и приоритетный bypass (>= 80) (`ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008, `ALONZO_CHERCH_21_DERIVED_CUTOFF` / D008);
     * **Группа 8: Секция XLIV — Очистка журнала проекта (`ProjectEventLogRetentionService`, 13 тестов):**
       - `ProjectEventLogRetentionServiceTest` (10/10), `SystemStatusControllerTest` (3/3): частый fixedDelay-контур очистки вместо суточного cron, однострочный `PageRequest.of(excess - 1, 1)` для исключения риска переполнения кучи, неприкосновенность непринятых и замороженных проектов (`ALONZO_CHERCH_21_DERIVED_CUTOFF` / D010, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `AYZEK_LEVI_10_DEFEASIBLE_EXCEPTION_LEDGER` / D007);
     * **Группа 9: Секция XVIII — Чего в этом перечне нет:**
       - Демаркация границ фронтенда и производственного суверенитета: соблюдение догмата разделения станка и детали.
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D012, D013, D011, D007)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация: запрет ложного оптимума в верёвке TOC (`Flow unmeasured` при <= 1 этапе) и фальсификационные заслоны гейта экранного качества на физических артефактах (DOM/CSS геометрия, скриншоты).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и надежная цепочка вывода: 4-значное разделение метрик качества, однострочная выборка границы очистки журнала без переполнения кучи JVM, каузальная трассировка лизинга финализаций.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтический заслон изменяющих операций с AI-ресурсами (отказ 401/403) и институциональный аудит вызывающего.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D004, D006 Boundary topology / Varzi]:**
  * Монопольное владение счетчиками графа TOC, топологическая изоляция путей медиа и безопасное CAS-освобождение зависших финализаций.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` & `ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` [D012 Truth status table / Belnap & Tarski]:**
  * Многозначная логика истинности в отчетах качества: ликвидация дефекта 388 ложных сбоев путем строгого разграничения passed, failed, undetermined и unapplied.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` & `ALVA_NOE_17_CAUSAL_PROCESS_TRACE` [D013 Runtime drift / Wittgenstein & Noë]:**
  * Анти-зеркало рантайма: чтения DBR не мутируют узлы и граф; каузальный след процессов в рантайме при выметании брошенных сессий.
- **`ENDI_KLARK_02_GROUPING_PROXIMITY_GATE` & `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Perception failure / Clark & Dretske]:**
  * Заслон близости Гештальта и геометрии экранов; единый кэш провайдера в сайдкаре с телеосемантической связью потребителя.
- **`AYZEK_LEVI_10_DEFEASIBLE_EXCEPTION_LEDGER` [D007 Institutional defect record / Levi]:**
  * Иммунитет непринятых и замороженных проектов от возрастного удаления в журнале событий.

---

## 3. Регламент отчёта и фиксации для L2

1. Провести единый непрерывный пакетный прогон тестов Секций XXXVII–XLIV в Docker-контейнере Maven 3.9.9 Temurin-21 (`-m 2g`):
   `mvn test -Dtest=TocSentinelServiceTest,TocSentinelControllerTest,GoogleAiResourceControllerTest,GoogleAiResourceSecurityIntegrationTest,SixSigmaAuditServiceTest,QualityMetricsControllerTest,QualityGateControllerTest,StrandedFinalizingSweepServiceTest,BranchGarbageCollectorServiceTest,DesignExcellenceGateTest,VerificationEvidenceGateTest,LayoutGeometryAuditServiceTest,TocOptimizerTest,ProjectEventLogRetentionServiceTest,SystemStatusControllerTest`
2. Зафиксировать результат (143/143 green) и актуализировать строки `*Живое:*` в `docs/FACTORY_MECHANISMS.md` и статус в `docs/ANTIGRAVITY_QUEUE.md`.
3. Составить краткий отчёт в `docs/reports/PROGRAMMER_STATE.md` (до 20 строк) со ссылкой на RAG-корпус.
4. Сделать семантический коммит в репозиторий фабрики:
   `feat(sections-xxxvii-xliv): complete Stage 4 batch verification (D008, D010, D006)`.
