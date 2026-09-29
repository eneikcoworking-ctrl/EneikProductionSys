package com.eneik.production.services.operational;

import com.eneik.production.controllers.projects.ProjectController;
import com.eneik.production.dto.operational.FlowCoreDto;
import com.eneik.production.dto.operational.FlowSpineDto;
import com.eneik.production.services.ProjectFlowService;
import com.eneik.production.services.verdict.VerdictGate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Фальсифицирующий тест-заслон для OperationalPolicyDeniedException.
 * <p>
 * Философский RAG-каркас:
 * - BARCAN-TAG-10_DEONTIC-PROHIBITION:03:dzhozef-raz / DZHOZEF_RAZ_01_PROHIBITION_AS_CODE (D006, Raz):
 *   Отказ как исполняемый путь с объяснимым основанием. Запрещённое действие не валится в непроверяемый
 *   сбой и не игнорируется молча, а выбрасывает типизированное OperationalPolicyDeniedException
 *   с сохранением проектного идентификатора, типа действия, состояния машины и объяснимой причины.
 * - BARCAN-TAG-10_DEONTIC-PROHIBITION:03:dzhozef-raz / DZHOZEF_RAZ_02_RIGHTS_DUTIES_MATRIX (D006, Raz):
 *   Матрица прав и обязанностей. Проверяет поведение requireAllowed для разрешённых и запрещённых
 *   действий по ключевым отношениям состояний (терминальные, блокирующие, наличие очереди/восстановления),
 *   а также интеграцию с исключениями восстановления (recovery exemption) и презентацию на границе HTTP (409 Conflict).
 */
@DisplayName("Falsification: OperationalPolicyDeniedException & Prohibition as Code (D006, Raz)")
class OperationalPolicyDeniedExceptionFalsificationTest {

    private final UUID projectId = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Nested
    @DisplayName("RAZ_01: Структурная целостность и исполняемый путь отказа (D006)")
    class ProhibitionAsCodeStructureTests {

        @Test
        @DisplayName("RAZ_01: Исключение сохраняет полный контекст отказа (projectId, action, state, authorizationStatus, reason)")
        void exceptionPreservesAllContextualFields() {
            String reason = "Operational action DISPATCH_QUEUED_TASKS is denied because queue is empty";
            OperationalPolicyDeniedException ex = new OperationalPolicyDeniedException(
                    projectId,
                    OperationalAction.DISPATCH_QUEUED_TASKS,
                    "IDLE",
                    "AUTHORIZED",
                    reason
            );

            assertThat(ex.projectId())
                    .as("Идентификатор проекта в отказе должен сохраняться неизменным")
                    .isEqualTo(projectId);
            assertThat(ex.action())
                    .as("Тип операционного действия должен быть типизирован")
                    .isEqualTo(OperationalAction.DISPATCH_QUEUED_TASKS);
            assertThat(ex.state())
                    .as("Состояние машины состояний должно быть зафиксировано")
                    .isEqualTo("IDLE");
            assertThat(ex.authorizationStatus())
                    .as("Статус авторизации Flow Core должен сохраняться")
                    .isEqualTo("AUTHORIZED");
            assertThat(ex.getMessage())
                    .as("Сообщение исключения обязано содержать объяснимую причину отказа")
                    .isEqualTo(reason);
        }

        @Test
        @DisplayName("RAZ_01: requireAllowed выбрасывает типизированный отказ при запрете действия")
        void requireAllowedThrowsOperationalPolicyDeniedExceptionWhenProhibited() {
            OperationalFlowCoreService flowCoreService = mock(OperationalFlowCoreService.class);
            // Проект в состоянии BLOCKED_BY_TASK без очереди задач
            FlowCoreDto core = buildCore("BLOCKED_BY_TASK", "active", 0, 0, 0, 0, 0, 0);
            when(flowCoreService.build(projectId)).thenReturn(core);

            OperationalPolicyService policyService = new OperationalPolicyService(flowCoreService, null);

            OperationalPolicyDeniedException ex = assertThrows(
                    OperationalPolicyDeniedException.class,
                    () -> policyService.requireAllowed(projectId, OperationalAction.DISPATCH_QUEUED_TASKS),
                    "Запрет действия обязан приводить к OperationalPolicyDeniedException, а не молчаливому пропуску"
            );

            assertThat(ex.projectId()).isEqualTo(projectId);
            assertThat(ex.action()).isEqualTo(OperationalAction.DISPATCH_QUEUED_TASKS);
            assertThat(ex.state()).isEqualTo("BLOCKED_BY_TASK");
            assertThat(ex.getMessage()).contains("precondition is unmet - there is nothing for it to act on right now");
        }

