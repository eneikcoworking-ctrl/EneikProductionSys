package com.eneik.production.services;

import com.eneik.production.dto.OrchestrationResultDto;
import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.*;
import org.junit.jupiter.api.Test;
import org.awaitility.Awaitility;
import java.util.concurrent.TimeUnit;
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
    private ContinuousOrchestrationService continuousOrchestrationService;
    @org.springframework.boot.test.mock.mockito.MockBean
    private MLPredictionServiceClient mlPredictionServiceClient;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private com.eneik.production.repositories.WishlistRepository wishlistRepository;

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
        com.eneik.production.models.persistence.WishlistEntity item = new com.eneik.production.models.persistence.WishlistEntity();
        item.setProjectId(project.getId());
        item.setContent("Make the website beautiful and add backend API for leads");
        item.setSourceRoleTag("BARCAN-TAG-00");
        item.setSource(com.eneik.production.models.persistence.WishlistSource.client);
        item.setStatus(com.eneik.production.models.persistence.WishlistStatus.pending);
        item = wishlistRepository.save(item);
        // 3. Create idle account
        AccountEntity account = new AccountEntity();
        account.setName("Agent Smith");
        account.setCapabilities("*");
        account.setStatus(AccountStatus.idle);
        account.setEnabled(true);
        account = accountRepository.save(account);

        // 4. Orchestrate

                java.util.Map<String, Object> aiResponse = new java.util.HashMap<>();
        aiResponse.put("jtbd", "Automated UI Verification");
        aiResponse.put("acceptanceCriteria", "Visuals match reference");
        org.mockito.Mockito.when(mlPredictionServiceClient.generateTaskMetadata(org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(aiResponse);
        project.setFactoryStatus("ready_local");
        projectRepository.save(project);

        // 1. First run creates the task
        continuousOrchestrationService.continuousOrchestrate();

        Awaitility.await().atMost(5, TimeUnit.SECONDS).until(() -> !taskRepository.findAll().isEmpty());

        // 2. Second run dispatches the task
        continuousOrchestrationService.continuousOrchestrate();

        Awaitility.await()
            .atMost(5, TimeUnit.SECONDS)
            .pollInterval(100, TimeUnit.MILLISECONDS)
            .until(() -> taskRepository.findAll().get(0).getStatus() == com.eneik.production.models.persistence.TaskStatus.claimed);

        // 3. Assertions
        java.util.List<TaskEntity> tasks = taskRepository.findAll();
        assertThat(tasks).isNotEmpty();
        UUID taskId = tasks.get(0).getId();


        TaskEntity task = taskRepository.findById(taskId).orElseThrow();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.claimed);
        final java.util.UUID finalAccId = account.getId();
        org.awaitility.Awaitility.await().atMost(2, java.util.concurrent.TimeUnit.SECONDS).until(() -> accountRepository.findById(finalAccId).get().getStatus() == AccountStatus.busy);
        assertThat(accountRepository.findById(account.getId()).orElseThrow().getStatus()).isEqualTo(AccountStatus.busy);

        // 6. Complete implementer claim (Simulation of PR Open or manual completion)
        claimService.complete(taskId);


        task = taskRepository.findById(taskId).orElseThrow();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.review);


        // 7. Dispatch Reviewer
        AccountEntity reviewerAccount = new AccountEntity();
        reviewerAccount.setName("Reviewer Agent");
        reviewerAccount.setCapabilities("*");
        reviewerAccount.setStatus(AccountStatus.idle);
        reviewerAccount.setEnabled(true);
        reviewerAccount = accountRepository.save(reviewerAccount);
        final java.util.UUID revId = reviewerAccount.getId();

        claimService.claimReviewer(taskId, revId);

        assertThat(accountRepository.findById(revId).orElseThrow().getStatus()).isEqualTo(AccountStatus.busy);

        // 8. Complete reviewer claim
        claimService.complete(taskId);



        task = taskRepository.findById(taskId).orElseThrow();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.done);

        // Some asynchronous operations might take time to release the claim. Just wait a tiny bit or force it if it's meant to be idle.
        // Actually, if it's 'busy' in the DB, Awaitility will help.
        Awaitility.await().atMost(2, TimeUnit.SECONDS).until(() -> accountRepository.findById(revId).get().getStatus() == AccountStatus.idle);
    }
}
