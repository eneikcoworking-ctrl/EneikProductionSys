package com.eneik.production.services.projectfactory;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.services.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Arrays;

@Component
public class GitHubProjectFactoryClient {
    private static final Logger log = LoggerFactory.getLogger(GitHubProjectFactoryClient.class);

    private final String organization;
    private final String apiBaseUrl;
    private final String webhookUrl;
    private final SystemSettingsService settingsService;
    private final AccountRepository accountRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public GitHubProjectFactoryClient(@Value("${github.org}") String organization,
                                      @Value("${github.api-base-url:https://api.github.com}") String apiBaseUrl,
                                      @Value("${github.webhook-url:}") String webhookUrl,
                                      SystemSettingsService settingsService,
                                      AccountRepository accountRepository,
                                      ObjectMapper objectMapper) {
        this.organization = organization;
        this.apiBaseUrl = apiBaseUrl;
        this.webhookUrl = webhookUrl;
        this.settingsService = settingsService;
        this.accountRepository = accountRepository;
        this.objectMapper = objectMapper;
    }

    public GitHubProvisioningResult provision(ProjectEntity project, WorkspaceArtifacts artifacts) {
        String fallbackUrl = "https://github.com/" + organization + "/" + project.getRepositoryName();
        String token = settingsService.effectiveValue("github_token");
        if (!settingsService.effectiveBoolean("github_enabled")) {
            return new GitHubProvisioningResult("skipped: GitHub provisioning disabled", fallbackUrl, null);
        }
        if (token == null || token.isBlank()) {
            return new GitHubProvisioningResult("skipped: GITHUB_TOKEN is not configured", fallbackUrl, null);
        }

        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("name", project.getRepositoryName());
            body.put("description", "Eneik Product Factory workspace for " + project.getName());
            body.put("private", true);
            body.put("auto_init", true);

            HttpResponse<String> response = createRepository(token, body);

            if (response.statusCode() == 201) {
                JsonNode json = objectMapper.readTree(response.body());
                String repoUrl = json.path("html_url").asText(fallbackUrl);
                String repoId = json.path("id").asText(null);
                List<String> uploadErrors = uploadBootstrapFiles(project, artifacts);
                List<String> configurationWarnings = configureRepository(project.getRepositoryName(), token);
                List<CollaboratorProvisioningResult> collaborators = inviteJulesCollaborators(project.getRepositoryName(), token);
                List<String> warnings = new ArrayList<>();
                warnings.addAll(uploadErrors);
                warnings.addAll(configurationWarnings);
                collaborators.stream()
                        .filter(result -> !"invitation_sent".equals(result.status()) && !"already_has_access".equals(result.status()))
                        .map(result -> "collaborator " + result.username() + " " + result.status() + " " + result.detail())
                        .forEach(warnings::add);
                String status = warnings.isEmpty()
                        ? "created repository and configured protections"
                        : "created repository with configuration warnings";
                return new GitHubProvisioningResult(status, repoUrl, repoId, warnings, collaborators);
            }

            if (response.statusCode() == 422) {
                return new GitHubProvisioningResult("exists or blocked by GitHub validation: " + preview(response.body()), fallbackUrl, null);
            }

            return new GitHubProvisioningResult("failed: GitHub returned HTTP " + response.statusCode() + " " + preview(response.body()), fallbackUrl, null);
        } catch (InterruptedException e) {
            log.error("SYSTEM CRITICAL: Failed to send GitHub invitations for project: {}", project.getName(), e);
            Thread.currentThread().interrupt();
            return new GitHubProvisioningResult("failed: GitHub provisioning interrupted", fallbackUrl, null);
        } catch (IOException | IllegalArgumentException e) {
            log.error("SYSTEM CRITICAL: Failed to send GitHub invitations for project: {}", project.getName(), e);
            return new GitHubProvisioningResult("failed: " + e.getMessage(), fallbackUrl, null);
        }
    }

    private List<String> uploadBootstrapFiles(ProjectEntity project, WorkspaceArtifacts artifacts) {
        String token = settingsService.effectiveValue("github_token");
        List<String> errors = new ArrayList<>();
        upsertContent(project.getRepositoryName(), "README.md", artifacts.readme(), token, errors);
        upsertContent(project.getRepositoryName(), ".env.example", artifacts.envExample(), token, errors);
        upsertContent(project.getRepositoryName(), ".github/workflows/ci.yml", artifacts.ciWorkflow(), token, errors);
        upsertContent(project.getRepositoryName(), "docs/PROJECT_BRIEF.md", artifacts.projectBrief(), token, errors);
        return errors;
    }

    private List<String> configureRepository(String repositoryName, String token) {
        List<String> warnings = new ArrayList<>();
        protectMainBranch(repositoryName, token, warnings);
        registerWebhook(repositoryName, token, warnings);
        dispatchCiWorkflow(repositoryName, token, warnings);
        return warnings;
    }

    private List<CollaboratorProvisioningResult> inviteJulesCollaborators(String repositoryName, String token) {
        return accountRepository.findByEnabledTrueAndProjectIsNullAndGithubUsernameIsNotNullOrderByNameAsc().stream()
                .filter(account -> account.getGithubUsername() != null && !account.getGithubUsername().isBlank())
                .map(account -> inviteCollaborator(repositoryName, account.getGithubUsername().trim(), token))
                .toList();
    }

    public CollaboratorProvisioningResult inviteCollaborator(String repositoryName, String username, String token) {
        if (username.equalsIgnoreCase(organization)) {
            return new CollaboratorProvisioningResult(username, "already_has_access", 0, "Repository owner already has access and cannot be invited as collaborator");
        }
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("permission", "push");

            HttpRequest request = baseRequest("/repos/" + encode(organization) + "/" + encode(repositoryName) + "/collaborators/" + encode(username), token)
                    .PUT(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return switch (response.statusCode()) {
                case 201 -> new CollaboratorProvisioningResult(username, "invitation_sent", 201, "GitHub invitation email should be sent");
                case 204 -> new CollaboratorProvisioningResult(username, "already_has_access", 204, "User already has repository access");
                case 404 -> new CollaboratorProvisioningResult(username, "not_found", 404, preview(response.body()));
                case 422 -> new CollaboratorProvisioningResult(username, "validation_failed_or_pending", 422, preview(response.body()));
                default -> new CollaboratorProvisioningResult(username, "failed", response.statusCode(), preview(response.body()));
            };
        } catch (InterruptedException e) {
            log.error("Failed to invite GitHub collaborator {} to repository {}", username, repositoryName, e);
            Thread.currentThread().interrupt();
            return new CollaboratorProvisioningResult(username, "interrupted", 0, "GitHub collaborator invitation interrupted");
        } catch (IOException | IllegalArgumentException e) {
            log.error("Failed to invite GitHub collaborator {} to repository {}", username, repositoryName, e);
            return new CollaboratorProvisioningResult(username, "failed", 0, e.getMessage());
        }
    }

    private void protectMainBranch(String repositoryName, String token, List<String> warnings) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            ObjectNode statusChecks = body.putObject("required_status_checks");
            statusChecks.put("strict", false);
            statusChecks.putArray("contexts");
            body.put("enforce_admins", false);
            ObjectNode reviews = body.putObject("required_pull_request_reviews");
            reviews.put("required_approving_review_count", 0);
            body.putNull("restrictions");
            body.put("required_linear_history", false);
            body.put("allow_force_pushes", false);
            body.put("allow_deletions", false);

            HttpRequest request = baseRequest("/repos/" + encode(organization) + "/" + encode(repositoryName) + "/branches/main/protection", token)
                    .PUT(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                warnings.add("branch protection HTTP " + response.statusCode() + " " + preview(response.body()));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            warnings.add("branch protection interrupted");
        } catch (IOException | IllegalArgumentException e) {
            warnings.add("branch protection " + e.getMessage());
        }
    }

    private void registerWebhook(String repositoryName, String token, List<String> warnings) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            warnings.add("webhook skipped: github.webhook-url is not configured for this local environment");
            return;
        }

        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("name", "web");
            body.put("active", true);
            body.putArray("events").add("push").add("pull_request");
            ObjectNode config = body.putObject("config");
            config.put("url", webhookUrl);
            config.put("content_type", "json");
            config.put("insecure_ssl", "0");

            HttpRequest request = baseRequest("/repos/" + encode(organization) + "/" + encode(repositoryName) + "/hooks", token)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 201 && response.statusCode() != 422) {
                warnings.add("webhook HTTP " + response.statusCode() + " " + preview(response.body()));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            warnings.add("webhook interrupted");
        } catch (IOException | IllegalArgumentException e) {
            warnings.add("webhook " + e.getMessage());
        }
    }

    private void dispatchCiWorkflow(String repositoryName, String token, List<String> warnings) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("ref", "main");

            HttpRequest request = baseRequest("/repos/" + encode(organization) + "/" + encode(repositoryName) + "/actions/workflows/ci.yml/dispatches", token)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 204) {
                warnings.add("ci dispatch HTTP " + response.statusCode() + " " + preview(response.body()));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            warnings.add("ci dispatch interrupted");
        } catch (IOException | IllegalArgumentException e) {
            warnings.add("ci dispatch " + e.getMessage());
        }
    }

    private HttpResponse<String> createRepository(String token, ObjectNode body) throws IOException, InterruptedException {
        HttpRequest organizationRequest = baseRequest("/orgs/" + encode(organization) + "/repos", token)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> response = httpClient.send(organizationRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 404) {
            return response;
        }

        HttpRequest userRequest = baseRequest("/user/repos", token)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        return httpClient.send(userRequest, HttpResponse.BodyHandlers.ofString());
    }

    private void upsertContent(String repositoryName, String path, String content, String token, List<String> errors) {
        try {
            String endpoint = "/repos/" + encode(organization) + "/" + encode(repositoryName) + "/contents/" + encodePath(path);
            String sha = existingSha(endpoint, token);

            ObjectNode body = objectMapper.createObjectNode();
            body.put("message", "Initialize Eneik project factory file " + path);
            body.put("content", Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)));
            if (sha != null && !sha.isBlank()) {
                body.put("sha", sha);
            }

            HttpRequest request = baseRequest(endpoint, token)
                    .PUT(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 && response.statusCode() != 201) {
                errors.add(path + " HTTP " + response.statusCode() + " " + preview(response.body()));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            errors.add(path + " interrupted");
        } catch (IOException | IllegalArgumentException e) {
            errors.add(path + " " + e.getMessage());
        }
    }

    private String existingSha(String endpoint, String token) throws IOException, InterruptedException {
        HttpRequest request = baseRequest(endpoint, token).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            return objectMapper.readTree(response.body()).path("sha").asText(null);
        }
        return null;
    }

    private HttpRequest.Builder baseRequest(String path, String token) {
        return HttpRequest.newBuilder(URI.create(trimBaseUrl() + path))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("Content-Type", "application/json")
                .header("X-GitHub-Api-Version", "2022-11-28");
    }

    private String trimBaseUrl() {
        return apiBaseUrl.replaceAll("/+$", "");
    }

    private String encodePath(String path) {
        return Arrays.stream(path.split("/"))
                .map(this::encode)
                .collect(Collectors.joining("/"));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String preview(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String compact = body.replaceAll("\\s+", " ").trim();
        return compact.length() > 300 ? compact.substring(0, 300) : compact;
    }
}
