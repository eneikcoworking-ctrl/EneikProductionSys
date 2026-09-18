package com.eneik.production.services.advice;

import com.eneik.production.models.persistence.ProjectEntity;
import com.eneik.production.models.persistence.ProjectStatus;
import com.eneik.production.models.persistence.TaskStatus;
import com.eneik.production.models.persistence.WishlistStatus;
import com.eneik.production.repositories.ProjectRepository;
import com.eneik.production.repositories.TaskRepository;
import com.eneik.production.repositories.WishlistRepository;
import com.eneik.production.services.logging.LogScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Poka-yoke observing guard against speculative wishlist generation during project idle cycles.
 * <p>
 * Epistemological grounding: FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK (D011 Perception failure).
 * A feedback signal is valid only if it aids real function and prevents erroneous action.
 * This service formally disclaims speculative advice generation ("снимает claim advice"):
 * when an active project has no pending wishlists, queued tasks, or active tasks, this guard
 * explicitly prevents speculative work synthesis. In accordance with the Sovereign Boundary Invariant,
 * the next product iteration may only originate from empirical falsification (FalsificationCycleService).
 */
@Service
public class IdleProjectAdviceService {
    private static final Logger log = LoggerFactory.getLogger(IdleProjectAdviceService.class);

    public enum IdleGuardVerdict {
        ACTIVE_WORK_PRESENT,
        IDLE_SPECULATIVE_WORK_PREVENTED
    }

    private final ProjectRepository projectRepository;
    private final WishlistRepository wishlistRepository;
    private final TaskRepository taskRepository;

    public IdleProjectAdviceService(ProjectRepository projectRepository,
                                    WishlistRepository wishlistRepository,
                                    TaskRepository taskRepository) {
        this.projectRepository = projectRepository;
        this.wishlistRepository = wishlistRepository;
        this.taskRepository = taskRepository;
    }

    /**
     * Evaluates the idle guard for a given project.
     * Prevents speculative wishlist generation if the project is idle.
     *
     * @param project the project to evaluate
     * @return IdleGuardVerdict indicating whether active work is present or speculative work was prevented
     */
    public IdleGuardVerdict checkIdleGuard(ProjectEntity project) {
        if (project == null || project.getId() == null) {
            return IdleGuardVerdict.ACTIVE_WORK_PRESENT;
        }
        if (isIdle(project)) {
            log.info("Poka-yoke: project {} is idle; observing guard active, speculative wishlist suppressed. "
                    + "The next product iteration may only come from falsification.", project.getName());
            return IdleGuardVerdict.IDLE_SPECULATIVE_WORK_PREVENTED;
        }
        return IdleGuardVerdict.ACTIVE_WORK_PRESENT;
    }

    @Scheduled(cron = "${idle-project-advice.cron:0 */15 * * * ?}")
    public Map<UUID, IdleGuardVerdict> generateIdleProjectAdvice() {
        LogScope.system();
        Map<UUID, IdleGuardVerdict> results = new HashMap<>();
        try {
            for (ProjectEntity project : projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)) {
                try {
                    LogScope.project(project.getId());
                    IdleGuardVerdict verdict = checkIdleGuard(project);
                    results.put(project.getId(), verdict);
                } catch (Exception e) {
                    log.error("IdleProjectAdviceService: failed to observe project {}", project.getName(), e);
                } finally {
                    LogScope.system();
                }
            }
            return Collections.unmodifiableMap(results);
        } finally {
            LogScope.clear();
        }
    }

    public boolean isIdle(ProjectEntity project) {
        if (project == null || project.getId() == null) {
            return false;
        }
        boolean noPendingWishlist = wishlistRepository
                .findByProjectIdAndStatus(project.getId(), WishlistStatus.pending)
                .isEmpty();
        boolean noQueuedTasks = taskRepository.countByProjectIdAndStatus(project.getId(), TaskStatus.queued) == 0;
        boolean noActiveTasks = taskRepository.findActiveTasksByProject(project.getId()).isEmpty();
        return noPendingWishlist && noQueuedTasks && noActiveTasks;
    }
}
