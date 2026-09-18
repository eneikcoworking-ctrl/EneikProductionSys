package com.eneik.production.services.advice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Poka-yoke observing guard against speculative wishlist work upon task completion.
 * <p>
 * Epistemological grounding: FRED_DRETSKE_07_TELEOSEMANTIC_FEEDBACK (D011 Perception failure).
 * A feedback signal is valid only if it aids real function and prevents erroneous action.
 * This service formally disclaims speculative advice generation ("снимает claim advice"):
 * it is observation-only and strictly suppresses follow-up wishlist creation upon task completion.
 * In accordance with the Sovereign Boundary Invariant, subsequent product iterations may only
 * originate from empirical falsification (FalsificationCycleService), eliminating recursive brief-echoing.
 */
@Service
public class RoleAdviceLoopService {
    private static final Logger log = LoggerFactory.getLogger(RoleAdviceLoopService.class);

    public enum RoleAdviceGuardVerdict {
        TASK_NULL_OR_SKIPPED,
        SPECULATIVE_WORK_SUPPRESSED
    }

    @Transactional
    public RoleAdviceGuardVerdict afterTaskComplete(UUID taskId) {
        if (taskId == null) {
            log.warn("RoleAdviceLoopService: taskId is null, guard skipped");
            return RoleAdviceGuardVerdict.TASK_NULL_OR_SKIPPED;
        }
        log.info("Poka-yoke: task {} completed; observing guard active, role advice is observation-only "
                + "and speculative wishlist generation is strictly suppressed. "
                + "The next product iteration may only come from falsification.", taskId);
        return RoleAdviceGuardVerdict.SPECULATIVE_WORK_SUPPRESSED;
    }
}

