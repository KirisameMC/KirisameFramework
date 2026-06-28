package org.kirisame.mc.core.console;

import org.tinylog.core.LogEntry;
import org.tinylog.core.LogEntryValue;
import org.tinylog.writers.Writer;

import java.io.PrintStream;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Custom tinylog writer that writes directly to the ORIGINAL System.out
 * (bypassing ConsoleInterceptor), with timestamps matching MC server log format.
 *
 * Format: [HH:mm:ss] [ThreadName/LEVEL]: message
 *
 * Configured via tinylog.writer = org.kirisame.mc.core.console.DirectConsoleWriter
 */
public class DirectConsoleWriter implements Writer {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    public DirectConsoleWriter() {
        this(Collections.emptyMap());
    }

    public DirectConsoleWriter(java.util.Map<String, String> properties) {
    }

    @Override
    public Set<LogEntryValue> getRequiredLogEntryValues() {
        return EnumSet.of(LogEntryValue.LEVEL, LogEntryValue.MESSAGE, LogEntryValue.THREAD);
    }

    @Override
    public void write(LogEntry logEntry) throws Exception {
        String time = LocalTime.now().format(TIME_FMT);
        String thread = logEntry.getThread().getName();
        String level = logEntry.getLevel().name();
        String message = logEntry.getMessage();
        if (message == null) message = "";

        // Format matching MC: [HH:mm:ss] [thread/LEVEL]: message
        String line = "[" + time + "] [" + thread + "/" + level + "]: " + message + System.lineSeparator();

        PrintStream out = OriginalOutput.get();
        out.print(line);
        out.flush();
    }

    @Override
    public void flush() throws Exception {
        OriginalOutput.get().flush();
    }

    @Override
    public void close() throws Exception {
    }
}
