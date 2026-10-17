# Директивы и наряд для L2 (2026-10-17 04:55 UTC)

**От:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и ручных согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком.
   - Кластер Секций XVI и XXIX («Реестр 59 архитектурных предписаний L2: что и как чинить» и «Цех дизайна: порождение экранов, геометрия и дизайн-система») полностью заверен инженером (коммит `bfbc417a`, 272/272 green в 29 тест-сьютах Docker Maven 3.9.9 Temurin-21 (-m 2g), BUILD SUCCESS, строго 0 строк изменений рабочего кода).
   - Секция VII (TOC) и Секция VIII (Flow & Metrics, Оркестрация) ранее полностью замерены и закрыты (ideal).
   - Настоящая директива открывает пакетный замер следующей стратегической секции по оси потока фабрики — **Кластер Секций XXXVII, XXXVIII, XXXIX, XL, XLI, XLII, XLIII, XLIV и XVIII («Финальный контур TOC, авторизация AI-ресурсов, метрики Six Sigma, выметающие обходы, заслоны экранов, кэш корпуса, верёвка TOC, очистка журналов и завершающий баланс фабрики»)** целиком (**143 теста в едином непрерывном пакетном прогоне в Docker Maven [15 тест-сьютов]**):
     * **Группа 1: Секция XXXVII — Сторож TOC и адаптивный тайминг (20 тестов, 2 сьюта):**
       - `TocSentinelServiceTest` (18/18 green), `TocSentinelControllerTest` (2/2 green): чистое чтение кэшированного снимка `getDbrStatus()` без паразитной мутации графа, адаптивная частота таймера (`min(meanDurationMs) / 2`), партитивное владение графом (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `ALONZO_CHERCH_21_DERIVED_CUTOFF` / D008, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004).
     * **Группа 2: Секция XXXVIII — Авторизация и безопасность AI-ресурсов (18 тестов, 2 сьюта):**
       - `GoogleAiResourceControllerTest` (6/6 green), `GoogleAiResourceSecurityIntegrationTest` (12/12 green): деонтический барьер на все мутирующие пути AI-ресурсов (401/403), нормализация путей и институциональный аудит актора (`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006, `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006, `AHILLE_VARTSI_03_BOUNDARY_TOPOLOGY` / D006).
     * **Группа 3: Секция XXXIX — Аудит Six Sigma и метрики качества (31 тест, 3 сьюта):**
       - `SixSigmaAuditServiceTest` (26/26 green), `QualityMetricsControllerTest` (3/3 green), `QualityGateControllerTest` (2/2 green): 4-значная решётка истинности Тарского и Белнапа (`passed`, `failed`, `undetermined`, `not-applied`), ликвидация ложного дефекта «388 сбоев» разделением уровней абстракции проверки и задачи, расчёты DPMO без `findAll()` (`ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` / D012, `ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010, `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010).
     * **Группа 4: Секция XL — Выметающий обход и сборка мусора (21 тест, 2 сьюта):**
       - `StrandedFinalizingSweepServiceTest` (8/8 green), `BranchGarbageCollectorServiceTest` (13/13 green): атомарный CAS-релиз брошенных финализаций `finalizing -> pending`, динамический расчёт лизинга по фактическим выборкам `FINALIZING_DURATION`, иммунитет активных держателей и безопасная сборка мусора веток (`ALVA_NOE_17_CAUSAL_PROCESS_TRACE` / D013, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004, `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 5: Секция XLI — Гейты совершенства дизайна и свидетельств верификации (33 теста, 3 сьюта):**
       - `DesignExcellenceGateTest` (12/12 green), `VerificationEvidenceGateTest` (8/8 green), `LayoutGeometryAuditServiceTest` (13/13 green): проверка физических экранных артефактов и геометрии PR (`desktop-1440.png`, `mobile-375.png`, `layout-check.json`) вместо самоотчётов исполнителя (`ANDI_KLARK_10_EMBODIED_INTERFACE_PREDICTION` / D011, `KARL_POPPER_01_FALSIFICATION_HARNESS` / D008).
     * **Группа 6: Секция XLII — Кэш постоянного корпуса (декоммиссионирован / inert):**
       - `GeminiContextCacheManager` декоммиссионирован, инертный стаб, изоляция и отсечение утечек RAG (`ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` / D014).
     * **Группа 7: Секция XLIII — Оптимизатор TOC и верёвка (7 тестов, 1 сьют):**
       - `TocOptimizerTest` (7/7 green): расчет критического пути, сходимость верёвки TOC DBR без вырождения в бесконечный цикл (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ALONZO_CHERCH_03_LAMBDA_CORE_REDUCTION` / D008).
     * **Группа 8: Секция XLIV — Очистка журналов проекта (10 тестов, 1 сьют):**
       - `ProjectEventLogRetentionServiceTest` (10/10 green): безопасная постраничная очистка журналов без переполнения памяти JVM и без блокировок таблицы (`ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` / D010, `AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` / D004).
     * **Группа 9: Секция XVIII — Завершающий баланс фабрики и читающие контроллеры (3 теста, 1 сьют):**
       - `SystemStatusControllerTest` (3/3 green): завершающий баланс фабрики, GET/no-mutation контракты без скрытых мутаций (`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` / D013, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено вносить любые изменения в рабочий код сервисов, контроллеров, репозиториев, сущностей, моделей, миграций и скриптов (`src/main/java`, `src/main/resources`, `scripts/`, `runtime-launcher/`, `judgment-proxy/`, `src/models/ml/`) при проходящих тестах!
   - Механизмы уже воплощены в кодовой базе фабрики в идеальной форме. Задача инженера L2 — подтвердить нерушимость заслонов единым непрерывным пакетным замером в рантайме Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D011, D012, D013, D004, D014)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `ALFRED_TARSKIY_01_FALSIFICATION_HARNESS` [D008 False green / Popper & Tarski]:**
  * Попперовская фальсификационная оснастка: фальсификационные заслоны гейтов дизайна (`DesignExcellenceGateTest`), свидетельств верификации (`VerificationEvidenceGateTest`), Six Sigma (`SixSigmaAuditServiceTest`) и оптимизатора TOC (`TocOptimizerTest`).
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss / Goldman]:**
  * Лок уровней абстракции и цепочка надежности: строгое разделение уровней абстракции проверки и задачи (ликвидация ложного дефекта «388 сбоев» в `QualityMetricsControllerTest`), безопасная ротация и очистка журнала проекта без подъёма памяти в JVM (`ProjectEventLogRetentionServiceTest`), сохранение каузальной трассировки.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity / Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода фабрики при проходящих тестах (exclusionary reasons);**
  * Деонтический барьер на все мутирующие пути AI-ресурсов (401/403) и аудит актора в `GoogleAiResourceControllerTest` и `GoogleAiResourceSecurityIntegrationTest`.
