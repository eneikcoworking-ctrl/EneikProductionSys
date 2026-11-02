package com.eneik.production.services.worker.agy;

import com.eneik.production.services.worker.worktree.ExclusionaryReasonViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LocalAgyCommandBuilderTest {

    private final LocalAgyCommandBuilder builder = new LocalAgyCommandBuilder();

    @Test
    @DisplayName("Cynefin Model Routing: 'complex' maps to gemini-3.8-pro; 'clear' and 'complicated' map to gemini-3.8-flash-medium")
    void testCynefinRouting() {
        assertEquals("gemini-3.8-pro", builder.resolveModel("complex"));
        assertEquals("gemini-3.8-pro", builder.resolveModel("COMPLEX"));
        assertEquals("gemini-3.8-flash-medium", builder.resolveModel("clear"));
        assertEquals("gemini-3.8-flash-medium", builder.resolveModel("complicated"));
        assertEquals("gemini-3.8-flash-medium", builder.resolveModel("simple"));
        assertEquals("gemini-3.8-flash-medium", builder.resolveModel(null));
    }

    @Test
    @DisplayName("Command generation for remotecli3 includes sudo -n -u remotecli3 and flags")
    void testRemotecli3CommandGeneration() {
        List<String> cmd = builder.buildCommand("remotecli3", "complicated", "Refactor backend service");

        assertEquals("sudo", cmd.get(0));
        assertEquals("-n", cmd.get(1));
        assertEquals("-u", cmd.get(2));
        assertEquals("remotecli3", cmd.get(3));
        assertEquals("/home/remotecli3/.local/bin/agy", cmd.get(4));
        assertTrue(cmd.contains("--model"));
        assertTrue(cmd.contains("gemini-3.8-flash-medium"));
        assertTrue(cmd.contains("--dangerously-skip-permissions"));
        assertTrue(cmd.contains("-p"));
        assertTrue(cmd.contains("Refactor backend service"));
    }

    @Test
    @DisplayName("Command generation for root runs directly without sudo")
    void testRootCommandGeneration() {
        List<String> cmd = builder.buildCommand("root", "complex", "Architectural redesign");

        assertEquals("/usr/local/bin/agy", cmd.get(0));
        assertTrue(cmd.contains("--model"));
        assertTrue(cmd.contains("gemini-3.8-pro"));
        assertTrue(cmd.contains("--dangerously-skip-permissions"));
        assertTrue(cmd.contains("-p"));
        assertTrue(cmd.contains("Architectural redesign"));
    }

    @Test
    @DisplayName("Raz Deontic Gate: building command for remotecli or remotecli2 throws ExclusionaryReasonViolationException")
    void testForbiddenUsersThrow() {
        assertThrows(ExclusionaryReasonViolationException.class, () ->
                builder.buildCommand("remotecli", "clear", "test prompt")
        );
        assertThrows(ExclusionaryReasonViolationException.class, () ->
                builder.buildCommand("remotecli2", "complex", "test prompt")
        );
    }
}
