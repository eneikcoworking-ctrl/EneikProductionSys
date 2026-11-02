# БОЕВОЙ НАРЯД ДЛЯ L2 (ИНЖЕНЕР): ИНКОРПОРАЦИЯ СУВЕРЕННОГО ДИЗАЙНЕРА В ЯДРО ФАБРИКИ (ВЗАМЕН GOOGLE STITCH)
**Дата:** 2026-11-02 01:35 UTC  
**От:** Оператор Дмитрий Ефремов & Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** ОБЯЗЫВАЮЩАЯ ДИРЕКТИВА АВТОНОМИИ (PRIORITY #1 — REPLACING STITCH WITH SOVEREIGN DESIGNER)  
**Контекст:** Проект `designer-eneik` (ef264aea-dd30-46e2-bfac-e7e40918d5ab) успешно принят оператором (`DELIVERED`, HTTP 200). Полнофункциональный код Суверенного Дизайнера готов к инкорпорации из `/tmp/designer-eneik`.

---

## 1. ЭПИСТЕМИЧЕСКИЙ ФУНДАМЕНТ И УСТРАНЕНИЕ СИСТЕМНОГО ДЕФЕКТА

### 1.1. Ликвидация ложной дихотомии и внешней зависимости (Деннетт: «Cranes, not Skyhooks»)
До сегодняшнего дня в ядре Фабрики (`DesignAssetService.java:378-386`) сохранялась фатальная зависимость от внешнего платного провайдера Google Stitch (`stitch.googleapis.com`):
```java
if (requireImplementableHtml) {
    // Reached only when Stitch is disabled or unconfigured. The caller asked for HTML and the only
    // producer of HTML is not available...
    log.warn("DesignAssetService: implementable HTML required but Stitch is not enabled/configured; "
            + "no image fallback for project {}", project == null ? "unknown" : project.getId());
    return DesignAssetResult.unavailable(
            "Implementable HTML required, but Stitch (the only HTML producer) is not enabled or configured.");
}
```
Это порождало непрерывный цикл ретраев в `DesignShopOrchestrationService`:
`design generation unavailable for project ... retrying next tick`.
Фабрика ждала «небесного крюка» (Skyhook) в виде платного ключа Google Cloud с кредитной картой ($8 за сессию).

### 1.2. Факт физического артефакта (Витгенштейн & ТЗ Раздел 3)
Проект `designer-eneik` создал и доказал работоспособность автономного инженерного крана (Crane):
- 100% суверенная генерация сертифицированного HTML5 + Tailwind CSS (`mockup.html`);
- Зрительный контур (`PlaywrightVisionHarness`) с двухвьюпортным захватом (Desktop 1440px / Mobile 375px);
- Зрительный аудитор (`VisionAuditLoop`) с деонтическим заслоном BARCAN-TAG-11 (нулевая толерантность к псевдо-Leica, GPS и фиолетовым клише);
- Себестоимость генерации: **0 рублей 00 копеек**.

**Императив:** Фабрика обязана инкорпорировать этот движок в ядро и сделать его основным генератором имплементируемого HTML.

---

## 2. АРХИТЕКТУРА ИНКОРПОРАЦИИ В ЯДРО ФАБРИКИ

### 2.1. Пакетная структура: `com.eneik.production.services.design.sovereign.*`
Все компоненты из `/tmp/designer-eneik/src/main/java/com/eneik/generated/designer/` переносятся в ядро `EneikProductionSys`:

```
src/main/java/com/eneik/production/services/design/sovereign/
├── domain/
│   ├── ProductArchetype.java
│   ├── LayoutGridPattern.java
│   ├── DesignTokenSet.java
│   ├── SynthesizedDomDocument.java
│   ├── ViewportType.java
│   ├── ViewportCapture.java
│   ├── DeonticGateResult.java
│   ├── CorrectionDirective.java
│   ├── VisionAuditReport.java
│   ├── SynthesisStatus.java
│   └── SynthesisSession.java
├── exception/
│   └── RenderTimeoutException.java
├── telemetry/
│   ├── TelemetryEventType.java
│   ├── TelemetryEvent.java
│   ├── DesignTelemetryCollector.java
│   ├── InMemoryDesignTelemetryCollector.java
│   ├── DesignTelemetrySummary.java
│   └── ArchetypeTelemetrySummary.java
└── service/
    ├── DesignTokenCompiler.java
    ├── DefaultDesignTokenCompiler.java
    ├── LayoutPatternRegistry.java
    ├── DefaultLayoutPatternRegistry.java
    ├── SovereignProjectAssetResolver.java (адаптация интерфейса DesignAssetService из /tmp)
    ├── DefaultSovereignProjectAssetResolver.java
    ├── SemanticLayoutSynthesizer.java
    ├── DefaultSemanticLayoutSynthesizer.java
    ├── PlaywrightVisionHarness.java
    ├── DefaultPlaywrightVisionHarness.java
    ├── VisionAuditLoop.java
    ├── DefaultVisionAuditLoop.java
    ├── PrimaryAssetManagementSystem.java
    ├── SovereignDesignSynthesisService.java
    └── DefaultSovereignDesignSynthesisService.java
```

### 2.2. Разрешение коллизий имён и реализация сервисных бинов
1. **Коллизия `DesignAssetService`:**  
   В исходном `/tmp/designer-eneik` интерфейс разрешения ассетов назывался `DesignAssetService`. Чтобы исключить путаницу с фабричным `com.eneik.production.services.design.DesignAssetService`, в суверенном пакете переименовать его в **`SovereignProjectAssetResolver`** (с методом `resolveProjectAssets(String projectContextKey)`).
2. **Spring Bean конфигурация (реализации по умолчанию):**
   - **`DefaultDesignTokenCompiler`**: реализует компиляцию токенов для всех архетипов `ProductArchetype` (`DEV_TOOLS`, `B2B_SAAS`, `FIN_TECH`, `CONSUMER`, `CREATIVE_AGENCY`, `HEALTHCARE`, `AI_AGENTIC_SYSTEMS`) с адаптивными `clamp(...)` и dark-obsidian / slate палитрами.
   - **`DefaultLayoutPatternRegistry`**: регистрирует композиционные сетки (Swiss 7/5, 8/4, Bento Grid 2.0).
   - **`DefaultVisionAuditLoop`**: проводит аудит DOM-документа и скриншотов на деонтический гейт BARCAN-TAG-11 (проверка отсутствия Leica, GPS-координат, отсутствия горизонтального скролла на 375px, соблюдение типографической иерархии).
   - **`DefaultSovereignDesignSynthesisService`**: пометить `@Service` (или `@Component`) со внедрением всех зависимостей, обеспечив полностью готовый к работе Spring Bean.

---

## 3. ПОДКЛЮЧЕНИЕ К КОНВЕЙЕРУ ФАБРИКИ

### 3.1. Модификация `DesignAssetService.java`:
1. Внедрить зависимость:
   ```java
   private final SovereignDesignSynthesisService sovereignDesignSynthesisService;
   ```
2. В методе `generateAsset(...)`:
   Вместо возврата `DesignAssetResult.unavailable("Implementable HTML required, but Stitch...")`:
   ```java
   if (requireImplementableHtml) {
       // Если Stitch не включен или не сконфигурирован — используем Суверенный Дизайнер Фабрики!
       return generateViaSovereignDesigner(project, brief, assetType, context, designSystemColors, designSystemFonts);
   }
   ```
3. Метод `generateViaSovereignDesigner(...)`:
   - Определяет `ProductArchetype` по брифу/контексту (по умолчанию `ProductArchetype.DEV_TOOLS` или `B2B_SAAS`).
   - Вызывает `sovereignDesignSynthesisService.executeDesignSynthesis(archetype, projectSlug)`.
   - При успешном завершении (`session.getStatus().isTerminal()` и макет сгенерирован):
     - Извлекает финальный HTML из `SynthesizedDomDocument`.
     - Сохраняет `mockup.html`, дизайн-токены `tokens.json` и скриншоты в `assetRoot` директорию проекта.
     - Совершает коммит в ветку репозитория через существующий метод:
       ```java
       String repoDraftPath = commitDraftToGitHub(project, basename, htmlBytes, screenshotBytes, ".png");
       ```
     - Возвращает зелёный `DesignAssetResult`:
       ```java
       return new DesignAssetResult(
           true,
           "ok",
           "sovereign-designer",
           imagePath,
           metadataPath,
           "text/html",
           "Generated design asset via Autonomous Sovereign Designer.",
           repoDraftPath,
           "sovereign",
           session.getId().toString()
       );
       ```

### 3.2. Синхронизация с `DesignShopOrchestrationService.java`:
- `DesignShopOrchestrationService` ожидает `result.available() == true` и наличие `repoDraftPath` с файлом `mockup.html`.
- Суверенный дизайнер полностью удовлетворяет этот инвариант.
- `captureBaseline` автоматически считывает `declaredColors` и `declaredFonts` из синтезированного `mockup.html`.
- Конвейер беспрепятственно продвигает цикл в `STAGE_AWAITING_REVIEW`.

---

## 4. ПЕРЕНОС ТЕСТОВ И ТЕСТ-ЗАСЛОН ПОППЕРА (D008)

### 4.1. Перенос модульных тестов из `/tmp/designer-eneik/src/test/`:
Перенести в `src/test/java/com/eneik/production/services/design/sovereign/`:
- `SynthesisSessionTest.java`
- `DesignTelemetryCollectorTest.java`
- `DefaultPlaywrightVisionHarnessTest.java`
- `DefaultSemanticLayoutSynthesizerTest.java`
- `DefaultSovereignDesignSynthesisServiceTest.java`

### 4.2. Новый интеграционный тест-заслон: `SovereignDesignAssetIntegrationTest`
Создать `src/test/java/com/eneik/production/services/design/SovereignDesignAssetIntegrationTest.java`:
- Проверяет, что при `stitch_enabled=false` вызов `designAssetService.generateAsset(project, context, brief, "mockup", "fast", false, null, List.of(), List.of(), true)` возвращает `result.available() == true`, `result.model().equals("sovereign-designer")`, и что репозиторий получает физический `mockup.html`.
- Тест фальсификации: падение при любой попытке выдать "unavailable" или вернуть изображение вместо имплементируемого HTML.

---

## 5. РЕГЛАМЕНТ ИСПОЛНЕНИЯ И КРИТЕРИИ ПРИЁМКИ ДЛЯ L2

1. **Квантование шага:**
   - Перенос классов и адаптация пакетов.
   - Реализация бинов и интеграция в `DesignAssetService`.
   - Запуск тестов и сборка.
2. **Прогон тестов в изолированном Docker Maven:**
   ```bash
   docker run --rm -v /root/.m2:/root/.m2 -v /opt/EneikProductionSys:/workspace -w /workspace -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=DefaultSovereignDesignSynthesisServiceTest,DefaultSemanticLayoutSynthesizerTest,DefaultPlaywrightVisionHarnessTest,SovereignDesignAssetIntegrationTest -DfailIfNoTests=false
   ```
3. **Горячая пересборка и развёртывание бэкенда:**
   ```bash
   docker compose up -d --build backend
   ```
4. **Фиксация коммитом:**
   ```bash
   git commit -m "feat(design): incorporate Autonomous Sovereign Designer engine into factory core replacing Stitch"
   ```
5. **Отчёт инженера:**
   Краткая запись в `docs/reports/PROGRAMMER_STATE.md`.
