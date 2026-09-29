package com.eneik.production.migrations;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectHotspotFileEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.ProjectHotspotFileRepository;
import com.eneik.production.repositories.TaskRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Falsification test-screen for Migration V25 (tasks.depends_on and project_hotspot_files schema).
 *
 * Epistemic & Architectural grounding:
 * - BARCAN-TAG-01_ACTUALIST-OBJECT:05:dzhonatan-shaffer / DZHONATAN_SHAFFER_04_PART_WHOLE_OWNERSHIP [D004, Schaffer]:
 *   Directed merology and referential dependency in work-unit graphs. Explicit foreign key fk_tasks_depends_on
 *   prevents phantom dependency roots; repository dispatch skips tasks with unresolved dependencies until parent is done.
 * - BARCAN-TAG-01_ACTUALIST-OBJECT:05:dzhonatan-shaffer / DZHONATAN_SHAFFER_01_ACTUAL_OBJECT_REGISTER [D002, Schaffer]:
 *   Actual object register with owner, identity, and cascade deletion semantics. project_hotspot_files requires
 *   an existing project (NOT NULL + fk_hotspots_project) and cascade-deletes upon project deletion.
 */
@DataJpaTest
@ActiveProfiles("test")
class V25MigrationFalsificationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ProjectHotspotFileRepository hotspotRepository;

    private RoleEntity createRole(String tag) {
        RoleEntity role = new RoleEntity();
        role.setTag(tag);
        role.setRulesPath("rules/" + tag);
        role.setActive(true);
        RoleEntity persisted = entityManager.persist(role);
        entityManager.flush();
        return persisted;
    }

    private ProjectEntity createProject(String slug) {
        ProjectEntity project = new ProjectEntity();
        project.setName("Project " + slug);
        project.setSlug(slug);
        project.setStatus(ProjectStatus.active);
        project.setRepositoryName("repo-" + slug);
        project.setRepositoryUrl("https://github.com/eneikcoworking-ctrl/" + slug);
        ProjectEntity persisted = entityManager.persist(project);
        entityManager.flush();
        return persisted;
    }

    @Test
    @DisplayName("D004 / Schaffer: tasks.depends_on rejects phantom parent ID refuting ghost dependency edges")
    void tasksDependsOnFailsWhenReferencingNonExistentTask() {
        RoleEntity role = createRole("TAG-DEP-TEST");
        UUID ghostParentId = UUID.randomUUID();

        // Direct SQL insert attempting to point to a non-existent parent task ID
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO tasks (id, tag, description, status, depends_on, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), role.getTag(), "Orphan dependent task", "queued",
                ghostParentId, Instant.now(), Instant.now()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("FK_TASKS_DEPENDS_ON");
    }

    @Test
    @DisplayName("D004 / Schaffer: tasks.depends_on preserves directed dependency edge between valid tasks")
    void tasksDependsOnPersistsAndTraversesDirectedGraphEdge() {
        RoleEntity role = createRole("TAG-GRAPH");
        ProjectEntity project = createProject("proj-graph");

        TaskEntity parent = new TaskEntity();
        parent.setProject(project);
        parent.setRole(role);
        parent.setDescription("Parent work unit");
        parent.setStatus(TaskStatus.in_progress);
        parent.setCreatedAt(Instant.now());
        parent.setUpdatedAt(Instant.now());
        entityManager.persist(parent);

        TaskEntity child = new TaskEntity();
        child.setProject(project);
        child.setRole(role);
        child.setDescription("Child dependent unit");
        child.setStatus(TaskStatus.queued);
        child.setDependsOn(parent);
        child.setCreatedAt(Instant.now());
        child.setUpdatedAt(Instant.now());
        entityManager.persist(child);

        entityManager.flush();
        entityManager.clear();

        TaskEntity reloadedChild = taskRepository.findById(child.getId()).orElseThrow();
        assertThat(reloadedChild.getDependsOn()).isNotNull();
        assertThat(reloadedChild.getDependsOn().getId()).isEqualTo(parent.getId());
        assertThat(reloadedChild.getDependsOn().getDescription()).isEqualTo("Parent work unit");
    }

    @Test
    @DisplayName("D004 / Schaffer: hasUnresolvedDependency halts lockNextQueuedTask until parent task status is 'done'")
    void taskRepositoryEnforcesUnresolvedDependencyGate() {
        RoleEntity role = createRole("TAG-GATE");
        ProjectEntity project = createProject("proj-gate");

        TaskEntity parent = new TaskEntity();
        parent.setProject(project);
        parent.setRole(role);
        parent.setDescription("Initial Step");
        parent.setStatus(TaskStatus.in_progress);
        parent.setCreatedAt(Instant.now());
        parent.setUpdatedAt(Instant.now());
        entityManager.persist(parent);

        TaskEntity child = new TaskEntity();
        child.setProject(project);
        child.setRole(role);
        child.setDescription("Dependent Step");
        child.setStatus(TaskStatus.queued);
        child.setDependsOn(parent);
        child.setPriority(100); // Higher priority than anything else
        child.setCreatedAt(Instant.now());
        child.setUpdatedAt(Instant.now());
        entityManager.persist(child);

        entityManager.flush();

        // 1. When parent is in_progress, child has an unresolved dependency and cannot be locked
        assertThat(taskRepository.hasUnresolvedDependency(child)).isTrue();
        Optional<TaskEntity> lockedBefore = taskRepository.lockNextQueuedTask(List.of("TAG-GATE"));
        assertThat(lockedBefore).isEmpty();

        // 2. Transition parent to done
        parent.setStatus(TaskStatus.done);
        entityManager.persist(parent);
        entityManager.flush();

        // 3. Dependency is now resolved; child can be claimed and locked
        assertThat(taskRepository.hasUnresolvedDependency(child)).isFalse();
        Optional<TaskEntity> lockedAfter = taskRepository.lockNextQueuedTask(List.of("TAG-GATE"));
        assertThat(lockedAfter).isPresent();
        assertThat(lockedAfter.get().getId()).isEqualTo(child.getId());
    }

    @Test
    @DisplayName("D002 / Schaffer: project_hotspot_files rejects phantom project_id refuting orphan registers")
    void hotspotFilesFailsWhenReferencingNonExistentProject() {
        UUID ghostProjectId = UUID.randomUUID();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO project_hotspot_files (id, project_id, file_path) VALUES (?, ?, ?)",
                UUID.randomUUID(), ghostProjectId, "src/main/resources/application.properties"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("FK_HOTSPOTS_PROJECT");
    }

    @Test
    @DisplayName("D002 / Schaffer: project_hotspot_files rejects null values on project_id and file_path")
    void hotspotFilesEnforcesNonNullConstraints() {
        ProjectEntity project = createProject("proj-notnull");

        // Null project_id violates NOT NULL
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO project_hotspot_files (id, project_id, file_path) VALUES (?, ?, ?)",
                UUID.randomUUID(), null, "src/main/java/App.java"))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Null file_path violates NOT NULL
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO project_hotspot_files (id, project_id, file_path) VALUES (?, ?, ?)",
                UUID.randomUUID(), project.getId(), null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("D002 / Schaffer: project_hotspot_files cascade-deletes upon project deletion (ON DELETE CASCADE)")
    void hotspotFilesCascadeDeletesWithProject() {
        ProjectEntity project = createProject("proj-cascade");
        UUID projectId = project.getId();

        ProjectHotspotFileEntity h1 = new ProjectHotspotFileEntity();
        h1.setProjectId(projectId);
        h1.setFilePath("src/main/java/CoreService.java");
        hotspotRepository.save(h1);

        ProjectHotspotFileEntity h2 = new ProjectHotspotFileEntity();
        h2.setProjectId(projectId);
        h2.setFilePath("pom.xml");
        hotspotRepository.save(h2);

        entityManager.flush();
        entityManager.clear();

        List<ProjectHotspotFileEntity> beforeDelete = hotspotRepository.findByProjectId(projectId);
        assertThat(beforeDelete).hasSize(2)
                .extracting(ProjectHotspotFileEntity::getFilePath)
                .containsExactlyInAnyOrder("src/main/java/CoreService.java", "pom.xml");

        // Deleting the owning project triggers the DB level ON DELETE CASCADE
        jdbcTemplate.update("DELETE FROM projects WHERE id = ?", projectId);
        entityManager.clear();

        List<ProjectHotspotFileEntity> afterDelete = hotspotRepository.findByProjectId(projectId);
        assertThat(afterDelete).as("Hotspot files must be cascade-deleted with their owning project").isEmpty();

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM project_hotspot_files WHERE project_id = ?", Integer.class, projectId);
        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("D002 / Schaffer: hotspot repository enforces project scope isolation between distinct projects")
    void hotspotFilesScopeIsolationBetweenProjects() {
        ProjectEntity projectA = createProject("proj-alpha");
        ProjectEntity projectB = createProject("proj-beta");

        ProjectHotspotFileEntity hA = new ProjectHotspotFileEntity();
        hA.setProjectId(projectA.getId());
        hA.setFilePath("src/Alpha.java");
        hotspotRepository.save(hA);

        ProjectHotspotFileEntity hB = new ProjectHotspotFileEntity();
        hB.setProjectId(projectB.getId());
        hB.setFilePath("src/Beta.java");
        hotspotRepository.save(hB);

        entityManager.flush();
        entityManager.clear();

        List<ProjectHotspotFileEntity> alphaHotspots = hotspotRepository.findByProjectId(projectA.getId());
        List<ProjectHotspotFileEntity> betaHotspots = hotspotRepository.findByProjectId(projectB.getId());

        assertThat(alphaHotspots).hasSize(1);
        assertThat(alphaHotspots.get(0).getFilePath()).isEqualTo("src/Alpha.java");

        assertThat(betaHotspots).hasSize(1);
        assertThat(betaHotspots.get(0).getFilePath()).isEqualTo("src/Beta.java");
    }
}
