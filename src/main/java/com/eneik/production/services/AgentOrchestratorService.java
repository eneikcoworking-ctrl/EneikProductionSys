package com.eneik.production.services;

import com.eneik.production.dto.agents.AgentAccountDTO;
import com.eneik.production.dto.agents.AgentOrchestratorSnapshotDTO;
import com.eneik.production.dto.agents.AgentTaskDTO;
import com.eneik.production.models.persistence.AgentAccountEntity;
import com.eneik.production.models.persistence.AgentTaskEntity;
import com.eneik.production.models.persistence.AgentTaskStatus;
import com.eneik.production.repositories.AgentAccountRepository;
import com.eneik.production.repositories.AgentTaskRepository;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AgentOrchestratorService {
    private static final int ACCOUNT_WIP_LIMIT = 2;
    private static final List<AgentTaskStatus> ACTIVE_STATUSES = List.of(
            AgentTaskStatus.CLAIMED,
            AgentTaskStatus.IN_PROGRESS,
            AgentTaskStatus.REVIEW
    );

    private final AgentAccountRepository accountRepository;
    private final AgentTaskRepository taskRepository;

    public AgentOrchestratorService(AgentAccountRepository accountRepository,
                                    AgentTaskRepository taskRepository) {
        this.accountRepository = accountRepository;
        this.taskRepository = taskRepository;
    }

    @PostConstruct
    @Transactional
    public void seedDefaultAccounts() {
        if (accountRepository.count() > 0) {
            return;
        }

        accountRepository.save(new AgentAccountEntity("ACC-01", "Tech Lead and Mediator", "BARCAN-TAG-00,BARCAN-TAG-09"));
        accountRepository.save(new AgentAccountEntity("ACC-02", "Architecture and Backend", "BARCAN-TAG-01,BARCAN-TAG-02"));
        accountRepository.save(new AgentAccountEntity("ACC-03", "Data and ML", "BARCAN-TAG-04,BARCAN-TAG-08"));
        accountRepository.save(new AgentAccountEntity("ACC-04", "Experience and Frontend", "BARCAN-TAG-03,BARCAN-TAG-11"));
        accountRepository.save(new AgentAccountEntity("ACC-05", "Reliability and QA", "BARCAN-TAG-05,BARCAN-TAG-06"));
        accountRepository.save(new AgentAccountEntity("ACC-06", "Security and Compliance", "BARCAN-TAG-07,BARCAN-TAG-10"));
        accountRepository.save(new AgentAccountEntity("ACC-07", "Operations Reserve", "BARCAN-TAG-02,BARCAN-TAG-05,BARCAN-TAG-08,BARCAN-TAG-11"));
    }

    @Transactional
    public AgentOrchestratorSnapshotDTO createRequirement(String title, String description) {
        String normalizedTitle = normalizeTitle(title);
        String normalizedDescription = description == null ? "" : description.trim();
        UUID requirementId = UUID.randomUUID();

        for (TaskSpec taskSpec : decomposeRequirement(normalizedTitle, normalizedDescription)) {
            taskRepository.save(new AgentTaskEntity(
                    requirementId,
                    normalizedTitle,
                    taskSpec.description(),
                    taskSpec.agentTag()
            ));
        }

        autoClaimTodoTasks();
        return snapshot();
    }

    @Transactional
    public AgentOrchestratorSnapshotDTO claimNextForAccount(String accountCode) {
        AgentAccountEntity account = accountRepository.findByAccountCode(accountCode)
                .orElseThrow(() -> new IllegalArgumentException("Unknown account: " + accountCode));

        if (activeTaskCount(account) >= ACCOUNT_WIP_LIMIT) {
            return snapshot();
        }

        taskRepository.findByStatusOrderByCreatedAtAsc(AgentTaskStatus.TODO).stream()
                .filter(task -> account.canHandle(task.getAgentTag()))
                .findFirst()
                .ifPresent(task -> {
                    task.claim(account);
                    accountRepository.save(account);
                    taskRepository.save(task);
                });

        return snapshot();
    }

    @Transactional
    public AgentOrchestratorSnapshotDTO autoClaimTodoTasks() {
        List<AgentTaskEntity> todoTasks = taskRepository.findByStatusOrderByCreatedAtAsc(AgentTaskStatus.TODO);
        for (AgentTaskEntity task : todoTasks) {
            findBestAvailableAccount(task.getAgentTag()).ifPresent(account -> {
                task.claim(account);
                accountRepository.save(account);
                taskRepository.save(task);
            });
        }
        return snapshot();
    }

    @Transactional
    public AgentOrchestratorSnapshotDTO updateTaskStatus(UUID taskId, AgentTaskStatus status) {
        AgentTaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown task: " + taskId));
        task.updateStatus(status);
        taskRepository.save(task);
        if (task.getClaimedBy() != null) {
            refreshAccountStatus(task.getClaimedBy());
            accountRepository.save(task.getClaimedBy());
        }
        autoClaimTodoTasks();
        return snapshot();
    }

    @Transactional
    public AgentOrchestratorSnapshotDTO snapshot() {
        List<AgentAccountDTO> accounts = accountRepository.findAllByOrderByAccountCodeAsc().stream()
                .map(AgentAccountDTO::new)
                .toList();
        List<AgentTaskDTO> tasks = taskRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(AgentTaskDTO::new)
                .toList();

        Map<String, Long> summary = new LinkedHashMap<>();
        summary.put("accounts", (long) accounts.size());
        for (AgentTaskStatus status : AgentTaskStatus.values()) {
            summary.put(status.name().toLowerCase(), taskRepository.countByStatus(status));
        }

        return new AgentOrchestratorSnapshotDTO(accounts, tasks, summary);
    }

    private String normalizeTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            return "Untitled requirement";
        }
        return title.trim();
    }

    private List<TaskSpec> decomposeRequirement(String title, String description) {
        String text = (title + " " + description).toLowerCase();
        Map<String, String> tasks = new LinkedHashMap<>();

        tasks.put("BARCAN-TAG-09", "Clarify the requirement, acceptance criteria, and execution order.");
        tasks.put("BARCAN-TAG-00", "Review architecture, code quality, and final merge readiness.");
        tasks.put("BARCAN-TAG-01", "Validate domain boundaries and object model.");

        if (matches(text, "ui|frontend|screen|page|dashboard|interface|ux|design|component|svelte|view")) {
            tasks.put("BARCAN-TAG-03", "Design the user journey, states, and visual hierarchy.");
            tasks.put("BARCAN-TAG-11", "Implement frontend components and browser-facing behavior.");
        }
        if (matches(text, "api|backend|server|endpoint|integration|spring|controller|service")) {
            tasks.put("BARCAN-TAG-02", "Implement API endpoints, backend integration, and contracts.");
        }
        if (matches(text, "data|database|save|storage|schema|migration|repository|persistence|table")) {
            tasks.put("BARCAN-TAG-08", "Design persistence, schema, and data lineage.");
        }
        if (matches(text, "ml|model|predict|analytics|bottleneck|risk|metric|forecast")) {
            tasks.put("BARCAN-TAG-04", "Implement prediction, analytics, and model validation logic.");
        }
        if (matches(text, "docker|deploy|infra|infrastructure|ci|cd|pipeline|local|environment")) {
            tasks.put("BARCAN-TAG-05", "Prepare deployment, runtime, and environment automation.");
        }
        if (matches(text, "test|qa|quality|coverage|verify|validation")) {
            tasks.put("BARCAN-TAG-06", "Define and execute automated quality checks.");
        }
        if (matches(text, "auth|login|security|permission|role|account|token|jwt|secret")) {
            tasks.put("BARCAN-TAG-07", "Review authentication, permissions, and security boundaries.");
        }
        if (matches(text, "privacy|pii|compliance|policy|personal|gdpr")) {
            tasks.put("BARCAN-TAG-10", "Validate privacy, compliance, and prohibited data handling.");
        }

        List<TaskSpec> result = new ArrayList<>();
        tasks.forEach((tag, taskDescription) -> result.add(new TaskSpec(tag, taskDescription)));
        return result;
    }

    private boolean matches(String text, String expression) {
        return Pattern.compile(expression, Pattern.CASE_INSENSITIVE).matcher(text).find();
    }

    private java.util.Optional<AgentAccountEntity> findBestAvailableAccount(String agentTag) {
        return accountRepository.findAllByOrderByAccountCodeAsc().stream()
                .filter(account -> account.canHandle(agentTag))
                .filter(account -> activeTaskCount(account) < ACCOUNT_WIP_LIMIT)
                .min(Comparator.comparingLong(this::activeTaskCount));
    }

    private long activeTaskCount(AgentAccountEntity account) {
        return taskRepository.countByClaimedByAndStatusIn(account, ACTIVE_STATUSES);
    }

    private void refreshAccountStatus(AgentAccountEntity account) {
        account.setStatus(activeTaskCount(account) > 0 ? "ACTIVE" : "IDLE");
    }

    private record TaskSpec(String agentTag, String description) {
    }
}
