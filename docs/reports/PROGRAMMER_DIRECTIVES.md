# Пакетная директива L1/L3 -> L2: Секции XIV и XV (Философский словарь дефектов, Grounding Guard и защита от тихого размножения задач)

**Дата:** 2026-10-15 16:04 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Предыдущие этапы (включая Секцию VIII с 279 тестами green в коммите `0ec3555`) успешно замерены и закрыты. Настоящая директива охватывает целиком следующий стратегический кластер фабрики — **Секции XIV и XV**:
   - **Секция XIV. Сверка с корпусом философских образцов и Grounding Guard:**
     * XIV.1. `docs/philosopher-patterns` как внешний словарь дефектов (`KIT_DEROUZ_01_RELIABILITY_CHAIN` / D010);
     * XIV.2. Defect-family application map (`KIT_DEROUZ_13_LEVEL_OF_ABSTRACTION_LOCK` / D010);
     * XIV.3. Grounding guard against hallucinated mechanism records (`DZHUDA_PERL_15_RAG_GROUNDING_CAPSULE` / D014, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008) — заслоны `RequirementGroundingService`, `PhilosopherPatternCorpusConsistencyTest`.
   - **Секция XV. Класс дефекта «тихое размножение задач»: подпись, замер, случаи:**
     * XV.1. Доменная подпись тихого размножения задач (`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002) — разделение залипших дубликатов (`StuckDuplicateContent`) и скорости их порождения (`DuplicateGenerationVelocity`);
     * XV.2. Duplicate velocity journaler (`ALFRED_TARSKIY_10_INSTITUTIONAL_FACT_REGISTER` / D007) — `TaskDuplicateDetector`, `ContinuousOrchestrationService.checkDuplicateGenerationVelocity`, `DefectJournalService`;
     * XV.3. Recovery/reset generator boundary (`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` / D004) — `PlannedWorkRecoveryService`, сохранение неизменной идентичности задачи, категорический запрет лавинного порождения дубликатов задач, веток или сессий;
     * XV.4. Compiler work identity and completion admission (`DZHONATAN_SHAFFER_18_PERSISTENCE_SNAPSHOT` / D010) — `TaskEntity.contentKey` (`compile:<projectId>:<hash>`), `TechnicalLeadCompiler.findLiveSemanticTask`, `SemanticDuplicateVeto`, `JulesDispatchService.admitWishlistCompilationCompletion` с CAS-блокировкой `finalizing`.
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено трогать рабочий код сервисов и тестов при проходящих тестах!
   - Механизмы обладают сильной формой. Любая попытка несанкционированного «рефакторинга» работающего сервисного кода признается грубейшим нарушением регламента автономии.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green, Popper]:**
  * Запрет поверхностных проверок («код компилируется — значит работает»). Заслоны обязаны проверять пограничные условия и инварианты отказа:
  * В `PhilosopherPatternCorpusConsistencyTest` — доказательство отсутствия галлюцинированных паттернов и целостности связей с корпусом философов;
  * В `RequirementGroundingServiceFalsificationTest` — доказательство немедленной классификации невалидированных требований как ungrounded без ложной зелени;
  * В `TechnicalLeadCompilerFalsificationTest` — проверка инвариантов пространства имен (Закон 26) и предотвращение коллизий файлов.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `KIT_DEROUZ_01_RELIABILITY_CHAIN` [D010 Data lineage loss, Goldman/DeRose]:**
  * Лок уровней абстракции и тождества задач: `TaskEntity.contentKey` сохраняет связь семантического намерения и материализованной строки задачи;
  * Защита от потери родословной в компиляторе (`CompilerTaskIdentityTest`, `SemanticDuplicateVetoTest`): предотвращение повторной компиляции одного и того же требования в параллельные графы;
  * Сохранение надежности при восстановлении: `PlannedWorkRecoveryService` возвращает существующую задачу рубежа и не плодит новых артефактов.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity, Raz]:**
  * **Императивный запрет инженеру L2 на модификацию рабочего кода сервисов Секций XIV и XV при проходящих тестах;**
  * Деонтический запрет повторного входа и размножения задач при компиляции вишлистов;
  * Запрет размывания ответственности между фиксацией скорости дубликатов (наблюдение) и блокировкой выполнения (гейт).

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий замер целостности механизмов в Docker Maven (`docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=...`).
2. Подтвердить 100% green статус по всем ключевым классам-заслонам Секций XIV и XV:
   - `PhilosopherPatternCorpusConsistencyTest`, `RequirementGroundingServiceTest`, `RequirementGroundingServiceFalsificationTest`;
   - `TaskDuplicateDetectorTest`, `CompilerTaskIdentityTest`, `SemanticDuplicateVetoTest`, `PlannedWorkRecoveryServiceTest`;
   - `TechnicalLeadCompilerTest`, `TechnicalLeadCompilerIntegrationTest`, `TechnicalLeadCompilerFalsificationTest`.
3. Зафиксировать форму образца и актуализировать строки `*Живое:*` в `docs/FACTORY_MECHANISMS.md` (Ступень 4 closed, ideal).
4. Оформить лаконичный семантический коммит фабрики.
5. Обновить `docs/reports/PROGRAMMER_STATE.md` (до 20 строк).
