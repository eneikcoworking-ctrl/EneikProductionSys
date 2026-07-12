package com.eneik.production.services.advice;

import com.eneik.production.models.persistence.*;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RoleAdviceLoopService {

    private final TaskRepository taskRepository;
    private final WishlistRepository wishlistRepository;

    public RoleAdviceLoopService(TaskRepository taskRepository, WishlistRepository wishlistRepository) {
        this.taskRepository = taskRepository;
        this.wishlistRepository = wishlistRepository;
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
        wishlist.setContent("Recommendation based on task completion: " + task.getDescription());
        wishlist.setStatus(WishlistStatus.pending);

        wishlistRepository.save(wishlist);
    }
}
