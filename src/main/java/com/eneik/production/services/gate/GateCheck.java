package com.eneik.production.services.gate;

import com.eneik.production.models.persistence.TaskEntity;

public interface GateCheck {
    default GateStage stage() {
        return GateStage.TASK_SPEC;
    }

    default boolean supports(TaskEntity task) {
        return true;
    }

    GateResult check(TaskEntity task);
}
