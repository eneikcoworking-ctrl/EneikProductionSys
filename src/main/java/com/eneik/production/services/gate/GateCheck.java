package com.eneik.production.services.gate;

import com.eneik.production.models.persistence.TaskEntity;

public interface GateCheck {
    default GateStage stage() {
        return GateStage.TASK_SPEC;
    }

    default boolean supports(TaskEntity task) {
        return true;
    }

    // Mechanical polish/checklist gates (screenshot scoring, test-file presence, coverage) judge the
    // quality of finished work, not whether the work is safe or well-formed. During a project's build
    // phase (ClientDeliverableReadinessService.isBuildPhase) trust is maximal by design - the role's own
    // philosophical refusal criteria (enforced separately in AutoMergeService) is what should shape the
    // work from session one, not an external checklist. Gates that exist purely to polish/refine mature
    // output override this to true so GateOrchestrator skips them until the system has some shape.
    default boolean isBuildPhaseExempt() {
        return false;
    }

    GateResult check(TaskEntity task);
}
