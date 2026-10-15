# Пакетная директива L1/L3 -> L2: Секция II (Компиляция: требование становится задачами)

**Дата:** 2026-10-15 18:07 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секция I («Приём требования: рыночный корпус, уставный гейт и полевое исследование») полностью замерена и закрыта инженером (165/165 green в коммите `d492a6c`). Настоящая директива охватывает целиком следующий фундаментальный массив фабрики — **Секцию II («Компиляция: требование становится задачами»)**:
   - **Контур II.1. `TechnicalLeadCompiler` (Searle D007, Varzi D002, Raz D006):**
     * Институциональный регистр Серла (`DZHON_SERL_05_INSTITUTIONAL_FACT_REGISTER` / D007): трёхзначный статус аудита области файлов `NamespaceAuditStatus` (`ADMISSIBLE`, `VIOLATION`, `UNKNOWN_NAMESPACE`). Отсутствие неявного пропуска (`no silent pass`): неустановленный namespace (`UNKNOWN_NAMESPACE`) не проходит в отправку и регистрируется в дефектном журнале (`auditPathsAgainstNamespace`).
     * Реестр объектов и границ Варци (`AHILLE_VARTSI_01_ACTUAL_OBJECT_REGISTER` / D002): разделение задач и притязаний на файлы (`ProjectFileClaimEntity`), защита корня продукта клиента.
     * Деонтические исключающие основания Раза (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006): бюджет компиляции (`WISHLIST_COMPILE_ATTEMPT_BUDGET = 3`), отсечение нерелевантных путей (`pathsOutsideProductNamespace`).
   - **Контур II.2. `FeatureService` (Varzi D002, Raz D006):**
     * Реестр актуальных объектов Варци (`AHILLE_VARTSI_01_ACTUAL_OBJECT_REGISTER` / D002): личность эпика чеканится строго лениво (`resolveOrCreateFeatureId`) — требование, не ставшее работой, эпика не получает.
   - **Контур II.3. `EpistemicMetadataClassifier`, `KanoClass`, `EmsFlowStage` (Goldman D010, Kripke D001):**
     * Лок-уровень абстракции Голдмана (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010): классификатор метаданных на пустом входе или тексте без маркеров не выдумывает средних величин (`emptyInputExtractsNothingRatherThanGuessingAMiddleValue`).
     * Жесткие десигнаторы Крипке (`ANCHOR_BOUND_NAME` / D001): единственные экземпляры правил чтения классов Кано и стадий потока EMS; отсутствующий класс никогда не объявляется дефолтным `must-be` (`anAbsentClassIsNeverInventedAsMustBe`).
   - **Контур II.4. `SelfFalsificationEpicMatcher`, `WishlistContentSimilarityMatcher` (Belnap D009, Goldman D010):**
     * Оракул подстановок Белнапа (`NUEL_BELNAP_06_SUBSTITUTION_ORACLE` / D009): при равных весах конкурирующих эпиков возвращается пустой результат (`ambiguousTiedCandidatesReturnEmpty`), исключая ложную привязку.
     * Каузальная цепочка Голдмана (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010): бимодальная кластеризация подобия требований без искусственных фиксированных порогов.
   - **Контур II.5. `WishlistService` (Varzi D002, Goldman D010):**
     * Чистота реестра требований: метод `purgeGhostWishlists` точно изолирует фантомы, исключая удаление клиентских требований.
   - **Контур II.6. `StrandedFinalizingSweepService` (Gärdenfors D007, Salmon D013, Dworkin D012):**
     * Журнал пересмотра убеждений Гэрденфорса (`PITER_GERDENFORS_01_BELIEF_UPDATE_LEDGER` / D007): динамический расчет предела безопасной аренды `calculateEffectiveLeaseDuration` по медиане выборки дефектного журнала `FINALIZING_DURATION` (с 10x запасом безопасности и порогом >= 30 сек).
     * Каузальный след Сэлмона (`UESLI_SELMON_02_CAUSAL_PROCESS_TRACE` / D013): возраст заявки в `finalizing` отсчитывается строго от каузальной метки `finalizingSince` (миграция `V139`), а не сторонних меток `createdAt` или `lastCompileDispatchedAt`.
     * Принципиальная целостность Дворкина (`RONALD_DVORKIN_04_PRINCIPLED_INTEGRITY` / D012): атомарный CAS (`compareAndSetStatus(w.getId(), finalizing, pending)`) защищает активных воркеров от сноса.
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено модифицировать рабочий код сервисов (`src/main/java`) при проходящих тестах!
   - Механизмы Секции II уже полностью реализованы в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D007, D013, D012, D002, D009, D001)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green, Popper]:**
  * Попперовская фальсификация: тесты-заслоны обязаны падать при попытке silently pass'ить чужой пакет (`UNKNOWN_NAMESPACE`), выдумывать класс Кано или сбрасывать активного воркера в `StrandedFinalizingSweepService`;
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss, Goldman]:**
  * Лок-уровень абстракции: классификатор метаданных на пустом входе выдает пустой результат вместо подгонки под среднее;
  * Каузальная надежность кластеризации требований без произвольных пороговых констант;
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity, Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода сервисов Секции II при проходящих тестах;**
  * Деонтические исключающие основания: запрет генерации задач вне доказанного неймспейса, лимит попыток декомпозиции, запрет сноса живых исполнителей;
