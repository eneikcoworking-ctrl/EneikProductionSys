# Пакетная директива L1/L3 -> L2: Секции X и XXIX (Цех дизайна, дизайн-система, генерация экранов, Stitch и аудит дрифта)

**Дата:** 2026-10-15 16:15 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секции VII, VIII, XIV и XV (включая коммит `85bbb6b` с 65 тестами green) полностью замерены и закрыты. Настоящая директива охватывает целиком следующий стратегический кластер фабрики — **Секции X и XXIX (Цех дизайна, дизайн-система, генерация экранных артефактов, Stitch и аудит дрифта)**:
   - **Секция X. Семейство: дизайн-цех, дизайн-система и live drift:**
     * `DesignShopOrchestrationService` — оркестратор жизненного цикла дизайн-цеха, CAS-захват старта цикла (`V98__design_shop_start_cycle_claim.sql`), координация baseline и review;
     * `DesignShopCycleRepository` & `DesignShopCycleEntity` — персистентное состояние цикла, хранение `declared_colors` и `declared_fonts`, защита от гонок старта;
     * `DesignConsistencyAuditService` & `LayoutGeometryAuditService` — кросс-экранная консистентность Жаккара, аудит геометрии и близости элементов (D011, D015);
     * `DesignSystemFalsificationService` — фальсификатор проектной дизайн-системы, строгая сверка декларированных токенов против сгенерированного DOM (D008, D015);
     * `DesignDriftMonitorService` — детектор эстетического дрифта живого экрана относительно зафиксированного baseline (D015, D013);
     * `StrandedDesignCycleSweepService` — уборочный контур зависших дизайн-циклов без блокировки потока;
     * `VideoAssetService` — генерация и учет видео-артефактов дизайн-цеха.
   - **Секция XXIX. Цех дизайна: порождение экранов и сверка их с маркой:**
     * `DesignAssetService` (`generateAsset`, `generateViaStitch`, `auditExistingDrafts`) — синтез экранных артефактов, строгий отбор `mockup.html` с валидным DOM;
     * `StitchClient` — интеграционный адаптер к сервису генерации экранов Stitch с верификацией контрактов и отсечением ложных артефактов;
     * Заслоны от подмены (`NUEL_BELNAP_06_SUBSTITUTION_ORACLE` / D009): запрет подмены имплементируемого HTML картинками-заглушками при наличии требования `requireImplementableHtml=true`.
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено трогать рабочий код сервисов и тестов при проходящих тестах!
   - Механизмы обладают сильной формой. Любая попытка несанкционированного «рефакторинга» работающего сервисного кода признается нарушением регламента автономии.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D015)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` [D008 False green, Popper]:**
  * Фальсификация экранных артефактов в `DesignSystemFalsificationServiceTest` и `DesignExcellenceGateFalsificationTest`: экраны фальсифицируются против заявленных маркеров марки (`declared_colors`, `declared_fonts`). При наличии незадекларированных стилей или отсутствии чистого HTML артефакт безусловно отклоняется без ложной зелени.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ELVIN_GOLDMAN_01_RELIABILITY_CHAIN` [D010 Data lineage loss, Goldman]:**
  * Лок уровней абстракции между токенами дизайн-системы и материализованными элементами разметки: цвет и типографика сохраняют неразрывную цепочку от `DesignShopCycleEntity` до скоммиченного в GitHub файла `mockup.html`.
  * Надежность цепочки прогона в `DesignImplementationLoopClosureLaw3Test`: переход от дизайн-артефакта к реализации в ветке строго прослеживается.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` [D006 Authorization ambiguity, Raz]:**
  * **Императивный запрет инженеру L2 на модификацию рабочего кода сервисов Секций X и XXIX при проходящих тестах;**
  * Исключающий деонтический запрет подмены: `mockup.png` не может подменять `mockup.html` (`NUEL_BELNAP_06_SUBSTITUTION_ORACLE` / D009);
  * CAS-блокировка старта цикла (`start_cycle_claimed_at`): запрет конкурентного захвата одного и того же дизайн-цикла параллельными воркерами.
- **`RUT_BARKAN_MARKUS_10_TOKEN_TRACE_UNITY` & `LYUDVIG_VITGENSHTEYN_15_CROSS_SCREEN_JACCARD_GATE` [D015 Aesthetic drift]:**
  * Единство следа токенов: вычисление отношения покрытия заявленных токенов над фактическими стилями;
  * Кросс-экранная консистентность Жаккара: генерация соседних экранов одного проекта обязана оставаться в метрическом ядре дизайн-системы проекта.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий замер целостности механизмов в Docker Maven (`docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=...`).
2. Подтвердить 100% green статус по всем 12 классам-заслонам Секций X и XXIX (100 тестов):
   - `DesignShopOrchestrationServiceTest`, `DesignShopOrchestrationServiceLaw15Test`;
   - `DesignAssetServiceTest`, `DesignConsistencyAuditServiceTest`, `DesignSystemFalsificationServiceTest`;
   - `DesignDriftMonitorServiceTest`, `StrandedDesignCycleSweepServiceTest`, `StitchClientTest`;
   - `DesignImplementationLoopClosureLaw3Test`, `DesignExcellenceGateTest`, `DesignExcellenceGateFalsificationTest`;
   - `DesignShopCycleRepositoryIntegrationTest`.
3. Зафиксировать форму образца и актуализировать строки `*Живое:*` для Секций X и XXIX в `docs/FACTORY_MECHANISMS.md` (Ступень 4 closed, ideal).
4. Оформить лаконичный семантический коммит фабрики.
5. Обновить `docs/reports/PROGRAMMER_STATE.md` (до 20 строк).
