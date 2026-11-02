package com.eneik.production.services.design.sovereign.exception;

/**
 * Exception thrown when the rendering harness times out while capturing viewports.
 */
public class RenderTimeoutException extends RuntimeException {

    public RenderTimeoutException(String message) {
        super(message);
    }

    public RenderTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
