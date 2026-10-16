# Директивы и наряд для L2 (2026-10-16 14:15 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секции XXX–XXXVI («Клиент к предсказателю, Разбор чужого репозитория, Завод репозиториев, Пульт управления, Внутренний вход наблюдателя, Выбор аккаунта и Хранилище задач») полностью заверены инженером (коммит `1aa9046`, 136/136 green, 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического контура фабрики — **Секций XXXVII–XLIV и XVIII («Финальный контур TOC, авторизация AI-ресурсов, метрики Six Sigma, выметающие обходы, заслоны экранов, очистка журналов и завершающий баланс фабрики»)** целиком (17+ тест-сьютов, 143+ тестов в едином Docker Maven прогоне):
     * **Группа 1: Секция XXXVII — Сторож TOC (TocSentinelService, TocAnomalyDetector, TocSentinelController):**
       - `TocSentinelServiceTest` (18/18), `TocSentinelControllerTest` (2/2), `TocAnomalyDetectorTest` (7/7): чистое чтение состояния верёвки без паразитных записей (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013), адаптивная частота опроса сторожа по длительности шагов потока (`ALONZO_CHERCH_21_DERIVED_CUTOFF` / D010), инкапсуляция владения графом без утечек мутабельных структур (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
     * **Группа 2: Секция XXXVIII — Вход к ресурсам модели и медиа-активам (GoogleAiResourceController & VideoAssetService):**
       - `GoogleAiResourceControllerTest` (6/6), `GoogleAiResourceSecurityIntegrationTest` (12/12), `VideoAssetServiceTest` (6/6): проверка полномочий на всех мутирующих эндпоинтах модели (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006), валидация контента и изоляция генеративных черновиков;
     * **Группа 3: Секция XXXIX — Метрики качества и аудит Six Sigma (QualityMetricsController, QualityGateController, SixSigmaAuditService):**
       - `QualityMetricsControllerTest` (3/3), `QualityGateControllerTest` (2/2), `SixSigmaAuditServiceTest` (26/26): устранение категориальной ошибки «одно слово, два счёта» (388 vs 0), четкое разграничение трёх исходов: пройден, провален, не применялся (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012), защита от деления на ноль при пустом пуле задач;
     * **Группа 4: Секция XL — Выметающий обход и сборка мусора веток (StrandedFinalizingSweepService, BranchGarbageCollectorService):**
       - `StrandedFinalizingSweepServiceTest` (8/8), `BranchGarbageCollectorServiceTest` (6/6): выметание зависших сессий и возврат вишлистов в pending по истечении наблюдаемого лизинга (`ALVA_NOE_17_CAUSAL_PROCESS_TRACE` / D013), безопасное удаление мертвых веток без затрагивания активных веток разработки;
     * **Группа 5: Секция XLI — Заслон качества экрана и геометрия (DesignExcellenceGate, VerificationEvidenceGate, LayoutGeometryAuditService):**
       - `DesignExcellenceGateTest` (18/18), `VerificationEvidenceGateTest` (14/14), `LayoutGeometryAuditServiceTest` (5/5): проверка реального предмета (DOM/CSS-артефактов), а не самоотчёта генератора (`ENDI_KLARK_05_ENVIRONMENTAL_SCAFFOLDING` / D011, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008);
     * **Группа 6: Секция XLII — Кэш постоянного корпуса (снят/inert):**
       - `GeminiContextCacheManager` демонтирован как дублирующий (`ALONZO_CHERCH_03_LAMBDA_CORE_REDUCTION` / D008); питоновский кэш сайдкара FastEmbed/PredictionService сохранен на живом пути;
     * **Группа 7: Секция XLIII — Верёвка (TocOptimizer):**
       - `TocOptimizerTest` (7/7): при недостижимом буфере (граф $\le 1$ шага) честный статус `Flow unmeasured: single instrumented stage... cannot stretch buffer capacity; flow status undetermined` вместо ложного «System flow optimal» (`ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012);
     * **Группа 8: Секция XLIV — Очистка журнала проекта (ProjectEventLogRetentionService, SystemStatusController):**
       - `ProjectEventLogRetentionServiceTest` (6/6), `SystemStatusControllerTest` (5/5): частота подрезки приведена к скорости роста лога (ежеминутный срез вместо суточного cron), постраничная выборка `PageRequest.of(excess - 1, 1)` без подъема всех записей в память JVM (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D013, D012, D011)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация: фальсификационные заслоны готовности экрана (проверка реальной геометрии, а не самоотчета), честный статус ненатянутой верёвки TOC при $\le 1$ размеченном узле (`flow status undetermined` вместо ложного «optimal»).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и каузальная надежность происхождения: подрезка журнала событий через точный `PageRequest.of(excess - 1, 1)` без подъема гигабайтных коллекций в память JVM; вывод частоты опроса сторожа ТОС из наблюдаемой длительности стадий (`min(meanDurationMs) / 2`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтический заслон неавторизованных мутаций к ресурсам внешней модели через `ApiAuthorizationInterceptor`.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало телеметрии: `getDbrStatus()` является чистым чтением снимка без побочных эффектов мутации узлов рантайма.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status table / Belnap]:**
  * Многозначная таблица истинности: разграничение трёх исходов гейтов качества (passed, failed, undetermined/not applicable) для ликвидации рассинхронизации 388 vs 0.
- **`ENDI_KLARK_05_ENVIRONMENTAL_SCAFFOLDING` [D011 Grounding loss / Clark]:**
  * Заземление на реальные артефакты окружения: инспекция CSS/DOM и геометрических отступов вместо доверия синтетическому самоотчёту.

---

## 3. Ожидаемый протокол действий L2

1. Провести единый пакетный замер всех тест-сьютов Секций XXXVII–XLIV и XVIII в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -m 2g -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 \
     mvn test -Dtest=TocSentinelServiceTest,TocSentinelControllerTest,TocAnomalyDetectorTest,TocOptimizerTest,VideoAssetServiceTest,GoogleAiResourceControllerTest,GoogleAiResourceSecurityIntegrationTest,QualityMetricsControllerTest,QualityGateControllerTest,SixSigmaAuditServiceTest,StrandedFinalizingSweepServiceTest,BranchGarbageCollectorServiceTest,DesignExcellenceGateTest,VerificationEvidenceGateTest,LayoutGeometryAuditServiceTest,ProjectEventLogRetentionServiceTest,SystemStatusControllerTest
   ```
2. Подтвердить строго **143/143 green** (17 тест-сьютов, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
3. Соблюсти принцип Ступени 4: 0 строк правок рабочего кода сервисов, моделей, контроллеров и скриптов (`src/main/java`, `src/main/resources`, `scripts/`).
4. Зафиксировать статус ideal в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Кратко (не более 15–20 строк) отчитаться в `docs/reports/PROGRAMMER_STATE.md`.
6. Сделать семантический коммит: `feat(sections-xxxvii-xliv): complete Stage 4 batch verification (D008, D010, D006)`.
