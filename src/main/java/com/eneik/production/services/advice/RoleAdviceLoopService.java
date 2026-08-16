package com.eneik.production.services.advice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RoleAdviceLoopService {
    private static final Logger log = LoggerFactory.getLogger(RoleAdviceLoopService.class);

    @Transactional
    public void afterTaskComplete(UUID taskId) {
        log.info("Poka-yoke: task {} completed; role advice is observation-only and cannot create "
                + "wishlist work. The next product iteration may only come from falsification.", taskId);
    }
}
