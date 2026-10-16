# Пакетная директива L1/L3 -> L2: Секции XXXVII–XLIV и XVIII — Финальный контур TOC, авторизация AI-ресурсов, метрики Six Sigma, выметающие обходы, заслоны экранов, очистка журналов и завершающий баланс фабрики

**Дата:** 2026-10-16 05:47 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Предыдущий пакет Секций XXX–XXXVI («Внешние клиенты, завод проектов, командный пульт, авторизация наблюдателя, выбор аккаунтов и хранилище задач», 132/132 green в коммите `449c8df`) полностью заверен и закрыт инженером без единой строки правок рабочего кода. Настоящая директива охватывает следующий финальный кластер механизмов фабрики — **Секции XXXVII–XLIV и XVIII («Финальный контур TOC, авторизация AI-ресурсов, метрики Six Sigma, выметающие обходы, заслоны экранов, очистка журналов и завершающий баланс фабрики»)**:
   - **Секция XXXVII: Сторож ТОС: чтение, которое пересчитывает (`TocSentinelService`, `TocExecutionGraph`, `TocAnomalyDetector`, `TocOptimizer`, `TocSentinelController`, `VideoAssetService`):**
     * Чистые чтения кэшированного снимка `getDbrStatus()` без мутации графа и узлов при чтении;
     * Динамический шедулер частоты пробуждения (`min(meanDurationMs) / 2`, `[250ms, 10000ms]`) вместо константы 2000 мс; изоляция видео-генерации;
     * Заслоны: `TocSentinelServiceTest`, `TocOptimizerTest`, `TocSentinelControllerTest`, `GoogleAiResourceControllerTest`.
   - **Секция XXXVIII: Вход к ресурсам модели: изменяющие запросы под заслоном (`ApiAuthorizationInterceptor`, `GoogleAiResourceController`, `WebConfig`):**
     * Деонтический барьер на все 5 мутирующих путей AI-ресурсов (401/403), нормализация URI и институциональный аудит актора;
     * Заслоны: `ApiAuthorizationInterceptorTest`, `GoogleAiResourceControllerTest`.
   - **Секция XXXIX: Метрики качества: два счёта одного слова, 388 против нуля (`SixSigmaAuditService`, `QualityMetricsController`, `QualityGateController`):**
     * 4-значная логика истинности Белнапа/Тарского (`passed`, `failed`, `undetermined`, `not-applied`);
     * Устранение дефекта «388 ложных сбоев», делегирование вычислений DPMO без вызовов `findAll()`, строгое разделение трех слоев абстракции (фабрика, поставка, продукт);
     * Заслоны: `SixSigmaAuditServiceTest`, `QualityMetricsControllerTest`, `QualityGateControllerTest`.
   - **Секция XL: Выметающий обход: восстановление, которому не нужен держатель (`StrandedFinalizingSweepService`, `BranchGarbageCollectorService`):**
     * Атомарный CAS-релиз брошенных финализаций по динамическому лизингу `finalizingSince`; безопасная очистка веток;
     * Заслоны: `StrandedFinalizingSweepServiceTest`, `StrandedFinalizingSweepServiceFalsificationTest`, `BranchGarbageCollectorServiceTest`, `BranchGarbageCollectorServiceFalsificationTest`.
   - **Секция XLI: Заслон качества экрана: проверка предмета, а не отчёта о нём (`DesignExcellenceGate`, `BaseQualityGate`, `VerificationEvidenceGate`, `BackendContractGate`):**
     * Проверка физических артефактов, структуры и геометрии вместо деклараций в payload;
     * Формирование знаменателя проверок строго per-stage;
     * Заслоны: `DesignExcellenceGateTest`, `BaseQualityGateTest`, `VerificationEvidenceGateTest`, `BackendContractGateTest`.
   - **Секция XLII: Кэш постоянного корпуса: два механизма на одно дело, работает один (`GeminiContextCacheManager` [снят/inert], `PredictionService.py`):**
     * Ликвидация дублирующего Java-кэша, монопольное владение кэшем в ML-сайдкаре;
     * Заслоны: `GeminiContextServiceTest`.
   - **Секция XLIII: Верёвка TOC: ограничитель выпуска, который не может сработать (`TocSentinelService`, `TocOptimizer`):**
     * Честный статус `Flow unmeasured` при одностадийном графе; сохранение троттлинга DBR при действительном переполнении буфера; конфигурируемый порог емкости буфера;
     * Заслоны: `TocSentinelServiceTest`, `TocOptimizerTest`.
   - **Секция XLIV: Очистка журнала проекта: предел настоящий, частота выведена из роста (`ProjectEventLogRetentionService`, `ProjectEventLogRepository`):**
     * Частая проверка с `fixedDelay` вместо суточного cron; однострочная выборка границы `PageRequest.of(excess - 1, 1)` для исключения риска переполнения кучи; иммунитет непринятых проектов;
     * Заслоны: `ProjectEventLogRetentionServiceTest`.
   - **Секция XVIII: Чего в этом перечне нет: завершающий баланс фабрики:**
     * Полная демаркация суверенитета фабрики; фронтенд строго изолирован как отображение состояния, а не механизм; все 205 механизмов фабрики охвачены и защищены.
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код сервисов, моделей, шлюзов, контроллеров и политик (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D013, D004, D011)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация заслонов: фальсифицирующие тесты верёвки TOC (`Flow unmeasured`), заслоны выметающих обходов и сборщика веток (`StrandedFinalizingSweepServiceFalsificationTest`, `BranchGarbageCollectorServiceFalsificationTest`) падают при попытке ложного освобождения или пропуска финализаций.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и надежная родословная данных: разделение качества на три изолированных слоя (фабрика, поставка, продукт) в `SixSigmaAuditService`; однострочная выборка границы `PageRequest.of(excess - 1, 1)` в `ProjectEventLogRetentionService` защищает кучу JVM.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах;**
  * Запреты как код: `ApiAuthorizationInterceptor` деонтически блокирует несанкционированные вызовы мутирующих AI-эндпоинтов (401/403) и аудит актора.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status / Belnap]:**
  * Четырехзначная логика истинности в метриках качества Six Sigma: различение исходов `passed`, `failed`, `undetermined`, `not-applied` исключает категориальную путаницу и ликвидирует ложные дефекты.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало телеметрии: метод `getDbrStatus()` производит чистое чтение снимка без самовозбуждения и мутации состояния узлов и графа TOC.
