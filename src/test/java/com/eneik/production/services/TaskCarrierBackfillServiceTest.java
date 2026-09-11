package com.eneik.production.services;

import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TaskCarrierBackfillServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void backfillSynchronizesLegacyCarrierTasksAndSkipsRegularTasks() throws Exception {
        TaskRepository repository = mock(TaskRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        // Backfill not yet completed
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(TaskCarrierBackfillService.BACKFILL_SETTING_KEY)))
                .thenReturn(0);

        TaskCarrierBackfillService service = new TaskCarrierBackfillService(repository, jdbcTemplate);

        TaskEntity carrierTask = new TaskEntity();
        carrierTask.setId(UUID.randomUUID());
        carrierTask.setTitle("wishlist compiler run");
        carrierTask.setPayload(mapper.readTree("{\"taskType\":\"wishlist_compiler\"}"));
        // Force carrier field to false to simulate legacy database row before migration V138
        carrierTask.setCarrier(false);

        TaskEntity regularTask = new TaskEntity();
        regularTask.setId(UUID.randomUUID());
        regularTask.setTitle("implement user login");
        regularTask.setPayload(mapper.readTree("{\"feature\":\"auth\",\"story_points\":3}"));
        regularTask.setCarrier(false);

        when(repository.findCarrierBackfillCandidates()).thenReturn(List.of(carrierTask, regularTask));

        int updated = service.backfillCarrierColumn();

        assertThat(updated).isEqualTo(1);
        assertThat(carrierTask.getCarrier()).isTrue();
        assertThat(carrierTask.isCarrier()).isTrue();
        assertThat(regularTask.getCarrier()).isFalse();
        assertThat(regularTask.isCarrier()).isFalse();

        verify(repository).findCarrierBackfillCandidates();
        verify(repository, times(1)).save(carrierTask);
        verify(repository, never()).save(regularTask);
        verify(jdbcTemplate).update(eq("DELETE FROM system_settings WHERE \"key\" = ?"), eq(TaskCarrierBackfillService.BACKFILL_SETTING_KEY));
    }

    @Test
    void emptyCandidatesReturnsZeroAndMarksCompleted() {
        TaskRepository repository = mock(TaskRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(TaskCarrierBackfillService.BACKFILL_SETTING_KEY)))
                .thenReturn(0);

        TaskCarrierBackfillService service = new TaskCarrierBackfillService(repository, jdbcTemplate);

        when(repository.findCarrierBackfillCandidates()).thenReturn(List.of());

        int updated = service.backfillCarrierColumn();

        assertThat(updated).isZero();
        verify(repository).findCarrierBackfillCandidates();
        verify(repository, never()).save(any());
        verify(jdbcTemplate).update(eq("DELETE FROM system_settings WHERE \"key\" = ?"), eq(TaskCarrierBackfillService.BACKFILL_SETTING_KEY));
    }

    @Test
    void secondStartupSkipsTaskScanWhenBackfillAlreadyMarkedCompleted() {
        TaskRepository repository = mock(TaskRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        // Backfill already completed on previous startup
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(TaskCarrierBackfillService.BACKFILL_SETTING_KEY)))
                .thenReturn(1);

        TaskCarrierBackfillService service = new TaskCarrierBackfillService(repository, jdbcTemplate);

        int updated = service.backfillCarrierColumn();

        assertThat(updated).isZero();
        // Verifies second startup does NOT read candidate rows from task repository
        verify(repository, never()).findCarrierBackfillCandidates();
        verify(repository, never()).save(any());
    }
}
