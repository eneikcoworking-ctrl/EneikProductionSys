package com.eneik.production.services.jules;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.TaskEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JulesDispatchService {
    private final JulesClient julesClient;
    private final String sourcePrefix;
    private final String startingBranch;

    public JulesDispatchService(JulesClient julesClient,
                                @Value("${jules.source-prefix:sources/github/eneikcoworking-ctrl/}") String sourcePrefix,
                                @Value("${jules.starting-branch:main}") String startingBranch) {
        this.julesClient = julesClient;
        this.sourcePrefix = sourcePrefix;
        this.startingBranch = startingBranch;
    }

    public JulesDispatchResult dispatch(TaskEntity task) {
        ProjectEntity project = task.getProject();
        if (project == null) {
            return new JulesDispatchResult(false, null, "Task has no project");
        }

        String sourceName = sourcePrefix + project.getRepositoryName();
        String title = task.getRole().getTag() + " " + project.getName();
        return julesClient.createSession(sourceName, startingBranch, title, buildPrompt(project, task));
    }

    private String buildPrompt(ProjectEntity project, TaskEntity task) {
        return """
                You are Jules, acting as execution power for Eneik Production System.

                Project:
                - Name: %s
                - Repository: %s
                - Linear project key: %s

                Selected role paint:
                - %s

                Technical Lead task:
                %s

                Mandatory execution contract:
                1. Treat the task payload as the source of truth.
                2. Preserve business intent, JTBD, Definition of Done, Lean value, TOC constraint, Six Sigma quality target, and acceptance metrics.
                3. Make the smallest coherent code change that satisfies the DoD.
                4. Run relevant tests or add focused tests when coverage is missing.
                5. Open a pull request when complete.
                6. Do not broaden scope beyond the task unless the DoD cannot be met without it.
                """.formatted(
                project.getName(),
                project.getRepositoryName(),
                project.getLinearProjectKey(),
                task.getRole().getTag(),
                task.getDescription()
        );
    }
}
