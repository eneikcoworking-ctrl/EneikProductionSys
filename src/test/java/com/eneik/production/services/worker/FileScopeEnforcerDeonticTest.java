package com.eneik.production.services.worker;

import com.eneik.production.models.persistence.TaskEntity;
import com.eneik.production.services.worker.worktree.ExclusionaryReasonViolationException;
import com.eneik.production.services.worker.worktree.FileScopeEnforcer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileScopeEnforcerDeonticTest {

    private final FileScopeEnforcer enforcer = new FileScopeEnforcer(new ObjectMapper());

    @Test
    @DisplayName("Raz Deontic Gate D006: Exact match within scope passes without violations")
    void testScopeCleanWhenAllFilesWithinDeclaredScope() {
        List<String> declared = List.of(
                "src/main/java/com/eneik/production/ServiceA.java",
                "src/test/java/com/eneik/production/ServiceATest.java"
        );
        List<String> actual = List.of(
                "src/main/java/com/eneik/production/ServiceA.java"
        );

        List<String> violations = enforcer.findViolations(actual, declared);
        assertTrue(violations.isEmpty(), "Modifications within declared scope must yield zero violations");
    }

    @Test
    @DisplayName("Raz Deontic Gate D006: Any file outside declared scope causes Exclusionary Denial")
    void testScopeViolationWhenUndeclaredFileModified() {
        List<String> declared = List.of(
                "src/main/java/com/eneik/production/ServiceA.java"
        );
        List<String> actual = List.of(
                "src/main/java/com/eneik/production/ServiceA.java",
                "src/main/java/com/eneik/production/UnauthorizedSecret.java"
        );

        List<String> violations = enforcer.findViolations(actual, declared);
        assertFalse(violations.isEmpty(), "Violating files must be caught");
        assertEquals(1, violations.size());
        assertEquals("src/main/java/com/eneik/production/UnauthorizedSecret.java", violations.get(0));
    }

    @Test
    @DisplayName("Parses JSON and comma-delimited fileScope strings accurately")
    void testFileScopeParsing() {
        String jsonScope = "[\"src/main/A.java\", \"src/main/B.java\"]";
        List<String> parsedJson = enforcer.parseDeclaredScope(jsonScope);
        assertEquals(2, parsedJson.size());
        assertEquals("src/main/A.java", parsedJson.get(0));

        String commaScope = "src/main/A.java, src/main/B.java";
        List<String> parsedComma = enforcer.parseDeclaredScope(commaScope);
        assertEquals(2, parsedComma.size());
        assertEquals("src/main/A.java", parsedComma.get(0));
    }
}
