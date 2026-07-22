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

@Service
public class IdleProjectAdviceService {
    private static final Logger log = LoggerFactory.getLogger(IdleProjectAdviceService.class);

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

    @Scheduled(cron = "${idle-project-advice.cron:0 */15 * * * ?}")
    public void generateIdleProjectAdvice() {
        LogScope.system();
        try {
            for (ProjectEntity project : projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.active)) {
                try {
                    LogScope.project(project.getId());
                    if (isIdle(project)) {
                        log.info("Poka-yoke: project {} is idle; no speculative wishlist is generated. "
                                + "The next product iteration may only come from falsification.", project.getName());
                    }
                } catch (Exception e) {
                    log.error("IdleProjectAdviceService: failed to observe project {}", project.getName(), e);
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
}
