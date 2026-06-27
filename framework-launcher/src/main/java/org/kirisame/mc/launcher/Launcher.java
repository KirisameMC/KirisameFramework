package org.kirisame.mc.launcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Launcher that starts the KirisameFramework as a child process
 * and supports automatic reboot.
 */
public class Launcher {

    private static final String REBOOT_FLAG = "REBOOT_FLAG.flag";
    private final LauncherConfig config;
    private final Path workDir;

    public Launcher(LauncherConfig config) {
        this.config = config;
        this.workDir = Path.of(System.getProperty("user.dir"));
    }

    /**
     * Main launcher loop: start process → wait → check reboot flag → restart or exit.
     */
    public void run(String[] args) {
        int rebootCount = 0;

        do {
            rebootCount++;
            System.out.println("[Kirisame Launcher] Starting KirisameFramework (attempt " + rebootCount + ")...");

            try {
                Process process = startKirisame(args);
                process.waitFor();

                int exitCode = process.exitValue();
                System.out.println("[Kirisame Launcher] Process exited with code: " + exitCode);

            } catch (Exception e) {
                System.err.println("[Kirisame Launcher] Error starting process: " + e.getMessage());
                return;
            }

            // Check for reboot flag
            if (shouldReboot()) {
                System.out.println("[Kirisame Launcher] Reboot flag detected, restarting...");
                deleteRebootFlag();
            } else {
                System.out.println("[Kirisame Launcher] No reboot flag, shutting down launcher.");
                break;
            }

        } while (true);

        System.out.println("[Kirisame Launcher] Launcher exited.");
    }

    private Process startKirisame(String[] args) throws IOException {
        String javaExe = config.javaHome() + "/bin/java";

        List<String> command = new ArrayList<>();
        command.add(javaExe);

        // Agent JAR
        Path agentPath = workDir.resolve(config.agentJar());
        if (Files.exists(agentPath)) {
            command.add("-javaagent:" + agentPath.toAbsolutePath());
        } else {
            System.err.println("[Kirisame Launcher] WARNING: Agent JAR not found: " + agentPath);
        }

        // User JVM args
        command.addAll(config.jvmArgs());

        // Core JAR
        Path corePath = workDir.resolve(config.kirisameJar());
        command.add("-jar");
        command.add(corePath.toAbsolutePath().toString());

        // Game args
        command.addAll(config.gameArgs());

        System.out.println("[Kirisame Launcher] Command: " + String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir.toFile());
        pb.inheritIO();
        return pb.start();
    }

    private boolean shouldReboot() {
        return Files.exists(workDir.resolve(REBOOT_FLAG));
    }

    private void deleteRebootFlag() {
        try {
            Files.deleteIfExists(workDir.resolve(REBOOT_FLAG));
        } catch (IOException e) {
            System.err.println("[Kirisame Launcher] Failed to delete reboot flag: " + e.getMessage());
        }
    }
}
