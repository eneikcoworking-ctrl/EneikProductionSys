# Директивы и наряд для L2 (2026-10-16 11:20 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XVII, XVIIб, XIX и XX («Обездвиженное ядро, неназванная периферия контроллеров, внешние сайдкары и потоковые хранилища») успешно заверен инженером (коммит `02f10e1`, 158/158 green в едином пакетном прогоне в Docker Maven, 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующей стратегической секции фабрики — **Секции XXI («Сущности, несущие поведение, девять пространств состояний, алгебра решений, граф ограничений, лестница доверия, журнал дефектов и уплотнение хранилища»: подразделы XXI, XXIб, XXIв, XXIг, XXIд, XXIе, XXIж, XXIз)** целиком (22 тест-сьюта, 133 теста в едином пакетном прогоне в Docker Maven):
     * **Подраздел XXI: Сущности, несущие поведение (27 тестов, 6 сьютов):**
       - `TaskEntityLaw20Test` (6/6), `DeliveryQuestionRecordTest` (3/3), `TaskAcceptanceCriteriaGuardTest` (1/1), `TaskEntityLaw1CarrierTest` (5/5), `DeliveryVerificationPredicateTest` (6/6), `DecompositionVerdictTest` (5/5) — разведение трех исходов доставки (`deliveryRuledByCriteria`, `deliveryQuestionPutButUnsettled`, `isDeliveryVerificationAbsent`), изоляция носителя Закон 1, абсолютная защита от перезаписи терминальных статусов Закон 20, носитель требований `WishlistEntity`, разграничение отказа декомпозиции и недостижимости компилятора (`decompositionRefused` vs `decompositionUnreached`), разделение самопоказания фабрики и записи канала (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
     * **Подраздел XXIб: Девять пространств состояний (20 тестов, 3 сьюта):**
       - `LeanValueTest` (9/9), `AccountStatusConjunctionTest` (7/7), `TargetContextTest` (4/4) — строгая типизация ценности Lean без ложного одобрения муды, двухконъюнктная модель статуса аккаунта `status && enabled` без скрытых блокировок, цель задачи с явным значением `undetermined` вместо слепого умолчания репозитория (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
     * **Подраздел XXIв: Алгебра решений и решётка вердиктов (46 тестов, 5 сьютов):**
       - `VerdictGateTest` (14/14), `VerdictReconciliationTest` (11/11), `RuntimeVerdictLayerTest` (6/6), `AcceptanceVerdictLayerTest` (7/7), `InfrastructureVerdictLayerTest` (8/8) — монотонность сужения разрешений решётки готовности (гейт может только ужесточить суждение), детерминированное согласование вердиктов (`WITHHOLD` доминирует), стратификация уровней вердиктов без категориального смешения (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
     * **Подразделы XXIг, XXIд, XXIе, XXIж, XXIз: Граф ограничений, память об отказах и хранилище (40 тестов, 8 сьютов):**
       - `TocOptimizerTest` (7/7), `TocNodeTimeoutTest` (4/4), `TocPersistenceSnapshotTest` (4/4) — граф ограничений ТОС, Welford-тайминги и персистентные снимки памяти барабана-буфера-верёвки (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `DEREK_PARFIT_01_PERSISTENCE_SNAPSHOT` / D010, `DZHONATAN_SHAFFER_18_PERSISTENCE_SNAPSHOT` / D010);
       - `DefectJournalServiceTest` (3/3), `DefectJournalRootCauseAttributionTest` (6/6), `LogScopeAndDefectJournalInusFalsificationTest` (5/5) — память о собственных отказах фабрики, детерминированная каузальная атрибуция причин INUS (`DZHON_MEKI_01_INUS_FACTOR_CHECK` / D007, `AYZEK_LEVI_01_BELIEF_UPDATE_LEDGER` / D007);
       - `LogScopeBufferTest` (6/6) — изоляция буфера логов от посторонних промптов кодера (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `EneikProductionApplicationShutdownCompactTest` (6/6) — компактное уплотнение H2-хранилища при завершении работы без раздувания базы на диске (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код подсистем фабрики, сущностей, перечислений, гейтов и хранилищ (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D002, D004, D007, D013)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация: воспроизводимые заслоны недостижимости компилятора (`DecompositionVerdictTest`), решётки готовности вердиктов (`VerdictGateTest`), Welford-таймингов TOC (`TocNodeTimeoutTest`) и инвариантов доставки (`DeliveryQuestionRecordTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции: стратификация уровней вердиктов (`RuntimeVerdictLayer`, `AcceptanceVerdictLayer`, `InfrastructureVerdictLayer`), изоляция буфера `LogScope` и сохранение каузальной цепи данных.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Непреодолимая защита от перезаписи терминальных статусов (Закон 20), деонтические барьеры допуска к раздаче.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Paraconsistent quarantine / Belnap]:**
  * 4-значная логика истинности: разведение 3 исходов доставки (`deliveryRuledByCriteria`, `deliveryQuestionPutButUnsettled`, `isDeliveryVerificationAbsent`), конъюнкция статуса аккаунта (`status && enabled`), невыразимость «неизвестно» в закрытых пространствах состояний.
- **`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error scan / Ryle]:**
  * Предотвращение категориальных ошибок: разделение отказа декомпозиции и недостижимости компилятора, защита от смешения ролей и уровней вердиктов.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` [D004 Part-whole ownership / Varzi & Shaffer]:**
  * Монополия владения агрегатами: изоляция носителя Закон 1, целостность графа ограничений TOC.
- **`DZHON_MEKI_01_INUS_FACTOR_CHECK` & `AYZEK_LEVI_01_BELIEF_UPDATE_LEDGER` [D007 Belief update without ledger / Mackie & Levi]:**
  * Каузальная атрибуция причин INUS и реестр обновления убеждений в журнале дефектов фабрики.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Честное отражение фактов рантайма: компактное уплотнение хранилища H2 на выходе (`SHUTDOWN COMPACT`).

---

## 3. Ожидаемый протокол действий L2

1. **Единый пакетный запуск тестов Секции XXI:**
   - **Docker Maven (133 теста, 22 сьюта):**
     ```bash
     docker run --rm -m 2g -v /root/.m2:/root/.m2 -v /opt/EneikProductionSys:/app -w /app maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=TaskEntityLaw20Test,DeliveryQuestionRecordTest,TaskAcceptanceCriteriaGuardTest,TaskEntityLaw1CarrierTest,DeliveryVerificationPredicateTest,DecompositionVerdictTest,LeanValueTest,AccountStatusConjunctionTest,TargetContextTest,VerdictGateTest,VerdictReconciliationTest,RuntimeVerdictLayerTest,AcceptanceVerdictLayerTest,InfrastructureVerdictLayerTest,TocOptimizerTest,TocNodeTimeoutTest,TocPersistenceSnapshotTest,DefectJournalServiceTest,DefectJournalRootCauseAttributionTest,LogScopeAndDefectJournalInusFalsificationTest,LogScopeBufferTest,EneikProductionApplicationShutdownCompactTest
     ```
2. **Верификация результата:**
   - Строго **133/133 green** (0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
3. **Инвариант Ступени 4:**
   - 0 строк изменений рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`). Запрещено менять код при проходящих тестах!
4. **Фиксация в документации и коммит:**
   - Зафиксировать статус идеала Секции XXI в `docs/FACTORY_MECHANISMS.md`.
   - Заверить закрытие пунктов Секции XXI в `docs/ANTIGRAVITY_QUEUE.md`.
   - Обновить краткий отчёт (до 15–20 строк) в `docs/reports/PROGRAMMER_STATE.md`.
   - Закоммитить: `feat(section-xxi): complete Stage 4 batch verification (D008, D010, D006)`.
