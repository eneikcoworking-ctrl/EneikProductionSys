# Пакетная директива L1/L3 -> L2: Секция IV (Ревью, гейт, слияние и GitHub-инварианты)

**Дата:** 2026-10-15 17:00 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секция VIII (Самопроверка, Flow & Metrics, Операционная Правда, Кайдзен, Фальсификация и Оркестрация) полностью подтверждена и закрыта инженером (281/281 green в коммите `97fb509`). Настоящая директива охватывает целиком следующий фундаментальный массив фабрики — **Секцию IV («Ревью, гейт, слияние»)**:
   - **Контур IV.1. Арбитраж слияния и защита целостности ядра (Popper D008, Goldman D010, Raz D006):**
     * `AutoMergeService` — арбитр слияния, проверка обязательного наличия кода (`hasRequiredMergeEvidence`), деонтический запрет мутации файлов куайновского ядра (`QuineanCoreViolation`), терминация задач и возврат карточек вытягивания (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
     * `PrReviewPipelineService` — сквозной конвейер ревью с деонтическим форсированием `riskLevel = high` при изменении критических путей фабрики (`ClaimService`, `GateOrchestrator`) или падающем CI (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
   - **Контур IV.2. Заслоны качества и оркестрация гейтов (Belnap D012, Popper D008, Varzi D004, Raz D006):**
     * `VerificationEvidenceGate` — заслон свидетельств верификации: DoDGate, AcceptanceCriteriaGate, BusinessValueGate, BackendContractGate, RepoUrlGate, ActiveRoleGate (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
     * `GateOrchestrator` — детерминированная композиция и оркестрация обязательных проверок качества перед допуском к слиянию (`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
     * `CodeChangeClassifier` — категориальная типизация изменений кода (production, test, migration, config, docs) без смешения уровней абстракции (`GILBERT_RAYL_02_CATEGORY_ERROR_SCAN` / D002).
   - **Контур IV.3. GitHub-транспорт, бюджет квот и санитария веток (Wittgenstein D013, Church D010, Salmon D013, Raz D006):**
     * `GitHubPullRequestService` — транспортный контур взаимодействия с GitHub, работа с физическими байтами диффов и ветками без фантомных подмен (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013);
     * `GitHubApiBudgetService` — динамический учет квот GitHub API по токенам, предупреждение исчерпания лимитов (`ALONZO_CHERCH_21_DERIVED_CUTOFF` / D010);
     * `GithubAccessService` — верификация прав доступа к целевым репозиториям (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
     * `BranchGarbageCollectorService` — причинная трассировка процессов и безопасная утилизация завершенных/заброшенных веток без потери данных (`UESLI_SELMON_02_CAUSAL_PROCESS_TRACE` / D013).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено трогать рабочий код сервисов и тестов при проходящих тестах!
   - Механизмы Секции IV уже реализованы в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D013, D012, D002, D004)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green, Popper]:**
  * Заслон слияния в `AutoMergeService`: попытка слить PR без доказанного кода (`hasRequiredMergeEvidence = false`) опровергает ложную успешность и отправляет задачу на повторный цикл;
  * Фальсификация рисков в `PrReviewPipelineService`: расхождение между заявленным и реальным статусом риска.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss, Goldman]:**
  * Лок уровней абстракции артефактов поставки: разделение фактов компиляции, тестов и слияния;
  * Цепочка надежности свидетельств верификации в `VerificationEvidenceGate` и бюджетирования в `GitHubApiBudgetService`.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity, Raz]:**
  * **Императивный запрет инженеру L2 на модификацию рабочего кода сервисов Секции IV при проходящих тестах;**
  * Деонтический заслон ядра: запрет мутации файлов миграций и core-слоя периферийными задачами (`QuineanCoreViolation`);
  * Исключающее основание: автоматическое форсирование `riskLevel = high` при наличии изменений в критических сервисах.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Belnap 4-valued logic]:**
  * Разделение статусов гейтов: `passed`, `failed`, `undetermined`, `unapplied` — невозможность трактовать отсутствие данных как успешную проверку.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift, Wittgenstein]:**
  * Анти-зеркало GitHub-транспорта: работа строго с реальным состоянием удаленного репозитория и физическими диффами.
- **`GILBERT_RAYL_02_CATEGORY_ERROR_SCAN` [D002 Category error, Ryle]:**
  * Разделение категорий изменений (код продукта vs тесты vs инфраструктура) в `CodeChangeClassifier`.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий пакетный замер механизмов Секции IV в Docker Maven (`docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=...`).
2. Подтвердить 100% green статус по ключевым классам-заслонам Секции IV:
   - `AutoMergeServiceTest`, `AutoMergeServiceFalsificationTest`;
   - `VerificationEvidenceGateTest`, `QualityGateSuiteTest`;
   - `GateOrchestratorTest`, `GateOrchestratorFalsificationTest`;
   - `GitHubPullRequestServiceTest`;
   - `GitHubApiBudgetServiceTest`;
   - `GithubAccessServiceTest`;
   - `CodeChangeClassifierTest`, `CodeChangeClassifierLaw16Test`;
   - `BranchGarbageCollectorServiceTest`, `BranchGarbageCollectorServiceFalsificationTest`;
   - `PrReviewPipelineServiceTest`, `PrReviewPipelineServiceFalsificationTest`.
3. Зафиксировать форму образца и актуализировать статус Секции IV в `docs/FACTORY_MECHANISMS.md` (Ступень 4 closed, ideal, добавить `### Итог Секции IV`).
4. Синхронизировать `docs/ANTIGRAVITY_QUEUE.md`.
5. Оформить лаконичный семантический коммит фабрики.
6. Обновить `docs/reports/PROGRAMMER_STATE.md` (до 20 строк).
