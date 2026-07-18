package com.eneik.production.services.advice;

import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.MLPredictionServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RoleAdviceLoopService {
    private static final Logger log = LoggerFactory.getLogger(RoleAdviceLoopService.class);

    private final TaskRepository taskRepository;
    private final WishlistRepository wishlistRepository;
    private final MLPredictionServiceClient mlPredictionServiceClient;

    public RoleAdviceLoopService(TaskRepository taskRepository, WishlistRepository wishlistRepository,
                                 MLPredictionServiceClient mlPredictionServiceClient) {
        this.taskRepository = taskRepository;
        this.wishlistRepository = wishlistRepository;
        this.mlPredictionServiceClient = mlPredictionServiceClient;
    }

    @Transactional
    public void afterTaskComplete(UUID taskId) {
        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));

        if (task.getStatus() != TaskStatus.done) {
            // Logic says afterTaskComplete, so we assume it should be done.
            // If not done, we might not want to create a wishlist yet.
            // But the requirement just says "creates wishlist-entry source='role'".
        }

        String description = task.getDescription();
        if (description != null) {
            String lower = description.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("recommendation based on") ||
                lower.contains("kano refactoring") ||
                lower.contains("refactoring:") ||
                lower.contains("рекомендация") ||
                lower.contains("cleanup required")) {
                // Skip recursive wishlist generation for recommendation/refactoring tasks
                return;
            }
        }

        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setProjectId(task.getProject().getId());
        wishlist.setSource(WishlistSource.role);
        wishlist.setSourceRoleTag(task.getRole().getTag());
        wishlist.setContent(generateFollowUpRecommendation(task));
        wishlist.setStatus(WishlistStatus.pending);

        wishlistRepository.save(wishlist);
    }

    private String generateFollowUpRecommendation(TaskEntity task) {
        String roleTag = task.getRole().getTag();
        String prompt = """
                A Jules-dispatched task just finished successfully for role %s:
                "%s"

                Propose ONE concrete, high-value next step for this same role to work on next,
                building directly on what this task just delivered. Be specific about what to build
                or change, not generic advice. Reply with only the recommendation text itself (2-4
                sentences), no preamble, no markdown headers.
                """.formatted(roleTag, task.getDescription());
        String systemInstruction = "You are a senior " + roleTag + " advisor recommending the next concrete unit of work after a completed task.";

        try {
            String response = mlPredictionServiceClient.chat(prompt, systemInstruction);
            if (response != null && !response.isBlank()
                    && !response.startsWith("ERROR:")
                    && !response.startsWith("The assistant is temporarily unavailable")) {
                return response.trim();
            }
            log.warn("RoleAdviceLoopService: Gemini follow-up recommendation unavailable for task {}, falling back to template", task.getId());
        } catch (Exception e) {
            log.warn("RoleAdviceLoopService: follow-up recommendation call failed for task {}: {}", task.getId(), e.getMessage());
        }
        return "Recommendation based on task completion: " + task.getDescription();
    }
}