- **`DZHON_SERL_05_INSTITUTIONAL_FACT_REGISTER` [D007 Fact drift, Searle]:**
  * Трёхзначный институциональный статус аудита `NamespaceAuditStatus` (`ADMISSIBLE`, `VIOLATION`, `UNKNOWN_NAMESPACE`);
- **`PITER_GERDENFORS_01_BELIEF_UPDATE_LEDGER` [D007 Belief revision, Gärdenfors]:**
  * Динамический пересмотр лимита аренды `finalizing` на основе эмпирической выборки дефектного журнала;
- **`UESLI_SELMON_02_CAUSAL_PROCESS_TRACE` [D013 Runtime drift, Salmon]:**
  * Отсчет времени аренды по прямой каузальной метке `finalizingSince` (Flyway V139);
- **`RONALD_DVORKIN_04_PRINCIPLED_INTEGRITY` [D012 Integrity violation, Dworkin]:**
  * Защита от race condition через атомарный CAS (`compareAndSetStatus`), сохранение целостности системы;
- **`AHILLE_VARTSI_01_ACTUAL_OBJECT_REGISTER` & `ANCHOR_BOUND_NAME` [D002 & D001 Ontology & Rigid designators, Varzi & Kripke]:**
  * Ленивая чеканка эпиков, сохранение семантики классов и стадий во всех контекстах вызова.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий пакетный замер механизмов Секции II в Docker Maven (`docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=...`).
2. Подтвердить 100% green статус по ключевым классам-заслонам Секции II:
   - Компиляция и аудит пространств имен: `TechnicalLeadCompilerTest`, `TechnicalLeadCompilerFalsificationTest`, `TechnicalLeadCompilerIntegrationTest`, `CompilerTaskIdentityTest`, `WishlistCompileBudgetLaw9Test`;
   - Эпики и классификация: `FeatureServiceTest`, `FeatureServiceFalsificationTest`, `EpistemicMetadataClassifierTest`, `EpistemicMetadataClassifierFalsificationTest`, `KanoClassTest`, `KanoClassFalsificationTest`, `EmsFlowStageTest`, `EmsFlowStageFalsificationTest`;
   - Сличение и подобие: `SelfFalsificationEpicMatcherTest`, `SelfFalsificationEpicMatcherFalsificationTest`, `WishlistContentSimilarityMatcherTest`, `WishlistContentSimilarityMatcherFalsificationTest`, `WishlistSimilarityTest`, `WishlistSimilarityKripkeIntensionFalsificationTest`;
   - Заявки и освобождение застрявших состояний: `WishlistServiceFalsificationTest`, `WishlistControllerIntegrationTest`, `StrandedFinalizingSweepServiceTest`, `StrandedFinalizingSweepServiceFalsificationTest`, `StrandedFinalizingDynamicTimeoutFalsificationTest`.
3. Зафиксировать форму образца и актуализировать статус Секции II в `docs/FACTORY_MECHANISMS.md` (добавить итоговую секцию `### Итог Секции II`, подтверждающую Ступень 4 closed, ideal, 100% green).
4. Синхронизировать `docs/ANTIGRAVITY_QUEUE.md`.
5. Оформить лаконичный семантический коммит фабрики.