        @Test
        @DisplayName("RAZ_01: requireAllowed выполняется штатно без исключений при разрешённом действии")
        void requireAllowedExecutesSilentlyWhenPermitted() {
            OperationalFlowCoreService flowCoreService = mock(OperationalFlowCoreService.class);
            // Активный проект с задачами в очереди
            FlowCoreDto core = buildCore("IDLE", "active", 3, 0, 0, 0, 0, 0);
            when(flowCoreService.build(projectId)).thenReturn(core);

            OperationalPolicyService policyService = new OperationalPolicyService(flowCoreService, null);

            assertDoesNotThrow(
                    () -> policyService.requireAllowed(projectId, OperationalAction.DISPATCH_QUEUED_TASKS),
                    "Разрешённое действие не должно выбрасывать исключений"
            );
        }
    }

    @Nested
    @DisplayName("RAZ_02: Матрица прав и обязанностей по состояниям (D006)")
    class RightsDutiesMatrixTests {

        @Test
        @DisplayName("RAZ_02: Отношение терминальности (ARCHIVED) — запрет оркестрации и добавления требований, разрешение очистки")
        void terminalStateMatrix() {
            OperationalFlowCoreService flowCoreService = mock(OperationalFlowCoreService.class);
            FlowCoreDto core = buildCore("ARCHIVED", "archived", 0, 0, 0, 0, 0, 0);
            when(flowCoreService.build(projectId)).thenReturn(core);

            OperationalPolicyService policyService = new OperationalPolicyService(flowCoreService, null);

            // Запрещено: ORCHESTRATE
            OperationalPolicyDeniedException exOrch = assertThrows(
                    OperationalPolicyDeniedException.class,
                    () -> policyService.requireAllowed(projectId, OperationalAction.ORCHESTRATE)
            );
            assertThat(exOrch.action()).isEqualTo(OperationalAction.ORCHESTRATE);
            assertThat(exOrch.state()).isEqualTo("ARCHIVED");

            // Запрещено: ADD_WISHLIST
            OperationalPolicyDeniedException exWish = assertThrows(
                    OperationalPolicyDeniedException.class,
                    () -> policyService.requireAllowed(projectId, OperationalAction.ADD_WISHLIST)
            );
            assertThat(exWish.action()).isEqualTo(OperationalAction.ADD_WISHLIST);

            // Разрешено: CLEANUP_TERMINAL_PROJECT
            assertDoesNotThrow(
                    () -> policyService.requireAllowed(projectId, OperationalAction.CLEANUP_TERMINAL_PROJECT)
            );
        }

        @Test
        @DisplayName("RAZ_02: Отношение глобальной блокировки (FROZEN / GITHUB_RATE_LIMITED) — запрет диспетчеризации и восстановления")
        void globallyBlockingStateMatrix() {
            OperationalFlowCoreService flowCoreService = mock(OperationalFlowCoreService.class);
            FlowCoreDto core = buildCore("FROZEN", "frozen", 5, 0, 0, 0, 0, 2);
            when(flowCoreService.build(projectId)).thenReturn(core);

            OperationalPolicyService policyService = new OperationalPolicyService(flowCoreService, null);

            // Запрещено: DISPATCH_QUEUED_TASKS (даже при наличии 5 задач)
            OperationalPolicyDeniedException exDispatch = assertThrows(
                    OperationalPolicyDeniedException.class,
                    () -> policyService.requireAllowed(projectId, OperationalAction.DISPATCH_QUEUED_TASKS)
            );
            assertThat(exDispatch.action()).isEqualTo(OperationalAction.DISPATCH_QUEUED_TASKS);
            assertThat(exDispatch.state()).isEqualTo("FROZEN");

            // Запрещено: RECOVER_FAILED_FRONTIER (даже при наличии сбойных задач для восстановления)
            OperationalPolicyDeniedException exRecover = assertThrows(
                    OperationalPolicyDeniedException.class,
                    () -> policyService.requireAllowed(projectId, OperationalAction.RECOVER_FAILED_FRONTIER)
            );
            assertThat(exRecover.action()).isEqualTo(OperationalAction.RECOVER_FAILED_FRONTIER);
        }

