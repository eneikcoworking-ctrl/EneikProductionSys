package com.eneik.production.services;

import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.repositories.TaskRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TaskCarrierBackfillServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void backfillSynchronizesLegacyCarrierTasksAndSkipsRegularTasks() throws Exception {
        TaskRepository repository = mock(TaskRepository.class);
        TaskCarrierBackfillService service = new TaskCarrierBackfillService(repository);

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
    }

    @Test
    void emptyCandidatesReturnsZero() {
        TaskRepository repository = mock(TaskRepository.class);
        TaskCarrierBackfillService service = new TaskCarrierBackfillService(repository);

        when(repository.findCarrierBackfillCandidates()).thenReturn(List.of());

        int updated = service.backfillCarrierColumn();

        assertThat(updated).isZero();
        verify(repository).findCarrierBackfillCandidates();
        verify(repository, never()).save(any());
    }
}
