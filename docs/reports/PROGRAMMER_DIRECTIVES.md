# Пакетная директива L1/L3 -> L2: Секция I (Приём требования: рыночный корпус, уставный гейт и полевое исследование)

**Дата:** 2026-10-15 17:56 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секция VI («Суждение: решётка суждения, слой опровержений, рычаги») полностью замерена и закрыта инженером (139/139 green в коммите `3480f81`). Настоящая директива охватывает целиком следующий фундаментальный массив фабрики — **Секцию I («Приём требования: рыночный корпус, уставный гейт и полевое исследование»)**:
   - **Контур I.1. `ProjectFlowService` (Shaffer D004, Goldman D010, Raz D006):**
     * Мерологическое владение Шаффера (`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` / D004): приоритет целого над частями, явное закрепление владельцев переходов состояний задач и заявок вишлиста, защита терминальных статусов через атомарные CAS (`writeStatusUnlessTerminalAt`, `compareAndSetStatusAt`), лимит попыток декомпозиции (`WISHLIST_COMPILE_ATTEMPT_BUDGET = 3`).
     * Изоляция проектных границ и надежность выборок Голдмана (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010): ликвидация N+1 и смешивания данных чужих проектов через `findByTaskIdIn`, детерминированное упорядочение проектного каталога `listProjects` (`findAllByOrderByCreatedAtDesc`).
   - **Контур I.2. `ProjectFactoryService`, `GitHubProjectFactoryClient`, `LinearProjectFactoryClient` (Belnap D009, Goldman D010, Varzi D006):**
     * Многозначный оракул подстановок Белнапа (`NUEL_BELNAP_06_SUBSTITUTION_ORACLE` / D009): при отсутствии или сбое провижининга фабрика строго возвращает `null` и передает `null` в Linear, исключая генерацию фантомных URL (`phantom URL`).
     * Демаркация онбординга Голдмана (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010): строгое разграничение greenfield (выброс `name_conflict` при существующем удаленном репозитории) и brownfield (сохранение подтвержденного URL), автоматическая регистрация 4 стандартных хотспотов (`ProjectHotspotFileEntity`), валидированный `factoryReport`.
     * Топология границ Варци (`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006): отсутствие внешних сетевых клиентов в транзакционном графе приёма требования.
   - **Контур I.3. `ProjectWorkspaceFactoryService` (Varzi D006):**
     * Топология файловых границ Варци (`AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006): безопасная генерация базовых артефактов (`README.md`, `.env.example`, `.github/workflows/ci.yml`, `docs/PROJECT_BRIEF.md`) со строгим удержанием границы корня проекта `workspaceRoot`.
   - **Контур I.4. `OnboardingAuditService`, `RepositoryStackAnalyzer` (Belnap D012, Ryle D002, Goldman D010):**
     * Трёхзначная таблица истинности Белнапа (`NUEL_BELNAP_03_TRUTH_STATUS_TABLE` / D012): статусы аудита представлены в системе типов `InspectionStatus` (`YES`, `NO`, `UNCHECKED`) с выводом «не проверено» вместо ложного булева false.
     * Защита от категориальной ошибки Райла (`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` / D002): категорический запрет конвертации проблем сетевого доступа фабрики в дефекты или находки о коде клиента (`assertThat(savedFindings).isEmpty()` при `isUnchecked()`).
     * Цепочка надежности Голдмана (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010): атомарная очистка устаревших находок (`deleteAll`) перед записью свежих данных.
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено трогать рабочий код сервисов (`src/main/java`) при проходящих тестах!
   - Механизмы Секции I уже реализованы в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D004, D009, D012, D002)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green, Popper]:**
  * Попперовская фальсификация: тесты-заслоны обязаны падать при попытке подсунуть чужой проект, нарушить DBR Rope или подменить статус без правки в БД;
- **`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss, Goldman]:**
  * Каузальная надежность процесса: четкое разделение greenfield/brownfield, устранение N+1 через `findByTaskIdIn`, атомарное удаление старых находок аудита;
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity, Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода сервисов Секции I при проходящих тестах;**
  * Деонтические исключающие основания: запрет сетевых вызовов в транзакции приёма, запрет перезаписи терминальных статусов задач;
- **`DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP` [D004 Boundary drift, Shaffer]:**
  * Мерологический приоритет целого над частями: `ProjectFlowService` как координатор жизненного цикла проектов удерживает согласованность 15 хранилищ;
- **`NUEL_BELNAP_06_SUBSTITUTION_ORACLE` & `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D009 & D012 Many-valued diagnostics, Belnap]:**
  * Честный `null` при сбое провижининга вместо фантомных URL;
  * Трёхзначная решётка `InspectionStatus` (`YES`, `NO`, `UNCHECKED`) с отображением «не проверено»;
- **`GILBERT_RAYL_03_CATEGORY_ERROR_SCAN` [D002 Category mistake, Ryle]:**
  * Категориальная граница: сбой доступа фабрики не является дефектом кода заказчика.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий пакетный замер механизмов Секции I в Docker Maven (`docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=...`).
2. Подтвердить 100% green статус по ключевым классам-заслонам Секции I:
   - Приём проектов и жизненный цикл: `ProjectFlowServiceTest`, `ProjectFlowServiceFalsificationTest`, `ProjectFlowServiceLaw1JulesDispatchTest`, `ProjectFlowServiceResetTest`, `ProjectFlowServiceDbrRopeTest`, `ProjectFlowIntegrationTest`;
   - Провижининг: `ProjectFactoryServiceTest`, `ProjectFactoryServiceFalsificationTest`, `GitHubProjectFactoryClientTest`, `LinearProjectFactoryClientTest`;
   - Онбординг и стек: `OnboardingAuditServiceTest`, `OnboardingAuditServiceFalsificationTest`, `RepositoryStackAnalyzerTest`.
3. Зафиксировать форму образца и актуализировать статус Секции I в `docs/FACTORY_MECHANISMS.md` (добавить итоговую секцию `### Итог Секции I`, подтверждающую Ступень 4 closed, ideal, 100% green).
4. Синхронизировать `docs/ANTIGRAVITY_QUEUE.md`.
5. Оформить лаконичный семантический коммит фабрики.
