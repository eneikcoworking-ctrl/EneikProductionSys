package com.eneik.production.services.worker.verification;

public record VerificationResult(
        boolean passed,
        int exitCode,
        String output,
        String errorSignature
) {
    public static VerificationResult success(String output) {
        return new VerificationResult(true, 0, output, null);
    }

    public static VerificationResult success(int exitCode, String output) {
        return new VerificationResult(true, exitCode, output, null);
    }

    public static VerificationResult failure(int exitCode, String output, String errorSignature) {
        return new VerificationResult(false, exitCode, output, errorSignature);
    }
}
