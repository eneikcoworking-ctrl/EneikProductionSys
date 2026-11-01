package com.eneik.production.services.worker.verification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Duhem-Quine Thesis and Semantic Loop Detector (Dennett Autonomy Architecture).
 * Prevents cognitive spinning and token burning when the model emits identical patches
 * or runs into the exact same compilation error signature consecutively.
 */
public class DuhemQuineDetector {

    private static final Logger log = LoggerFactory.getLogger(DuhemQuineDetector.class);

    private String previousPatchHash;
    private String previousErrorSignature;
    private int consecutiveIdenticalErrors = 0;

    public boolean isLoopDetected(String currentPatchContent, String currentErrorSignature) {
        String currentHash = computeHash(currentPatchContent);

        // 1. Identical semantic diff generated twice
        if (previousPatchHash != null && previousPatchHash.equals(currentHash) && !currentHash.isEmpty()) {
            log.warn("Duhem-Quine Loop Detected: model emitted identical patch hash '{}' twice", currentHash);
            return true;
        }

        // 2. Exact same error signature repeated 2 times
        if (currentErrorSignature != null && !currentErrorSignature.isBlank()) {
            if (currentErrorSignature.equals(previousErrorSignature)) {
                consecutiveIdenticalErrors++;
                if (consecutiveIdenticalErrors >= 2) {
                    log.warn("Duhem-Quine Loop Detected: identical compiler/test error signature repeated {} times: [{}]",
                            consecutiveIdenticalErrors, currentErrorSignature);
                    return true;
                }
            } else {
                consecutiveIdenticalErrors = 1;
            }
        } else {
            consecutiveIdenticalErrors = 0;
        }

        this.previousPatchHash = currentHash;
        this.previousErrorSignature = currentErrorSignature;
        return false;
    }

    public static String computeHash(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(text.hashCode());
        }
    }

    public static String extractErrorSignature(String logs) {
        if (logs == null || logs.isBlank()) {
            return "UNKNOWN_ERROR";
        }
        // Extract up to 3 lines containing [ERROR] or Failure/Exception
        StringBuilder sig = new StringBuilder();
        int count = 0;
        for (String line : logs.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.contains("[ERROR]") || trimmed.contains("Exception") || trimmed.contains("Compilation failure")
                    || trimmed.contains("FAILURE") || trimmed.contains("cannot find symbol")) {
                sig.append(trimmed).append(" | ");
                count++;
                if (count >= 3) {
                    break;
                }
            }
        }
        return sig.length() > 0 ? sig.toString() : (logs.length() > 100 ? logs.substring(0, 100) : logs);
    }
}
