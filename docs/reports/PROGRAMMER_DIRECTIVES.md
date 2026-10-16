# Пакетная директива L1/L3 -> L2: Секции XXXVII–XLIV и XVIII («Финальный контур TOC, авторизация AI-ресурсов, метрики Six Sigma, выметающие обходы, заслоны экранов, очистка журналов и завершающий баланс фабрики»)

**Дата:** 2026-10-16 00:25 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Фабрика функционирует автономно в замкнутом цикле без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секции XXX–XXXVI полностью заверены и закрыты инженером (коммит `aa16f5b`, 145/145 green). Настоящая директива охватывает целиком финальный открытый кластер фабрики — **Секции XXXVII, XXXVIII, XXXIX, XL, XLI, XLII, XLIII, XLIV и XVIII**:
   - **XXXVII. Сторож ТОС: чтение, которое пересчитывает:**
     * `TocSentinelService`, `TocExecutionGraph`, `TocAnomalyDetector`, `TocOptimizer`, `TocSentinelController`, `TocNode`, `TocEdge`, `TocToken` (чистое чтение DBR без мутации графа `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013; инкапсуляция графа `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004; динамический триггер адаптации частоты `ALONZO_CHERCH_21_DERIVED_CUTOFF` / D008);
     * `VideoAssetService` и периферийные пути видеогенерации (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010).
   - **XXXVIII. Вход к ресурсам модели: изменяющие запросы под заслоном:**
     * `ApiAuthorizationInterceptor`, `WebConfig`, `GoogleAiResourceController` (деонтические запреты мутирующих операций по API-ключам `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, матрица прав и обязанностей `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006, топологические границы Varzi D006).
   - **XXXIX. Метрики качества: два счёта одного слова, 388 против нуля:**
     * `QualityMetricsController`, `QualityGateController`, `SixSigmaAuditService`, `OperationalTruthService` (различение `passed`, `failed`, `undetermined` и `not-applied` по таблице истинности Тарского и Белнапа `ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` / D012, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012; разделение уровней абстракции проверок и задач Goldman D010).
   - **XL. Выметающий обход: восстановление, которому не нужен держатель:**
     * `StrandedFinalizingSweepService` и `BranchGarbageCollectorService` (восстановление зависших сессий и безопасная очистка веток без гонок владения `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013).
   - **XLI. Заслон качества экрана: проверка предмета, а не отчёта о нём:**
     * `DesignExcellenceGate`, `VerificationEvidenceGate`, `GateOrchestrator`, `BackendContractGate` (верификация фактического кода и артефактов вместо самоотчетов `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).
   - **XLII. Кэш постоянного корпуса: два механизма на одно дело, работает один:**
     * Деактивированный дублирующий `GeminiContextCacheManager` (снят/inert) и живой Python-кэш `PredictionService.py` (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013).
   - **XLIII. Верёвка: ограничитель выпуска, который не может сработать:**
     * `TocOptimizer.computeRecommendation` и `DEFAULT_MAX_BUFFER_CAPACITY` (честный статус `Flow unmeasured` при $\le 1$ шаге вместо ложного оптимума `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008, удержание `Throttling active!`).
   - **XLIV. Очистка журнала проекта: предел настоящий, частота выведена из роста:**
     * `ProjectEventLogRetentionService` (однострочная выборка границы `PageRequest.of(excess - 1, 1)` для исключения риска переполнения кучи OOM и иммунитет непринятых проектов `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).
   - **XVIII. Чего в этом перечне нет: завершающий баланс фабрики:**
     * Фиксация полной картины архитектурных границ, демаркация отсутствующих сущностей и окончательное подтверждение суверенитета фабрики.
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено модифицировать рабочий код сервисов, моделей, контроллеров и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы Секций XXXVII–XLIV и XVIII полностью работоспособны и заслонены тестами. Задача L2 — подтвердить нерушимость инвариантов строгим пакетным замером в рантайме без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D013, D004)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green, Popper & Tarski]:**
  * Фальсификация ложных зеленых: `TocOptimizerTest` фальсифицирует статус «System flow optimal» при отсутствии измерений; `BranchGarbageCollectorServiceFalsificationTest` фальсифицирует удаление активных веток; `StrandedFinalizingSweepServiceFalsificationTest` фальсифицирует гонки завершения сессий.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss, Goldman]:**
  * Лок уровней абстракции: счет проверок гейта не подменяет статус поставки задачи; дефекты DPMO четко привязаны к источнику и окну выборки; видео-метаданные не подменяют физический файл.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity, Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода при проходящих тестах;**
  * Деонтические запреты рантайма: 401/403 в `ApiAuthorizationInterceptor` на все мутирующие пути AI-ресурсов; запрет мутации состояния узлов при холостом чтении `getDbrStatus()`.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` & `ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` [D012 Truth status, Belnap & Tarski]:**
  * Разделение 4 статусов истинности в отчетах качества и контрольных картах: `passed`, `failed`, `undetermined`, `not-applied`.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift, Wittgenstein]:**
  * Наблюдение не искажает наблюдаемое: чтения телеметрии через `/api/toc/**` и `/api/quality/**` не сдвигают метки времени оценки и не влияют на реальный поток.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D004 Concurrency conflict, Varzi]:**
  * Монопольное владение счетчиками `inFlightCount` через жизненный цикл `enterStep`/`exitStep`; немодифицируемые фасады графа TOC.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий пакетный замер механизмов Секций XXXVII–XLIV и XVIII в Docker Maven:
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=TocSentinelServiceTest,TocOptimizerTest,TocSentinelControllerTest,ApiAuthorizationInterceptorTest,GoogleAiResourceControllerTest,QualityMetricsControllerTest,QualityGateControllerTest,SixSigmaAuditServiceTest,StrandedFinalizingSweepServiceTest,StrandedFinalizingSweepServiceFalsificationTest,BranchGarbageCollectorServiceTest,BranchGarbageCollectorServiceFalsificationTest,DesignExcellenceGateTest,VerificationEvidenceGateTest,BaseQualityGateTest,BackendContractGateTest,ProjectEventLogRetentionServiceTest
   ```
2. Зафиксировать результаты в `*Живое:*` блоков Секций XXXVII–XLIV и XVIII в `docs/FACTORY_MECHANISMS.md`, оформить блок `### Итог Секций XXXVII–XLIV и XVIII`, синхронизировать `docs/ANTIGRAVITY_QUEUE.md`.
3. Составить краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
4. Закоммитить с семантическим сообщением `feat(final-mechanisms-loop): complete Stage 4 batch verification (D008, D010, D006)`.
