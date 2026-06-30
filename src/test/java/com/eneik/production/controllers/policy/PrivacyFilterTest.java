package com.eneik.production.controllers.policy;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

public class PrivacyFilterTest {

    @Test
    void testMaskData() {
        Map<String, Object> data = new HashMap<>();
        data.put("pii", "secret");
        data.put("other", "public");

        Map<String, Object> masked = PrivacyFilter.maskData(data);
        assertEquals("****", masked.get("pii"));
        assertEquals("public", masked.get("other"));
    }

    @Test
    void testVerifyKnowledge() {
        assertTrue(PrivacyFilter.verifyKnowledge("VALID_KNOWLEDGE_TOKEN"));
        assertFalse(PrivacyFilter.verifyKnowledge("INVALID"));
    }
}
