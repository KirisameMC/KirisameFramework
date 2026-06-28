package org.kirisame.mc.core.plugin;

import org.kirisame.mc.api.plugin.PluginLogger;
import org.kirisame.mc.core.console.OriginalOutput;

import java.io.PrintStream;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Logger implementation for plugins.
 * Writes to the ORIGINAL System.out (bypassing ConsoleInterceptor),
 * with timestamps matching MC server log format.
 *
 * Format: [HH:mm:ss] [Plugin/Name/LEVEL]: message
 */
public class PluginLoggerImpl implements PluginLogger {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final String pluginName;

    public PluginLoggerImpl(String pluginName) {
        this.pluginName = pluginName;
    }

    @Override
    public void info(String message) {
        print("INFO", message);
    }

    @Override
    public void info(String format, Object... args) {
        print("INFO", format(format, args));
    }

    @Override
    public void warn(String message) {
        print("WARN", message);
    }

    @Override
    public void warn(String format, Object... args) {
        print("WARN", format(format, args));
    }

    @Override
    public void error(String message) {
        print("ERROR", message);
    }

    @Override
    public void error(String format, Object... args) {
        print("ERROR", format(format, args));
    }

    @Override
    public void debug(String message) {
        print("DEBUG", message);
    }

    @Override
    public void debug(String format, Object... args) {
        print("DEBUG", format(format, args));
    }

    private void print(String level, String message) {
        String time = LocalTime.now().format(TIME_FMT);
        String thread = Thread.currentThread().getName();
        // Format: [HH:mm:ss] [Plugin/Name - thread/LEVEL]: message
        String line = "[" + time + "] [Plugin/" + pluginName + " - " + thread + "/" + level + "]: " + message;

        PrintStream out = OriginalOutput.get();
        out.println(line);
        out.flush();
    }

    private static String format(String fmt, Object... args) {
        if (args == null || args.length == 0) return fmt;
        StringBuilder sb = new StringBuilder();
        int argIdx = 0;
        int i = 0;
        while (i < fmt.length()) {
            if (i + 1 < fmt.length() && fmt.charAt(i) == '{' && fmt.charAt(i + 1) == '}') {
                sb.append(argIdx < args.length ? args[argIdx++] : "{}");
                i += 2;
            } else {
                sb.append(fmt.charAt(i));
                i++;
            }
        }
        return sb.toString();
    }
}
