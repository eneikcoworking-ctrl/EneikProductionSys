package com.eneik.production.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verification for SchedulingConfig thread pool sizing and registrar configuration (D004, PART_WHOLE_OWNERSHIP).
 */
class SchedulingConfigTest {

    @Test
    @DisplayName("SchedulingConfig: taskScheduler pool size is 10 and initialized")
    void taskSchedulerPoolSizeIsTen() {
        SchedulingConfig config = new SchedulingConfig();
        ThreadPoolTaskScheduler scheduler = config.taskScheduler();
        try {
            assertThat(scheduler).isNotNull();
            assertThat(scheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(10);
            assertThat(scheduler.getThreadNamePrefix()).isEqualTo("scheduling-task-");
        } finally {
            scheduler.destroy();
        }
    }

    @Test
    @DisplayName("SchedulingConfig: configureTasks binds scheduler to ScheduledTaskRegistrar")
    void configureTasksBindsScheduler() {
        SchedulingConfig config = new SchedulingConfig();
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();
        config.configureTasks(registrar);
        assertThat(registrar.getScheduler()).isNotNull();
    }
}
