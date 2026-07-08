package com.eneik.production.services;

import com.eneik.production.dto.OrchestrationResultDto;
import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
public class OrchestrationStatusTest {

    @Autowired
    private ProjectFlowService projectFlowService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private WishlistItemRepository wishlistItemRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ClaimService claimService;

    @Autowired
    private JulesSessionRepository julesSessionRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    public void testFullTaskLifecycleWithReview() {
        // 1. Create project
        ProjectEntity project = new ProjectEntity();
        project.setName("Test Project");
        project.setSlug("test-project-bug");
        project.setRepositoryName("test-project-bug");
        project.setStatus(ProjectStatus.active);
        project = projectRepository.save(project);

        // 2. Create wishlist item
        WishlistItemEntity item = new WishlistItemEntity();
        item.setProject(project);
        item.setText("Need a login page");
        item.setStatus(WishlistItemStatus.open);
        item = wishlistItemRepository.save(item);

        // 3. Create idle account
        AccountEntity account = new AccountEntity();
        account.setName("Agent Smith");
        account.setCapabilities("*");
        account.setStatus(AccountStatus.idle);
        account.setEnabled(true);
        account = accountRepository.save(account);

        // 4. Orchestrate
        OrchestrationResultDto result = projectFlowService.orchestrate(project.getId());

        // 5. Verify implementer claim
        assertThat(result.createdTasks()).isNotEmpty();
        UUID taskId = result.createdTasks().get(0).id();

        TaskEntity task = taskRepository.findById(taskId).orElseThrow();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.claimed);
        assertThat(accountRepository.findById(account.getId()).orElseThrow().getStatus()).isEqualTo(AccountStatus.busy);

        // 6. Complete implementer claim (Simulation of PR Open or manual completion)
        claimService.complete(taskId);

        entityManager.clear();
        task = taskRepository.findById(taskId).orElseThrow();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.review);
        assertThat(accountRepository.findById(account.getId()).orElseThrow().getStatus()).isEqualTo(AccountStatus.idle);

        // 7. Dispatch Reviewer
        AccountEntity reviewerAccount = new AccountEntity();
        reviewerAccount.setName("Reviewer Agent");
        reviewerAccount.setCapabilities("*");
        reviewerAccount.setStatus(AccountStatus.idle);
        reviewerAccount.setEnabled(true);
        reviewerAccount = accountRepository.save(reviewerAccount);

        claimService.claimReviewer(taskId, reviewerAccount.getId());

        assertThat(accountRepository.findById(reviewerAccount.getId()).orElseThrow().getStatus()).isEqualTo(AccountStatus.busy);

        // 8. Complete reviewer claim
        claimService.complete(taskId);

        entityManager.clear();
        task = taskRepository.findById(taskId).orElseThrow();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.done);
        assertThat(accountRepository.findById(reviewerAccount.getId()).orElseThrow().getStatus()).isEqualTo(AccountStatus.idle);
    }
}
