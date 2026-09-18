package com.eneik.production.services;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TaskContentKeyBackfillServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private ProjectEntity project(UUID id) {
        ProjectEntity p = new ProjectEntity();
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }

    @Test
    @DisplayName("Persistence snapshot (Parfit 1984 / D010): Backfill derives and materializes content_key for legacy compiler tasks")
    void backfillPopulatesContentKeyForLegacyCompilerTasks() throws Exception {
        TaskRepository repository = mock(TaskRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(TaskContentKeyBackfillService.BACKFILL_SETTING_KEY)))
                .thenReturn(0);

        TaskContentKeyBackfillService service = new TaskContentKeyBackfillService(repository, jdbcTemplate);

        UUID projectId = UUID.randomUUID();
        UUID w1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID w2 = UUID.fromString("22222222-2222-2222-2222-222222222222");

        // 1. Legacy batch compiler task
        TaskEntity batchCompilerTask = new TaskEntity();
        batchCompilerTask.setId(UUID.randomUUID());
        batchCompilerTask.setProject(project(projectId));
        batchCompilerTask.setTitle("Compile 2 wishlist(s) into task graph");
        batchCompilerTask.setPayload(mapper.readTree("{\"taskType\":\"wishlist_compiler\",\"compilesWishlistIds\":[\"" + w2 + "\",\"" + w1 + "\"]}"));
        batchCompilerTask.setContentKey(null);

        // 2. Legacy persistent worker compiler task
        TaskEntity persistentWorkerTask = new TaskEntity();
        persistentWorkerTask.setId(UUID.randomUUID());
        persistentWorkerTask.setProject(project(projectId));
        persistentWorkerTask.setTitle("Persistent wishlist compiler worker (abcd)");
        persistentWorkerTask.setPayload(mapper.readTree("{\"taskType\":\"wishlist_compiler\",\"persistentWorkerCarrier\":true}"));
        persistentWorkerTask.setContentKey(null);

        // 3. Regular feature task (non-compiler)
        TaskEntity regularTask = new TaskEntity();
        regularTask.setId(UUID.randomUUID());
        regularTask.setProject(project(projectId));
        regularTask.setTitle("Build UI header");
        regularTask.setPayload(mapper.readTree("{\"feature\":\"header\"}"));
        regularTask.setContentKey(null);

        when(repository.findContentKeyBackfillCandidates())
                .thenReturn(List.of(batchCompilerTask, persistentWorkerTask, regularTask));

        int updated = service.backfillContentKeyColumn();

        assertThat(updated).isEqualTo(2);

        // Verify batch compiler task got canonical sorted content_key
        String expectedBatchKey = ProjectFlowService.compilerContentKeyFromIds(projectId, List.of(w1, w2));
        assertThat(batchCompilerTask.getContentKey()).isEqualTo(expectedBatchKey);
        assertThat(batchCompilerTask.getContentKey()).startsWith("compile:" + projectId + ":");
        assertThat(batchCompilerTask.getContentKey().length()).isEqualTo(109);

        // Verify persistent worker got worker content_key
        assertThat(persistentWorkerTask.getContentKey()).isEqualTo("compile-worker:" + projectId);

        // Verify regular task was not touched
        assertThat(regularTask.getContentKey()).isNull();

        verify(repository).save(batchCompilerTask);
        verify(repository).save(persistentWorkerTask);
        verify(repository, never()).save(regularTask);
        verify(jdbcTemplate).update(eq("DELETE FROM system_settings WHERE \"key\" = ?"), eq(TaskContentKeyBackfillService.BACKFILL_SETTING_KEY));
    }

    @Test
    @DisplayName("Repetition of legacy work finds the backfilled task row instead of minting a duplicate")
    void repeatOfLegacyWorkFindsBackfilledTaskRow() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID w1 = UUID.randomUUID();
        UUID w2 = UUID.randomUUID();

        // Legacy task created before V137 (initially contentKey = null)
        TaskEntity legacyTask = new TaskEntity();
        legacyTask.setId(UUID.randomUUID());
        legacyTask.setProject(project(projectId));
        legacyTask.setTitle("Compile 2 wishlist(s)");
        legacyTask.setPayload(mapper.readTree("{\"taskType\":\"wishlist_compiler\",\"compilesWishlistIds\":[\"" + w1 + "\",\"" + w2 + "\"]}"));

        // Derive content_key via backfill logic
        String contentKey = TaskContentKeyBackfillService.deriveContentKeyFromPayload(projectId, legacyTask.getTitle(), legacyTask.getPayload());
        assertThat(contentKey).isNotNull();
        legacyTask.setContentKey(contentKey);

        // Mock repository lookup: finding task by contentKey
        TaskRepository repository = mock(TaskRepository.class);
        when(repository.findByProjectIdAndContentKeyOrderByCreatedAtDesc(projectId, contentKey))
                .thenReturn(List.of(legacyTask));

        // When the same work is asked for again, it finds the backfilled row
        String requestKey = ProjectFlowService.compilerContentKeyFromIds(projectId, List.of(w2, w1));
        List<TaskEntity> existing = repository.findByProjectIdAndContentKeyOrderByCreatedAtDesc(projectId, requestKey);

        assertThat(existing).isNotEmpty();
        assertThat(existing.get(0).getId()).isEqualTo(legacyTask.getId());
        assertThat(existing.get(0).getContentKey()).isEqualTo(contentKey);
    }

    @Test
    @DisplayName("Empty candidates or completed backfill skips task table scan on startup")
    void completedBackfillSkipsTaskScan() {
        TaskRepository repository = mock(TaskRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(TaskContentKeyBackfillService.BACKFILL_SETTING_KEY)))
                .thenReturn(1);

        TaskContentKeyBackfillService service = new TaskContentKeyBackfillService(repository, jdbcTemplate);

        int updated = service.backfillContentKeyColumn();

        assertThat(updated).isZero();
        verify(repository, never()).findContentKeyBackfillCandidates();
        verify(repository, never()).save(any());
    }
}
