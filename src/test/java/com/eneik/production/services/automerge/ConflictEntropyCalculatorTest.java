package com.eneik.production.services.automerge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ConflictEntropyCalculatorTest {

    private final ConflictEntropyCalculator calculator = new ConflictEntropyCalculator();

    @Test
    void testPureOrchestratorConflictHasLowEntropyAndIsTrivial() {
        List<String> files = List.of(".eneik/agents.json", ".gitignore", "README.md");
        var result = calculator.calculateEntropy(files);

        assertThat(result.isTrivialOrchestratorConflict()).isTrue();
        assertThat(result.orchestratorFilesCount()).isEqualTo(3);
        assertThat(result.productFilesCount()).isEqualTo(0);
        assertThat(result.shannonEntropy()).isEqualTo(0.0);
    }

    @Test
    void testComplexProductCodeConflictHasHighEntropy() {
        List<String> files = List.of(
                "src/main/java/com/eneik/production/services/AutoMergeService.java",
                "src/main/java/com/eneik/production/controllers/HomeController.java",
                ".eneik/agents.json"
        );
        var result = calculator.calculateEntropy(files);

        assertThat(result.isTrivialOrchestratorConflict()).isFalse();
        assertThat(result.productFilesCount()).isEqualTo(2);
        assertThat(result.shannonEntropy()).isGreaterThan(0.5);
    }
}
