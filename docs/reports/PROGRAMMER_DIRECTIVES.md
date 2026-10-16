# Пакетная директива L1/L3 -> L2: Секция III — Отправка: задача уходит в работу

**Дата:** 2026-10-16 02:55 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секции I и II («Приём требования и Компиляция») успешно закрыты инженером (коммит `0ff2143`, 324/324 green в едином Docker Maven прогоне, 0 строк изменений рабочего кода). Настоящая директива охватывает следующий фундаментальный контур жизненного цикла фабрики — **Секцию III («Отправка: задача уходит в работу: Jules, сессии, притязания, аренда, здоровье аккаунтов и узкие места»)**:
   - **Механизмы ядра и сервисы отправки:**
     * `JulesDispatchService`, `JulesApiClient`, `SessionLifecycleService`, `PersistentWorkerSessionService`, `ClaimService` (`TaskClaimService`), `LeaseWatchdogService`, `AccountHealthService`, `BottleneckAwarePriorityService`, `BottleneckDetectionService`;
     * Сущности и модели контура: `TaskClaim`, `TaskClaimRepository`, `JulesSessionEntity`, `AccountEntity`, `PersistentWorkerSession`, `AccountHealth`, `BottleneckScore`;
   - **Заслоны верификации:**
     * `JulesDispatchServiceTest`, `JulesDispatchServiceFalsificationTest`, `JulesDispatchServiceLaw4MergeEvidenceTest`, `JulesDispatchServiceTocDbrAdmissionTest`, `JulesRefusalKindsTest`, `JulesRoleCapabilitiesFalsificationTest`, `JulesApiClientTest`, `JulesApiClientFalsificationTest`, `SessionLifecycleServiceTest`, `SessionLifecycleServiceFalsificationTest`, `PersistentWorkerSessionServiceTest`, `PersistentWorkerSessionServiceFalsificationTest`, `TaskClaimServiceTest`, `ClaimServiceFalsificationTest`, `ClaimServiceRaceGuardTest`, `ClaimRightsDutiesMatrixTest`, `ClaimResultStatusTruthTableFalsificationTest`, `TocPriorityClaimTest`, `LeaseWatchdogServiceTest`, `LeaseWatchdogServiceFalsificationTest`, `AccountHealthServiceTest`, `AccountHealthServiceFalsificationTest`, `AccountHealthServiceLaw14Test`, `BottleneckAwarePriorityServiceTest`, `BottleneckAwarePriorityServiceFalsificationTest`, `BottleneckDetectionServiceTest`, `BottleneckDetectionServiceFalsificationTest`.
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые модификации в рабочий код сервисов, диспетчеров, контроллеров и клиентов (`src/main/java`, `src/main/resources`, `scripts/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D012, D007, D011, D005)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `TIMOTI_UILYAMSON_18_FALSIFICATION_HARNESS` [D008 False green, Popper & Williamson]:**
  * Попперовская фальсификация: заслоны обязаны падать при попытке двойного клейма одной задачи, при утечке сессий поверх лимита аккаунта, при пропуске сетевого запроса к Jules без ключа или при нарушении квот бюджета диспетчеризации (`JulesDispatchServiceFalsificationTest`, `ClaimServiceRaceGuardTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss, Goldman]:**
  * Лок уровней абстракции и каузальная цепочка: контекст отказа (`CreateSessionResult.failure`) строго фиксирует `promptLength`, `source`, `startingBranch`; полная изоляция задач по проектам и аккаунтам; ликвидация N+1 при определении узких мест воркеров.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity, Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах;**
  * Исключающие основания Раза: `JulesApiClient` категорически запрещает сетевые вызовы при отключенной интеграции (`jules_enabled == false`) или пустом URL; `ClaimService` блокирует выдачу задачи при исчерпании лизы или деградации здоровья аккаунта.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D004, Varzi]:**
  * Мереологическое разделение владения: атомарный CAS и изоляция жизненного цикла притязаний на задачу (`TaskClaim`) между сессиями воркеров.
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` & `UILYAM_ESHBI_01_REQUISITE_VARIETY` [D012 Multi-valued truth, Belnap & Ashby]:**
  * Четырехзначная логика истинности статусов притязаний и достаточное разнообразие состояний сессий Jules без схлопывания неизвестного в ложный успех.
- **`MARGRET_GILBERT_04_INSTITUTIONAL_DEFECT_RECORD` & `MARGRET_GILBERT_01_JOINT_COMMITMENT` [D007 / D004, Gilbert]:**
  * Совместное обязательство и институциональный регистр: фиксация отказов диспетчеризации в журнале дефектов без потери контекста.
- **`FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK` [D011 Teleosemantic feedback, Dretske]:**
  * Обратная связь по здоровью аккаунтов (`AccountHealthService`): перевод аккаунта в карантин при серийных сбоях токена.
- **`FRENK_RAMSEY_01_MINIMAX_REGRET_DISPATCH` [D005 Minimax regret, Ramsey]:**
  * Приоритизация очереди задач с учетом узких мест барабана TOC (`BottleneckAwarePriorityService`) для минимизации потерь пропускной способности.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий единый пакетный замер механизмов Секции III в Docker Maven:
   ```bash
   docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=JulesDispatchServiceTest,JulesDispatchServiceFalsificationTest,JulesDispatchServiceLaw4MergeEvidenceTest,JulesDispatchServiceTocDbrAdmissionTest,JulesRefusalKindsTest,JulesRoleCapabilitiesFalsificationTest,JulesApiClientTest,JulesApiClientFalsificationTest,SessionLifecycleServiceTest,SessionLifecycleServiceFalsificationTest,PersistentWorkerSessionServiceTest,PersistentWorkerSessionServiceFalsificationTest,TaskClaimServiceTest,ClaimServiceFalsificationTest,ClaimServiceRaceGuardTest,ClaimRightsDutiesMatrixTest,ClaimResultStatusTruthTableFalsificationTest,TocPriorityClaimTest,LeaseWatchdogServiceTest,LeaseWatchdogServiceFalsificationTest,AccountHealthServiceTest,AccountHealthServiceFalsificationTest,AccountHealthServiceLaw14Test,BottleneckAwarePriorityServiceTest,BottleneckAwarePriorityServiceFalsificationTest,BottleneckDetectionServiceTest,BottleneckDetectionServiceFalsificationTest
   ```
2. Подтвердить статус `ideal` в `*Живое:*` механизмов Секции III в `docs/FACTORY_MECHANISMS.md`, убедиться в наличии блока `### Итог Секции III`, синхронизировать `docs/ANTIGRAVITY_QUEUE.md` (внести Секцию III в `## Дальше`).
3. Составить краткий отчет в `docs/reports/PROGRAMMER_STATE.md` (не более 15–20 строк).
4. Закоммитить с семантическим сообщением `feat(dispatch): complete Section III batch verification (D008, D010, D006)`.