        @Test
        @DisplayName("RAZ_02: Отношение наличия работы — запрет при отсутствии факта работы, разрешение при наличии")
        void workAvailabilityMatrix() {
            OperationalFlowCoreService flowCoreService = mock(OperationalFlowCoreService.class);

            // 1. Очередь пуста -> DISPATCH_QUEUED_TASKS запрещено
            FlowCoreDto emptyCore = buildCore("IDLE", "active", 0, 0, 0, 0, 0, 0);
            when(flowCoreService.build(projectId)).thenReturn(emptyCore);
            OperationalPolicyService policyService = new OperationalPolicyService(flowCoreService, null);

            OperationalPolicyDeniedException exEmpty = assertThrows(
                    OperationalPolicyDeniedException.class,
                    () -> policyService.requireAllowed(projectId, OperationalAction.DISPATCH_QUEUED_TASKS)
            );
            assertThat(exEmpty.getMessage()).contains("precondition is unmet - there is nothing for it to act on right now");

            // 2. В очереди есть задачи -> DISPATCH_QUEUED_TASKS разрешено
            FlowCoreDto workCore = buildCore("IDLE", "active", 2, 0, 0, 0, 0, 0);
            when(flowCoreService.build(projectId)).thenReturn(workCore);
            assertDoesNotThrow(() -> policyService.requireAllowed(projectId, OperationalAction.DISPATCH_QUEUED_TASKS));

            // 3. Нет задач для восстановления -> RECOVER_FAILED_FRONTIER запрещено
            OperationalPolicyDeniedException exNoRecovery = assertThrows(
                    OperationalPolicyDeniedException.class,
                    () -> policyService.requireAllowed(projectId, OperationalAction.RECOVER_FAILED_FRONTIER)
            );
            assertThat(exNoRecovery.getMessage()).contains("precondition is unmet - there is nothing for it to act on right now");

            // 4. Есть задачи для восстановления -> RECOVER_FAILED_FRONTIER разрешено
            FlowCoreDto recoveryCore = buildCore("IDLE", "active", 0, 0, 0, 0, 0, 1);
            when(flowCoreService.build(projectId)).thenReturn(recoveryCore);
            assertDoesNotThrow(() -> policyService.requireAllowed(projectId, OperationalAction.RECOVER_FAILED_FRONTIER));
        }
    }

    @Nested
    @DisplayName("RAZ_01/02: Интеграция с VerdictGate и исключение восстановления (D006)")
    class VerdictGateIntegrationTests {

        @Test
        @DisplayName("RAZ_01: Запрет VerdictGate транслируется в OperationalPolicyDeniedException с именем правила")
        void verdictGateProhibitionThrowsExceptionWithRuleName() {
            OperationalFlowCoreService flowCoreService = mock(OperationalFlowCoreService.class);
            VerdictGate verdictGate = mock(VerdictGate.class);

            FlowCoreDto core = buildCore("IDLE", "active", 2, 0, 0, 0, 0, 0);
            when(flowCoreService.build(projectId)).thenReturn(core);

            // VerdictGate запрещает диспетчеризацию по инфраструктурному правилу
            when(verdictGate.evaluateActionProhibition(any(), eq("DISPATCH_QUEUED_TASKS")))
                    .thenReturn(VerdictGate.ActionProhibition.denied(
                            "infrastructure",
                            "the orchestrator's own database is healthy",
                            "INFRASTRUCTURE_HEALTH_DISPATCH_PROHIBITION",
                            "database bloat exceeds safety threshold"
                    ));

            OperationalPolicyService policyService = new OperationalPolicyService(flowCoreService, verdictGate);

            OperationalPolicyDeniedException ex = assertThrows(
                    OperationalPolicyDeniedException.class,
                    () -> policyService.requireAllowed(projectId, OperationalAction.DISPATCH_QUEUED_TASKS)
            );

            assertThat(ex.action()).isEqualTo(OperationalAction.DISPATCH_QUEUED_TASKS);
            assertThat(ex.getMessage())
                    .contains("INFRASTRUCTURE_HEALTH_DISPATCH_PROHIBITION")
                    .contains("database bloat exceeds safety threshold");
        }

