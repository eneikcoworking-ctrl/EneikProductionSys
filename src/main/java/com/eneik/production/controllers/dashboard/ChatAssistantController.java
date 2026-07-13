package com.eneik.production.controllers.dashboard;

import com.eneik.production.services.MLPredictionServiceClient;
import com.eneik.production.services.dashboard.ProjectOperationalContextService;
import com.eneik.production.services.dashboard.ProjectOperationalContextService.ProjectOperationalContext;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Provides an interactive AI assistant endpoint grounded in backend metrics.
 */
@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "http://localhost:3000")
public class ChatAssistantController {

    private final MLPredictionServiceClient mlPredictionServiceClient;
    private final ProjectOperationalContextService projectOperationalContextService;

    public ChatAssistantController(MLPredictionServiceClient mlPredictionServiceClient,
                                   ProjectOperationalContextService projectOperationalContextService) {
        this.mlPredictionServiceClient = mlPredictionServiceClient;
        this.projectOperationalContextService = projectOperationalContextService;
    }

    @PostMapping("/chat")
    public Map<String, String> askAssistant(@RequestBody Map<String, String> payload) {
        String userMessage = payload.getOrDefault("message", "").trim();
        if (userMessage.isEmpty()) {
            return Map.of("response", "Запрос пуст. Задайте вопрос по текущему проекту.");
        }

        UUID projectId = parseProjectId(payload.get("projectId"));
        String projectName = payload.getOrDefault("projectName", "");

        ProjectOperationalContext context;
        try {
            context = projectOperationalContextService.build(projectId, projectName);
        } catch (Exception e) {
            return Map.of("response", "Не удалось собрать данные по текущему проекту: " + e.getMessage());
        }

        if (projectOperationalContextService.isPrReviewQuestion(userMessage)) {
            return Map.of("response", projectOperationalContextService.answerPrReviewQuestion(context));
        }

        String systemInstruction = """
                You answer as a factive project analyst for Eneik Command Center.

                DATA CONTRACT:
                - The only source of truth is PROJECT_FACT_PACK below.
                - This is Williamson-style factive knowledge: assert only facts present in PROJECT_FACT_PACK.
                - Scope is selected_project_only. No global system facts are provided. Do not mention global totals unless the user explicitly asks and the facts are present.
                - Do not greet, do not introduce yourself, do not use Agile/Lean boilerplate unless the user explicitly asks for theory.
                - First sentence must directly answer the user's question.
                - If the fact is absent, say exactly that the fact is not in the project context.
                - For current GitHub PR counts use githubPullRequestsLive.
                - For PR review decisions and merge outcomes use databasePrReviews.
                - For Jules execution and unanswered questions use julesSessions, accountsAvailableForProject, tasks, and conflicts.
                - Never invent accounts, tags, PR counts, sessions, hidden workers, or failures.
                - Respond in Russian, concise but with exact numbers and IDs when relevant.

                PROJECT_FACT_PACK:
                """ + context.promptJson();

        String aiResponse = mlPredictionServiceClient.chat(userMessage, systemInstruction);
        return Map.of("response", aiResponse);
    }

    private UUID parseProjectId(String rawProjectId) {
        if (rawProjectId == null || rawProjectId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(rawProjectId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
