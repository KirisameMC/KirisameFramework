package org.kirisame.mc.api.exception;

/**
 * Thrown when plugin dependency resolution fails (missing required dependency or circular dependency).
 */
public class DependencyException extends RuntimeException {

    public DependencyException(String message) {
        super(message);
    }

    public DependencyException(String message, Throwable cause) {
        super(message, cause);
    }
}
