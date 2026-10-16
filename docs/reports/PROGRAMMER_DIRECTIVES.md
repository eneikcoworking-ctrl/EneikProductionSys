# Пакетная директива L1/L3 -> L2: Секции IX, X и XI («Внешние системы, Дизайн-цех и Наблюдение за живым продуктом клиента»)

**Дата:** 2026-10-16 01:10 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Фабрика функционирует автономно в замкнутом цикле без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секция VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Оркестрация») полностью заверена и закрыта инженером (коммит `8706b01`, 281/281 green в едином прогоне). Настоящая директива охватывает целиком следующий стратегический кластер фабрики — **Секции IX, X и XI («Внешние системы, Дизайн-цех и Наблюдение за живым продуктом клиента»)**:
   - **Секция IX («Внешние системы: внешняя AI/RAG/Runtime-граница фабрики»):**
     * `GoogleAiResourceService` & `GoogleAiResourceController`: типобезопасная граница AI-ресурсов, авторизация key-gate и fail-closed защита без скрытых мутаций (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
     * `GeminiContextService` & `EmbeddingSimilarityUtil`: проекции векторов и чистая функция сходства без подъема всего корпуса (`ALONZO_CHERCH_21_DERIVED_CUTOFF` / D010, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
     * `StitchClient`: JSON-RPC клиент к Stitch MCP с изоляцией проектов и сохранением `screenId` (`RUT_BARKAN_MARKUS_01_MODAL_NECESSITY_MAP` / D002);
     * `DesignAssetService`: валидация дизайн-токенов, запрет подмены HTML на изображения и отсечение офф-токен экранов (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
     * `RuntimeLauncherClient`: физическая демаркация сбоя инструмента (`observed=false`) от сбоя продукта (`answered=false`) (`ELVIN_GOLDMAN_13_TRUTH_STATUS_TABLE` / D012);
     * `GeminiObserverActionService` & `InternalGeminiObserverController`: операционные действия наблюдателя строго через `OperationalPolicyService` с запретом на прямые коммиты (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006).
   - **Секция X («Дизайн-цех, порождение экранов, токенизация бренда, дизайн-система и live drift»):**
     * `DesignShopOrchestrationService`, `DesignShopCycleEntity`, `DesignShopCycleRepository`: замкнутый цикл порождения экранов и токенов (`RUT_BARKAN_MARKUS_01_MODAL_NECESSITY_MAP` / D002);
     * `DesignConsistencyAuditService` & `LayoutGeometryAuditService`: аудит согласованности и геометрии вьюпортов (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
     * `DesignSystemFalsificationService` & `DesignDriftMonitorService`: мониторинг дрейфа стилей и фальсификация регрессий интерфейса (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
     * `StrandedDesignCycleSweepService`: безопасное CAS-освобождение зависших дизайн-попыток по TTL (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
     * `DesignExcellenceGate`: строгий фальсификационный гейт качества экранов по скриншотам и токенам без галлюцинаций LLM (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
   - **Секция XI («Наблюдение за живым продуктом клиента, апостериорный каданс и доказательства возможностей продукта»):**
     * `ClientRuntimeObservabilityService`: наблюдение в тике оркестрации без собственного cron, четкое разделение инструментального сбоя и сбоя продукта, bounded preview window (`ELVIN_GOLDMAN_07_SELF_MODEL_SANITY` / D013);
     * `BetaPosterior`: чистая математическая модель апостериорного бета-распределения, отсечение дубликатов commit SHA (`ELVIN_GOLDMAN_03_BELIEF_UPDATE_LEDGER` / D010);
     * `RuntimeHealthShiftDetector`: точный биномиальный детектор сдвигов здоровья рантайма во времени без смешения с u-картами эпиков (`LYUDVIG_VITGENSHTEYN_02_CATEGORY_ERROR_SCAN` / D002);
     * `ProductCapabilityService`: обнаружение возможностей продукта строго по контрактам OpenAPI (`docs/contracts/*.openapi.*`), зондирование через лаунчер, демаркация 401/403 как барьеров инструмента (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
     * `RuntimeVerdictLayer`: чистая проекция продуктовых вердиктов рантайма (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено модифицировать рабочий код сервисов, моделей, контроллеров и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы Секций IX, X и XI полностью работоспособны и заслонены тестами. Задача инженера L2 — подтвердить нерушимость инвариантов строгим пакетным замером в рантайме без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D013, D002, D004)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green, Popper]:**
  * Фальсификационные заслоны артефактов дизайна (`DesignExcellenceGate`, `DesignSystemFalsificationService`) и сдвигов здоровья рантайма (`RuntimeHealthShiftDetector`); заслон от фиктивного прохождения проверок без физических скриншотов или при отсутствии OpenAPI-контрактов.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss, Goldman]:**
  * Сохранение каузальной цепочки свидетельств от OpenAPI-контракта до наблюдения (`ProductCapabilityService`); четкая изоляция уровней абстракции (попытка, продукт, артефакт, возможность); векторные проекции контекста без подъема всего корпуса.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity, Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода сервисов при проходящих тестах;**
  * Деонтические запреты рантайма: категорический запрет на коммит офф-токен экранов или подмену HTML картинками в `DesignAssetService`; строгий запрет на неавторизованные мутации наблюдателя в `GeminiObserverActionService`.
- **`ELVIN_GOLDMAN_13_TRUTH_STATUS_TABLE` & `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status, Goldman & Belnap]:**
  * Таблица статусов истинности демаркации сбоя инструмента (`observed=false`, `instrumentFailure=true`) от дефекта продукта (`answered=false`, `productFailure`). Сбой инструмента никогда не открывает продуктовых ограничений.
- **`ELVIN_GOLDMAN_07_SELF_MODEL_SANITY` & `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift, Goldman & Wittgenstein]:**
  * Анти-зеркало рантайма: наблюдение за контейнерами лаунчера, проверка скриншотов и контрактов опираются на физические факты без приватного языка и самообмана; апостериорный каданс BetaPosterior без искажения вероятностей.
- **`LYUDVIG_VITGENSHTEYN_02_CATEGORY_ERROR_SCAN` [D002 Category error, Wittgenstein]:**
  * Детекция категориальных ошибок: запрет на повторное использование контрольных карт эпиков на временных рядах рантайма; демаркация сетевых ошибок 401/403 как барьеров инструмента, а не продуктовых дефектов.
- **`RUT_BARKAN_MARKUS_01_MODAL_NECESSITY_MAP` [D002 Modal actualist object, Barcan Marcus]:**
  * Нарастающий фронт сути до опций: порождение экранов дизайн-цеха создает реальные версионированные HTML-артефакты со стабильным `screenId`, исключая эвристические заглушки.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий единый пакетный замер механизмов Секций IX, X и XI в Docker Maven:
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=GoogleAiResourceSecurityIntegrationTest,GoogleAiResourceControllerTest,GeminiContextServiceTest,StitchClientTest,DesignAssetServiceTest,ClientRuntimeObservabilityServiceTest,ObservationHostingDemarcationLaw26Test,GeminiObserverActionServiceTest,InternalGeminiObserverControllerTest,InternalGeminiObserverSecurityIntegrationTest,DesignShopOrchestrationServiceTest,DesignShopFalsificationTest,DesignConsistencyAuditServiceTest,LayoutGeometryAuditServiceTest,DesignSystemFalsificationTest,DesignDriftMonitorTest,StrandedDesignCycleSweepServiceTest,DesignImplementationLoopClosureLaw3Test,DesignExcellenceGateTest,DesignExcellenceGateFalsificationTest,FailureDemarcationLaw22Test,BetaPosteriorTest,RuntimeHealthShiftDetectorTest,ProductCapabilityServiceTest,RuntimeVerdictLayerTest
   ```
2. Подтвердить статус `ideal` в `*Живое:*` блоков Секций IX, X и XI в `docs/FACTORY_MECHANISMS.md`, оформить блоки `### Итог Секции IX`, `### Итог Секции X`, `### Итог Секции XI` (или общий итог кластера), синхронизировать `docs/ANTIGRAVITY_QUEUE.md`.
3. Составить краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
4. Закоммитить с семантическим сообщением `feat(external-design-and-runtime): complete Stage 4 batch verification (D008, D010, D006)`.
