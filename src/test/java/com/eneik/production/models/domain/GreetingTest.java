package com.eneik.production.models.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

public class GreetingTest {

    @Test
    void testGreetingCreation() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Greeting greeting = new Greeting(id, "Hello", GreetingStatus.RECEIVED, now, 42);

        assertEquals(id, greeting.getId());
        assertEquals("Hello", greeting.getMessage());
        assertEquals(GreetingStatus.RECEIVED, greeting.getCurrentStatus());
        assertEquals(now, greeting.getCreatedAt());
        assertEquals(42, greeting.getLeadTimeSeconds());
    }

    @Test
    void testGreetingValidation() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Greeting(UUID.randomUUID(), "", GreetingStatus.RECEIVED, Instant.now(), 42);
        });
    }
}
