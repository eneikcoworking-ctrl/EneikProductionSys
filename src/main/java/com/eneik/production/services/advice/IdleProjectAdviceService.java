package com.eneik.production.services.advice;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.RoleEntity;
import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.WishlistEntity;
import com.eneik.production.models.persistence.WishlistSource;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.RoleRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.MLPredictionServiceClient;
import com.eneik.production.services.logging.LogScope;
import com.eneik.production.services.logging.LogScopeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Breaks the wishlist-starvation loop: RoleAdviceLoopService only ever fires after a task actually
 * completes, so a project stuck on a low merge rate (circuit-breaker bugs, review backlog, etc.)
 * silently runs out of fuel - confirmed during the 8h autonomous run where all real task claims
 * happened in one 13-minute burst and then nothing for the rest of the day. This service detects a
 * genuinely idle active project (no pending wishlist, no queued work, nothing in flight) and asks
 * Gemini for exactly one next wishlist item, deliberately targeted at the role this project has used
 * least - the same 8h run only ever touched 3 of 12 roles because the single client wishlist never
 * needed the others.
 *
 * Deliberately capped at one role / one item / project / cycle: an idle-driven generator is the
 * single biggest risk in this design (it could otherwise flood the wishlist overnight and burn Jules
 * quota on speculative work nobody asked for).
 */
@Service
public class IdleProjectAdviceService {
    private static final Logger log = LoggerFactory.getLogger(IdleProjectAdviceService.class);

    private final ProjectRepository projectRepository;
    private final WishlistRepository wishlistRepository;
    private final TaskRepository taskRepository;
    private final RoleRepository roleRepository;
    private final MLPredictionServiceClient mlPredictionServiceClient;

    public IdleProjectAdviceService(ProjectRepository projectRepository,
                                    WishlistRepository wishlistRepository,
                                    TaskRepository taskRepository,
                                    RoleRepository roleRepository,
                                    MLPredictionServiceClient mlPredictionServiceClient) {
        this.projectRepository = projectRepository;
        this.wishlistRepository = wishlistRepository;
        this.taskRepository = taskRepository;
        this.roleRepository = roleRepository;
        this.mlPredictionServiceClient = mlPredictionServiceClient;
    }

    @Scheduled(cron = "${idle-project-advice.cron:0 */15 * * * ?}")
    public void generateIdleProjectAdvice() {
        LogScope.system();
        try {
            for (ProjectEntity project : projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)) {
                try {
                    LogScope.project(project.getId());
                    if (isIdle(project)) {
                        generateOneAdvice(project);
                    }
                } catch (Exception e) {
                    log.error("IdleProjectAdviceService: failed to process project {}", project.getName(), e);
                } finally {
                    LogScope.system();
                }
            }
        } finally {
            LogScope.clear();
        }
    }

    private boolean isIdle(ProjectEntity project) {
        boolean noPendingWishlist = wishlistRepository
                .findByProjectIdAndStatus(project.getId(), WishlistStatus.pending)
                .isEmpty();
        boolean noQueuedTasks = taskRepository.countByProjectIdAndStatus(project.getId(), TaskStatus.queued) == 0;
        boolean noActiveTasks = taskRepository.findActiveTasksByProject(project.getId()).isEmpty();
        return noPendingWishlist && noQueuedTasks && noActiveTasks;
    }

    @Transactional
    void generateOneAdvice(ProjectEntity project) {
        RoleEntity targetRole = leastRepresentedActiveRole(project);
        if (targetRole == null) {
            return;
        }

        String recentActivity = String.join("\n", LogScopeBuffer.recent(project.getId(), 40));
        String originalBrief = wishlistRepository.findByProjectId(project.getId()).stream()
                .filter(w -> w.getSource() == WishlistSource.client)
                .map(WishlistEntity::getContent)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("(no client brief on file)");

        String prompt = """
                Project "%s" has gone idle: no pending wishlist items, nothing queued, nothing in
                flight. Of the 12 available roles, "%s" (%s) has been used least so far on this
                project.

                Original client brief:
                %s

                Recent operational activity:
                %s

                Propose ONE ambitious, concrete wishlist item scoped specifically to the %s role's
                domain that would move this project forward. Reply with only the wishlist item text
                itself (3-6 sentences, specific and actionable), no preamble, no markdown headers.
                """.formatted(
                project.getName(),
                targetRole.getTag(),
                targetRole.getDescription(),
                originalBrief,
                recentActivity.isBlank() ? "(none recorded)" : recentActivity,
                targetRole.getTag()
        );
        String systemInstruction = "You are a senior " + targetRole.getTag()
                + " advisor proposing the next wishlist item for a project that has gone idle.";

        String content;
        try {
            String response = mlPredictionServiceClient.chat(prompt, systemInstruction);
            if (response == null || response.isBlank()
                    || response.startsWith("ERROR:")
                    || response.startsWith("The assistant is temporarily unavailable")) {
                log.warn("IdleProjectAdviceService: Gemini unavailable for project {}, skipping this cycle", project.getName());
                return;
            }
            content = response.trim();
        } catch (Exception e) {
            log.warn("IdleProjectAdviceService: advice generation failed for project {}: {}", project.getName(), e.getMessage());
            return;
        }

        WishlistEntity wishlist = new WishlistEntity();
        wishlist.setProjectId(project.getId());
        wishlist.setSource(WishlistSource.idle_generation);
        wishlist.setSourceRoleTag(targetRole.getTag());
        wishlist.setContent(content);
        wishlist.setStatus(WishlistStatus.pending);
        wishlistRepository.save(wishlist);

        log.info("IdleProjectAdviceService: project {} was idle, generated one wishlist item for role {}",
                project.getName(), targetRole.getTag());
    }

    private RoleEntity leastRepresentedActiveRole(ProjectEntity project) {
        List<RoleEntity> activeRoles = roleRepository.findAll().stream()
                .filter(RoleEntity::isActive)
                .toList();
        if (activeRoles.isEmpty()) {
            return null;
        }

        Map<String, Long> taskCountByRoleTag = new HashMap<>();
        for (TaskEntity task : taskRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())) {
            if (task.getRole() != null) {
                taskCountByRoleTag.merge(task.getRole().getTag(), 1L, Long::sum);
            }
        }

        return activeRoles.stream()
                .min(Comparator
                        .<RoleEntity>comparingLong(r -> taskCountByRoleTag.getOrDefault(r.getTag(), 0L))
                        .thenComparing(RoleEntity::getTag))
                .orElse(null);
    }
}
