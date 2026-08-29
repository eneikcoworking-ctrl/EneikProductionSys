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

    /**
     * Plan §4.37. Section 2 of the plan states that a status change writes the state mark atomically. It
     * did not: measured 2026-08-30 on the live database, 271 of 412 tasks carried updated_at exactly equal
     * to created_at, including 257 of 375 done ones, because these CAS writes are bulk JPQL updates and
     * never touched it - so every "has not moved in over X" predicate was reading an age, not a movement.
     */
    @Test
    void aStatusChangeWritesTheMovementMarkInTheSameAtomicWrite() throws InterruptedException {
        RoleEntity role = new RoleEntity();
        role.setTag("MOVEMENT-TAG");
        role.setRulesPath("rules/");
        entityManager.persist(role);

        Instant createdAt = Instant.now().minusSeconds(3600);
        TaskEntity task = new TaskEntity();
        task.setRole(role);
        task.setDescription("Moves through states");
        task.setStatus(TaskStatus.queued);
        task.setCreatedAt(createdAt);
        task.setUpdatedAt(createdAt);
        entityManager.persist(task);
        entityManager.flush();

        assertEquals(1, taskRepository.compareAndSetStatus(task.getId(), TaskStatus.queued, TaskStatus.claimed));
        entityManager.clear();

        TaskEntity moved = taskRepository.findById(task.getId()).orElseThrow();
        assertEquals(TaskStatus.claimed, moved.getStatus());
        assertTrue(moved.getUpdatedAt().isAfter(createdAt),
                "the mark must move with the status, not stay at creation time");
    }

    /**
     * The other half, and it is not optional: the mark must be the one the write was given, not whatever
     * the entity callback would stamp on the way past. Without this the callback alone could satisfy the
     * test above while the CAS - the write that actually changes state, and the one a bulk JPQL update
     * performs without ever reaching a callback - still left the mark behind.
     */
    @Test
    void theMarkTheStatusChangeWritesIsTheMomentItWasGiven() {
        RoleEntity role = new RoleEntity();
        role.setTag("EXACT-MARK-TAG");
        role.setRulesPath("rules/");
        entityManager.persist(role);

        Instant createdAt = Instant.now().minusSeconds(7200);
        TaskEntity task = new TaskEntity();
        task.setRole(role);
        task.setDescription("Moves at a known instant");
        task.setStatus(TaskStatus.queued);
        task.setCreatedAt(createdAt);
        task.setUpdatedAt(createdAt);
        entityManager.persist(task);
        entityManager.flush();
        entityManager.clear();

        Instant movedAt = createdAt.plusSeconds(1800);
        assertEquals(1, taskRepository.compareAndSetStatusAt(
                task.getId(), TaskStatus.queued, TaskStatus.claimed, movedAt));
        entityManager.clear();

        assertEquals(movedAt.toEpochMilli(),
                taskRepository.findById(task.getId()).orElseThrow().getUpdatedAt().toEpochMilli(),
                "the status change writes the mark itself, in the same statement");
    }

    @Test
    void testFileScopeLockIsolation() {
        com.eneik.production.models.persistence.ProjectEntity project = new com.eneik.production.models.persistence.ProjectEntity();
        project.setName("Test Project");
        project.setSlug("test-project");
        project.setStatus(com.eneik.production.models.persistence.ProjectStatus.active);
        project.setRepositoryName("test-repo");
        project.setRepositoryUrl("https://github.com/test/repo");
        entityManager.persist(project);

        RoleEntity role = new RoleEntity();
        role.setTag("TEST-TAG");
        role.setRulesPath("rules/");
        entityManager.persist(role);

        // Task 1: already claimed, file scope ["src/main/java/Service.java"]
        TaskEntity task1 = new TaskEntity();
        task1.setProject(project);
        task1.setRole(role);
        task1.setDescription("Task 1");
        task1.setStatus(TaskStatus.claimed);
        task1.setFileScope("[\"src/main/java/Service.java\"]");
        task1.setCreatedAt(Instant.now());
        task1.setUpdatedAt(Instant.now());
        entityManager.persist(task1);

        // Task 2: queued, conflicting file scope ["src/main/java/Service.java"]
        TaskEntity task2 = new TaskEntity();
        task2.setProject(project);
        task2.setRole(role);
        task2.setDescription("Task 2");
        task2.setStatus(TaskStatus.queued);
        task2.setFileScope("[\"src/main/java/Service.java\"]");
        task2.setCreatedAt(Instant.now().plusSeconds(10));
        task2.setUpdatedAt(Instant.now().plusSeconds(10));
        entityManager.persist(task2);

        // Task 3: queued, non-conflicting file scope ["src/main/java/Other.java"]
        TaskEntity task3 = new TaskEntity();
        task3.setProject(project);
        task3.setRole(role);
        task3.setDescription("Task 3");
        task3.setStatus(TaskStatus.queued);
        task3.setFileScope("[\"src/main/java/Other.java\"]");
        task3.setCreatedAt(Instant.now().plusSeconds(20));
        task3.setUpdatedAt(Instant.now().plusSeconds(20));
        entityManager.persist(task3);

        entityManager.flush();

        // When we lock task for the capable tag TEST-TAG
        java.util.Optional<TaskEntity> locked = taskRepository.lockNextQueuedTask(List.of("TEST-TAG"));
        
        // Then we should NOT lock task2 (conflicting with active task1), but we SHOULD lock task3 (non-conflicting)
        assertTrue(locked.isPresent());
        assertEquals("Task 3", locked.get().getDescription());
    }
}
