package com.eneik.production.services.worker;

import com.eneik.production.services.worker.verification.DuhemQuineDetector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DuhemQuineAntiLoopingTest {

    @Test
    @DisplayName("Duhem-Quine Anti-Looping: flags loop when model outputs identical patch twice")
    void testLoopDetectedOnIdenticalPatch() {
        DuhemQuineDetector detector = new DuhemQuineDetector();
        String patch = "{\"files\": [{\"path\": \"A.java\", \"content\": \"class A{}\"}]}";

        // First attempt: records hash
        boolean loop1 = detector.isLoopDetected(patch, "compilation error line 1");
        assertFalse(loop1);

        // Second attempt with identical patch content: loop flagged!
        boolean loop2 = detector.isLoopDetected(patch, "compilation error line 2");
        assertTrue(loop2, "Identical patch content must trigger Duhem-Quine loop detection");
    }

    @Test
    @DisplayName("Duhem-Quine Anti-Looping: flags loop when compilation error signature repeats twice")
    void testLoopDetectedOnRepeatedErrorSignature() {
        DuhemQuineDetector detector = new DuhemQuineDetector();
        String signature = "[ERROR] /A.java:[10,5] cannot find symbol class Foo";

        assertFalse(detector.isLoopDetected("patch1", signature));
        assertTrue(detector.isLoopDetected("patch2", signature), "Same error signature repeated must trigger loop detection");
    }

    @Test
    @DisplayName("Progressive evolution with different patches and errors proceeds normally")
    void testProgressiveReflectionProceeds() {
        DuhemQuineDetector detector = new DuhemQuineDetector();
        assertFalse(detector.isLoopDetected("patch1", "error A"));
        assertFalse(detector.isLoopDetected("patch2", "error B"));
        assertFalse(detector.isLoopDetected("patch3", null));
    }
}
