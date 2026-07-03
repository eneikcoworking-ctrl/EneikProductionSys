package com.eneik.production.services.projectfactory;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.services.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
    private final String organization;
    private final String apiBaseUrl;
    private final SystemSettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public GitHubProjectFactoryClient(@Value("${github.org:eneikcoworking-ctrl}") String organization,
                                      @Value("${github.api-base-url:https://api.github.com}") String apiBaseUrl,
                                      SystemSettingsService settingsService,
                                      ObjectMapper objectMapper) {
        this.organization = organization;
        this.apiBaseUrl = apiBaseUrl;
        this.settingsService = settingsService;
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

            HttpRequest request = baseRequest("/orgs/" + encode(organization) + "/repos", token)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201) {
                JsonNode json = objectMapper.readTree(response.body());
                String repoUrl = json.path("html_url").asText(fallbackUrl);
                String repoId = json.path("id").asText(null);
                List<String> uploadErrors = uploadBootstrapFiles(project, artifacts);
                String status = uploadErrors.isEmpty()
                        ? "created repository and bootstrap files"
                        : "created repository with bootstrap warnings: " + String.join("; ", uploadErrors);
                return new GitHubProvisioningResult(status, repoUrl, repoId);
            }

            if (response.statusCode() == 422) {
                return new GitHubProvisioningResult("exists or blocked by GitHub validation: " + preview(response.body()), fallbackUrl, null);
            }

            return new GitHubProvisioningResult("failed: GitHub returned HTTP " + response.statusCode() + " " + preview(response.body()), fallbackUrl, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new GitHubProvisioningResult("failed: GitHub provisioning interrupted", fallbackUrl, null);
        } catch (IOException | IllegalArgumentException e) {
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
