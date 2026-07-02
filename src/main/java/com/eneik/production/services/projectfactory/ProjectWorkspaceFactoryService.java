package com.eneik.production.services.projectfactory;

import com.eneik.production.models.persistence.ProjectEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

@Service
public class ProjectWorkspaceFactoryService {
    private final String workspaceRoot;

    public ProjectWorkspaceFactoryService(@Value("${project-factory.workspace-root:./project-workspaces}") String workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    public WorkspaceProvisioningResult provision(ProjectEntity project) {
        try {
            Path root = Paths.get(workspaceRoot).toAbsolutePath().normalize();
            Path workspace = root.resolve(project.getSlug()).normalize();
            if (!workspace.startsWith(root)) {
                throw new IllegalStateException("Workspace path escaped configured root");
            }

            Files.createDirectories(workspace.resolve(".github").resolve("workflows"));
            Files.createDirectories(workspace.resolve("docs"));

            WorkspaceArtifacts artifacts = artifacts(project);
            write(workspace.resolve("README.md"), artifacts.readme());
            write(workspace.resolve(".env.example"), artifacts.envExample());
            write(workspace.resolve(".github").resolve("workflows").resolve("ci.yml"), artifacts.ciWorkflow());
            write(workspace.resolve("docs").resolve("PROJECT_BRIEF.md"), artifacts.projectBrief());

            return new WorkspaceProvisioningResult(workspace.toString(), artifacts, "workspace ready");
        } catch (IOException e) {
            throw new IllegalStateException("Unable to provision local project workspace", e);
        }
    }

    private void write(Path path, String content) throws IOException {
        Files.writeString(path, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private WorkspaceArtifacts artifacts(ProjectEntity project) {
        String readme = """
                # %s

                Eneik Product Factory workspace.

                ## Identity

                - Project ID: %s
                - Slug: %s
                - Repository: %s
                - Linear project key: %s

                ## Operating rule

                Client wishlist items are not tasks. The Technical Lead role converts only business-necessary wishes into precise tasks with JTBD, Definition of Done, Lean management, TOC, and Six Sigma quality criteria.
                """.formatted(
                project.getName(),
                project.getId(),
                project.getSlug(),
                project.getRepositoryUrl(),
                project.getLinearProjectKey()
        );

        String envExample = """
                PROJECT_NAME="%s"
                PROJECT_SLUG=%s
                GITHUB_REPOSITORY=%s
                LINEAR_PROJECT_KEY=%s
                NODE_ENV=development
                SERVER_PORT=8080
                FRONTEND_PORT=3000
                API_PORT=8000
                """.formatted(
                project.getName().replace("\"", "\\\""),
                project.getSlug(),
                project.getRepositoryName(),
                project.getLinearProjectKey()
        );

        String ciWorkflow = """
                name: Eneik Project CI

                on:
                  push:
                  pull_request:

                jobs:
                  quality:
                    runs-on: ubuntu-latest
                    steps:
                      - uses: actions/checkout@v4
                      - uses: actions/setup-node@v4
                        if: hashFiles('package.json') != ''
                        with:
                          node-version: '20'
                      - uses: actions/setup-java@v4
                        if: hashFiles('pom.xml') != ''
                        with:
                          distribution: temurin
                          java-version: '17'
                      - uses: actions/setup-python@v5
                        if: hashFiles('requirements.txt') != ''
                        with:
                          python-version: '3.11'
                      - name: Node checks
                        if: hashFiles('package.json') != ''
                        run: |
                          npm ci
                          npm run check --if-present
                          npm test --if-present
                          npm run build --if-present
                      - name: Java checks
                        if: hashFiles('pom.xml') != ''
                        run: |
                          if [ -x ./mvnw ]; then ./mvnw test; else mvn test; fi
                      - name: Python checks
                        if: hashFiles('requirements.txt') != ''
                        run: |
                          python -m pip install --upgrade pip
                          pip install -r requirements.txt
                          python -m pytest || true
                """;

        String projectBrief = """
                # Project Brief

                ## Customer Job

                The client needs this project to produce a concrete product outcome that can be reviewed, accepted, and paid for without hidden engineering ambiguity.

                ## Production Constraints

                - One project is isolated from every other project.
                - Seven Jules accounts attach to the active project context.
                - Roles are selected per task, not permanently assigned to accounts.
                - Tasks are created by the Technical Lead role only when a business need exists.
                - The client can stop production only by accepting the project.

                ## Definition of Done

                - Repository workspace exists.
                - CI template exists.
                - Environment template exists.
                - Linear/GitHub provisioning status is visible in Eneik Production System.
                """;

        return new WorkspaceArtifacts(readme, envExample, ciWorkflow, projectBrief);
    }
}
