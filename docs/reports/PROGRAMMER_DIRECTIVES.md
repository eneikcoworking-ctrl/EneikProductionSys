# Пакетная директива L1/L3 -> L2: Секция XXI — Сущности, несущие поведение, пространства состояний и алгебра решений

**Дата:** 2026-10-16 04:14 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Предыдущий Кластер Секций XVII, XVIIб, XIX и XX («Обездвиженное ядро, неназванная периферия контроллеров, внешние сайдкары и потоковые хранилища») успешно заверен, подтвержден и закрыт инженером (коммит `7bfcfd8`, 158/158 green в Docker Maven, 0 строк изменений рабочего кода). Настоящая директива охватывает следующий масштабный контур фабрики — **Секцию XXI («Сущности, несущие поведение, пространства состояний и алгебра решений»)** целиком со всеми 8 подразделами (XXI, XXIб, XXIв, XXIг, XXIд, XXIе, XXIж, XXIз):
   - **Контур XXI (Сущности с поведением и доставка):** `TaskEntity`, `WishlistEntity`, `JulesSessionEntity` — разведение исходов доставки, неизменяемость терминальных статусов (Закон 20), предикаты `isVerifiedForDelivery` и признак `carrier` (Закон 1). Заслоны: `TaskEntityLaw20Test` (6/6), `DeliveryQuestionRecordTest` (3/3), `TaskAcceptanceCriteriaGuardTest` (1/1), `TaskEntityLaw1CarrierTest` (5/5), `DeliveryVerificationPredicateTest` (6/6).
   - **Контур XXIб (Девять пространств состояний):** `TargetContext`, `AccountStatus`, `LeanValue`, `TaskStatus`, `ProjectStatus` — строгое отсутствие состояния «неизвестно» как скрытого согласия, 4-значная логика Белнапа (`undetermined`), декомпозиция вердиктов. Заслоны: `LeanValueTest` (9/9), `AccountStatusConjunctionTest` (7/7), `TargetContextTest` (4/4), `DecompositionVerdictTest` (5/5).
   - **Контур XXIв (Алгебра решений и решётка вердиктов):** `VerdictGate`, `VerdictReconciliation`, `Verdict`, `AcceptanceVerdictLayer`, `RuntimeVerdictLayer`, `InfrastructureVerdictLayer` — монотонность решётки готовности (гейт может только ужесточить, но не ослабить суждение), отказ при пустой решётке или отключенном флаге без ложных допусков. Заслоны: `VerdictGateTest` (14/14), `VerdictReconciliationTest` (11/11), `InfrastructureVerdictLayerTest` (8/8), `AcceptanceVerdictLayerTest` (7/7), `RuntimeVerdictLayerTest` (6/6).
   - **Контур XXIг (Граф ограничения и сторож ТОС):** `TocOptimizer`, `TocPersistenceSnapshotTest`, `TocNodeTimeoutTest` — чистые чтения кэшированного снимка DBR, динамический шедулинг watchdog, инкапсуляция графа. Заслоны: `TocOptimizerTest` (7/7), `TocNodeTimeoutTest` (4/4), `TocPersistenceSnapshotTest` (4/4).
   - **Контур XXIд, XXIе, XXIж, XXIз (Лестница доверия, журнал дефектов, память об отказах, компактность H2):** `TrustSnapshotService`, `LogScopeBuffer`, `DefectJournalService`, `KaizenProposalEntity`, `DesignShopCycleEntity`, `EneikProductionApplication.compactH2StoreOnShutdown`. Заслоны: `DefectJournalServiceTest` (3/3), `DefectJournalRootCauseAttributionTest` (6/6), `LogScopeBufferTest` (6/6), `LogScopeAndDefectJournalInusFalsificationTest` (5/5), `EneikProductionApplicationShutdownCompactTest` (6/6).
   - **Итоговый единый пакетный замер секции:** 133 теста (22 тестовых класса), 100% green в едином непрерывном прогоне в Docker Maven 3.9.9 Temurin-21 (-m 2g).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код сущностей, моделей, энумов, сервисов, репозиториев и конфигураций (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D002, D004)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green / Popper]:**
  * Попперовская фальсификация: падение заслонов при попытке перезаписи терминальных статусов задач (`TaskEntityLaw20Test`), нарушении DoR при неопределенной Lean-ценности (`LeanValueTest`), искажении предикатов доставки (`DeliveryVerificationPredicateTest`), рассинхронизации инвариантов INUS в журнале дефектов (`LogScopeAndDefectJournalInusFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и цепочка надежности: четкое разделение контекста оркестратора и контекста заказчика в `TargetContextTest`; изоляция статусов аккаунтов в `AccountStatusConjunctionTest`; снимки персистентности Шаффера и Парфита в `TocPersistenceSnapshotTest`; гарантия целостности данных при компактном выключении H2 (`EneikProductionApplicationShutdownCompactTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах;**
  * Запрет как исполняемый код: запрет терминального воскрешения задач на уровне сущности (`TaskEntity.setStatus`), деонтическое вето монотонной решётки готовности `VerdictGate`, исключающее ослабление требований приемки.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status / Belnap]:**
  * Четырехзначная логика истинности в `LeanValue`, `TargetContext`, `VerdictReconciliation`: неизвестное (`undetermined`) сохраняет статус неисследованного и никогда не превращается в ложное согласие («да»).
- **`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error / Ryle]:**
  * Разделение категорий единиц работы и признаков доставки: «сделано» (merged) не приравнивается к «доставлено заказчику» (`DeliveryQuestionRecordTest`, `DeliveryVerificationPredicateTest`).
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` [D004 Part-whole ownership / Varzi & Shaffer]:**
  * Монопольное владение старт-циклами и инкапсуляция графа TOC в `TocOptimizerTest`.

---

## 3. Ожидаемый протокол действий L2

1. Провести единый непрерывный пакетный замер механизмов Секции XXI в Docker Maven (133 теста green):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=TocOptimizerTest,TocNodeTimeoutTest,TocPersistenceSnapshotTest,EneikProductionApplicationShutdownCompactTest,LogScopeBufferTest,LogScopeAndDefectJournalInusFalsificationTest,DefectJournalRootCauseAttributionTest,InfrastructureVerdictLayerTest,AcceptanceVerdictLayerTest,RuntimeVerdictLayerTest,VerdictReconciliationTest,VerdictGateTest,TaskEntityLaw20Test,DeliveryQuestionRecordTest,DecompositionVerdictTest,LeanValueTest,AccountStatusConjunctionTest,TargetContextTest,TaskAcceptanceCriteriaGuardTest,TaskEntityLaw1CarrierTest,DeliveryVerificationPredicateTest,DefectJournalServiceTest
   ```
2. Подтвердить статус `ideal` в блоке `### Итог Секции XXI` в `docs/FACTORY_MECHANISMS.md`, синхронизировать `docs/ANTIGRAVITY_QUEUE.md`.
3. Составить краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
4. Закоммитить с семантическим сообщением `feat(section-xxi): complete Stage 4 batch verification (D008, D010, D006)`.
