# Пакетная директива L1/L3 -> L2: Секция VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Оркестрация»)

**Дата:** 2026-10-16 00:55 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Фабрика функционирует автономно в замкнутом цикле без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секция VII (TOC) уже полностью заверена и закрыта инженером. Настоящая директива охватывает целиком следующий стратегический кластер фабрики — **Секцию VIII («Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Оркестрация»)**:
   - **`FalsificationCycleService`:** суточный цикл по дефектам кода, недельный философский цикл фальсификации ложных зеленых, исключительное владение агрегатами `falsification_runs` и `code_integrity_findings` (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004);
   - **`EvidenceCoherenceService`:** релаксация графа свидетельств по алгоритму ECHO Тагарда, объяснительная связность без ложной уверенности на пустых данных (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` / D011);
   - **`OpsAuditorService`:** аудитор операций на независимо проверенных фактах (`independently-verified facts`) с обязательной тройной валидацией предусловий перед записью действий (`ELVIN_GOLDMAN_02_KNOWLEDGE_FIRST_GATE` / D006, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
   - **`PlatformSelfReferenceDetector`:** отсечение внутризаводского шума и самореференций платформы от продуктового бэклога заказчика (`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
   - **`FactorySelfHealthService`:** непрерывный мониторинг собственного здоровья фабрики (размер БД, пул соединений, очереди задач) с эскалацией в Кайдзен (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
   - **`KaizenService` & `DefectJournalService`:** жизненный цикл предложений микро-улучшений PDCA, дедупликация по `(projectId, category, targetComponent)` с фиксацией в журнале дефектов (`MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` / D007);
   - **`FlowMetricsService`:** расчет времени цикла, WIP, throughput и проверка сходимости закона Литтла с варнингом при расхождениях (`ALONZO_CHERCH_03_LAMBDA_CORE_REDUCTION` / D008, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010);
   - **`LeverPromotionService`:** лестница продвижения рычагов от `observe_only` к активным действиям на основе накопленных согласованных наблюдений (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
   - **`PlannedWorkRecoveryService`:** восстановление упавших задач со строгим сохранением стабильной идентичности задачи и запретом паразитного размножения задач/заявок (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002);
   - **`ContinuousOrchestrationService`:** единый детерминированный тик оркестрации фабрики без неконтролируемых сторонних фоновых кронов (`MAJKL_BRATMAN_01_SHARED_COOPERATIVE_ACTIVITY` / D004, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
   - **`OperationalPolicyService`, `OperationalFlowCoreService`, `OperationalTruthService`, `OperationalAction`, `FlowSpineService`:** деонтические заслоны переходов политики, честное отражение метрик рантайма (`done_is_not_delivery`), 4-значная логика истинности (`ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` / D008, `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
   - **`TrustSnapshotService`:** снимки входных сигналов доверия с отложенным дозаполнением фактических исходов сдачи/отклонения (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено модифицировать рабочий код сервисов, моделей, контроллеров и скриптов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы Секции VIII полностью работоспособны и заслонены тестами. Задача инженера L2 — подтвердить нерушимость инвариантов строгим пакетным замером в рантайме без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D012, D013, D004, D002, D007)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green, Popper & Tarski]:**
  * Фальсификация ложных зеленых: проверка устойчивости суточного цикла фальсификации; заслон от фиктивного прохождения проверок без реального подтверждения фактов.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss, Goldman]:**
  * Лок уровней абстракции: поток метрик (WIP, lead time, throughput) изолирован от проекций интерфейса; релайабилизм процессов (источник полномочий важнее субъективной уверенности); снимки доверия привязаны к каузальной цепи исходов.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity, Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода при проходящих тестах;**
  * Деонтические запреты рантайма: строгие барьеры переходов в `OperationalPolicyService` и `OpsAuditorService` (запрет мутаций при неустановленном предусловии).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` & `ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` [D012 Truth status, Belnap & Tarski]:**
  * 4-значная логика истинности (`passed`, `failed`, `undetermined`, `not-applied`) в `OperationalTruthService` и `FlowSpineService`.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift, Wittgenstein]:**
  * Телеметрия отражает физическую реальность без самоукрашения: инвариант `done_is_not_delivery`, честные предупреждения о расхождениях.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Concurrency conflict, Varzi]:**
  * Монопольное владение агрегатами: `falsification_runs` и `code_integrity_findings` принадлежат только `FalsificationCycleService`; стабильная идентичность задач в `PlannedWorkRecoveryService`.
- **`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error, Ryle]:**
  * Детекция категориальных ошибок: платформенный шум никогда не просачивается в продуктовый скоуп клиента (`PlatformSelfReferenceDetector`).

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий пакетный замер механизмов Секции VIII в Docker Maven:
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=FalsificationCycleServiceTest,EvidenceCoherenceServiceTest,EvidenceCoherenceV80TerminationFalsificationTest,OpsAuditorServiceTest,PlatformSelfReferenceDetectorTest,FactorySelfHealthServiceTest,KaizenServiceTest,DefectJournalServiceTest,DefectJournalRootCauseAttributionTest,LogScopeAndDefectJournalInusFalsificationTest,FlowMetricsServiceTest,LeverPromotionServiceTest,LeverPromotionServiceFalsificationTest,PlannedWorkRecoveryServiceTest,ContinuousOrchestrationServiceTest,OperationalFlowCoreServiceTest,OperationalFlowCoreFalsificationTest,OperationalPolicyServiceTest,OperationalPolicyReasonTest,OperationalPolicyDeniedExceptionFalsificationTest,TrustSnapshotServiceTest,OperationalTruthServiceTest,FlowSpineServiceTest
   ```
2. Подтвердить статус `ideal` в `*Живое:*` блоков Секции VIII в `docs/FACTORY_MECHANISMS.md`, оформить блок `### Итог Секции VIII`, синхронизировать `docs/ANTIGRAVITY_QUEUE.md`.
3. Составить краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
4. Закоммитить с семантическим сообщением `feat(section-viii): complete Stage 4 batch verification (D008, D010, D006)`.
