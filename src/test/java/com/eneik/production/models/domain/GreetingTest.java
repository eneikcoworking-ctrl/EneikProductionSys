package com.eneik.production.models.domain;

import com.eneik.production.models.persistence.Status;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

public class GreetingTest {

    @Test
    void testGreetingCreation() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Greeting greeting = new Greeting(id, "Hello", Status.RECEIVED, now, null, null);

        assertEquals(id, greeting.getId());
        assertEquals("Hello", greeting.getMessage());
        assertEquals(Status.RECEIVED, greeting.getCurrentStatus());
        assertEquals(now, greeting.getCreatedAt());
        assertTrue(greeting.getLeadTimeSeconds() >= 0);
    }

    @Test
    void testGreetingValidation() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Greeting(UUID.randomUUID(), "", Status.RECEIVED, Instant.now(), null, null);
        });
    }
}
