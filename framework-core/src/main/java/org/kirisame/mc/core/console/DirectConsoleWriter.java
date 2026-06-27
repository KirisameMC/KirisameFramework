package org.kirisame.mc.core.console;

import org.tinylog.core.LogEntry;
import org.tinylog.core.LogEntryValue;
import org.tinylog.writers.Writer;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Custom tinylog writer that writes directly to the ORIGINAL System.out PrintStream,
 * bypassing any interception. This prevents tinylog from going through the
 * ConsoleInterceptor and avoids circular writes.
 *
 * Configured via tinylog.writer = org.kirisame.mc.core.console.DirectConsoleWriter
 */
public class DirectConsoleWriter implements Writer {

    private final PrintStream target;
    private final boolean colored;

    /**
     * Constructor called by tinylog with properties from the config.
     */
    public DirectConsoleWriter() {
        this(Collections.emptyMap());
    }

    /**
     * Constructor called by tinylog with properties from the config.
     */
    public DirectConsoleWriter(java.util.Map<String, String> properties) {
        // Capture the REAL System.out at class load time (before ConsoleInterceptor)
        // If this writer is instantiated before the interceptor, this is the true console.
        // If after, it's the original captured by the interceptor.
        // Either way, we write to the console directly.
        this.target = System.out;
        this.colored = Boolean.parseBoolean(properties.getOrDefault("colored", "false"));
    }

    @Override
    public Set<LogEntryValue> getRequiredLogEntryValues() {
        return EnumSet.of(LogEntryValue.LEVEL, LogEntryValue.MESSAGE);
    }

    @Override
    public void write(LogEntry logEntry) throws Exception {
        String level = logEntry.getLevel().name();
        String message = logEntry.getMessage();
        if (message == null) message = "";

        // Format: [LEVEL] message
        String line;
        if (colored) {
            line = "[" + level + "] " + message + System.lineSeparator();
        } else {
            line = "[" + level + "] " + message + System.lineSeparator();
        }

        target.print(line);
        target.flush();
    }

    @Override
    public void flush() throws Exception {
        target.flush();
    }

    @Override
    public void close() throws Exception {
        // Don't close System.out
    }
}
