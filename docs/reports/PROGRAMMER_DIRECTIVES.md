# Директивы и наряд для L2 (2026-10-16 18:13 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Секции XXIV и XXV («Скрипты и Позвоночник потока») успешно заверены инженером (коммит `976feb6`, 169/169 green: 149 в Docker Maven + 20 в Python, 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующей стратегической секции — **Секция VIII Flow & Metrics («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Оркестрация»)** целиком (**290 тестов в 25 тест-сьютах в едином пакетном прогоне в Docker Maven**):
     * **Группа 1: Фальсификационный цикл, связность свидетельств и самопроверка (Секция VIII — 71 тест):**
       - `FalsificationCycleServiceTest` (28/28 green): суточный цикл фальсификации, монопольное владение `falsification_runs` и `code_integrity_findings` (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
       - `EvidenceCoherenceServiceTest` (21/21 green), `EvidenceCoherenceV80TerminationFalsificationTest` (5/5 green): ECHO Тагарда на графе свидетельств, расчет связности без натягивания пустых данных (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `DZHON_MEKI_01_INUS_CONDITION` / D007);
       - `OpsAuditorServiceTest` (11/11 green): независимый аудит операций с тройной проверкой предусловий (`ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` / D006);
       - `PlatformSelfReferenceDetectorTest` (7/7 green): отсечение фабричного шума от задач клиента (`GILBERT_RAYL_02_CATEGORY_ERROR_SCAN` / D002);
       - `FactorySelfHealthServiceTest` (5/5 green): самодиагностика системных ресурсов фабрики, пула соединений и размера хранилища (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006).
     * **Группа 2: Кайдзен, журнал дефектов, метрики потока и продвижение рычагов (Секция VIII — 51 тест):**
       - `KaizenServiceTest` (10/10 green), `DefectJournalServiceTest` (3/3 green), `DefectJournalRootCauseAttributionTest` (6/6 green), `LogScopeAndDefectJournalInusFalsificationTest` (5/5 green): журнал дефектов, INUS-условие Мэки и причинная атрибуция сбоев (`DZHON_MEKI_01_INUS_CONDITION` / D007, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
       - `FlowMetricsServiceTest` (4/4 green): расчет показателей потока Lean/TOC без подъема сырых таблиц в память (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
       - `LeverPromotionServiceTest` (7/7 green), `LeverPromotionServiceFalsificationTest` (4/4 green): подчинение ограничению по ТОС и строгое продвижение рычагов по свидетельствам (`ELI_GOLDRATT_01_TOC_SUBORDINATION` / D006, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `PlannedWorkRecoveryServiceTest` (15/15 green): безопасное восстановление незавершенной работы (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006).
     * **Группа 3: Оркестрация, операционное ядро, политики и операционная правда (Секция VIII — 168 тестов):**
       - `ContinuousOrchestrationServiceTest` (16/16 green): непрерывная оркестрация, защита от тихого размножения задач (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `FlowSpineServiceTest` (32/32 green): позвоночник потока, чистое чтение DBR-снимка без побочных эффектов (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
       - `OperationalFlowCoreServiceTest` (6/6 green), `OperationalFlowCoreFalsificationTest` (39/39 green): жесткие фальсифицируемые инварианты жизненного цикла потока (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
       - `OperationalPolicyServiceTest` (17/17 green), `OperationalPolicyReasonTest` (5/5 green), `OperationalPolicyDeniedExceptionFalsificationTest` (9/9 green): деонтические запреты операций и отказы политик (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006);
       - `TrustSnapshotServiceTest` (9/9 green), `OperationalTruthServiceTest` (17/17 green): асимметричная динамика доверия Голдмана и 4-значная решётка истинности Белнапа (`ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` / D010, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012);
       - `MarketResearchServiceTest` (3/3 green), `MarketResearchServiceFalsificationTest` (6/6 green): исследование рынка и фальсификационный гейт рыночной ценности (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D013, D012)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификация операционного ядра (`OperationalFlowCoreFalsificationTest`), завершения связности V80 (`EvidenceCoherenceV80TerminationFalsificationTest`), рыночного гейта (`MarketResearchServiceFalsificationTest`) и субординации ТОС (`LeverPromotionServiceFalsificationTest`). Заслон от ложно-зеленых тестов.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и надежность цепочки происхождения данных. Запрет смешивания слоев агрегации метрик потока с сырыми данными таблиц базы. Асимметричный рост доверия строго по свидетельствам (`ELVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS`).
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтические запреты на уровне кода в `OperationalPolicyService` с типизированным отказом `OperationalPolicyDeniedException`.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало телеметрии в `FlowSpineService`: физическое состояние базы и продвижение задач всегда первичны по отношению к нарративу агента. Запрет мутаций на чтении.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Multi-valued Truth Status / Belnap]:**
  * Четырехзначная логика истинности в `OperationalTruthService` и `TrustSnapshotService`: при отсутствии данных возвращается `undetermined`, а не ложное благополучие.

---

## 3. Регламент отчёта и фиксации для L2

1. Провести единый непрерывный пакетный прогон тестов Секции VIII в Docker-контейнере Maven 3.9.9 Temurin-21 (`-m 2g`):
   `mvn test -Dtest=FalsificationCycleServiceTest,EvidenceCoherenceServiceTest,EvidenceCoherenceV80TerminationFalsificationTest,OpsAuditorServiceTest,PlatformSelfReferenceDetectorTest,FactorySelfHealthServiceTest,KaizenServiceTest,DefectJournalServiceTest,DefectJournalRootCauseAttributionTest,LogScopeAndDefectJournalInusFalsificationTest,FlowMetricsServiceTest,LeverPromotionServiceTest,LeverPromotionServiceFalsificationTest,PlannedWorkRecoveryServiceTest,ContinuousOrchestrationServiceTest,OperationalTruthServiceTest,FlowSpineServiceTest,OperationalFlowCoreServiceTest,OperationalFlowCoreFalsificationTest,OperationalPolicyServiceTest,OperationalPolicyReasonTest,OperationalPolicyDeniedExceptionFalsificationTest,TrustSnapshotServiceTest,MarketResearchServiceTest,MarketResearchServiceFalsificationTest` (290 тестов в 25 тест-сьютах).
2. Зафиксировать результат (290/290 green) и подтвердить актуальность строк `*Живое:*` в `docs/FACTORY_MECHANISMS.md` и статуса в `docs/ANTIGRAVITY_QUEUE.md`.
3. Составить краткий отчёт в `docs/reports/PROGRAMMER_STATE.md` (до 15–20 строк) со ссылкой на RAG-корпус.
4. Сделать семантический коммит в репозиторий фабрики:
   `feat(section-viii): complete Stage 4 batch verification (D008, D010, D006)`.
