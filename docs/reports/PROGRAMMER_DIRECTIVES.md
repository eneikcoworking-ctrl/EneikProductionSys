# Директивы и наряд для L2 (2026-10-16 09:15 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию/кластер механизмов целиком.
   - Кластер Секций XVII, XVIIб, XIX и XX («Обездвиженное ядро, неназванная периферия контроллеров, внешние сайдкары и потоковые хранилища») успешно заверен инженером (коммит `9337d31`, 158/158 green в Docker Maven, 0 строк изменений кода).
   - Настоящая директива открывает пакетный замер следующего стратегического кластера фабрики — **Секции XXI («Сущности, несущие поведение, девять пространств состояний, алгебра решений, граф ограничений, лестница доверия, журнал дефектов и уплотнение хранилища»: подразделы XXI, XXIб, XXIв, XXIг, XXIд, XXIе, XXIж, XXIз)** целиком (22 тест-сьюта, 133 теста в едином пакетном прогоне Docker Maven):
     * **Подраздел XXI: Сущности, несущие поведение (27 тестов, 4 сьюта):**
       - `TaskEntityLaw20Test` (6/6), `DeliveryQuestionRecordTest` (3/3), `TaskAcceptanceCriteriaGuardTest` (1/1), `TaskEntityLaw1CarrierTest` (5/5), `DeliveryVerificationPredicateTest` (6/6) — разведение трех исходов доставки (`deliveryRuledByCriteria`, `deliveryQuestionPutButUnsettled`, `isDeliveryVerificationAbsent`), изоляция носителя Закон 1 и абсолютная защита от перезаписи терминальных статусов Закон 20 (D012 Belnap, D006 Raz, D008 Popper);
       - `DecompositionVerdictTest` (5/5) — носитель требования `WishlistEntity`, разграничение отказа декомпозиции и недостижимости компилятора (`decompositionRefused` vs `decompositionUnreached`), разделение самопоказания фабрики и записи канала `lastCompileReachedAt` (D012 Belnap, D013 Wittgenstein).
     * **Подраздел XXIб: Девять пространств состояний (20 тестов, 3 сьюта):**
       - `LeanValueTest` (9/9) — строгая типизация ценности Lean, исключение неразобранных ответов модели;
       - `AccountStatusConjunctionTest` (7/7) — двухконъюнктная модель статуса аккаунта без фиктивных разрешений;
       - `TargetContextTest` (4/4) — цель задачи с явным значением «не установлено» вместо слепого умолчания (D012 Belnap, D010 Goldman).
     * **Подраздел XXIв: Алгебра решений и решётка вердиктов (46 тестов, 5 сьютов):**
       - `VerdictGateTest` (14/14) — монотонность решётки готовности (гейт может только ужесточить суждение, но не ослабить);
       - `VerdictReconciliationTest` (11/11) — детерминированное согласование вердиктов;
       - `RuntimeVerdictLayerTest` (6/6), `AcceptanceVerdictLayerTest` (7/7), `InfrastructureVerdictLayerTest` (8/8) — стратификация уровней вердиктов без категориального смешения (D008 Popper, D010 Goldman, D002 Ryle).
     * **Подразделы XXIг, XXIд, XXIе, XXIж, XXIз: Граф ограничений, память об отказах и хранилище (40 тестов, 10 сьютов):**
       - `TocOptimizerTest` (7/7), `TocNodeTimeoutTest` (4/4), `TocPersistenceSnapshotTest` (4/4) — граф ограничений ТОС, тайм-ауты узлов и снимки персистентности (D010 Parfit & Shaffer, D013 Wittgenstein);
       - `DefectJournalServiceTest` (3/3), `DefectJournalRootCauseAttributionTest` (6/6), `LogScopeAndDefectJournalInusFalsificationTest` (5/5), `LogScopeBufferTest` (6/6) — память о собственных отказах фабрики, детерминированная атрибуция причин INUS и буфер логов (D008 Popper, D010 Goldman, D007 Levi);
       - `EneikProductionApplicationShutdownCompactTest` (6/6) — компактное уплотнение H2-хранилища при завершении работы без повреждения целостности (D010 Goldman).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, сущностей, энумов, слоев вердиктов и сервисов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D002, D013)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green / Popper]:**
  * Попперовская фальсификация: тесты-заслоны обязаны воспроизводимо падать при перезаписи терминальных статусов (`TaskEntityLaw20Test`), ложном признании декомпозиции завершенной (`DecompositionVerdictTest`), сбое решётки вердиктов (`VerdictGateTest`) и нарушении причинно-следственной связи отказов (`LogScopeAndDefectJournalInusFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и каузальная цепочка надёжности: пространства состояний (`LeanValue`, `TargetContext`, `AccountStatus`), слои вердиктов (`InfrastructureVerdictLayer`, `AcceptanceVerdictLayer`, `RuntimeVerdictLayer`) и журнал дефектов жестко изолированы по уровням абстракции, не допуская смешения данных и метаданных.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтические запреты в коде: запрет возврата из терминальных статусов, запрет признания доставки без критериального суждения, монотонность решётки готовности.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth status table / Belnap]:**
  * 4-значная логика истинности (True/False/Both/Neither): «неизвестное» отделено от «отказано» в предикатах доставки (`deliveryQuestionPutButUnsettled` vs `isDeliveryVerificationAbsent`) и в декомпозиции (`decompositionRefused` vs `decompositionUnreached`).
- **`GILBERT_RAYL_02_CATEGORY_ERROR_SCAN` [D002 Category error / Ryle]:**
  * Недопущение категориальных ошибок при согласовании вердиктов (`VerdictReconciliationTest`) и разделение технических ошибок инфраструктуры от проектных дефектов поставки.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало рантайма: телеметрия графа ограничений ТОС и снимки состояний отражают физические факты исполнения, а не словесные самодекларации.

---

## 3. Ожидаемый протокол действий L2

1. Провести единый пакетный замер 22 тест-сьютов Секции XXI в Docker Maven 3.9.9 Temurin-21 (`-m 2g`):
   ```bash
   docker run --rm -m 2g -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 \
     mvn test -Dtest=TocOptimizerTest,TocNodeTimeoutTest,TocPersistenceSnapshotTest,EneikProductionApplicationShutdownCompactTest,LogScopeBufferTest,LogScopeAndDefectJournalInusFalsificationTest,DefectJournalRootCauseAttributionTest,InfrastructureVerdictLayerTest,AcceptanceVerdictLayerTest,RuntimeVerdictLayerTest,VerdictReconciliationTest,VerdictGateTest,TaskEntityLaw20Test,DeliveryQuestionRecordTest,DecompositionVerdictTest,LeanValueTest,AccountStatusConjunctionTest,TargetContextTest,TaskAcceptanceCriteriaGuardTest,TaskEntityLaw1CarrierTest,DeliveryVerificationPredicateTest,DefectJournalServiceTest
   ```
2. Подтвердить 133/133 green (0 failures, 0 errors, BUILD SUCCESS).
3. Соблюсти принцип Ступени 4: 0 строк правок рабочего кода сервисов и сущностей (`src/main/java`).
4. Зафиксировать статус ideal в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Кратко (не более 15–20 строк) отчитаться в `docs/reports/PROGRAMMER_STATE.md`.
6. Сделать семантический коммит: `feat(section-xxi): complete Stage 4 batch verification (D008, D010, D006)`.