        @Test
        @DisplayName("RAZ_02: Запрет VerdictGate с recovery exemption не блокирует цикл диспетчеризации (защита от дедлока починки)")
        void verdictGateProhibitionWithRecoveryExemptionPermitsDispatch() {
            OperationalFlowCoreService flowCoreService = mock(OperationalFlowCoreService.class);
            VerdictGate verdictGate = mock(VerdictGate.class);

            FlowCoreDto core = buildCore("IDLE", "active", 2, 0, 0, 0, 0, 0);
            when(flowCoreService.build(projectId)).thenReturn(core);

            // Правило доктрины блокирует обычную работу, но даёт recovery exemption
            when(verdictGate.evaluateActionProhibition(any(), eq("DISPATCH_QUEUED_TASKS")))
                    .thenReturn(VerdictGate.ActionProhibition.deniedWithRecoveryExemption(
                            "doctrine",
                            "doctrine BARCAN-TAG-00 accepts current state",
                            "DOCTRINE_UNRECOVERED_FAILURE_PROHIBITION",
                            "Owner-role execution has unrecovered failed work"
                    ));

            OperationalPolicyService policyService = new OperationalPolicyService(flowCoreService, verdictGate);

            // Диспетчеризация не блокируется глобально — recovery задачи должны иметь возможность выйти
            assertDoesNotThrow(
                    () -> policyService.requireAllowed(projectId, OperationalAction.DISPATCH_QUEUED_TASKS),
                    "Запрет с recovery exemption не должен выбрасывать исключение для DISPATCH_QUEUED_TASKS"
            );
        }
    }

    @Nested
    @DisplayName("RAZ_01/02: Трансляция на границе HTTP в ProjectController (409 Conflict)")
    class CatchSitePresentationTests {

        @Test
        @DisplayName("RAZ_01: ProjectController транслирует OperationalPolicyDeniedException в HTTP 409 со структурированным телом")
        void projectControllerTranslatesDeniedExceptionTo409Conflict() {
            ProjectFlowService flowService = mock(ProjectFlowService.class);
            OperationalPolicyDeniedException ex = new OperationalPolicyDeniedException(
                    projectId,
                    OperationalAction.ORCHESTRATE,
                    "ARCHIVED",
                    "UNAUTHORIZED",
                    "Operational action ORCHESTRATE is denied in state ARCHIVED"
            );
            when(flowService.orchestrate(projectId)).thenThrow(ex);

            ProjectController controller = new ProjectController(
                    flowService,
                    null, null, null, null, null, null, null, null, null, null
            );

            ResponseEntity<?> response = controller.orchestrate(projectId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody()).isInstanceOf(Map.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsEntry("code", 409);
            assertThat(body).containsEntry("action", "ORCHESTRATE");
            assertThat(body).containsEntry("state", "ARCHIVED");
            assertThat(body).containsEntry("authorizationStatus", "UNAUTHORIZED");
            assertThat(body).containsEntry("error", ex.getMessage());
        }
    }

    private FlowCoreDto buildCore(String state,
                                  String projectStatus,
                                  long queuedTasks,
                                  long reviewTasks,
                                  int openReviews,
                                  long pendingWishlist,
                                  long compilingWishlist,
                                  long failedTasksRecoveryCanResume) {
        FlowSpineDto.Transition transition = new FlowSpineDto.Transition(
                state, "NEXT", "owner", "action", List.of("evidence"), "reason");
        FlowSpineDto snapshot = new FlowSpineDto(
                Instant.EPOCH,
                "observe_only",
                new FlowSpineDto.ProjectRef(projectId, "test-project", projectStatus, "test-slug"),
                state,
                "value_in_progress",
                state.startsWith("BLOCKED_") ? "blocked" : "",
                transition,
                List.of(transition),
                List.of(),
                List.of(),
                List.of(),
                new FlowSpineDto.EvidenceVector(0, openReviews, 0, 0, 0,
                        pendingWishlist, compilingWishlist, 0, "ok", state.equals("BLOCKED_BY_DUPLICATE_CONTENT")),
                new FlowSpineDto.FlowCounts(
                        queuedTasks, 0, reviewTasks, 0, failedTasksRecoveryCanResume, 0, 0,
                        1, 0, 1, 0, 0, "unknown", false, pendingWishlist == 0 && compilingWishlist == 0,
                        0, 0, 0, 0, 0.0),
                List.of(),
                new FlowSpineDto.JournalSummary(null, null, null, null, "hash", false, 0),
                "deterministic precedence"
        );
        FlowCoreDto.Decision decision = OperationalFlowCoreService.decide(snapshot);
        FlowCoreDto.Authorization authorization = OperationalFlowCoreService.authorization(snapshot, decision);
        return new FlowCoreDto(
                Instant.EPOCH,
                "flow_core_enforced",
                snapshot.project(),
                snapshot,
                decision,
                authorization,
                new FlowCoreDto.MathematicalContract("facts", "decision", "precedence", "safety", List.of()),
                new FlowCoreDto.Journal(null, null, false, 0)
        );
    }
}
