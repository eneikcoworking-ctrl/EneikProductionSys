# Пакетная директива L1/L3 -> L2: Секция VI (Суждение: решётка суждения, слой опровержений и продвижение рычагов)

**Дата:** 2026-10-15 17:42 UTC  
**Эмитент:** Менеджер-философ (L1/L3)  
**Адресат:** Ведущий инженер фабрики (L2)  
**Статус:** Императивный пакетный наряд (Batching Work Order)

---

## 1. Фундаментальные инварианты оператора Дмитрия Ефремова

1. **Полная автономия:** Непрерывный замкнутый цикл фабрики без ожидания внешних подтверждений, пауз и согласований.
2. **ПАКЕТНЫЙ РЕЖИМ (Batching):** Выдавать Work Order сразу на всю следующую незакрытую секцию механизмов целиком. Секция IX («Внешние системы: AI/RAG/Runtime-граница») полностью подтверждена и закрыта инженером (142/142 green в коммите `04fed6b`). Настоящая директива охватывает целиком следующий фундаментальный массив фабрики — **Секцию VI («Суждение: решётка суждения, слой опровержений и продвижение рычагов»)**:
   - **Контур VI.1. Решётка суждения и 5 слоёв истины (Popper D012, Goldman D010, Raz D006):**
     * `VerdictLayer`, `Verdict`, `Judgement`, `VerdictReconciliation`, `VerdictGate`, `VerdictController`, `AutonomousVerdictObservationService` — трёхзначная решётка Белнапа/Поппера (`PERMIT`, `WITHHOLD`, `ABSTAIN`), монотонное сужение (`WITHHOLD` доминирует, `ABSTAIN` блокирует продвижение), запрет на преобразование эпистемического долга в разрешение (`advance=ABSTAIN` при $D(P)>0$), строгая типизация кодов отказа `reasonCode` (`KARL_POPPER_03_TRUTH_STATUS_TABLE` / D012, `DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` / D006).
     * Специализированные слои: `AcceptanceVerdictLayer`, `RuntimeVerdictLayer`, `DoctrineVerdictLayer`, `InfrastructureVerdictLayer`, `SixSigmaVerdictLayer` — изоляция предметов суждения без смешивания уровней абстракции (`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` / D010).
   - **Контур VI.2. Фабричный суд и асимметрия фальсификации (Popper D008, Goldman D010, Raz D006):**
     * `FactoryJudgmentService` & `JudgmentAgentClient` — модель суждения вызывается строго по опровержению (`unjudged refutations`), а не по расписанию; сетевой сбой (`UNAVAILABLE`) сохраняет `judged_at = null` для повторной попытки; нерассудимая строка (`UNJUDGEABLE`) помечается прочитанной для защиты от зависания очереди (`KARL_POPPER_01_FALSIFICATION_HARNESS` / D008, `ALVIN_GOLDMAN_01_RELIABLE_PROCESS_AUDIT` / D010).
   - **Контур VI.3. Суд сданной работы и Закон 17 отбора свидетельств (Goldman D007, Popper D008, Raz D006, Wittgenstein D013):**
     * `DeliveredWorkJudgmentService` & `CriteriaEvidenceSelector` — сопоставление физического diff PR в GitHub с критериями приёмки задачи; категорический запрет механической обрезки диффа по чарам (Закон 17, `splitAtFileBoundaries` с именованием опущенных файлов); вердикт `REFUTED` порождает дедуплицированный review-only wishlist `delivery_refuted`, не ломая завершённую задачу (`ELVIN_GOLDMAN_11_CONVERSATION_MAXIM` / D007, `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` / D006).
   - **Контур VI.4. Лестница доверия рычагов (Gärdenfors D007, Popper D008, Goldman D010):**
     * `LeverPromotionService`, `LeverStage`, `LeverAgreement`, `LeverObservation` — AGM-лестница пересмотра убеждений: старт нового рычага строго в `OBSERVE_ONLY`; жесткий попперовский ценз продвижения ($N \ge 20$ наблюдений, согласие $\ge 80\%$, окно 14 дней); однократное потребление пакета улик; немедленное понижение (`demote`) при единичном реальном расхождении (`PITER_GERDENFORS_01_BELIEF_UPDATE_LEDGER` / D007, `ALVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` / D010).
3. **ПРИНЦИП ANTIGRAVITY_QUEUE (Ступени 3 и 4):**
   - **СТРОГО ЗАМЕР В РАНТАЙМЕ, А НЕ ПРАВКА КОДА.**
   - **КАТЕГОРИЧЕСКИЙ ДЕОНТИЧЕСКИЙ ЗАПРЕТ (Раз D006):** Запрещено трогать рабочий код сервисов (`src/main/java`) при проходящих тестах!
   - Механизмы Секции VI уже реализованы в кодовой базе фабрики в сильной форме. Задача L2 — подтвердить нерушимость заслонов пакетным замером в рантайме и Docker Maven без единой строки мутации рабочих классов.
