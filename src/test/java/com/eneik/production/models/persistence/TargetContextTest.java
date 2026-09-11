package com.eneik.production.models.persistence;

import com.eneik.production.services.PlannedWorkRecoveryService;
import com.eneik.production.services.jules.JulesApiClient;
import com.eneik.production.services.jules.JulesDispatchResult;
import com.eneik.production.services.jules.JulesDispatchService;
import com.eneik.production.repositories.JulesSessionRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.TaskConflictRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Screen for first-class target context semantics
 * (NUEL_BELNAP_03_TRUTH_STATUS_TABLE / D012 Policy contradiction,
 *  GILBERT_RAYL_03_CATEGORY_ERROR_SCAN / D002 Invalid state).
 *
 * Proof obligations:
 * 1. TargetContext explicitly represents PRODUCT_CODEBASE, ORCHESTRATOR_SYSTEM, and UNDETERMINED.
 * 2. TaskEntity and WishlistEntity initialize targetContext to UNDETERMINED.
 * 3. Null targetContext getter fallbacks return UNDETERMINED, never silently coercing to PRODUCT_CODEBASE.
 * 4. Dispatching a task with UNDETERMINED target context is rejected with an explicit failure reason,
 *    preventing irreversible external dispatch to any repository (Law 2: Carrier isolation).
 * 5. PlannedWorkRecoveryService identifies meta tasks through carrier type or ORCHESTRATOR_SYSTEM target context,
 *    eliminating brittle substring heuristics on task titles or descriptions.
 */
class TargetContextTest {

    @Test
    void targetContextEnumHasThreeExplicitStates() {
        assertThat(TargetContext.values()).containsExactlyInAnyOrder(
                TargetContext.PRODUCT_CODEBASE,
                TargetContext.ORCHESTRATOR_SYSTEM,
                TargetContext.UNDETERMINED
        );

        assertThat(TargetContext.UNDETERMINED.isUndetermined()).isTrue();
        assertThat(TargetContext.UNDETERMINED.isProductCodebase()).isFalse();
        assertThat(TargetContext.UNDETERMINED.isOrchestratorSystem()).isFalse();

        assertThat(TargetContext.PRODUCT_CODEBASE.isProductCodebase()).isTrue();
        assertThat(TargetContext.PRODUCT_CODEBASE.isUndetermined()).isFalse();

        assertThat(TargetContext.ORCHESTRATOR_SYSTEM.isOrchestratorSystem()).isTrue();
        assertThat(TargetContext.ORCHESTRATOR_SYSTEM.isUndetermined()).isFalse();
    }

    @Test
    void taskEntityAndWishlistEntityDefaultToUndeterminedAndNeverCoerceNullToProductCodebase() {
        TaskEntity task = new TaskEntity();
        assertThat(task.getTargetContext()).isEqualTo(TargetContext.UNDETERMINED);

        task.setTargetContext(null);
        assertThat(task.getTargetContext()).isEqualTo(TargetContext.UNDETERMINED);

        task.setTargetContext(TargetContext.PRODUCT_CODEBASE);
        assertThat(task.getTargetContext()).isEqualTo(TargetContext.PRODUCT_CODEBASE);

        WishlistEntity wishlist = new WishlistEntity();
        assertThat(wishlist.getTargetContext()).isEqualTo(TargetContext.UNDETERMINED);

        wishlist.setTargetContext(null);
        assertThat(wishlist.getTargetContext()).isEqualTo(TargetContext.UNDETERMINED);

        wishlist.setTargetContext(TargetContext.ORCHESTRATOR_SYSTEM);
        assertThat(wishlist.getTargetContext()).isEqualTo(TargetContext.ORCHESTRATOR_SYSTEM);
    }

    @Test
    void julesDispatchRejectsTaskWithUndeterminedTargetContextWithoutCallingExternalApi() {
        JulesApiClient apiClient = mock(JulesApiClient.class);
        JulesSessionRepository sessionRepository = mock(JulesSessionRepository.class);
        TaskRepository taskRepository = mock(TaskRepository.class);
        TaskConflictRepository conflictRepository = mock(TaskConflictRepository.class);

        when(sessionRepository.findByTaskId(any())).thenReturn(List.of());
        when(sessionRepository.save(any(JulesSessionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        JulesDispatchService dispatchService = mock(JulesDispatchService.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(dispatchService, "julesApiClient", apiClient);
        ReflectionTestUtils.setField(dispatchService, "julesSessionRepository", sessionRepository);
        ReflectionTestUtils.setField(dispatchService, "taskRepository", taskRepository);
        ReflectionTestUtils.setField(dispatchService, "taskConflictRepository", conflictRepository);

        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        ProjectEntity project = new ProjectEntity();
        project.setId(UUID.randomUUID());
        project.setRepositoryName("client-repo");
        task.setProject(project);

        // Explicitly UNDETERMINED
        task.setTargetContext(TargetContext.UNDETERMINED);
        JulesDispatchResult result = dispatchService.dispatch(task);

        assertThat(result.dispatched()).isFalse();
        assertThat(result.reason()).contains("undetermined");
        verify(apiClient, never()).createSession(any(), any(), any());
        verify(apiClient, never()).createSessionDetailed(any(), any(), any(), any(), any(), any());
    }

    @Test
    void plannedWorkRecoveryServiceIdentifiesMetaTaskWithoutSubstringHeuristics() {
        PlannedWorkRecoveryService recoveryService = mock(PlannedWorkRecoveryService.class, CALLS_REAL_METHODS);

        TaskEntity orchestratorTask = new TaskEntity();
        orchestratorTask.setTitle("Regular maintenance work");
        orchestratorTask.setTargetContext(TargetContext.ORCHESTRATOR_SYSTEM);
        Boolean isMeta1 = ReflectionTestUtils.invokeMethod(recoveryService, "isMetaTask", orchestratorTask);
        assertThat(isMeta1).isTrue();

        TaskEntity carrierTask = new TaskEntity();
        carrierTask.setTitle("Ordinary title");
        carrierTask.setTargetContext(TargetContext.PRODUCT_CODEBASE);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode payload = mapper.createObjectNode();
        payload.put("taskType", "compiler");
        carrierTask.setPayload(payload);
        carrierTask.setCarrier(true);
        Boolean isMeta2 = ReflectionTestUtils.invokeMethod(recoveryService, "isMetaTask", carrierTask);
        assertThat(isMeta2).isTrue();

        // Product task with misleading keywords should NOT be treated as a meta task
        TaskEntity productTask = new TaskEntity();
        productTask.setTitle("Fix stagnation in payment processing and compile 1 wishlist for export");
        productTask.setDescription("Include pr review fallback in customer support notes");
        productTask.setTargetContext(TargetContext.PRODUCT_CODEBASE);
        productTask.setCarrier(false);
        Boolean isMeta3 = ReflectionTestUtils.invokeMethod(recoveryService, "isMetaTask", productTask);
        assertThat(isMeta3).isFalse();
    }
}
