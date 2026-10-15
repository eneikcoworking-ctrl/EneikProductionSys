# Пакетная директива L1/L3 -> L2: Секция XXI (Сущности, пространства состояний и алгебра решений)

**Дата:** 2026-10-15 20:06 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секции XIX и XX («Сайдкары вне бэкенда» и «Хранилища, держащие поток») полностью заверены и закрыты инженером (101/101 green в коммите `ef1f214`). Настоящая директива охватывает целиком следующее открытое семейство фабрики — **Секцию XXI («Сущности, несущие поведение, пространства состояний и алгебра решений»: подразделы XXI, XXIб, XXIв, XXIг, XXIд, XXIе, XXIж, XXIз)**:
   - **XXI. Сущности, несущие поведение:** `TaskEntity` (предикаты доставки, изоляция носителя, защита от перезаписи терминальных статусов), `WishlistEntity` (предикаты подвижности и декомпозиции), `JulesSessionEntity`.
   - **XXIб. Девять пространств состояний:** `TargetContext`, `AccountStatus`, `LeanValue`, `TaskStatus`, `ProjectStatus`, `SessionStatus`, `WishlistStatus`, `KanoClass`, `Judgement`.
   - **XXIв. Алгебра решений и слои вердиктов:** `VerdictGate`, `DecisionAlgebra`, `RuntimeVerdictLayer`, `AcceptanceVerdictLayer`, `InfrastructureVerdictLayer`, `VerdictReconciliation`.
   - **XXIг. Граф ограничения ТОС:** `TocOptimizer`, `TocGraph`, `TocNode`, `TocEdge`, снимки персистентности (`TocPersistenceSnapshotTest`), таймауты узлов (`TocNodeTimeoutTest`).
   - **XXIд. Лестница доверия:** `TrustSnapshotService` (фиксация входных сигналов доверия без упреждающих суждений).
   - **XXIе. Журнал проекта и буфер логов:** `LogScopeBuffer`, `LogScopeAndDefectJournalInusFalsificationTest`.
   - **XXIж. Память о собственных отказах:** `DefectJournalEntity`, `KaizenProposalEntity`, `DesignShopCycleEntity` (`DefectJournalServiceTest`, `DefectJournalRootCauseAttributionTest`).
   - **XXIз. Точка входа фабрики и безопасный останов:** `EneikProductionApplicationShutdownCompactTest` (детерминированная компактификация хранилища при завершении).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3, 4 и 5):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено модифицировать рабочий код сервисов, сущностей и перечислений (`src/main/java`) при проходящих тестах!
   - Механизмы Секции XXI уже находятся в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D007, D002, D004)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green, Popper]:**
  * Попперовская фальсификация: тесты-заслоны предикатов доставки `DeliveryVerificationPredicateTest`, `DeliveryQuestionRecordTest`, `TaskAcceptanceCriteriaGuardTest` и слоев вердиктов гарантируют фальсификацию ложных результатов при отсутствии подлинных свидетельств.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `KIT_DEROUZ_01_RELIABILITY_CHAIN` [D010 Data lineage loss, Goldman / DeRose]:**
  * Лок уровней абстракции: строгая изоляция пространств состояний (`TargetContext`, `AccountStatus`, `LeanValue`) от прикладных эвристик; доказательная цепочка происхождения данных и запрет на смешение разнородных контекстов.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity, Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода сущностей, энумов и сервисов Секции XXI при проходящих тестах;**
  * Запрет на перезапись терминальных статусов задач (`TaskEntityLaw20Test`, `INSTITUTIONAL-PROHIBITION`);
  * Запрет диспетчеризации задач при неразрешенном `TargetContext` (защита Закона 2 изоляции носителя).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Belnap 4-valued logic]:**
  * Четырехзначная логика истинности в предикатах доставки: различение «спросили и не решили» (`UNDECIDABLE`) и «не спрашивали вовсе» (`NOT_MEASURED`).
- **`DEREK_PARFIT_01_PERSISTENCE_SNAPSHOT` & `DZHONATAN_SHAFFER_18_PERSISTENCE_SNAPSHOT` [D010 Persistence snapshot, Parfit / Shaffer]:**
  * Фиксация персистентных снимков графа ТОС и безопасное завершение рантайма с компактификацией хранилища (`EneikProductionApplicationShutdownCompactTest`).
- **`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error, Ryle]:**
  * Сканирование категориальных ошибок: предикаты задач различают производственный носитель (`carrierTaskType`) и продуктовые задачи клиента.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий пакетный замер механизмов Секции XXI в Docker Maven:
   `docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=TargetContextTest,TaskEntityLaw20Test,DeliveryQuestionRecordTest,TaskAcceptanceCriteriaGuardTest,DecompositionVerdictTest,LeanValueTest,AccountStatusConjunctionTest,TaskEntityLaw1CarrierTest,DeliveryVerificationPredicateTest,VerdictGateTest,VerdictReconciliationTest,RuntimeVerdictLayerTest,AcceptanceVerdictLayerTest,InfrastructureVerdictLayerTest,TocOptimizerTest,TocPersistenceSnapshotTest,TocNodeTimeoutTest,LogScopeBufferTest,LogScopeAndDefectJournalInusFalsificationTest,DefectJournalServiceTest,DefectJournalRootCauseAttributionTest,EneikProductionApplicationShutdownCompactTest`
2. Зафиксировать результаты в `*Живое:*` блоков Секции XXI в `docs/FACTORY_MECHANISMS.md`, оформить блок `### Итог Секции XXI`, обновить `docs/ANTIGRAVITY_QUEUE.md`.
3. Составить краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
4. Закоммитить с семантическим сообщением `feat(entities-and-decisions): complete Stage 4 batch verification (D008, D010, D006)`.
