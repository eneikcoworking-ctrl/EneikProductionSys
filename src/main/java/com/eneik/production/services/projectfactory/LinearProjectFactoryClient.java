package com.eneik.production.services.projectfactory;

import com.eneik.production.models.persistence.ProjectEntity;
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

@Component
public class LinearProjectFactoryClient {
    private final boolean enabled;
    private final String apiKey;
    private final String teamId;
    private final String apiUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public LinearProjectFactoryClient(@Value("${linear.enabled:false}") boolean enabled,
                                      @Value("${linear.api-key:}") String apiKey,
                                      @Value("${linear.team-id:}") String teamId,
                                      @Value("${linear.api-url:https://api.linear.app/graphql}") String apiUrl,
                                      ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.apiKey = apiKey;
        this.teamId = teamId;
        this.apiUrl = apiUrl;
        this.objectMapper = objectMapper;
    }

    public LinearProvisioningResult provision(ProjectEntity project, String repositoryUrl) {
        if (!enabled) {
            return new LinearProvisioningResult("skipped: Linear provisioning disabled", null, null);
        }
        if (apiKey == null || apiKey.isBlank()) {
            return new LinearProvisioningResult("skipped: LINEAR_API_KEY is not configured", null, null);
        }
        if (teamId == null || teamId.isBlank()) {
            return new LinearProvisioningResult("skipped: LINEAR_TEAM_ID is not configured", null, null);
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

    private String preview(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String compact = body.replaceAll("\\s+", " ").trim();
        return compact.length() > 300 ? compact.substring(0, 300) : compact;
    }
}
