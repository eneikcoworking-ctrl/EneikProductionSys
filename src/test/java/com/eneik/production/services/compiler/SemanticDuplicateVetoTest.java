package com.eneik.production.services.compiler;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.ProjectFileClaimRepository;
import com.eneik.production.repositories.ProjectGenerationStateRepository;
import com.eneik.production.repositories.ProjectHotspotFileRepository;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.RoleRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.BottleneckAwarePriorityService;
import com.eneik.production.services.FeatureService;
import com.eneik.production.services.GeminiContextService;
import com.eneik.production.services.gate.GateOrchestrator;
import com.eneik.production.services.github.GitHubPullRequestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Plan 4.45. The semantic-duplicate veto refuses to create a task when one carrying the same key already
 * exists. Measured on the live circuit 2026-08-30: it counted `failed` ones, so the two Must-Be client
 * requirements V129/V132 returned to the flow were each pointed straight back at the dead task they were
 * meant to replace and dismissed again -
 *
 *     19:00:38  task d55621e0 marked failed - PR#437 closed without merge on GitHub
 *     10:24:27  wishlist b8dca98a collapsed into the existing semantic duplicate task d55621e0
 *
 * What survives a failure is the requirement, not the task identity.
 */
class SemanticDuplicateVetoTest {

    private static final String KEY = "ems:ab9d5bb56e61122911219ab4";

    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final TechnicalLeadCompiler compiler = new TechnicalLeadCompiler(
            mock(WishlistRepository.class),
            taskRepository,
            mock(ProjectRepository.class),
            mock(RoleRepository.class),
            mock(ProjectGenerationStateRepository.class),
            mock(GateOrchestrator.class),
            mock(BottleneckAwarePriorityService.class),
            objectMapper,
            mock(ProjectHotspotFileRepository.class),
            mock(FeatureService.class),
            mock(GitHubPullRequestService.class),
            mock(ProjectFileClaimRepository.class),
            mock(GeminiContextService.class));

    @Test
    void aFailedTaskDoesNotVetoItsOwnReplacement() {
        UUID projectId = UUID.randomUUID();
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId))
                .thenReturn(List.of(task(projectId, KEY, TaskStatus.failed)));

        assertThat(compiler.findLiveSemanticTask(projectId, KEY)).isEmpty();
    }

    @Test
    void aLiveTaskStillVetoesADuplicate() {
        // The veto's whole purpose. Without this the fix would trade a lost requirement for duplicate work.
        UUID projectId = UUID.randomUUID();
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId))
                .thenReturn(List.of(task(projectId, KEY, TaskStatus.queued)));

        assertThat(compiler.findLiveSemanticTask(projectId, KEY)).isPresent();
    }

    @Test
    void aDoneTaskStillVetoesADuplicate() {
        // A delivered requirement is satisfied - recompiling its brief must not mint a second task for it.
        UUID projectId = UUID.randomUUID();
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId))
                .thenReturn(List.of(task(projectId, KEY, TaskStatus.done)));

        assertThat(compiler.findLiveSemanticTask(projectId, KEY)).isPresent();
    }

    @Test
    void aNewerFailedAttemptDoesNotHideAnOlderLiveTask() {
        // The lookup is newest-first. Filtering the result instead of the stream would answer "empty" here
        // and mint a duplicate alongside work that is still running.
        UUID projectId = UUID.randomUUID();
        when(taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId))
                .thenReturn(List.of(task(projectId, KEY, TaskStatus.failed), task(projectId, KEY, TaskStatus.in_progress)));

        assertThat(compiler.findLiveSemanticTask(projectId, KEY)).isPresent();
    }

    private TaskEntity task(UUID projectId, String semanticKey, TaskStatus status) {
        TaskEntity task = new TaskEntity();
        task.setId(UUID.randomUUID());
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);
        task.setProject(project);
        task.setStatus(status);
        task.setPayload(objectMapper.createObjectNode().put("ems_semantic_key", semanticKey));
        return task;
    }
}
