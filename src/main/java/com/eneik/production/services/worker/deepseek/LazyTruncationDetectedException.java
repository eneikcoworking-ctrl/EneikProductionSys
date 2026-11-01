package com.eneik.production.services.worker.deepseek;

/**
 * Thrown by Anti-Truncation Gate when model response contains lazy stop-markers
 * such as comments indicating omitted code.
 * Prevents codebase corruption and triggers deterministic reflection.
 */
public class LazyTruncationDetectedException extends RuntimeException {
    private final String markerFound;
    private final String filePath;

    public LazyTruncationDetectedException(String markerFound, String filePath) {
        super("Anti-Truncation Gate: Lazy truncation marker detected '" + markerFound + "' in file '" + filePath + "'");
        this.markerFound = markerFound;
        this.filePath = filePath;
    }

    public String getMarkerFound() {
        return markerFound;
    }

    public String getFilePath() {
        return filePath;
    }
}
