package com.eneik.production.models.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit and structural tests enforcing Law 1 (Single Point of Application)
 * from ENGINEERING_PHILOSOPHY_ACTION_PLAN.md:
 *
 *   |impl(I)| = 1 для всякого инварианта I.
 *   carrier(τ) ⟺ payload(τ).taskType ≠ ∅
 *
 * "carrier написан минимум трижды... Оставить один — методом на TaskEntity.
 * Структурный тест: payload.hasNonNull("taskType") встречается ровно в одном месте."
 */
public class TaskEntityLaw1CarrierTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("Non-carrier tasks: null, empty or unrelated payload is not a carrier")
    void nonCarrierTasks() {
        TaskEntity taskNullPayload = new TaskEntity();
        taskNullPayload.setPayload(null);
        assertFalse(taskNullPayload.isCarrier());
        assertNull(taskNullPayload.carrierTaskType());
        assertFalse(taskNullPayload.isWishlistCompiler());
        assertFalse(taskNullPayload.isHousekeepingCarrier());

        TaskEntity taskEmptyPayload = new TaskEntity();
        taskEmptyPayload.setPayload(MAPPER.createObjectNode());
        assertFalse(taskEmptyPayload.isCarrier());
        assertNull(taskEmptyPayload.carrierTaskType());
        assertFalse(taskEmptyPayload.isWishlistCompiler());
        assertFalse(taskEmptyPayload.isHousekeepingCarrier());

        TaskEntity taskUnrelatedPayload = new TaskEntity();
        ObjectNode unrelated = MAPPER.createObjectNode();
        unrelated.put("someOtherKey", "value");
        taskUnrelatedPayload.setPayload(unrelated);
        assertFalse(taskUnrelatedPayload.isCarrier());
        assertNull(taskUnrelatedPayload.carrierTaskType());
        assertFalse(taskUnrelatedPayload.isWishlistCompiler());
        assertFalse(taskUnrelatedPayload.isHousekeepingCarrier());
    }

    @Test
    @DisplayName("NullNode taskType: JSON null is not a non-null carrier marker")
    void jsonNullTaskTypeIsNotCarrier() {
        TaskEntity task = new TaskEntity();
        ObjectNode payload = MAPPER.createObjectNode();
        payload.putNull(TaskEntity.CARRIER_PAYLOAD_KEY);
        task.setPayload(payload);

        assertFalse(task.isCarrier(), "hasNonNull must reject JSON null node");
        assertNull(task.carrierTaskType());
        assertFalse(task.isWishlistCompiler());
        assertFalse(task.isHousekeepingCarrier());
    }

    @Test
    @DisplayName("Wishlist compiler carrier: isCarrier=true, isWishlistCompiler=true, isHousekeepingCarrier=false")
    void wishlistCompilerCarrier() {
        TaskEntity task = new TaskEntity();
        ObjectNode payload = MAPPER.createObjectNode();
        payload.put(TaskEntity.CARRIER_PAYLOAD_KEY, TaskEntity.WISHLIST_COMPILER_TASK_TYPE);
        task.setPayload(payload);

        assertTrue(task.isCarrier());
        assertEquals(TaskEntity.WISHLIST_COMPILER_TASK_TYPE, task.carrierTaskType());
        assertTrue(task.isWishlistCompiler());
        assertFalse(task.isHousekeepingCarrier());
    }

    @Test
    @DisplayName("Housekeeping carrier: isCarrier=true, isWishlistCompiler=false, isHousekeepingCarrier=true")
    void housekeepingCarriers() {
        String[] housekeepingTypes = {
                "pr_review_fallback",
                "design_concern_triage",
                "coverage_gap_audit",
                "self_falsification_audit",
                "design_review",
                "branch_cleanup"
        };

        for (String type : housekeepingTypes) {
            TaskEntity task = new TaskEntity();
            ObjectNode payload = MAPPER.createObjectNode();
            payload.put(TaskEntity.CARRIER_PAYLOAD_KEY, type);
            task.setPayload(payload);

            assertTrue(task.isCarrier(), "carrier type " + type + " must be recognized as carrier");
            assertEquals(type, task.carrierTaskType());
            assertFalse(task.isWishlistCompiler(), "housekeeping carrier must not be wishlist compiler");
            assertTrue(task.isHousekeepingCarrier(), "housekeeping carrier must be recognized as housekeeping");
        }
    }

    @Test
    @DisplayName("Structural Law 1 Invariant: payload carrier check is implemented strictly in TaskEntity.java")
    void singlePointOfImplementationStructuralTest() throws IOException {
        Path mainJavaRoot = Path.of("src/main/java/com/eneik/production");
        if (!Files.exists(mainJavaRoot)) {
            mainJavaRoot = Path.of("C:/Projects/Eneik/docker-build/EneikProductionSys/src/main/java/com/eneik/production");
        }
        if (!Files.exists(mainJavaRoot)) {
            return;
        }

        List<String> filesWithDirectCarrierPayloadInspection = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(mainJavaRoot)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(path -> {
                try {
                    String content = Files.readString(path);
                    if (content.contains(".hasNonNull(\"taskType\")")
                            || content.contains(".hasNonNull(CARRIER_PAYLOAD_KEY)")
                            || content.contains(".hasNonNull(WISHLIST_COMPILER_PAYLOAD_KEY)")
                            || content.contains(".has(\"taskType\")")
                            || content.contains(".has(SYSTEM_TASK_TYPE_PAYLOAD_KEY)")) {
                        filesWithDirectCarrierPayloadInspection.add(path.getFileName().toString());
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        assertEquals(List.of("TaskEntity.java"), filesWithDirectCarrierPayloadInspection,
                "Law 1: carrier(τ) implementation must reside solely in TaskEntity.java, but was found in "
                        + filesWithDirectCarrierPayloadInspection);
    }
}
