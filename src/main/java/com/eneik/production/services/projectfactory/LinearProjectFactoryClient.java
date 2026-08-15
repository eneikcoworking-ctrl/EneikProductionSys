package com.eneik.production.services.projectfactory;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.services.settings.SystemSettingsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class LinearProjectFactoryClient {
    private final String apiUrl;
    private final SystemSettingsService settingsService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    public LinearProjectFactoryClient(@Value("${linear.api-url:https://api.linear.app/graphql}") String apiUrl,
                                      SystemSettingsService settingsService,
                                      ObjectMapper objectMapper) {
        this.apiUrl = apiUrl;
        this.settingsService = settingsService;
        this.objectMapper = objectMapper;
    }

    public LinearProvisioningResult provision(ProjectEntity project, String repositoryUrl) {
        String apiKey = settingsService.effectiveValue("linear_api_key");
        String teamId = settingsService.effectiveValue("linear_team_id");
        if (!settingsService.effectiveBoolean("linear_enabled")) {
            return new LinearProvisioningResult("skipped: Linear provisioning disabled", null, null);
        }
        if (apiKey == null || apiKey.isBlank()) {
            return new LinearProvisioningResult("skipped: LINEAR_API_KEY is not configured", null, null);
        }
        if (teamId == null || teamId.isBlank()) {
            return new LinearProvisioningResult("skipped: LINEAR_TEAM_ID is not configured", null, null);
        }
        // Closes F8. Every projectCreate on test-forty-fifth and test-forty-sixth failed with Linear's
        // generic "Argument Validation Error", which said nothing about why. The live setting was
        // linear_team_id = "Eneik Production System" - a team NAME where ProjectCreateInput.teamIds requires
        // a UUID. A configuration mistake was arriving as an opaque API error, so the same failure repeated
        // on every project while looking like a transient integration problem.
        //
        // Checked here rather than left to Linear, because the shape is knowable locally and a remote
        // rejection cannot name the local cause. Deliberately no name-to-id lookup: silently accepting a
        // name would make the setting mean two things, and a setting that accepts what it should reject is
        // how the mistake survives into the next system that reads it.
        if (!LINEAR_ID.matcher(teamId.trim()).matches()) {
            return new LinearProvisioningResult(
                    "skipped: linear_team_id is '" + teamId + "', which is not a Linear team id. Linear "
                            + "expects a UUID here (ProjectCreateInput.teamIds), not the team's name or key "
                            + "- open the team in Linear and take the id from its settings URL.",
                    null, null);
        }

        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("query", """
                    mutation ProjectCreate($input: ProjectCreateInput!) {
                      projectCreate(input: $input) {
                        success
                        project {
                          id
                          name
                          url
                        }
                      }
                    }
                    """);

            ObjectNode variables = objectMapper.createObjectNode();
            ObjectNode input = objectMapper.createObjectNode();
            input.put("name", project.getName());
            input.put("description", "Eneik Product Factory project. Repository: " + repositoryUrl);
            ArrayNode teamIds = objectMapper.createArrayNode();
            teamIds.add(teamId);
            input.set("teamIds", teamIds);
            variables.set("input", input);
            root.set("variables", variables);

            HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
                    .header("Authorization", apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(root.toString()))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return new LinearProvisioningResult("failed: Linear returned HTTP " + response.statusCode() + " " + preview(response.body()), null, null);
            }

            JsonNode json = objectMapper.readTree(response.body());
            if (json.hasNonNull("errors")) {
                return new LinearProvisioningResult("failed: Linear GraphQL errors " + preview(json.path("errors").toString()), null, null);
            }

            JsonNode projectNode = json.path("data").path("projectCreate").path("project");
            String id = projectNode.path("id").asText(null);
            String url = projectNode.path("url").asText(null);
            if (id == null || id.isBlank()) {
                return new LinearProvisioningResult("failed: Linear response did not include project id", null, null);
            }
            return new LinearProvisioningResult("created Linear project", id, url);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new LinearProvisioningResult("failed: Linear provisioning interrupted", null, null);
        } catch (IOException | IllegalArgumentException e) {
            return new LinearProvisioningResult("failed: " + e.getMessage(), null, null);
        }
    }

    /** Linear identifies teams and projects by UUID; anything else is a name or a key, and cannot work. */
    private static final java.util.regex.Pattern LINEAR_ID = java.util.regex.Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private String preview(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String compact = body.replaceAll("\\s+", " ").trim();
        return compact.length() > 300 ? compact.substring(0, 300) : compact;
    }
}
