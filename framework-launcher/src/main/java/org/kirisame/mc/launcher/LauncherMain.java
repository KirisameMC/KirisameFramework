package org.kirisame.mc.launcher;

/**
 * Entry point for the launcher process.
 */
public class LauncherMain {

    public static void main(String[] args) {
        System.out.println("[Kirisame Launcher] Starting...");
        LauncherConfig config = LauncherConfig.load();
        Launcher launcher = new Launcher(config);
        launcher.run(args);
    }
}