- **`LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift / Wittgenstein]:**
  * Анти-зеркало телеметрии: чтение статуса верёвки `getDbrStatus()` как чистый кэшированный снимок без паразитных побочных эффектов мутации графа (`TocSentinelServiceTest`), GET/no-mutation контракты (`SystemStatusControllerTest`).
- **`ANDI_KLARK_10_EMBODIED_INTERFACE_PREDICTION` [D011 Embodied interface prediction / Clark]:**
  * Проверка физических экранных артефактов и геометрии PR (`desktop-1440.png`, `mobile-375.png`, `layout-check.json`) вместо самоотчётов исполнителя (`DesignExcellenceGateTest`, `LayoutGeometryAuditServiceTest`).
- **`ALFRED_TARSKIY_02_TRUTH_STATUS_TABLE` & `NUEL_BELNAP_03_TRUTH_STATUS_TABLE` [D012 Policy contradiction / Belnap & Tarski]:**
  * 4-значная решётка истинности вердиктов качества (`passed`, `failed`, `undetermined`, `not-applied`) в `SixSigmaAuditServiceTest`.
- **`AHILLE_VARTSI_02_PART_WHOLE_OWNERSHIP` [D004 Mereology / Varzi]:**
  * Партитивное владение графом TOC и атомарный CAS-релиз брошенных финализаций `finalizing -> pending` в `StrandedFinalizingSweepServiceTest`.
- **`ALONZO_CHERCH_17_RAG_GROUNDING_CAPSULE` [D014 Grounding drift / Church]:**
  * Изоляция и декоммиссионирование неиспользуемых кэшей контекста без утечек RAG.

---

## 3. Команда исполнения для L2 (единый непрерывный пакетный прогон)

```bash
docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=TocSentinelServiceTest,TocSentinelControllerTest,GoogleAiResourceControllerTest,GoogleAiResourceSecurityIntegrationTest,SixSigmaAuditServiceTest,QualityMetricsControllerTest,QualityGateControllerTest,StrandedFinalizingSweepServiceTest,BranchGarbageCollectorServiceTest,DesignExcellenceGateTest,VerificationEvidenceGateTest,LayoutGeometryAuditServiceTest,TocOptimizerTest,ProjectEventLogRetentionServiceTest,SystemStatusControllerTest
```
