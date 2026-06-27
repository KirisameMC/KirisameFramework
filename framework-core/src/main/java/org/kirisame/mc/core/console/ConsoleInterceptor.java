package org.kirisame.mc.core.console;

import org.kirisame.mc.core.console.message.ParsedMessage;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Intercepts System.out to parse Minecraft log lines.
 * Lines starting with "!!" are routed to the command system.
 * All output is forwarded to the original System.out unchanged.
 */
public class ConsoleInterceptor {

    // Standard MC log format: [HH:mm:ss] [thread/LEVEL]: message
    private static final Pattern LOG_PATTERN = Pattern.compile(
            "\\[(\\d{2}:\\d{2}:\\d{2})]\\s*\\[([^/]+)/([A-Z]+)]:\\s*(.*)");

    private final Consumer<ParsedMessage> messageHandler;
    private final Consumer<String> commandHandler;
    private final PrintStream originalOut;

    public ConsoleInterceptor(Consumer<ParsedMessage> messageHandler, Consumer<String> commandHandler) {
        this.messageHandler = messageHandler;
        this.commandHandler = commandHandler;
        this.originalOut = System.out;
    }

    /**
     * Installs the interceptor on System.out.
     */
    public void install() {
        // Create a TeeOutputStream that:
        //   1. Forwards all bytes to originalOut (so output is always visible)
        //   2. Buffers chars until newline, then parses the line
        TeeOutputStream teeOut = new TeeOutputStream(originalOut);

        // autoFlush = true: flush after every println() / write(byte[] + newline)
        PrintStream tee = new PrintStream(teeOut, true);
        System.setOut(tee);
    }

    /**
     * Restores the original System.out.
     */
    public void uninstall() {
        System.setOut(originalOut);
    }

    /**
     * TeeOutputStream: forwards all bytes to the original stream, while also
     * buffering characters until a newline is seen, then dispatches the line.
     */
    private class TeeOutputStream extends OutputStream {
        private final OutputStream target;
        private final StringBuilder lineBuffer = new StringBuilder();

        TeeOutputStream(OutputStream target) {
            this.target = target;
        }

        @Override
        public void write(int b) throws IOException {
            // Always forward to the original stream first
            target.write(b);

            // Buffer chars until newline
            if (b == '\n') {
                String line = lineBuffer.toString();
                // Remove trailing \r if present (Windows line endings)
                if (!line.isEmpty() && line.charAt(line.length() - 1) == '\r') {
                    line = line.substring(0, line.length() - 1);
                }
                processLine(line);
                lineBuffer.setLength(0);
            } else {
                lineBuffer.append((char) b);
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            // Always forward the full byte array to the original stream immediately
            target.write(b, off, len);

            // Also buffer for line parsing
            for (int i = off; i < off + len; i++) {
                int c = b[i] & 0xFF;
                if (c == '\n') {
                    String line = lineBuffer.toString();
                    if (!line.isEmpty() && line.charAt(line.length() - 1) == '\r') {
                        line = line.substring(0, line.length() - 1);
                    }
                    processLine(line);
                    lineBuffer.setLength(0);
                } else {
                    lineBuffer.append((char) c);
                }
            }
        }

        @Override
        public void flush() throws IOException {
            // Critical: delegate flush to the target stream so buffered data is actually written
            target.flush();
        }

        @Override
        public void close() throws IOException {
            target.close();
        }
    }

    private void processLine(String line) {
        if (line.isEmpty()) return;

        // Check for framework commands
        if (line.startsWith("!!")) {
            commandHandler.accept(line.substring(2));
            return;
        }

        // Try to parse as a standard MC log line
        Matcher matcher = LOG_PATTERN.matcher(line);
        if (matcher.matches()) {
            ParsedMessage msg = new ParsedMessage(
                    matcher.group(1),
                    matcher.group(2),
                    matcher.group(3),
                    matcher.group(4)
            );
            messageHandler.accept(msg);
        }
    }
}
