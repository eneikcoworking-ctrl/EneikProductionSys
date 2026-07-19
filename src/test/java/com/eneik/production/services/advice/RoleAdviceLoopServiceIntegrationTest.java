package com.eneik.production.services.advice;

import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.RoleRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class RoleAdviceLoopServiceIntegrationTest {

    @Autowired
    private RoleAdviceLoopService adviceService;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private WishlistRepository wishlistRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    public void testAfterTaskCompleteSkipsWishlistWhenAdviceServiceUnavailable() {
        // In the test profile the ML service is unreachable, so this exercises the honest-fallback path:
        // no fabricated advice should be created (the old fallback echoed the entire task description -
        // which always carries the full original client brief - producing an oversized wishlist item that
        // triggered a full re-decomposition of the brief; skipping is correct, not a regression).
        ProjectEntity project = new ProjectEntity();
        project.setName("Test Project");
        project.setSlug("test-project-advice");
        project.setRepositoryName("test-repo-advice");
        project = projectRepository.save(project);

        RoleEntity role = roleRepository.findById("BARCAN-TAG-02").orElseThrow();

        TaskEntity task = new TaskEntity();
        task.setProject(project);
        task.setRole(role);
        task.setDescription("Completed task");
        task.setStatus(TaskStatus.done);
        task = taskRepository.save(task);

        adviceService.afterTaskComplete(task.getId());

        var wishlists = wishlistRepository.findAll();
        boolean found = wishlists.stream().anyMatch(w ->
            w.getSource() == WishlistSource.role &&
            "BARCAN-TAG-02".equals(w.getSourceRoleTag())
        );

        assertFalse(found, "No wishlist should be created when advice generation is unavailable - fabricating one from the raw task description was the bug being fixed");
    }
}
