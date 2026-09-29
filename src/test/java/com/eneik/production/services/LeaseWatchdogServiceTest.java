package com.eneik.production.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit test suite for {@link LeaseWatchdogService}.
 */
class LeaseWatchdogServiceTest {

    private ClaimService claimService;
    private LeaseWatchdogService watchdogService;

    @BeforeEach
    void setUp() {
        claimService = mock(ClaimService.class);
        watchdogService = new LeaseWatchdogService(claimService);
    }

    @Test
    @DisplayName("reapExpiredLeases delegates directly to ClaimService.reapExpiredLeases")
    void reapExpiredLeases_delegatesToClaimService() {
        watchdogService.reapExpiredLeases();
        verify(claimService, times(1)).reapExpiredLeases();
    }

    @Test
    @DisplayName("reapExpiredLeases method carries @Scheduled annotation with fixedRate = 60000 ms")
    void reapExpiredLeases_hasScheduledAnnotationWithFixedRate() throws NoSuchMethodException {
        Method method = LeaseWatchdogService.class.getMethod("reapExpiredLeases");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertNotNull(scheduled, "Method reapExpiredLeases must be annotated with @Scheduled");
        assertEquals(60000L, scheduled.fixedRate(), "Scheduled fixedRate must be exactly 60000 ms (1 minute)");
    }

    @Test
    @DisplayName("reapExpiredLeases propagates runtime exceptions from ClaimService without swallowing")
    void reapExpiredLeases_propagatesExceptions() {
        doThrow(new IllegalStateException("Database lock timeout")).when(claimService).reapExpiredLeases();

        assertThrows(IllegalStateException.class, () -> watchdogService.reapExpiredLeases());
        verify(claimService, times(1)).reapExpiredLeases();
    }
}