- **`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Part-whole ownership]:**
  * Монопольное владение счетчиками и графом TOC внутри `TocSentinelService`, изоляция CAS-переходов статусов финализации в `StrandedFinalizingSweepService`.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий пакетный замер механизмов Секций XXXVII–XLIV и XVIII в Docker Maven:
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=TocSentinelServiceTest,TocOptimizerTest,TocSentinelControllerTest,GoogleAiResourceControllerTest,ApiAuthorizationInterceptorTest,SixSigmaAuditServiceTest,QualityMetricsControllerTest,QualityGateControllerTest,StrandedFinalizingSweepServiceTest,StrandedFinalizingSweepServiceFalsificationTest,BranchGarbageCollectorServiceTest,BranchGarbageCollectorServiceFalsificationTest,DesignExcellenceGateTest,BaseQualityGateTest,VerificationEvidenceGateTest,BackendContractGateTest,ProjectEventLogRetentionServiceTest
   ```
2. Зафиксировать отметки живых замеров и статус `ideal` в `docs/FACTORY_MECHANISMS.md` (Секции XXXVII–XLIV и XVIII), актуализировать `docs/ANTIGRAVITY_QUEUE.md`.
3. Составить краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
4. Закоммитить с семантическим сообщением `feat(final-toc-and-closing-balance): complete Stage 4 batch verification (D008, D010, D006)`.
