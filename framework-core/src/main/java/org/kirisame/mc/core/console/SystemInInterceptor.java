package org.kirisame.mc.core.console;

import java.io.*;
import java.util.function.Consumer;

/**
 * Intercepts System.in to detect "!!" prefixed commands.
 * Lines starting with "!!" are routed to the command handler.
 * All other lines are forwarded to the original stdin (for the MC server to read).
 *
 * Uses a PipedInputStream to replace System.in, with a reader thread
 * that parses each line and routes accordingly.
 */
public class SystemInInterceptor {

    private final Consumer<String> commandHandler;
    private final InputStream originalIn;
    private PipedInputStream pipedIn;
    private PipedOutputStream pipedOut;
    private Thread readerThread;
    private volatile boolean running = true;

    public SystemInInterceptor(Consumer<String> commandHandler) {
        this.commandHandler = commandHandler;
        this.originalIn = System.in;
    }

    /**
     * Installs the interceptor: replaces System.in and starts the reader thread.
     */
    public void install() throws IOException {
        pipedOut = new PipedOutputStream();
        pipedIn = new PipedInputStream(pipedOut, 4096);

        // Replace System.in with our piped stream
        System.setIn(pipedIn);

        // Start a thread that reads from original stdin and routes lines
        readerThread = new Thread(this::readLoop, "kirisame-console-input");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    /**
     * Restores the original System.in and stops the reader thread.
     */
    public void uninstall() {
        running = false;
        System.setIn(originalIn);
        try {
            if (pipedOut != null) pipedOut.close();
        } catch (IOException ignored) {}
        if (readerThread != null) readerThread.interrupt();
    }

    /**
     * Sends a line of text to the MC server's stdin (via the piped stream).
     */
    public void sendToServer(String line) {
        if (pipedOut == null) return;
        try {
            pipedOut.write((line + "\n").getBytes());
            pipedOut.flush();
        } catch (IOException e) {
            System.err.println("[Kirisame] Failed to send to server stdin: " + e.getMessage());
        }
    }

    private void readLoop() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(originalIn))) {
            while (running) {
                String line = reader.readLine();
                if (line == null) break; // stdin closed

                if (line.startsWith("!!")) {
                    // Route to command system
                    String command = line.substring(2).trim();
                    if (!command.isEmpty()) {
                        commandHandler.accept(command);
                    }
                } else {
                    // Forward to MC server via piped stream
                    byte[] bytes = (line + "\n").getBytes();
                    try {
                        pipedOut.write(bytes);
                        pipedOut.flush();
                    } catch (IOException e) {
                        if (running) {
                            // Pipe broken, MC server closed its stdin
                            break;
                        }
                    }
                }
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("[Kirisame] SystemInInterceptor error: " + e.getMessage());
            }
        }
    }
}
