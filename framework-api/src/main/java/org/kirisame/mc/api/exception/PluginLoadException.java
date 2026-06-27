package org.kirisame.mc.api.exception;

/**
 * Thrown when a plugin fails to load.
 */
public class PluginLoadException extends RuntimeException {

    public PluginLoadException(String message) {
        super(message);
    }

    public PluginLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
