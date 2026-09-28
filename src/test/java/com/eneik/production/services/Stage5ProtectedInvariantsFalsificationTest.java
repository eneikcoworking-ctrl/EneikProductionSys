package com.eneik.production.services;

import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.services.lever.LeverAgreement;
import com.eneik.production.services.lever.LeverStage;
import com.eneik.production.services.monitor.AiHealthTracker;
import com.eneik.production.services.settings.SystemSettingsService;
import com.eneik.production.services.verdict.Judgement;
import com.eneik.production.services.verdict.Verdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Фальсифицирующий заслон для защищённых механизмов Ступени 5 (HOW_TO_READ_BEFORE_FIXING.md, раздел XVI).
 *
 * Проверяемые инварианты:
 * 1. TaskStatus & TaskEntity: запрет перезаписи конечного состояния (terminal immutability, Law 20 / Invariant S2).
 * 2. Verdict & Judgement: трёхзначная решётка Клини/Тарского (воздержание блокирует, отказ невозможно переголосовать).
 * 3. LeverStage & LeverAgreement: асимметричная лестница доверия и четырёхзначная диагностика Белнапа.
 * 4. MLPredictionServiceClient: возврат пустоты (fail-closed) при отсутствии ключа или отключённом Gemini.
 *
 * Паттерны:
 * - BARCAN-TAG-06_DEONTIC-CONSISTENCY:01:karl-popper / KARL_POPPER_01_FALSIFICATION_HARNESS [D008, Popper]
 * - BARCAN-TAG-00_CODE-GUARDIAN:01:lyudvig-vitgenshteyn / LYUDVIG_VITGENSHTEYN_14_ANTI_MIRROR_TELEMETRY [D013, Wittgenstein]
 */
class Stage5ProtectedInvariantsFalsificationTest {

    // --- 1. TaskStatus & TaskEntity: Terminal Immutability ---

