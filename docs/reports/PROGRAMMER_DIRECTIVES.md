# Пакетная директива L1/L3 -> L2: Секция XXIX («Цех дизайна: порождение экранов и сверка их с маркой»)

**Дата:** 2026-10-16 01:57 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секция VII (TOC), Секции VIII, IX, X, XI, XII, XIII, XIV, XV, XVI, XVII/XVIIб замерены и закрыты. Настоящая директива охватывает целиком следующий стратегический контур фабрики — **Секцию XXIX («Цех дизайна: порождение экранов и сверка их с маркой»)**:
   - **Контур XXIX.1. `DesignShopOrchestrationService` (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010):**
     * Метод `startCycle` проверяет готовность и baseline; атомарный CAS-клейм `startCycleClaimedAt` исключает дублирующие параллельные циклы генерации макетов;
     * Заслон в `DesignShopOrchestrationServiceTest` (13/13 green) и `DesignShopOrchestrationServiceLaw15Test` (7/7 green).
   - **Контур XXIX.2. `DesignAssetService` & `StitchClient` (`NUEL_BELNAP_06_SUBSTITUTION_ORACLE` / D009, `NUEL_BELNAP_15_TOKEN_TRACE_UNITY` / D015):**
     * Генерация артефакта строго в требуемой форме (`requireImplementableHtml=true`); оракул подстановок Белнапа: генерация только картинок не может заменять собой `mockup.html`; fallback на `GoogleAiResourceService` допустим только когда HTML не требуется;
     * Заслон в `DesignAssetServiceTest` (12/12 green), `StitchClientTest` (10/10 green).
   - **Контур XXIX.3. `DesignConsistencyAuditService` & `LayoutGeometryAuditService` (`NUEL_BELNAP_15_TOKEN_TRACE_UNITY` / D015, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013):**
     * Извлечение визуальных токенов, расчет коэффициента трассировки к декларации проекта и кросс-экранный коэффициент Жаккара; геометрический аудит вьюпортов и сеток;
     * Заслон в `DesignConsistencyAuditServiceTest` (15/15 green), `LayoutGeometryAuditServiceTest` (13/13 green).
   - **Контур XXIX.4. `DesignSystemFalsificationService` & `DesignDriftMonitorService` (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008):**
     * Попперовская фальсификация эстетического дрейфа: выявление расхождений макетов с брендбуком до их продвижения в прод;
     * Заслон в `DesignSystemFalsificationServiceTest` (6/6 green), `DesignDriftMonitorServiceTest` (7/7 green).
   - **Контур XXIX.5. `DesignExcellenceGate`, репозиторный носитель и выметающий обход (`ENDI_KLARK_02_GROUPING_PROXIMITY_GATE` / D011, `DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` / D004):**
     * Заслон `DesignExcellenceGate` (12/12 green); персистентность `DesignShopCycleRepository` (6/6 green); CAS-выметание брошенных циклов `StrandedDesignCycleSweepService` (4/4 green); репозиторный трансфер черновиков в `GitHubPullRequestService` и продвижение в `JulesDispatchService.completeDesignReview` (`DesignImplementationLoopClosureLaw3Test` 2/2 green, `DesignImplementationLoopClosureTest` 3/3 green).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено модифицировать рабочий код сервисов и контроллеров (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы Секции XXIX уже реализованы в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D015, D009, D013, D011, D004)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green, Popper]:**
  * Фальсификация эстетического дрейфа и ложных отчетов о верстке: тесты-заслоны обязаны опровергать ложноположительные суждения; любая попытка подсунуть картинку вместо HTML или объявить макет валидным при нарушении токенов обязана завершаться отказом.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss, Goldman]:**
  * Блокировка уровней абстракции: визуальные токены проекта, верстка `mockup.html`, репозиторные файлы черновиков и гейты качества не должны смешиваться; происхождение каждого макета доказательно связано с проектным скоупом.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity, Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода сервисов цеха дизайна при проходящих тестах;**
  * Деонтический запрет на деградацию: если клиенту требуется верстка, выдача только скриншотов строго заблокирована как отказ.
- **`NUEL_BELNAP_15_TOKEN_TRACE_UNITY` & `NUEL_BELNAP_06_SUBSTITUTION_ORACLE` [D015 Aesthetic drift / D009 Substitution failure, Belnap]:**
  * Единство трассировки токенов: каждый цвет и шрифт в `mockup.html` обязан иметь однозначную трассировку к палитре проекта; оракул подстановок отвергает подмену функционального HTML декоративной картинкой.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift, Wittgenstein]:**
  * Принцип анти-зеркала: проверка реальных физических артефактов верстки и геометрии вьюпортов, а не самоотчетов сервисов генерации.
- **`ENDI_KLARK_02_GROUPING_PROXIMITY_GATE` [D011 Teleosemantic feedback, Clark]:**
  * Группировка и топологическая близость элементов дизайна при проверке экранного совершенства в `DesignExcellenceGate`.
- **`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` [D004 Part-whole ownership, Shaffer]:**
  * Четкое владение жизненным циклом макетов от `draft` к `approved` через репозиторный носитель и CAS-выметание брошенных циклов.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий единый пакетный замер механизмов Секции XXIX в Docker Maven:
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=DesignShopOrchestrationServiceTest,DesignShopOrchestrationServiceLaw15Test,DesignAssetServiceTest,DesignConsistencyAuditServiceTest,LayoutGeometryAuditServiceTest,DesignSystemFalsificationServiceTest,DesignDriftMonitorServiceTest,DesignExcellenceGateTest,DesignShopCycleRepositoryIntegrationTest,StrandedDesignCycleSweepServiceTest,DesignImplementationLoopClosureLaw3Test,DesignImplementationLoopClosureTest,StitchClientTest
   ```
2. Подтвердить статус `ideal` в `*Живое:*` блоков Секции XXIX в `docs/FACTORY_MECHANISMS.md`, подтвердить блок `### Итог Секции XXIX` (107+ green), синхронизировать `docs/ANTIGRAVITY_QUEUE.md`.
3. Составить краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
4. Закоммитить с семантическим сообщением `feat(design-shop-section-xxix): complete Stage 4 batch verification (D008, D010, D006)`.
