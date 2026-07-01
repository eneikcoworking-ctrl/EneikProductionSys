package com.eneik.production.repositories;

import com.eneik.production.dto.dashboard.QueueDashboardDto;
import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class TaskRepositoryIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TaskRepository taskRepository;

    @Test
    void testQueuedGroupedByTag() {
        RoleEntity role = new RoleEntity();
        role.setTag("TEST-TAG");
        role.setRulesPath("rules/");
        entityManager.persist(role);

        TaskEntity task = new TaskEntity();
        task.setRole(role);
        task.setDescription("Test task");
        task.setStatus(TaskStatus.queued);
        task.setCreatedAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        entityManager.persist(task);

        entityManager.flush();

        List<QueueDashboardDto.TagCountDto> results = taskRepository.queuedGroupedByTag();

        assertFalse(results.isEmpty());
        assertEquals("TEST-TAG", results.get(0).tag());
        assertEquals(1, results.get(0).count());
    }
}
