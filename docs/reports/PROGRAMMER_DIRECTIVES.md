# Директивы и наряд для L2 (2026-10-16 12:00 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секции XXX–XXXVI («Клиент к предсказателю, Разбор чужого репозитория, Завод репозиториев, Пульт управления, Внутренний вход наблюдателя, Выбор аккаунта и Хранилище задач») успешно заверены инженером (коммит `973c584`, 136/136 green, 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующего стратегического завершающего кластера фабрики — **Секций XXXVII, XXXVIII, XXXIX, XL, XLI, XLII, XLIII и XLIV** целиком (22 тест-сьюта, 213 тестов в едином пакетном прогоне Docker Maven):
     * **Секция XXXVII: Сторож ТОС (27 тестов, 3 сьюта):**
       - `TocSentinelServiceTest` (18/18), `TocOptimizerTest` (7/7), `TocSentinelControllerTest` (2/2): разделение чистого чтения `getDbrStatus()` и мутации графа, адаптивный динамический шедулинг частоты сторожа, обнаружение дедлоков в графе wait-for, изоляция фасадного доступа (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `ALONZO_CHERCH_21_DERIVED_CUTOFF` / D008).
     * **Секция XXXVIII: Вход к ресурсам модели (26 тестов, 2 сьюта):**
       - `ApiAuthorizationInterceptorTest` (20/20), `GoogleAiResourceControllerTest` (6/6), а также сквозной заслон `GoogleAiResourceSecurityIntegrationTest`: деонтический барьер на все 5 изменяющих эндпоинтов AI-ресурсов (401/403), нормализация путей медиа (`..` traversal guard) и институциональный аудит актора (`DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).
     * **Секция XXXIX: Метрики качества (48 тестов, 4 сьюта):**
       - `SixSigmaAuditServiceTest` (26/26), `OperationalTruthServiceTest` (17/17), `QualityMetricsControllerTest` (3/3), `QualityGateControllerTest` (2/2): 4-значная логика истинности Тарского и Белнапа (`passed`, `failed`, `undetermined`, `not-applied`), полная ликвидация дефекта «388 ложных сбоев» разделением уровней абстракции проверки и задачи, делегирование гейта в SixSigma без `findAll()` (`ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` / D012, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010).
     * **Секция XL: Выметающий обход (33 теста, 4 сьюта):**
       - `StrandedFinalizingSweepServiceTest` (8/8), `StrandedFinalizingSweepServiceFalsificationTest` (6/6), `BranchGarbageCollectorServiceTest` (13/13), `BranchGarbageCollectorServiceFalsificationTest` (6/6): атомарный CAS-релиз брошенных финализаций `finalizing -> pending`, динамический расчет лизинга по фактическим выборкам `FINALIZING_DURATION`, иммунитет активных держателей и безопасная сборка мусора веток (`ALVA_NOE_17_CAUSAL_PROCESS_TRACE` / D013, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008).
     * **Секция XLI: Заслон качества экрана (47 тестов, 6 сьютов):**
       - `DesignExcellenceGateTest` (12/12), `DesignExcellenceGateFalsificationTest` (6/6), `LayoutGeometryAuditServiceTest` (13/13), `BaseQualityGateTest` (10/10), `VerificationEvidenceGateTest` (8/8), `BackendContractGateTest` (5/5): проверка реальных физических артефактов и геометрии PR (`desktop-1440.png`, `mobile-375.png`, `layout-check.json`) вместо самоотчётов исполнителя, гештальт-соотношение близости (`ratio < 1.0`), отсутствие коллизий и запрет блокировки зума вьюпорта (`ENDI_KLARK_02_GROUPING_PROXIMITY_GATE` / D011, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).
     * **Секция XLII: Кэш постоянного корпуса (3 теста, 1 сьют):**
       - `SystemStatusControllerTest` (3/3): ликвидация дублирующего бэкенд-кэша `GeminiContextCacheManager` (снят), отсутствие ссылок на `cachedContents` в Java, перенос провайдерного кэша в сайдкар `PredictionService.py` (`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004).
     * **Секция XLIII: Верёвка TOC (25 тестов, 2 сьюта):**
       - `TocOptimizerTest` (7/7), `TocSentinelServiceTest` (18/18): ограничение выпуска DBR rope, честный статус `Flow unmeasured` при отсутствии стадий (защита от ложного оптимума), приоритетный обход (`priority >= 80`) и троттлинг низкоприоритетных задач при заполнении буфера (`ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008, `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011).
     * **Секция XLIV: Очистка журнала проекта (10 тестов, 1 сьют):**
       - `ProjectEventLogRetentionServiceTest` (10/10): частая задержка `fixedDelay`, однострочная выборка границы `PageRequest.of(excess - 1, 1)` для защиты памяти кучи, сохранение журналов непринятых проектов и потолковое удержание записей (`ALONZO_CHERCH_21_DERIVED_CUTOFF` / D010, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, моделей, сущностей, энумов, контроллеров, миграций и сервисов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в сильной/идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D011, D013, D004)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация: тесты обязаны воспроизводимо падать при повреждении заслонов готовности, попытках объявить «оптимальный поток» на неразмеченном графе или пропуске UI без реальных скриншотов в PR.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции: строгое разделение проверки отдельного чека, статуса задачи и готовности проекта; однострочная выборка границы `PageRequest.of(excess - 1, 1)` для очистки логов без подъёма кучи.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтический заслон изменяющих запросов AI-ресурсов (401/403) на уровне `ApiAuthorizationInterceptor`.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` & `ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` [D012 Multi-valued truth / Belnap & Tarski]:**
  * 4-значная логика: `passed`, `failed`, `undetermined`, `not-applied`. Неприменённая проверка не превращается в дефект.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` & `ALVA_NOE_17_CAUSAL_PROCESS_TRACE` [D013 Runtime drift / Noë & Wittgenstein]:**
  * Чистое чтение телеметрии без мутации наблюдаемого объекта (`getDbrStatus()` не меняет граф); каузальная трассировка лизинга финализаций `finalizingSince`.
