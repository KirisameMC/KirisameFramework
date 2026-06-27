package org.kirisame.mc.core.plugin;

import org.kirisame.mc.api.plugin.PluginLogger;

/**
 * Simple logger implementation for plugins.
 * Writes directly to System.out to avoid depending on any logging library.
 * The format is: [Plugin/Name] [LEVEL] message
 */
public class PluginLoggerImpl implements PluginLogger {

    private final String prefix;

    public PluginLoggerImpl(String pluginName) {
        this.prefix = "[Plugin/" + pluginName + "]";
    }

    @Override
    public void info(String message) {
        System.out.println(prefix + " [INFO] " + message);
    }

    @Override
    public void info(String format, Object... args) {
        System.out.println(prefix + " [INFO] " + format(format, args));
    }

    @Override
    public void warn(String message) {
        System.out.println(prefix + " [WARN] " + message);
    }

    @Override
    public void warn(String format, Object... args) {
        System.out.println(prefix + " [WARN] " + format(format, args));
    }

    @Override
    public void error(String message) {
        System.err.println(prefix + " [ERROR] " + message);
    }

    @Override
    public void error(String format, Object... args) {
        System.err.println(prefix + " [ERROR] " + format(format, args));
    }

    @Override
    public void debug(String message) {
        System.out.println(prefix + " [DEBUG] " + message);
    }

    @Override
    public void debug(String format, Object... args) {
        System.out.println(prefix + " [DEBUG] " + format(format, args));
    }

    private static String format(String format, Object... args) {
        if (args == null || args.length == 0) return format;
        // Simple {} placeholder replacement (SLF4J-style)
        StringBuilder sb = new StringBuilder();
        int argIdx = 0;
        int i = 0;
        while (i < format.length()) {
            if (i + 1 < format.length() && format.charAt(i) == '{' && format.charAt(i + 1) == '}') {
                sb.append(argIdx < args.length ? args[argIdx++] : "{}");
                i += 2;
            } else {
                sb.append(format.charAt(i));
                i++;
            }
        }
        return sb.toString();
    }
}
