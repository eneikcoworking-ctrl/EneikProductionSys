# Директивы и наряд для L2 (2026-10-16 19:05 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XVII, XVIIб, XIX и XX («Обездвиженные компоненты, планировщик и читающие контроллеры, сайдкары вне бэкенда и ключевые хранилища потока») успешно заверен инженером (коммит `19e89bc4`, 158/158 green в едином Docker Maven прогоне, 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующей стратегической секции — **Секция XXI («Сущности, несущие поведение, девять пространств состояний, алгебра решений, граф ограничений, лестница доверия, журнал дефектов и уплотнение хранилища»: подразделы XXI, XXIб, XXIв, XXIг, XXIд, XXIе, XXIж, XXIз)** целиком (**133 теста в 22 тест-сьютах в едином пакетном прогоне в Docker Maven**):
     * **Группа 1: Подраздел XXI — Сущности, несущие поведение (27 тестов, 6 сьютов):**
       - Абсолютная защита от перезаписи терминальных статусов (Закон 20): `TaskEntityLaw20Test` (6/6);
       - Разведение трех исходов доставки и фиксация вопросов: `DeliveryQuestionRecordTest` (3/3), `TaskAcceptanceCriteriaGuardTest` (1/1), `DeliveryVerificationPredicateTest` (6/6);
       - Изоляция носителя фабрики (Закон 1): `TaskEntityLaw1CarrierTest` (5/5);
       - Разграничение отказа декомпозиции и недостижимости компилятора: `DecompositionVerdictTest` (5/5).
     * **Группа 2: Подраздел XXIб — Девять пространств состояний (20 тестов, 3 сьюта):**
       - Строгая типизация ценности Lean без ложного одобрения муды: `LeanValueTest` (9/9);
       - Двухконъюнктная модель статуса аккаунта `status && enabled`: `AccountStatusConjunctionTest` (7/7);
       - Цель задачи с явным значением `undetermined` вместо слепого умолчания: `TargetContextTest` (4/4).
     * **Группа 3: Подраздел XXIв — Алгебра решений и решётка вердиктов (46 тестов, 5 сьютов):**
       - Монотонность сужения разрешений решётки готовности (гейт может только ужесточить суждение): `VerdictGateTest` (14/14);
       - Детерминированное согласование вердиктов (`WITHHOLD` доминирует): `VerdictReconciliationTest` (11/11);
       - Стратификация уровней вердиктов без категориального смешения: `RuntimeVerdictLayerTest` (6/6), `AcceptanceVerdictLayerTest` (7/7), `InfrastructureVerdictLayerTest` (8/8).
     * **Группа 4: Подразделы XXIг, XXIд, XXIе, XXIж, XXIз — Граф ограничений, память об отказах и хранилище (40 тестов, 8 сьютов):**
       - Граф ограничений ТОС, тайминги Велфорда и снимки персистентности: `TocOptimizerTest` (7/7), `TocNodeTimeoutTest` (4/4), `TocPersistenceSnapshotTest` (4/4);
       - Память о собственных отказах фабрики и каузальная атрибуция причин INUS: `DefectJournalServiceTest` (3/3), `DefectJournalRootCauseAttributionTest` (6/6), `LogScopeAndDefectJournalInusFalsificationTest` (5/5);
       - Изоляция скоупа логов от промптов и компактное уплотнение хранилища H2: `LogScopeBufferTest` (6/6), `EneikProductionApplicationShutdownCompactTest` (6/6).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, моделей, сущностей, перечислений, контроллеров и конфигураций (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D002, D007, D013)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация ложных подтверждений: заслон перезаписи терминальных статусов (`TaskEntityLaw20Test`), критерии приемки (`TaskAcceptanceCriteriaGuardTest`), фальсификация атрибуции отказов (`LogScopeAndDefectJournalInusFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции: стратификация уровней вердиктов (`AcceptanceVerdictLayerTest`, `InfrastructureVerdictLayerTest`), нерушимость происхождения данных при фиксации дефектов и таймингов узлов TOC (`TocNodeTimeoutTest`, `TocPersistenceSnapshotTest`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Закон 1 изоляции носителя фабрики (`TaskEntityLaw1CarrierTest`) и Закон 20 монопольного владения терминальным статусом (`TaskEntityLaw20Test`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status / Belnap]:**
  * Четырехзначная логика истинности в предикатах доставки (`DeliveryQuestionRecordTest`, `DeliveryVerificationPredicateTest`), явное представление неизвестного (`TargetContextTest.UNDETERMINED`, `LeanValueTest`), решетка вердиктов (`VerdictGateTest`).
- **`GILBERT_RAYL_02_CATEGORY_ERROR_SCAN` [D002 Category error / Ryle]:**
  * Исключение смешения категорий: разделение отказа декомпозиции и недостижимости компилятора (`DecompositionVerdictTest`), разделение слоев готовности рантайма и инфраструктуры.
- **`DURDZHON_MJEKKI_03_INUS_CONDITION` [D007 Epistemic fabrication / Mackie]:**
  * Детерминированная каузальная атрибуция дефектов по методу INUS без субъективных домыслов (`DefectJournalRootCauseAttributionTest`).
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Телеметрия журнала и уплотнение хранилища без искажения физического состояния базы (`EneikProductionApplicationShutdownCompactTest`).

---

## 3. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон тестов Секции XXI в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/app -w /app -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=TocOptimizerTest,TocNodeTimeoutTest,TocPersistenceSnapshotTest,EneikProductionApplicationShutdownCompactTest,LogScopeBufferTest,LogScopeAndDefectJournalInusFalsificationTest,DefectJournalRootCauseAttributionTest,InfrastructureVerdictLayerTest,AcceptanceVerdictLayerTest,RuntimeVerdictLayerTest,VerdictReconciliationTest,VerdictGateTest,TaskEntityLaw20Test,DeliveryQuestionRecordTest,DecompositionVerdictTest,LeanValueTest,AccountStatusConjunctionTest,TargetContextTest,TaskAcceptanceCriteriaGuardTest,TaskEntityLaw1CarrierTest,DeliveryVerificationPredicateTest,DefectJournalServiceTest
   ```
2. Подтвердить 100% green результат (133/133 green, 0 failures, 0 errors, 0 skipped, 22 тест-сьюта).
3. Обеспечить соблюдение инварианта Ступени 4: строго 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`).
4. Актуализировать строки *Живое:* в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md` для Секции XXI.
5. Зафиксировать результат в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
6. Завершить такт коммитом `feat(section-xxi): complete Stage 4 batch verification (D008, D010, D006)`.