    @Test
    @DisplayName("Инвариант 1: TaskStatus.isTerminal строго определяет терминальные состояния {done, failed, spike_completed}")
    void taskStatusTerminalPredicateIsExact() {
        assertThat(TaskStatus.done.isTerminal()).isTrue();
        assertThat(TaskStatus.failed.isTerminal()).isTrue();
        assertThat(TaskStatus.spike_completed.isTerminal()).isTrue();

        assertThat(TaskStatus.queued.isTerminal()).isFalse();
        assertThat(TaskStatus.claimed.isTerminal()).isFalse();
        assertThat(TaskStatus.in_progress.isTerminal()).isFalse();
        assertThat(TaskStatus.pending_review.isTerminal()).isFalse();
        assertThat(TaskStatus.review.isTerminal()).isFalse();
        assertThat(TaskStatus.blocked.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("Инвариант 1: TaskEntity запрещает перезапись терминального статуса 'done' (Law 20)")
    void taskEntityRefusesOverwritingDoneStatus() {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.initializeStatus(TaskStatus.done);

        assertThatThrownBy(() -> task.setStatus(TaskStatus.queued))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be overwritten");

        assertThatThrownBy(() -> task.setStatus(TaskStatus.in_progress))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be overwritten");

        // Идемпотентная установка того же статуса допустима
        task.setStatus(TaskStatus.done);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.done);
    }

    @Test
    @DisplayName("Инвариант 1: TaskEntity запрещает воскрешение задачи из терминального статуса 'failed'")
    void taskEntityRefusesOverwritingFailedStatus() {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        task.initializeStatus(TaskStatus.failed);

        assertThatThrownBy(() -> task.setStatus(TaskStatus.queued))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be overwritten");
    }

    // --- 2. Verdict & Judgement: Kleene 3-Valued Conjunction ---

    @Test
    @DisplayName("Инвариант 2: WITHHOLD не может быть переголосован ни одним PERMIT (No approval outvotes refusal)")
    void withholdCannotBeOutvoted() {
        assertThat(Verdict.WITHHOLD.and(Verdict.PERMIT)).isEqualTo(Verdict.WITHHOLD);
        assertThat(Verdict.PERMIT.and(Verdict.WITHHOLD)).isEqualTo(Verdict.WITHHOLD);
        assertThat(Verdict.WITHHOLD.and(Verdict.ABSTAIN)).isEqualTo(Verdict.WITHHOLD);
        assertThat(Verdict.ABSTAIN.and(Verdict.WITHHOLD)).isEqualTo(Verdict.WITHHOLD);
    }

    @Test
    @DisplayName("Инвариант 2: ABSTAIN блокирует продвижение (Abstention is not permission)")
    void abstainBlocksAdvancement() {
        assertThat(Verdict.PERMIT.and(Verdict.ABSTAIN)).isEqualTo(Verdict.ABSTAIN);
        assertThat(Verdict.ABSTAIN.and(Verdict.PERMIT)).isEqualTo(Verdict.ABSTAIN);
        assertThat(Verdict.ABSTAIN.and(Verdict.ABSTAIN)).isEqualTo(Verdict.ABSTAIN);
    }

    @Test
    @DisplayName("Инвариант 2: PERMIT достигается строго при истинности всех слоёв")
    void permitRequiresAllPermits() {
        assertThat(Verdict.PERMIT.and(Verdict.PERMIT)).isEqualTo(Verdict.PERMIT);
    }

    @Test
    @DisplayName("Инвариант 2: Null-вердикт безопасно редуцируется в ABSTAIN (неопределённость не есть разрешение)")
    void nullVerdictDefaultsToAbstain() {
        assertThat(Verdict.PERMIT.and(null)).isEqualTo(Verdict.ABSTAIN);
        assertThat(Verdict.WITHHOLD.and(null)).isEqualTo(Verdict.WITHHOLD);
    }

    @Test
    @DisplayName("Инвариант 2: Judgement фабричные методы создают консистентные свидетельства")
    void judgementFactoryMethodsPreserveInvariants() {
        Judgement permit = Judgement.permit("test-layer", "declared property holds", "ref-ok");
        assertThat(permit.verdict()).isEqualTo(Verdict.PERMIT);

        Judgement abstain = Judgement.abstain("test-layer", "declared property", "measurement unavailable");
        assertThat(abstain.verdict()).isEqualTo(Verdict.ABSTAIN);

        Judgement withhold = Judgement.withhold("test-layer", "declared property", "check failed", "ref-1");
        assertThat(withhold.verdict()).isEqualTo(Verdict.WITHHOLD);
        assertThat(withhold.evidence()).isEqualTo("ref-1");
    }

    // --- 3. LeverStage & LeverAgreement: Asymmetric Trust Ladder & Belnap 4-Valued State ---

    @Test
    @DisplayName("Инвариант 3: LeverAgreement.compare честно возвращает NEITHER при отсутствии ground truth (не подменяя на false)")
    void leverAgreementHonorsNeitherWhenNoGroundTruth() {
        assertThat(LeverAgreement.compare("incumbent", "candidate", null)).isEqualTo(LeverAgreement.NEITHER);
    }

    @Test
    @DisplayName("Инвариант 3: LeverAgreement.compare различает TRUE (превосходство кандидата) и FALSE (превосходство текущего)")
    void leverAgreementDistinguishesTrueAndFalse() {
        // Кандидат прав, текущий ошибся -> TRUE
        assertThat(LeverAgreement.compare("wrong", "right", "right")).isEqualTo(LeverAgreement.TRUE);
        // Текущий прав, кандидат ошибся -> FALSE
        assertThat(LeverAgreement.compare("right", "wrong", "right")).isEqualTo(LeverAgreement.FALSE);
        // Оба правы -> BOTH (не даёт сравнительного преимущества)
        assertThat(LeverAgreement.compare("right", "right", "right")).isEqualTo(LeverAgreement.BOTH);
        // Оба ошиблись -> NEITHER
        assertThat(LeverAgreement.compare("wrong1", "wrong2", "actual")).isEqualTo(LeverAgreement.NEITHER);
    }

    @Test
    @DisplayName("Инвариант 3: LeverStage лестница уровней доверия ограничена сверху и снизу")
    void leverStageLadderIsBounded() {
        assertThat(LeverStage.OBSERVE_ONLY.previous()).isEqualTo(LeverStage.OBSERVE_ONLY);
        assertThat(LeverStage.AUTO_REMEDIATE.next()).isEqualTo(LeverStage.AUTO_REMEDIATE);

        assertThat(LeverStage.OBSERVE_ONLY.next()).isEqualTo(LeverStage.WARN_ONLY);
        assertThat(LeverStage.WARN_ONLY.next()).isEqualTo(LeverStage.SOFT_GATE);
        assertThat(LeverStage.SOFT_GATE.next()).isEqualTo(LeverStage.HARD_GATE);
        assertThat(LeverStage.HARD_GATE.next()).isEqualTo(LeverStage.AUTO_REMEDIATE);

        // fromWireValue fallback
        assertThat(LeverStage.fromWireValue("unknown_value")).isEqualTo(LeverStage.OBSERVE_ONLY);
    }

    // --- 4. MLPredictionServiceClient: Fail-Closed Embedding Passthrough ---

    @Test
    @DisplayName("Инвариант 4: MLPredictionServiceClient.embed возвращает null (а не мусорный вектор) при отключенном Gemini")
    void mlClientEmbedFailsClosedWhenGeminiDisabled() {
        SystemSettingsService settingsService = mock(SystemSettingsService.class);
        when(settingsService.effectiveBoolean("gemini_enabled")).thenReturn(false);
        when(settingsService.effectiveValue("gemini_api_key")).thenReturn("some-key");

        MLPredictionServiceClient client = new MLPredictionServiceClient(
                new RestTemplateBuilder(), "http://localhost:8000", settingsService, new AiHealthTracker(), null);

        float[] embedding = client.embed("test query");
        assertThat(embedding).isNull();
    }

    @Test
    @DisplayName("Инвариант 4: MLPredictionServiceClient.embed возвращает null при отсутствии API ключа")
    void mlClientEmbedFailsClosedWhenApiKeyMissing() {
        SystemSettingsService settingsService = mock(SystemSettingsService.class);
        when(settingsService.effectiveBoolean("gemini_enabled")).thenReturn(true);
        when(settingsService.effectiveValue("gemini_api_key")).thenReturn("");

        MLPredictionServiceClient client = new MLPredictionServiceClient(
                new RestTemplateBuilder(), "http://localhost:8000", settingsService, new AiHealthTracker(), null);

        float[] embedding = client.embed("test query");
        assertThat(embedding).isNull();
    }
}
