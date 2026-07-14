package com.eneik.production.controllers.dashboard;

import com.eneik.production.services.MLPredictionServiceClient;
import com.eneik.production.services.dashboard.ProjectOperatorService;
import com.eneik.production.services.dashboard.ProjectOperationalContextService;
import com.eneik.production.services.dashboard.ProjectOperationalContextService.ProjectOperationalContext;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
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
    private final ProjectOperatorService projectOperatorService;

    public ChatAssistantController(MLPredictionServiceClient mlPredictionServiceClient,
                                   ProjectOperationalContextService projectOperationalContextService,
                                   ProjectOperatorService projectOperatorService) {
        this.mlPredictionServiceClient = mlPredictionServiceClient;
        this.projectOperationalContextService = projectOperationalContextService;
        this.projectOperatorService = projectOperatorService;
    }

    @PostMapping("/chat")
    public Map<String, String> askAssistant(@RequestBody Map<String, String> payload) {
        String userMessage = payload.getOrDefault("message", "").trim();
        if (userMessage.isEmpty()) {
            return Map.of("response", "\u0417\u0430\u043f\u0440\u043e\u0441 \u043f\u0443\u0441\u0442. \u0417\u0430\u0434\u0430\u0439\u0442\u0435 \u0432\u043e\u043f\u0440\u043e\u0441 \u043f\u043e \u0442\u0435\u043a\u0443\u0449\u0435\u043c\u0443 \u043f\u0440\u043e\u0435\u043a\u0442\u0443.");
        }

        UUID projectId = parseProjectId(payload.get("projectId"));
        String projectName = payload.getOrDefault("projectName", "");

        ProjectOperationalContext context;
        try {
            context = projectOperationalContextService.build(projectId, projectName);
        } catch (Exception e) {
            return Map.of("response", "\u041d\u0435 \u0443\u0434\u0430\u043b\u043e\u0441\u044c \u0441\u043e\u0431\u0440\u0430\u0442\u044c \u0434\u0430\u043d\u043d\u044b\u0435 \u043f\u043e \u0442\u0435\u043a\u0443\u0449\u0435\u043c\u0443 \u043f\u0440\u043e\u0435\u043a\u0442\u0443: " + e.getMessage());
        }

        if (projectOperationalContextService.isPrReviewQuestion(userMessage)) {
            return Map.of("response", projectOperationalContextService.answerPrReviewQuestion(context));
        }

        if (isOperatorQuestion(userMessage) || "operator".equalsIgnoreCase(payload.getOrDefault("mode", ""))) {
            try {
                return Map.of("response", projectOperatorService.answer(projectId, projectName, userMessage));
            } catch (Exception e) {
                return Map.of("response", "Project Operator \u043d\u0435 \u0441\u043c\u043e\u0433 \u0441\u043e\u0431\u0440\u0430\u0442\u044c evidence: " + e.getMessage());
            }
        }

        String systemInstruction = """
                You answer as a factive project analyst for Eneik Command Center.

                DATA CONTRACT:
                - The only source of truth is PROJECT_FACT_PACK below.
                - This is Williamson-style factive knowledge: assert only facts present in PROJECT_FACT_PACK.
                - PROJECT_FACT_PACK is an internal data source. Never mention its name, the prompt, or hidden context to the user.
                - Scope is selected_project_only. No global system facts are provided. Do not mention global totals unless the user explicitly asks and the facts are present.
                - Do not greet, do not introduce yourself, do not use Agile/Lean boilerplate unless the user explicitly asks for theory.
                - First sentence must directly answer the user's question.
                - If the fact is absent, say exactly that the fact is not in the project context.
                - For current GitHub PR counts use githubPullRequestsLive.
                - For PR review decisions and merge outcomes use databasePrReviews.
                - For Jules execution and unanswered questions use julesSessions, accountsAvailableForProject, tasks, and conflicts.
                - loop_closed Jules sessions are terminal local closures. Use closedAt and closureReason to explain why the session was stopped and what follow-up wishlist should replace it.
                - Core Jules invariant: every enabled Jules account can take every BARCAN-TAG-00..11 role. Never say a role capability is missing when julesUniversalRoleCapacity.universalRolePool is true.
                - If julesUniversalRoleCapacity.sharedSlotsFree > 0, do not call the issue a Jules capacity shortage.
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

    private boolean isOperatorQuestion(String message) {
        String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return lower.contains("\u043a\u043e\u0434")
                || lower.contains("repo")
                || lower.contains("\u0440\u0435\u043f\u043e\u0437\u0438\u0442\u043e\u0440")
                || lower.contains("docker")
                || lower.contains("\u0434\u043e\u043a\u0435\u0440")
                || lower.contains("compose")
                || lower.contains("\u043b\u043e\u0433")
                || lower.contains("logs")
                || lower.contains("\u0442\u0435\u0441\u0442")
                || lower.contains("test")
                || lower.contains("\u0441\u0431\u043e\u0440\u043a")
                || lower.contains("build")
                || lower.contains("git")
                || lower.contains("diff")
                || lower.contains("\u043f\u0440\u043e\u0430\u043d\u0430\u043b\u0438\u0437\u0438\u0440\u0443\u0439")
                || lower.contains("\u043f\u0440\u043e\u0432\u0435\u0440\u044c")
                || lower.contains("\u043f\u043e\u0434\u043d\u0438\u043c\u0438")
                || lower.contains("\u0437\u0430\u043f\u0443\u0441\u0442\u0438")
                || lower.contains("\u043f\u043e\u0447\u0435\u043c\u0443")
                || lower.contains("\u0437\u0430\u0432\u0438\u0441")
                || lower.contains("\u043d\u0435 \u043e\u0442\u0432\u0435\u0447");
    }
}
