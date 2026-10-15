# Пакетная директива L1/L3 -> L2: Секция XVI (Предписания для L2: архитектурное владение, заслоны истинности, тянущий контур и деонтические запреты)

**Дата:** 2026-10-15 16:26 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Предыдущие кластеры (включая Секции X и XXIX с 113 тестами green в коммите `2b655ed`) успешно заверены и закрыты. Настоящая директива охватывает целиком следующий фундаментальный массив фабрики — **Секцию XVI (Предписания для L2: что и как чинить — 59 предписаний: архитектурное владение, заслоны истинности, тянущий контур ТОС, деонтические запреты и заслоны от тихих сбоев)**:
   - **Контур XVI.1. Архитектурное владение и разделение ответственности (Shaffer D004):**
     * Пункты 1, 2, 3, 4: монопольное владение переходами состояний заявок (`ProjectFlowService`) и задач (`TechnicalLeadCompiler`, `AutoMergeService`), CAS-заслоны в хранилищах (`compareAndSetStatus`), демаркация ответственности `ProjectFlowService` и `JulesDispatchService`;
     * Пункт 22: носитель ревью без сессии и защита ёмкости пула аккаунтов.
   - **Контур XVI.2. Заслоны истинности, категориальные демаркации и защита от ложных находок (Belnap D012, Ryle D002, Austin D002):**
     * Пункты 9, 14, 42, 43, 44: четырехзначные таблицы истинности Belnap (`LeanValue.undetermined`, `TargetContext.UNDETERMINED`, конъюнкция доступности аккаунтов), защита от подмены «не установлено» утверждением «работа ценна»;
     * Пункты 10, 16: отсечение ошибок категории (Ryle D002) — неудача фабрики в `RepositoryStackAnalyzer` не становится фактом о заказчике; запрет круга самозаказа (Austin D002, закон 3);
     * Пункты 18, 19, 20: детекция дубликатов задач (`TaskDuplicateDetector`, `SemanticDuplicateVeto`), отсечение фальшивых причин отказа выбора аккаунта.
   - **Контур XVI.3. Тянущий контур ТОС, адаптивный сторож и защита связности (Popper D008, Goldman D010, Dretske D011, Pearl D013):**
     * Пункты 4, 6, 34, 35, 38, 39, 40, 41: адаптивный динамический сторож `TocSentinelService` (чтение кэшированного снимка `getDbrStatus` без паразитных записей, триггер частоты по времени шагов), выметающий обход и верёвка ТОС;
     * Пункт 11: асимметричный рост доверия в `OperationalTruthService` (Goldman D010, старт с `0.0`/`UNDETERMINED`, рост строго по подтвержденным свидетельствам);
     * Пункты 12, 13: метрики качества, разделение исходов (passed/failed/undetermined).
   - **Контур XVI.4. Деонтические запреты и изоляция границ (Raz D006, Varzi D006):**
     * Пункты 24, 27, 59: императивная проверка полномочий на изменяющих входах `InternalGeminiObserverController` и `GoogleAiResourceController`, исключение предположения «internal значит авторизован»;
     * Пункт 15: строгая валидация Flyway-миграций (`validate-on-migrate=true`, отмена `repair()` на старте).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено трогать рабочий код сервисов и тестов при проходящих тестах!
   - Все 59 предписаний Секции XVI уже материализованы в коде и архитектуре фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D002)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green, Popper]:**
  * Фальсификация архитектурных инвариантов и схемы БД: `FlywayMigrationValidationTest`, `WishlistServiceFalsificationTest`, `WishlistContentSimilarityMatcherFalsificationTest`. Заслоны обязаны падать при нарушении целостности контрактов или семантическом дрифте.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss, Goldman]:**
  * Лок уровней абстракции между намерениями, задачами и персистентными снимками (`contentKey`, `TaskEntity`, `WishlistItemEntity`);
  * Асимметричная динамика доверия (`ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS`): `OperationalTruthService` начинает с `UNDETERMINED` и прирастает только по цепочке валидированных свидетельств.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity, Raz]:**
  * **Императивный запрет инженеру L2 на модификацию рабочего кода сервисов Секции XVI при проходящих тестах;**
  * Деонтический запрет несанкционированного доступа: изменяющие операции `InternalGeminiObserverController` и `GoogleAiResourceController` защищены структурными авторизационными барьерами;
  * CAS-запрет гонок: исключение множественных параллельных писателей в состояние задач и заявок.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Belnap] & `GILBERT_RAYL_02_CATEGORY_ERROR_SCAN` [D002 Ryle]:**
  * Многозначная логика: отсечение ложного бинаризма через явный статус `undetermined` в `LeanValue` и `TargetContext`;
  * Устранение категориальных ошибок: недоступность инструмента не приравнивается к дефекту заказчика.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий замер целостности механизмов в Docker Maven (`docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=...`).
2. Подтвердить 100% green статус по ключевым классам-заслонам Секции XVI:
   - `TocSentinelServiceTest`, `TocSentinelControllerTest`;
   - `LeanValueTest`, `TargetContextTest`;
   - `RepositoryStackAnalyzerTest`, `OperationalTruthServiceTest`;
   - `TaskDuplicateDetectorTest`, `SemanticDuplicateVetoTest`;
   - `WishlistControllerIntegrationTest`, `WishlistServiceFalsificationTest`;
   - `WishlistContentSimilarityMatcherTest`, `WishlistSimilarityTest`;
   - `InternalGeminiObserverControllerTest`, `InternalGeminiObserverSecurityIntegrationTest`, `GeminiObserverActionServiceTest`;
   - `SettingsControllerIntegrationTest`, `SystemSettingsServiceTest`.
3. Зафиксировать форму образца и актуализировать статус Секции XVI в `docs/FACTORY_MECHANISMS.md` (Ступень 4 closed, ideal).
4. Оформить лаконичный семантический коммит фабрики.
5. Обновить `docs/reports/PROGRAMMER_STATE.md` (до 20 строк).
