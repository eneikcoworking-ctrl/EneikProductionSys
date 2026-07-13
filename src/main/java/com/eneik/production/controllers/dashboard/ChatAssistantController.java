package com.eneik.production.controllers.dashboard;

import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.repositories.AccountRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.services.MLPredictionServiceClient;
import com.eneik.production.services.dashboard.BottleneckDetectionService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * @file ChatAssistantController.java
 * @description Provides an interactive AI Assistant Chat Endpoint that connects with Gemini
 *              and analyzes the real-time project metrics, queue, and bottleneck data.
 */
@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "http://localhost:3000")
public class ChatAssistantController {

    private final TaskRepository taskRepository;
    private final BottleneckDetectionService bottleneckDetectionService;
    private final MLPredictionServiceClient mlPredictionServiceClient;

    public ChatAssistantController(TaskRepository taskRepository,
                                   BottleneckDetectionService bottleneckDetectionService,
                                   MLPredictionServiceClient mlPredictionServiceClient) {
        this.taskRepository = taskRepository;
        this.bottleneckDetectionService = bottleneckDetectionService;
        this.mlPredictionServiceClient = mlPredictionServiceClient;
    }

    @PostMapping("/chat")
    public Map<String, String> askAssistant(@RequestBody Map<String, String> payload) {
        String userMessage = payload.getOrDefault("message", "");
        if (userMessage.trim().isEmpty()) {
            return Map.of("response", "Запрос пуст. Пожалуйста, задайте вопрос.");
        }

        // 1. Gather real-time pipeline state
        long queued = taskRepository.countByStatus(TaskStatus.queued);
        long claimed = taskRepository.countByStatus(TaskStatus.claimed);
        long inProgress = taskRepository.countByStatus(TaskStatus.in_progress);
        long review = taskRepository.countByStatus(TaskStatus.review);
        long done = taskRepository.countByStatus(TaskStatus.done);
        long failed = taskRepository.countByStatus(TaskStatus.failed);

        // 2. Gather bottlenecks state
        String bottlenecksStr = bottleneckDetectionService.detect().stream()
                .map(b -> b.type() + " (" + (b.tag() != null ? b.tag() : "аккаунт " + b.accountId()) + "): " + b.reason())
                .collect(Collectors.joining(", "));

        if (bottlenecksStr.isEmpty()) {
            bottlenecksStr = "Заторов в системе не обнаружено.";
        }

        // 3. Construct System Instruction to ground Gemini in our actual dashboard state
        String systemInstruction = "You are a professional Agile and Lean Product Coach managing an automated AI Agent Agency called Eneik.\n" +
                "You are given real-time dashboard metrics and state:\n" +
                "- Queue (задачи в очереди queued): " + queued + "\n" +
                "- Claimed (задачи claimed): " + claimed + "\n" +
                "- In Progress: " + inProgress + "\n" +
                "- Review (на ревью): " + review + "\n" +
                "- Done (выполнено): " + done + "\n" +
                "- Failed (сбойные): " + failed + "\n" +
                "- Bottleneck analysis: " + bottlenecksStr + "\n\n" +
                "Use this exact dashboard state to explain the current statuses, bottlenecks, and project progress in detail.\n" +
                "Respond in clear, helpful, and motivating Russian language. Keep your explanation aligned with Theory of Constraints (TOC) and Lean software development.";

        // 4. Query Python ML service/Gemini
        String aiResponse = mlPredictionServiceClient.chat(userMessage, systemInstruction);

        return Map.of("response", aiResponse);
    }
}
