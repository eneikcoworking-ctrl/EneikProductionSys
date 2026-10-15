# Пакетная директива L1/L3 -> L2: Ступень 4 (Семейства I и II: Рыночный корпус, онбординг и компиляция требований)

**Дата:** 2026-10-15 11:40 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Настоящая директива охватывает 16 механизмов Ступени 4 `docs/ANTIGRAVITY_QUEUE.md` (Семейства I и II `docs/FACTORY_MECHANISMS.md`):
   - **Семейство I (Онбординг, рабочий каталог и рыночный корпус):**
     * `ProjectFlowService` (ядро)
     * `ProjectFactoryService`
     * `ProjectWorkspaceFactoryService` (ядро)
     * `RequirementGroundingService`
     * `MarketCorpusService`
     * `MarketComplianceGate`
     * `MarketResearchService`
     * `OnboardingAuditService` (ядро)
   - **Семейство II (Компиляция требований в типизированные задачи и область файлов):**
     * `TechnicalLeadCompiler` (ядро)
     * `FeatureService` (ядро)
     * `EpistemicMetadataClassifier`
     * `KanoClass` (ядро)
     * `EmsFlowStage` (ядро)
     * `SelfFalsificationEpicMatcher`
     * `WishlistContentSimilarityMatcher`
     * `WishlistService` (ядро)
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ:** Запрещено трогать рабочий код при проходящих тестах!
   - Любая правка исходного кода допустима исключительно при наличии воспроизводимого падающего теста, фальсифицирующего системный инвариант. Если тесты зеленые — код признается нерушимым и фиксируется замером.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green, Popper]:** Принцип фальсификации. Заслоны верифицируют отказные пути и негативные инварианты:
  * `auditPathsAgainstNamespace` в `TechnicalLeadCompiler` выдает отказ `VIOLATION` или `UNKNOWN_NAMESPACE` и статус `BLOCKED` при попытке направить исполнителя в чужие пакеты или пакеты фабрики;
  * `RequirementGroundingService` при недоступности RAG-контекста безопасно деградирует без изменения авторского текста;
  * `MarketCorpusService` отсекает любые гипотезы и трактует нечитаемые сроки годности как просроченные;
  * `OnboardingAuditService` при отсутствии доступа не генерирует находок против репозитория клиента (`isUnchecked`);
  * `EpistemicMetadataClassifier` возвращает строгий `null` на пустой ввод вместо выдумывания среднего значения.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` [D010 Data lineage loss, Goldman]:** Лок уровней абстракции и надежность цепочки происхождения данных (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN`):
  * Разделение первичной интенсии клиента и вторичной капсулы архитектурного заземления (`RequirementGroundingService`);
  * Неизменяемая родословная `originFeatureId` от клиентского брифа до конечных коммитов в `FeatureService` и `TechnicalLeadCompiler`;
  * Уставные требования рынка несут обязательную ссылку на закон (`source`), а наблюдения — метод, выборку и дату.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity, Raz]:** Деонтический запрет как исключающая причина (`exclusionary reason`) и исполняемый код:
  * **Императивный запрет инженеру L2 на изменение рабочего кода сервисов компилятора и онбординга при проходящих тестах;**
  * Запрет компилятору молча пропускать задачи с неизвестным пространством имен файлов (`UNKNOWN_NAMESPACE`);
  * Запрет на создание сиротских задач без привязки к эпику `FeatureEntity`.

---

## 3. Пакетный наряд по механизмам Семейств I и II

### Группа 1. Онбординг, рабочий каталог и рыночный корпус
1. **`ProjectFlowService` (ядро):**
   - **Проба:** Замерить координацию жизненного цикла проекта, онбординга, проверки рынка и компиляции.
   - **Инвариант:** Никаких мутаций рабочего кода; заслоны `ProjectFlowServiceTest`, `ProjectFlowServiceFalsificationTest`, `ProjectFlowIntegrationTest`.
