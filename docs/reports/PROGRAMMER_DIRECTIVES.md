# Директивы и наряд для L2 (2026-10-17 09:20 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batch Work Order)  
**Контекст потока:** Непрерывный замкнутый цикл фабрики EneikProductionSys

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XVI и XXIX («Реестр 59 архитектурных предписаний L2: что и как чинить» и «Цех дизайна: экраны, марка, геометрия и дизайн-система») полностью заверен и закрыт инженером (коммит `9c5bae9d`, 272/272 green в 29 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS, строго 0 строк изменений рабочего кода).
   - Настоящая директива открывает пакетный замер следующей стратегической секции по оси потока фабрики — **Кластер Секций XXX–XXXVI («Клиент к предсказателю», «Разбор чужого репозитория», «Завод репозиториев», «Пульт управления», «Внутренний вход наблюдателя», «Выбор аккаунта», «Хранилище задач»)** целиком (**174 теста в едином непрерывном пакетном прогоне в Docker Maven [22 тест-сьюта]**):
     * **Секция XXX — Клиент к предсказателю: единственная дверь фабрики к внешней модели:**
       - `MLPredictionServiceClientTest`: единственная точка выхода фабрики к внешней ML-модели, валидация контрактов и устойчивость к сетевым деградациям (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010);
     * **Секция XXXI — Разбор чужого репозитория: неудача фабрики не становится фактом о заказчике:**
       - `RepositoryStackAnalyzerTest`, `OnboardingAuditServiceTest`, `OnboardingAuditServiceFalsificationTest`: демаркация внутренних сбоев фабрики от фактов о заказчике (`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
     * **Секция XXXII — Завод репозиториев: адрес существует только после GitHub-свидетельства:**
       - `GitHubProjectFactoryClientTest`, `LinearProjectFactoryClientTest`, `ProjectFactoryServiceTest`, `ProjectFactoryServiceFalsificationTest`, `ProjectWorkspaceFactoryServiceFalsificationTest`: физическое существование репозитория подтверждается строго внешним API-свидетельством (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008);
     * **Секция XXXIII — Пульт управления: готовность нельзя объявить без клиентского свидетельства:**
       - `CommandDashboardControllerTest`, `CommandDashboardServiceTest`: готовность проекта не может быть объявлена без клиентского свидетельства, многозначная логика готовности (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012);
     * **Секция XXXIV — Внутренний вход наблюдателя: запрет является исполнимой границей:**
       - `InternalGeminiObserverControllerTest`, `InternalGeminiObserverSecurityIntegrationTest`, `ApiAuthorizationInterceptorTest`: институциональная защита маршрутов внутреннего наблюдателя как жесткая исполнительная граница (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006);
     * **Секция XXXV — Выбор аккаунта: наказание порядком, а не исключением:**
       - `AccountRepositoryIntegrationTest`, `AccountRepositoryFalsificationTest`, `AccountHealthServiceTest`, `AccountHealthServiceLaw14Test`, `AccountHealthServiceFalsificationTest`: наказание аккаунтов порядком ротации (`refusal run`) без вылета фатальных исключений (`DZHOZEF_RAZ_21_PENALTY_AS_ORDERING` / D006);
     * **Секция XXXVI — Хранилище задач: запрет, стоящий на обоих уровнях:**
       - `TaskRepositoryIntegrationTest`, `TaskEntityLaw20Test`, `TaskEntityLaw1CarrierTest`: неизменяемость терминальных статусов по Закону 20 и сохранение каузального следа задач-носителей по Закону 1 (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `ALVA_NOE_17_CAUSAL_PROCESS_TRACE` / D013).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей и моделей (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D002, D012, D013)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификационная оснастка: эмпирическая проверка заслонов онбординга (`OnboardingAuditServiceFalsificationTest`), фабрики проектов (`ProjectFactoryServiceFalsificationTest`, `ProjectWorkspaceFactoryServiceFalsificationTest`), хранилища и здоровья аккаунтов (`AccountRepositoryFalsificationTest`, `AccountHealthServiceFalsificationTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и цепочка надежности: сохранение надежности клиента ML-предсказателя (`MLPredictionServiceClientTest`), разделение фактов о заказчике и внутренних сбоев фабрики, локально выученные de re лимиты аккаунтов вместо глобальных эвристик.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_21_PENALTY_AS_ORDERING` & `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` [D006 Authorization ambiguity / Raz & Varzi]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтическая защита эндпоинтов наблюдателя (`InternalGeminiObserverSecurityIntegrationTest`, `ApiAuthorizationInterceptorTest`), наказание аккаунтов порядком ротации (`refusal run`), неизменяемость терминальных статусов по Закону 20 (`TaskEntityLaw20Test`).
- **`DZHILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category error / Ryle]:**
  * Демаркация категорий: внутренняя сетевая или авторизационная ошибка фабрики никогда не проецируется как дефект заказчика (`RepositoryStackAnalyzerTest`).
- **`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Truth value collapse / Belnap]:**
  * 3-значный статус онбординга и многозначная конъюнкция истинности в пульте готовности (`CommandDashboardServiceTest`).
- **`ALVA_NOE_17_CAUSAL_PROCESS_TRACE` [D013 Runtime drift / Noë]:**
  * Каузальный след переходов статусов задач и признака carrier по Закону 1 (`TaskEntityLaw1CarrierTest`).

---

## 3. Команда исполнения для L2 (единый непрерывный пакетный прогон)

```bash
docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test \
  -Dtest=MLPredictionServiceClientTest,\
RepositoryStackAnalyzerTest,\
OnboardingAuditServiceTest,\
OnboardingAuditServiceFalsificationTest,\
GitHubProjectFactoryClientTest,\
LinearProjectFactoryClientTest,\
ProjectFactoryServiceTest,\
ProjectFactoryServiceFalsificationTest,\
ProjectWorkspaceFactoryServiceFalsificationTest,\
CommandDashboardControllerTest,\
CommandDashboardServiceTest,\
InternalGeminiObserverControllerTest,\
InternalGeminiObserverSecurityIntegrationTest,\
ApiAuthorizationInterceptorTest,\
AccountRepositoryIntegrationTest,\
AccountRepositoryFalsificationTest,\
AccountHealthServiceTest,\
AccountHealthServiceLaw14Test,\
AccountHealthServiceFalsificationTest,\
TaskRepositoryIntegrationTest,\
TaskEntityLaw20Test,\
TaskEntityLaw1CarrierTest
```

## 4. Ожидаемый результат такта L2

1. Провести единый непрерывный пакетный прогон 22 тест-сьютов Кластера Секций XXX–XXXVI (174 теста) в Docker Maven 3.9.9 Temurin-21 (`-m 2g`).
2. Зафиксировать 100% green (**174/174 green**, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS).
3. Обеспечить строго **0 строк изменений** рабочего кода (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`).
4. Актуализировать строки `*Живое:*` и итоговый блок Кластера Секций XXX–XXXVI в `docs/FACTORY_MECHANISMS.md` и `docs/ANTIGRAVITY_QUEUE.md`.
5. Зафиксировать результат коммитом: `feat(sections-xxx-xxxvi): complete Stage 4 batch verification (D008, D010, D006)` и кратко отразить в `docs/reports/PROGRAMMER_STATE.md`.