4. **Основательность замеров:** Никаких искусственных спешек и дедлайнов — замеры телеметрии рантайма и заслоны верификации обязаны быть строгими, доказательными и воспроизводимыми в Docker-контейнере Maven.

---

## 2. Философский базис RAG-корпуса (D008, D010, D006, D007, D012, D013)

- **`KARL_POPPER_01_FALSIFICATION_HARNESS` & `KARL_POPPER_03_TRUTH_STATUS_TABLE` [D008 False green & D012 Multivalued truth, Popper]:**
  * Попперовская асимметрия фальсификации: модель зовётся к суду исключительно при наличии опровержения; подтверждения бесплатны и не требуют сетевых затрат;
  * Трёхзначная логика: `PERMIT`, `WITHHOLD`, `ABSTAIN` строго различимы; отсутствие свидетельства есть эпистемический долг (`debt`), а не разрешение.
- **`ELVIN_GOLDMAN_16_LEVEL_OF_ABSTRACTION_LOCK` & `ALVIN_GOLDMAN_21_ASYMMETRIC_TRUST_DYNAMICS` [D010 Data lineage loss, Goldman]:**
  * Строгий лок уровней абстракции: factory judgment и delivery judgment никогда не смешиваются;
  * Асимметрия доверия: для каждого следующего шага продвижения рычага требуется свежий пакет свидетельств.
- **`ELVIN_GOLDMAN_11_CONVERSATION_MAXIM` [D007 Conversation maxim / Law 17, Goldman]:**
  * Закон отбора свидетельств: запрет механической резки диффа по символам; разделение строго по границам файлов с явным перечислением опущенных сущностей.
- **`DZHOZEF_RAZ_01_PROHIBITION_AS_CODE` & `DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX` [D006 Authorization ambiguity, Raz]:**
  * **Императивный деонтический запрет инженеру L2 на модификацию рабочего кода сервисов Секции VI при проходящих тестах;**
  * `VerdictGate` монотонно вычитает права и запрещает действия по типизированным `reasonCode`.
- **`PITER_GERDENFORS_01_BELIEF_UPDATE_LEDGER` [D007 Belief revision, Gärdenfors]:**
  * AGM-модель ревизии убеждений: нулевое влияние кандидата до накопления доказательной базы (`OBSERVE_ONLY`).
- **`LUDVIG_VITGENSHTEYN_01_ANTI_MIRROR_TELEMETRY` [D013 Runtime drift, Wittgenstein]:**
  * Анти-зеркало рантайма: оценка delivery опирается на физический diff PR в GitHub; отсутствие diff фиксируется как `NOT_JUDGED_NO_DIFF`.

---

## 3. Ожидаемый протокол действий L2

1. Провести строгий пакетный замер механизмов Секции VI в Docker Maven (`docker run --rm -v /opt/EneikProductionSys:/workspace -w /workspace -v /root/.m2:/root/.m2 -m 2g maven:3.9.9-eclipse-temurin-21 mvn test -Dtest=...`).
2. Подтвердить 100% green статус по ключевым классам-заслонам Секции VI:
   - Решётка суждения и слои: `VerdictGateTest`, `VerdictReconciliationTest`, `VerdictLayerFalsificationTest`, `AcceptanceVerdictLayerTest`, `RuntimeVerdictLayerTest`, `InfrastructureVerdictLayerTest`, `AutonomousVerdictObservationServiceTest`;
   - Фабричный суд: `FactoryJudgmentServiceTest`, `FactoryJudgmentServiceFalsificationTest`, `JudgmentAgentClientTest`;
   - Суд сданной работы: `DeliveredWorkJudgmentServiceTest`, `DeliveredWorkJudgmentServiceFalsificationTest`, `JudgmentAgentClientLaw17Test`, `CriteriaEvidenceSelectorFalsificationTest`, `EvidenceSelectedByCriteriaTest`;
   - Рычаги доверия: `LeverPromotionServiceTest`, `LeverPromotionServiceFalsificationTest`, `TocSubordinationLeverTest`.
3. Зафиксировать форму образца и актуализировать статус Секции VI в `docs/FACTORY_MECHANISMS.md` (добавить итоговую секцию `### Итог Секции VI`, подтверждающую Ступень 4 closed, ideal, 100% green).
4. Синхронизировать `docs/ANTIGRAVITY_QUEUE.md`.
5. Оформить лаконичный семантический коммит фабрики.
