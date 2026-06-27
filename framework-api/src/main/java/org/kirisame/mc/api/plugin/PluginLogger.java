package org.kirisame.mc.api.plugin;

/**
 * Simple logger interface for plugins.
 * Avoids depending on any external logging library (SLF4J, Log4j, etc.)
 * so the framework JAR doesn't pollute the server's classpath.
 */
public interface PluginLogger {

    void info(String message);

    void info(String format, Object... args);

    void warn(String message);

    void warn(String format, Object... args);

    void error(String message);

    void error(String format, Object... args);

    void debug(String message);

    void debug(String format, Object... args);
}
