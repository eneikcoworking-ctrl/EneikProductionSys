# Директивы и наряд для L2 (2026-10-17 01:21 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XVII, XVIIб, XIX и XX («Обездвиженные механизмы, планировщик и читающие контроллеры, сайдкары вне бэкенда и ключевые хранилища потока») полностью заверен инженером (коммит `e3130421`, 158/158 green в 26 тест-сьютах Docker Maven, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующей стратегической секции фабрики — **Секция XXI («Сущности, несущие поведение, девять пространств состояний, алгебра решений, граф ограничений, лестница доверия, журнал дефектов и уплотнение хранилища»: подразделы XXI, XXIб, XXIв, XXIг, XXIд, XXIе, XXIж, XXIз)** целиком (**133 теста в 22 тест-сьютах в едином пакетном прогоне в Docker Maven**):
     * **Подраздел XXI: Сущности, несущие поведение (27 тестов, 6 сьютов):**
       - `TaskEntityLaw20Test` (6/6), `DeliveryQuestionRecordTest` (3/3), `TaskAcceptanceCriteriaGuardTest` (1/1), `TaskEntityLaw1CarrierTest` (5/5), `DeliveryVerificationPredicateTest` (6/6), `DecompositionVerdictTest` (5/5): разведение трех исходов доставки (`deliveryRuledByCriteria`, `deliveryQuestionPutButUnsettled`, `isDeliveryVerificationAbsent`), изоляция носителя Закон 1, абсолютная деонтическая защита от перезаписи терминальных статусов Закон 20, носитель требований `WishlistEntity`, строгое разграничение отказа декомпозиции и недостижимости компилятора (`decompositionRefused` vs `decompositionUnreached`), разделение самопоказания фабрики и объективной записи канала (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
     * **Подраздел XXIб: Девять пространств состояний (20 тестов, 3 сьюта):**
       - `LeanValueTest` (9/9), `AccountStatusConjunctionTest` (7/7), `TargetContextTest` (4/4): строгая типизация ценности Lean без ложного одобрения потерь/муды, двухконъюнктная модель статуса аккаунта `status && enabled` без скрытых блокировок, цель задачи с явным семантическим значением `undetermined` вместо слепого умолчания репозитория (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
     * **Подраздел XXIв: Алгебра решений и решётка вердиктов (46 тестов, 5 сьютов):**
       - `VerdictGateTest` (14/14), `VerdictReconciliationTest` (11/11), `RuntimeVerdictLayerTest` (6/6), `AcceptanceVerdictLayerTest` (7/7), `InfrastructureVerdictLayerTest` (8/8): монотонность сужения разрешений решётки готовности (гейт может только ужесточить суждение), детерминированное согласование вердиктов (`WITHHOLD` доминирует), стратификация уровней вердиктов без категориального смешения (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
     * **Подразделы XXIг, XXIд, XXIе, XXIж, XXIз: Граф ограничений, память об отказах и хранилище (40 тестов, 8 сьютов):**
       - `TocOptimizerTest` (7/7), `TocNodeTimeoutTest` (4/4), `TocPersistenceSnapshotTest` (4/4): граф ограничений ТОС, Welford-тайминги и снимки персистентности (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `DefectJournalServiceTest` (3/3), `DefectJournalRootCauseAttributionTest` (6/6), `LogScopeAndDefectJournalInusFalsificationTest` (5/5), `LogScopeBufferTest` (6/6): память о собственных отказах фабрики, детерминированная каузальная атрибуция причин INUS, изоляция кольцевого буфера логов от промптов кодера (`DURDZHON_MJEKKI_03_INUS_CONDITION` / D007, `MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` / D007, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `EneikProductionApplicationShutdownCompactTest` (6/6): компактное уплотнение H2-хранилища при завершении работы без повреждения физического состояния (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сущностей, моделей, перечислений, репозиториев и сервисов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D002, D007, D013, D004)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовские фальсификационные заслоны критериев доставки (`DeliveryVerificationPredicateTest`), монотонности сужения решётки вердиктов (`VerdictGateTest`), снимков ограничений TOC (`TocPersistenceSnapshotTest`) и изоляции носителя (`TaskEntityLaw1CarrierTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Строгий лок уровней абстракции сущностей, изоляция кольцевого буфера логов от промптов кодеров (`LogScopeBufferTest`), каузальная надежность происхождения статусов и двухконъюнктной доступности аккаунтов (`AccountStatusConjunctionTest`), Welford-аккумуляторы персистентности TOC.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтические запреты в коде: Закон 1 изоляции носителя фабрики (`TaskEntityLaw1CarrierTest`) и Закон 20 монопольного владения терминальным статусом задачи (`TaskEntityLaw20Test`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status / Belnap]:**
  * Четырехзначная логика истинности в предикатах доставки (`DeliveryQuestionRecordTest`), явное представление неизвестного (`TargetContextTest.UNDETERMINED`, `LeanValueTest`), детерминированная решётка вердиктов качества (`VerdictGateTest`, `VerdictReconciliationTest`).
- **`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error / Ryle]:**
  * Исключение смешения категорий: строгое разграничение отказа декомпозиции и недостижимости компилятора (`DecompositionVerdictTest`), стратификация инфраструктурных и приемочных вердиктов (`InfrastructureVerdictLayerTest`, `AcceptanceVerdictLayerTest`).
- **`DURDZHON_MJEKKI_03_INUS_CONDITION` & `MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` [D007 Epistemic fabrication / Mackie & Gilbert]:**
  * Детерминированная каузальная атрибуция дефектов по методу INUS без субъективных домыслов (`DefectJournalRootCauseAttributionTest`), институциональный учет отказов в журнале дефектов (`DefectJournalServiceTest`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркальная телеметрия: строгое разделение самопоказания фабрики и объективной записи канала в `WishlistEntity`, корректное уплотнение H2-хранилища при остановке без повреждения физического состояния (`EneikProductionApplicationShutdownCompactTest`).
- **`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Mereology / Shaffer & Varzi]:**
  * Монопольное владение жизненным циклом сущности задачи и атомарные переходы состояний в репозитории (`TaskEntity`, `TaskRepository`).

---

## 3. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон тестов Секции XXI в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=TocOptimizerTest,TocNodeTimeoutTest,TocPersistenceSnapshotTest,EneikProductionApplicationShutdownCompactTest,LogScopeBufferTest,LogScopeAndDefectJournalInusFalsificationTest,DefectJournalRootCauseAttributionTest,InfrastructureVerdictLayerTest,AcceptanceVerdictLayerTest,RuntimeVerdictLayerTest,VerdictReconciliationTest,VerdictGateTest,TaskEntityLaw20Test,DeliveryQuestionRecordTest,DecompositionVerdictTest,LeanValueTest,AccountStatusConjunctionTest,TargetContextTest,TaskAcceptanceCriteriaGuardTest,TaskEntityLaw1CarrierTest,DeliveryVerificationPredicateTest,DefectJournalServiceTest
   ```
2. Подтвердить 100% green результат (**133/133 green в 22 тест-сьютах**, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
3. Зафиксировать строго 0 строк изменений рабочего кода (`git status` чист по `src/main/java`, `src/main/resources`, `scripts/`).
4. Актуализировать строки *Живое:* и итоговый блок Секции XXI в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Сформировать лаконичный отчёт в `docs/reports/PROGRAMMER_STATE.md` (до 15–20 строк) и закоммитить: `feat(section-xxi): complete Stage 4 batch verification (D008, D010, D006)`.