2. **`ProjectFactoryService` & `ProjectWorkspaceFactoryService` (ядро):**
   - **Проба:** Замерить безопасную изоляцию каталога workspace и предотвращение path traversal.
   - **Инвариант:** Код не трогать; заслон `ProjectWorkspaceFactoryServiceFalsificationTest`.
3. **`RequirementGroundingService`:**
   - **Проба:** Замерить заземление требований через RAG без искажения текста заказчика.
   - **Инвариант:** Дословное сохранение первичной интенсии; заслон `RequirementGroundingServiceTest`, `RequirementGroundingServiceFalsificationTest`.
4. **`MarketCorpusService` & `MarketComplianceGate` & `MarketResearchService`:**
   - **Проба:** Замерить строгость уставных требований (statutory), regex-границы `\b` и исследовательские задачи.
   - **Инвариант:** Код не трогать; заслоны `MarketCorpusServiceTest`, `MarketComplianceGateTest`, `MarketResearchServiceTest` и их `*FalsificationTest`.
5. **`OnboardingAuditService` (ядро):**
   - **Проба:** Замерить трехзначный статус `InspectionStatus` (`YES, NO, UNCHECKED`) и запрет находок при `UNCHECKED`.
   - **Инвариант:** Код не трогать; заслон `OnboardingAuditServiceTest`, `OnboardingAuditServiceFalsificationTest`.

### Группа 2. Компиляция требований и доменные классификаторы
6. **`TechnicalLeadCompiler` (ядро):**
   - **Проба:** Замерить компиляцию задач строго через эпики `FeatureEntity` и трехзначный аудит namespace (`ProductNamespaceLaw26Test`).
   - **Инвариант:** Блокировка задач при `VIOLATION`/`UNKNOWN_NAMESPACE`. Код не трогать.
7. **`FeatureService` (ядро):**
   - **Проба:** Замерить ленивую чеканку эпика `resolveOrCreateFeatureId` и фиксацию родословной `originFeatureId`.
   - **Инвариант:** Код не трогать; заслон `FeatureServiceTest`, `FeatureServiceFalsificationTest`.
8. **`EpistemicMetadataClassifier`:**
   - **Проба:** Замерить двуязычный детерминированный экстрактор Cynefin/Kano и честный `null` при отсутствии маркеров.
   - **Инвариант:** Код не трогать; заслон `EpistemicMetadataClassifierTest`, `EpistemicMetadataClassifierFalsificationTest`.
9. **`KanoClass` & `EmsFlowStage` (ядро):**
   - **Проба:** Замерить единственный канонический словарь Кано и порядок стадий потока EMS.
   - **Инвариант:** Код не трогать; заслон `KanoClassTest`, `EmsFlowStageTest` и их `*FalsificationTest`.
10. **`SelfFalsificationEpicMatcher` & `WishlistContentSimilarityMatcher`:**
    - **Проба:** Замерить слияние и кластеризацию дубликатов требований.
    - **Инвариант:** Код не трогать; заслоны `SelfFalsificationEpicMatcherTest`, `WishlistContentSimilarityMatcherTest`.
11. **`WishlistService` (ядро):**
    - **Проба:** Замерить жизненный цикл требований и мягкий перевод в архив.
    - **Инвариант:** Код не трогать; заслон `WishlistServiceFalsificationTest`, `WishlistControllerIntegrationTest`.

---

## 4. Регламент отчета инженера L2

1. Провести единый пакетный прогон тестовых заслонов в Docker Maven (`-m 2g`).
2. Внести отметки `*Живое:*` и подтверждение статуса пробы в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
3. Сформировать лаконичный отчет в `docs/reports/PROGRAMMER_STATE.md` (до 20 строк).
4. Зафиксировать результат семантическим коммитом `feat(compiler-market): complete Stage 4 batch verification (D008, D010, D006)`.