- **`ENDI_KLARK_02_GROUPING_PROXIMITY_GATE` & `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Perception failure / Clark & Dretske]:**
  * Заслон близости Gestalt: элементы одной группы ближе друг к другу, чем к чужим (`ratio < 1.0`); телеосемантическая обратная связь: сигнал кэша имеет смысл только при фактическом потреблении.

---

## 3. Ожидаемый протокол действий L2

1. **Единый пакетный запуск тестов Секций XXXVII–XLIV (213 тестов, 22 сьюта в Docker Maven):**
   ```bash
   docker run --rm -m 2g -v /root/.m2:/root/.m2 -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=TocSentinelServiceTest,TocOptimizerTest,TocSentinelControllerTest,GoogleAiResourceControllerTest,GoogleAiResourceSecurityIntegrationTest,ApiAuthorizationInterceptorTest,QualityMetricsControllerTest,QualityGateControllerTest,SixSigmaAuditServiceTest,OperationalTruthServiceTest,StrandedFinalizingSweepServiceTest,StrandedFinalizingSweepServiceFalsificationTest,BranchGarbageCollectorServiceTest,BranchGarbageCollectorServiceFalsificationTest,DesignExcellenceGateTest,DesignExcellenceGateFalsificationTest,LayoutGeometryAuditServiceTest,BaseQualityGateTest,VerificationEvidenceGateTest,BackendContractGateTest,SystemStatusControllerTest,ProjectEventLogRetentionServiceTest
   ```
2. **Нулевая мутация кода:**
   - 0 строк изменений в `src/main/java`, `src/main/resources`, `scripts/`.
3. **Фиксация результатов:**
   - Обновить `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md` с подтверждением идеального статуса.
   - Записать отчёт в `docs/reports/PROGRAMMER_STATE.md` (до 20 строк).
   - Сделать семантический коммит в фабрику: `feat(sections-xxxvii-xliv): complete Stage 4 batch verification (D008, D010, D006)`.
